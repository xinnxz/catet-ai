package com.catetai.app.presentation.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.catetai.app.domain.repository.RecordingRepository
import dagger.hilt.android.lifecycle.HiltViewModel
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
 * ViewModel untuk Layar Utama (HomeScreen) dengan pola MVI.
 *
 * Mengamati [RecordingRepository.getAllRecordings] yang otomatis mengalirkan data
 * terbaru secara reaktif dari Room Database terenkripsi SQLCipher.
 */
@HiltViewModel
class HomeViewModel @Inject constructor(
    private val recordingRepository: RecordingRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(HomeUiState(isLoading = true))
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    private val _effects = Channel<HomeEffect>(Channel.BUFFERED)
    val effects: Flow<HomeEffect> = _effects.receiveAsFlow()

    init {
        processIntent(HomeIntent.LoadRecordings)
    }

    /**
     * Titik masuk tunggal untuk memproses setiap aksi pengguna dari UI (HomeScreen).
     */
    fun processIntent(intent: HomeIntent) {
        when (intent) {
            is HomeIntent.LoadRecordings -> observeRecordings()
            is HomeIntent.DeleteRecording -> deleteRecording(intent.recordingId)
            is HomeIntent.NavigateToDetail -> {
                viewModelScope.launch {
                    _effects.send(HomeEffect.NavigateToDetail(intent.recordingId))
                }
            }
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
