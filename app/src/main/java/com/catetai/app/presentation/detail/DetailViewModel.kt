package com.catetai.app.presentation.detail

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.catetai.app.domain.model.ExportFormat
import com.catetai.app.domain.model.RecordingStatus
import com.catetai.app.domain.repository.RecordingRepository
import com.catetai.app.domain.repository.TranscriptRepository
import com.catetai.app.domain.usecase.DownloadModelUseCase
import com.catetai.app.domain.usecase.ExportDocumentUseCase
import com.catetai.app.domain.usecase.GetModelStatusUseCase
import com.catetai.app.domain.usecase.GetSummaryUseCase
import com.catetai.app.domain.usecase.SummarizeTranscriptUseCase
import com.catetai.app.domain.usecase.TranscribeAudioUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * ViewModel untuk Layar Detail Sesi Rapat dengan pola MVI.
 *
 * Mengoordinasikan:
 * 1. Pengamatan reaktif entitas rekaman ([RecordingRepository]), segmen transkrip ([TranscriptRepository]),
 *    dan notulensi ringkasan rapat ([GetSummaryUseCase]).
 * 2. Eksekusi transkripsi audio ([TranscribeAudioUseCase]) dengan pemantauan progres real-time.
 * 3. Eksekusi perangkuman otomatis transkrip ([SummarizeTranscriptUseCase]).
 * 4. Pengunduhan otomatis model AI ([DownloadModelUseCase]) jika model belum terpasang.
 * 5. Pengaturan kosakata kustom (prompt biasing / F2-07).
 */
