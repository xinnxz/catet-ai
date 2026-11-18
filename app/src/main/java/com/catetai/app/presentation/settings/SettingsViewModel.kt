package com.catetai.app.presentation.settings

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.catetai.app.domain.model.ModelInfo
import com.catetai.app.domain.repository.ModelRepository
import com.catetai.app.domain.usecase.DownloadModelUseCase
import com.catetai.app.domain.usecase.GetModelStatusUseCase
import com.catetai.app.presentation.home.util.RecordingFormatters
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

/**
 * ViewModel untuk Layar Pengaturan (SettingsScreen) dengan pola MVI.
 *
 * Mengelola:
 * 1. Status berkas model AI Whisper Large v3 Turbo (~574MB) — cek ketersediaan, unduh, dan hapus.
 * 2. Kosakata kustom global (SharedPreferences).
 * 3. Pembersihan cache sementara pemutaran & ekspor.
 */
@HiltViewModel
class SettingsViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val modelRepository: ModelRepository,
    private val getModelStatusUseCase: GetModelStatusUseCase,
    private val downloadModelUseCase: DownloadModelUseCase
) : ViewModel() {

    private val prefs = context.getSharedPreferences("catet_ai_settings", Context.MODE_PRIVATE)

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    private val _effects = Channel<SettingsEffect>(Channel.BUFFERED)
    val effects: Flow<SettingsEffect> = _effects.receiveAsFlow()

    private var downloadJob: Job? = null

    init {
        processIntent(SettingsIntent.LoadSettings)
    }

    fun processIntent(intent: SettingsIntent) {
        when (intent) {
            is SettingsIntent.LoadSettings -> loadSettings()
            is SettingsIntent.StartModelDownload -> startModelDownload()
            is SettingsIntent.OpenDeleteModelDialog -> _uiState.update { it.copy(showDeleteModelDialog = true) }
            is SettingsIntent.DismissDeleteModelDialog -> _uiState.update { it.copy(showDeleteModelDialog = false) }
            is SettingsIntent.ConfirmDeleteModel -> deleteModel()
            is SettingsIntent.UpdateGlobalVocabulary -> _uiState.update { it.copy(globalVocabulary = intent.input) }
            is SettingsIntent.SaveGlobalVocabulary -> saveGlobalVocabulary()
            is SettingsIntent.ClearPlaybackCache -> clearCache()
        }
    }

    private fun loadSettings() {
        val isAvailable = getModelStatusUseCase.isModelAvailable()
        val modelFile = getModelStatusUseCase.getModelFile()
        val sizeFormatted = if (isAvailable && modelFile != null && modelFile.exists()) {
            RecordingFormatters.formatFileSize(modelFile.length())
        } else {
            "~574 MB (Belum Diunduh)"
        }

        val globalVocab = prefs.getString(KEY_GLOBAL_VOCABULARY, "").orEmpty()
        val cacheSize = calculateCacheSize()

        _uiState.update {
            it.copy(
                isModelAvailable = isAvailable,
                modelSizeBytesFormatted = sizeFormatted,
                globalVocabulary = globalVocab,
                cacheSizeFormatted = RecordingFormatters.formatFileSize(cacheSize)
            )
        }
    }

    private fun startModelDownload() {
        if (_uiState.value.isModelDownloading) return

        _uiState.update {
            it.copy(
                isModelDownloading = true,
                modelDownloadProgress = 0f
            )
        }

        downloadJob?.cancel()
        downloadJob = viewModelScope.launch {
            downloadModelUseCase(
                onProgress = { _, _, progress ->
                    _uiState.update { it.copy(modelDownloadProgress = progress) }
                }
            ).onSuccess {
                _uiState.update {
                    it.copy(
                        isModelDownloading = false,
                        modelDownloadProgress = 1f,
                        isModelAvailable = true,
                        modelSizeBytesFormatted = RecordingFormatters.formatFileSize(
                            getModelStatusUseCase.getModelFile()?.length() ?: ModelInfo.DEFAULT_MODEL_SIZE_BYTES
                        )
                    )
                }
                _effects.send(SettingsEffect.ShowSnackbar("Model Whisper Turbo berhasil diunduh dan siap digunakan!"))
            }.onFailure { error ->
                _uiState.update { it.copy(isModelDownloading = false, modelDownloadProgress = 0f) }
                _effects.send(
                    SettingsEffect.ShowSnackbar(
                        "Gagal mengunduh model: ${error.localizedMessage ?: "Periksa koneksi internet"}"
                    )
                )
            }
        }
    }

    private fun deleteModel() {
        val modelFile = getModelStatusUseCase.getModelFile()
        if (modelFile != null && modelFile.exists()) {
            val deleted = modelFile.delete()
            if (deleted) {
                _uiState.update {
                    it.copy(
                        showDeleteModelDialog = false,
                        isModelAvailable = false,
                        modelSizeBytesFormatted = "~574 MB (Belum Diunduh)"
                    )
                }
                viewModelScope.launch {
                    _effects.send(SettingsEffect.ShowSnackbar("Model Whisper telah dihapus dari penyimpanan internal."))
                }
            } else {
                viewModelScope.launch {
                    _effects.send(SettingsEffect.ShowSnackbar("Gagal menghapus berkas model."))
                }
            }
        } else {
            _uiState.update { it.copy(showDeleteModelDialog = false) }
        }
    }

    private fun saveGlobalVocabulary() {
        val vocab = _uiState.value.globalVocabulary.trim()
        prefs.edit().putString(KEY_GLOBAL_VOCABULARY, vocab).apply()
        viewModelScope.launch {
            _effects.send(SettingsEffect.ShowSnackbar("Kosakata kustom global berhasil disimpan."))
        }
    }

    private fun clearCache() {
        val cachePlaybackDir = File(context.cacheDir, "playback")
        val cacheExportsDir = File(context.cacheDir, "exports")
        cachePlaybackDir.deleteRecursively()
        cacheExportsDir.deleteRecursively()

        val newCacheSize = calculateCacheSize()
        _uiState.update {
            it.copy(cacheSizeFormatted = RecordingFormatters.formatFileSize(newCacheSize))
        }
        viewModelScope.launch {
            _effects.send(SettingsEffect.ShowSnackbar("Cache audio dan dokumen sementara berhasil dibersihkan."))
        }
    }

    private fun calculateCacheSize(): Long {
        var total = 0L
        fun addDir(file: File) {
            if (file.isDirectory) {
                file.listFiles()?.forEach { addDir(it) }
            } else {
                total += file.length()
            }
        }
        addDir(context.cacheDir)
        return total
    }

    companion object {
        const val KEY_GLOBAL_VOCABULARY = "global_custom_vocabulary"
    }
}
