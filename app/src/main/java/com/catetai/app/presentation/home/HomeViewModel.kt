package com.catetai.app.presentation.home

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.catetai.app.domain.repository.RecordingRepository
import com.catetai.app.domain.usecase.ImportAudioUseCase
import com.catetai.app.domain.usecase.SearchTranscriptUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
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
 * ViewModel untuk Layar Utama (HomeScreen) dengan pola MVI.
 *
 * Mengamati [RecordingRepository.getAllRecordings] yang otomatis mengalirkan data
 * terbaru secara reaktif dari Room Database terenkripsi SQLCipher.
 * Mengoordinasikan alur impor audio via [ImportAudioUseCase].
 * Mengoordinasikan pencarian teks transkrip via [SearchTranscriptUseCase] (F4-03).
 */
@HiltViewModel
class HomeViewModel @Inject constructor(
    private val recordingRepository: RecordingRepository,
    private val importAudioUseCase: ImportAudioUseCase,
    private val searchTranscriptUseCase: SearchTranscriptUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow(HomeUiState(isLoading = true))
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    private val _effects = Channel<HomeEffect>(Channel.BUFFERED)
    val effects: Flow<HomeEffect> = _effects.receiveAsFlow()

    private var searchJob: Job? = null

    init {
        processIntent(HomeIntent.LoadRecordings)
    }

    /**
     * Titik masuk tunggal untuk memproses setiap aksi pengguna dari UI (HomeScreen).
     */
    fun processIntent(intent: HomeIntent) {
        when (intent) {
            is HomeIntent.LoadRecordings -> observeRecordings()
            is HomeIntent.ImportAudio -> importAudio(intent.uri, intent.customTitle)
            is HomeIntent.DeleteRecording -> deleteRecording(intent.recordingId)
            is HomeIntent.NavigateToDetail -> {
                viewModelScope.launch {
                    _effects.send(HomeEffect.NavigateToDetail(intent.recordingId))
                }
            }
            is HomeIntent.UpdateSearchQuery -> onSearchQueryChanged(intent.query)
            is HomeIntent.ClearSearch -> clearSearch()
        }
    }

    private fun onSearchQueryChanged(query: String) {
        _uiState.update { it.copy(searchQuery = query) }
        searchJob?.cancel()

        if (query.isBlank()) {
            _uiState.update { it.copy(isSearching = false, searchResults = emptyList()) }
            return
        }

        searchJob = viewModelScope.launch {
            delay(250L) // Debounce 250ms agar tidak query tiap ketikan kilat
            _uiState.update { it.copy(isSearching = true) }
            searchTranscriptUseCase(query)
                .onSuccess { results ->
                    _uiState.update {
                        it.copy(
                            isSearching = false,
                            searchResults = results
                        )
                    }
                }
                .onFailure {
                    _uiState.update { it.copy(isSearching = false, searchResults = emptyList()) }
                }
        }
    }

    private fun clearSearch() {
        searchJob?.cancel()
        _uiState.update {
            it.copy(
                searchQuery = "",
                isSearching = false,
                searchResults = emptyList()
            )
        }
    }

    private fun observeRecordings() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            recordingRepository.getAllRecordings()
                .catch { error ->
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            errorMessage = error.localizedMessage ?: "Gagal memuat rekaman"
                        )
                    }
                }
                .collect { list ->
                    _uiState.update {
                        it.copy(
                            recordings = list,
                            isLoading = false,
                            errorMessage = null
                        )
                    }
                }
        }
    }

    private fun importAudio(uri: Uri, customTitle: String?) {
        viewModelScope.launch {
            _uiState.update { it.copy(isImporting = true, importProgress = 0f) }
            importAudioUseCase(
                uri = uri,
                customTitle = customTitle,
                onProgress = { progress ->
                    _uiState.update { it.copy(importProgress = progress) }
                }
            )
                .onSuccess { recording ->
                    _uiState.update { it.copy(isImporting = false, importProgress = 1f) }
                    _effects.send(HomeEffect.ShowSnackbar("Berhasil mengimpor: ${recording.title}"))
                    _effects.send(HomeEffect.NavigateToDetail(recording.id))
                }
                .onFailure { error ->
                    _uiState.update { it.copy(isImporting = false, importProgress = 0f) }
                    _effects.send(
                        HomeEffect.ShowSnackbar(
                            error.localizedMessage ?: "Gagal mengimpor berkas audio."
                        )
                    )
                }
        }
    }

    private fun deleteRecording(recordingId: String) {
        viewModelScope.launch {
            recordingRepository.deleteRecording(recordingId)
                .onFailure { error ->
                    _effects.send(
                        HomeEffect.ShowSnackbar(
                            error.localizedMessage ?: "Gagal menghapus rekaman."
                        )
                    )
                }
        }
    }
}