@HiltViewModel
class DetailViewModel @Inject constructor(
    private val savedStateHandle: SavedStateHandle,
    private val recordingRepository: RecordingRepository,
    private val transcriptRepository: TranscriptRepository,
    private val transcribeAudioUseCase: TranscribeAudioUseCase,
    private val getModelStatusUseCase: GetModelStatusUseCase,
    private val downloadModelUseCase: DownloadModelUseCase,
    private val getSummaryUseCase: GetSummaryUseCase,
    private val summarizeTranscriptUseCase: SummarizeTranscriptUseCase,
    private val exportDocumentUseCase: ExportDocumentUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow(DetailUiState())
    val uiState: StateFlow<DetailUiState> = _uiState.asStateFlow()

    private val _effects = Channel<DetailEffect>(Channel.BUFFERED)
    val effects: Flow<DetailEffect> = _effects.receiveAsFlow()

    private var transcriptionJob: Job? = null
    private var modelDownloadJob: Job? = null
    private var summaryJob: Job? = null

    init {
        val recordingId: String? = savedStateHandle["recordingId"]
        if (!recordingId.isNullOrBlank()) {
            processIntent(DetailIntent.LoadRecording(recordingId))
        }
    }

    fun processIntent(intent: DetailIntent) {
        when (intent) {
            is DetailIntent.LoadRecording -> loadRecording(intent.recordingId)
            is DetailIntent.StartTranscription,
            is DetailIntent.RetryTranscription -> checkModelAndStartTranscription()
            is DetailIntent.CancelTranscription -> cancelTranscription()

            is DetailIntent.GenerateSummary -> generateSummary()

            is DetailIntent.OpenExportDialog -> _uiState.update { it.copy(showExportDialog = true) }
            is DetailIntent.DismissExportDialog -> _uiState.update { it.copy(showExportDialog = false) }
            is DetailIntent.ExportDocument -> exportDocument(intent.format)

            is DetailIntent.OpenVocabularyDialog -> openVocabularyDialog()
            is DetailIntent.DismissVocabularyDialog -> _uiState.update { it.copy(showVocabularyDialog = false) }
            is DetailIntent.UpdateVocabularyInput -> _uiState.update { it.copy(vocabularyInput = intent.input) }
            is DetailIntent.SaveVocabulary -> saveVocabulary()

            is DetailIntent.ConfirmModelDownload -> startModelDownloadAndTranscribe()
            is DetailIntent.DismissModelDownloadDialog -> _uiState.update { it.copy(showModelDownloadDialog = false) }

            is DetailIntent.SelectTab -> _uiState.update { it.copy(selectedTab = intent.tab) }
        }
    }

    private fun loadRecording(recordingId: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }

            // 1. Ambil entitas rekaman sekali jalan atau amati perubahan
            val recording = recordingRepository.getRecordingById(recordingId)
            if (recording == null) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        errorMessage = "Rekaman dengan ID '$recordingId' tidak ditemukan."
                    )
                }
                return@launch
            }

            _uiState.update {
                it.copy(
                    recording = recording,
                    isLoading = false,
                    vocabularyInput = recording.customVocabulary.orEmpty()
                )
            }

            // 2. Amati aliran reaktif segmen transkrip (mengalir secara langsung saat transkripsi berlangsung)
            launch {
                transcriptRepository.getSegmentsByRecordingId(recordingId)
                    .catch { error ->
                        _effects.send(DetailEffect.ShowSnackbar("Gagal memuat transkrip: ${error.localizedMessage}"))
                    }
                    .collect { segmentList ->
                        _uiState.update { it.copy(segments = segmentList) }
                    }
            }

            // 3. Amati aliran reaktif notulensi ringkasan rapat (Fase 3)
            launch {
                getSummaryUseCase(recordingId)
                    .catch { error ->
                        _effects.send(DetailEffect.ShowSnackbar("Gagal memuat notulensi: ${error.localizedMessage}"))
                    }
                    .collect { summary ->
                        _uiState.update { it.copy(summary = summary) }
                    }
            }
        }
    }

    private fun checkModelAndStartTranscription() {
        val recording = _uiState.value.recording ?: return

        // Periksa apakah model Whisper sudah terunduh di disk
        if (!getModelStatusUseCase.isModelAvailable()) {
            _uiState.update { it.copy(showModelDownloadDialog = true) }
        } else {
            executeTranscription(recording.id)
        }
    }

    private fun startModelDownloadAndTranscribe() {
        val recording = _uiState.value.recording ?: return

        _uiState.update {
            it.copy(
                showModelDownloadDialog = false,
                isModelDownloading = true,
                modelDownloadProgress = 0f
            )
        }

        modelDownloadJob?.cancel()
        modelDownloadJob = viewModelScope.launch {
            downloadModelUseCase(
                onProgress = { _, _, progress ->
                    _uiState.update { it.copy(modelDownloadProgress = progress) }
                }
            ).onSuccess {
                _uiState.update { it.copy(isModelDownloading = false, modelDownloadProgress = 1f) }
                _effects.send(DetailEffect.ShowSnackbar("Model Whisper berhasil diunduh. Memulai transkripsi..."))
                executeTranscription(recording.id)
            }.onFailure { error ->
                _uiState.update { it.copy(isModelDownloading = false, modelDownloadProgress = 0f) }
                _effects.send(
                    DetailEffect.ShowSnackbar(
                        "Gagal mengunduh model: ${error.localizedMessage ?: "Terjadi kesalahan koneksi"}"
                    )
                )
            }
        }
    }

    private fun executeTranscription(recordingId: String) {
        val modelFile = getModelStatusUseCase.getModelFile()

        transcriptionJob?.cancel()
        transcriptionJob = viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isTranscribing = true,
                    transcriptionProgress = 0f,
                    selectedTab = DetailTab.TRANSCRIPT // Pindah otomatis ke tab transkrip untuk melihat progres teks
                )
            }

            transcribeAudioUseCase(
                recordingId = recordingId,
                modelFile = modelFile,
                onProgress = { progress ->
                    _uiState.update { it.copy(transcriptionProgress = progress) }
                }
            ).onSuccess { segments ->
                _uiState.update {
                    it.copy(
                        isTranscribing = false,
                        transcriptionProgress = 1f,
                        recording = it.recording?.copy(status = RecordingStatus.COMPLETED)
                    )
                }
                _effects.send(DetailEffect.ShowSnackbar("Transkripsi selesai! Dihasilkan ${segments.size} segmen teks. Menghasilkan notulensi..."))
                generateSummary(recordingId)
            }.onFailure { error ->
                _uiState.update {
                    it.copy(
                        isTranscribing = false,
                        transcriptionProgress = 0f,
                        recording = it.recording?.copy(status = RecordingStatus.FAILED)
                    )
                }
                _effects.send(
                    DetailEffect.ShowSnackbar(
                        "Transkripsi gagal: ${error.localizedMessage ?: "Terjadi kesalahan"}"
                    )
                )
            }
        }
    }

    private fun generateSummary(recordingId: String? = _uiState.value.recording?.id) {
        val targetId = recordingId ?: return

        summaryJob?.cancel()
        summaryJob = viewModelScope.launch {
            _uiState.update { it.copy(isSummarizing = true) }
            summarizeTranscriptUseCase(targetId)
                .onSuccess { summary ->
                    _uiState.update {
                        it.copy(
                            isSummarizing = false,
                            summary = summary,
                            recording = it.recording?.copy(status = RecordingStatus.COMPLETED)
                        )
                    }
                    _effects.send(DetailEffect.ShowSnackbar("Notulensi rapat cerdas berhasil dibuat!"))
                }
                .onFailure { error ->
                    _uiState.update { it.copy(isSummarizing = false) }
                    _effects.send(
                        DetailEffect.ShowSnackbar(
                            "Gagal membuat notulensi: ${error.localizedMessage ?: "Terjadi kesalahan"}"
                        )
                    )
                }
        }
    }

    private fun exportDocument(format: ExportFormat) {
        val recording = _uiState.value.recording ?: return

        viewModelScope.launch {
            _uiState.update { it.copy(isExporting = true, showExportDialog = false) }
            exportDocumentUseCase(recording.id, format)
                .onSuccess { file ->
                    _uiState.update { it.copy(isExporting = false) }
                    _effects.send(
                        DetailEffect.ShareExportedDocument(
                            file = file,
                            format = format,
                            title = recording.title
                        )
                    )
                }
                .onFailure { error ->
                    _uiState.update { it.copy(isExporting = false) }
                    _effects.send(
                        DetailEffect.ShowSnackbar(
                            "Gagal mengekspor dokumen: ${error.localizedMessage ?: "Terjadi kesalahan"}"
                        )
                    )
                }
        }
    }

    private fun cancelTranscription() {
        transcriptionJob?.cancel()
        transcribeAudioUseCase.cancel()
        _uiState.update {
            it.copy(
                isTranscribing = false,
                transcriptionProgress = 0f,
                recording = it.recording?.copy(status = RecordingStatus.INTERRUPTED)
            )
        }
        viewModelScope.launch {
            _effects.send(DetailEffect.ShowSnackbar("Transkripsi telah dibatalkan."))
        }
    }

    private fun openVocabularyDialog() {
        val currentVocab = _uiState.value.recording?.customVocabulary.orEmpty()
        _uiState.update {
            it.copy(
                showVocabularyDialog = true,
                vocabularyInput = currentVocab
            )
        }
    }

    private fun saveVocabulary() {
        val recording = _uiState.value.recording ?: return
        val newVocab = _uiState.value.vocabularyInput.trim()

        viewModelScope.launch {
            recordingRepository.updateCustomVocabulary(recording.id, newVocab)
                .onSuccess {
                    _uiState.update {
                        it.copy(
                            showVocabularyDialog = false,
                            recording = it.recording?.copy(customVocabulary = newVocab.ifBlank { null })
                        )
                    }
                    _effects.send(DetailEffect.ShowSnackbar("Kosakata kustom berhasil disimpan."))
                }
                .onFailure { error ->
                    _effects.send(
                        DetailEffect.ShowSnackbar(
                            "Gagal menyimpan kosakata: ${error.localizedMessage}"
                        )
                    )
                }
        }
    }
}
