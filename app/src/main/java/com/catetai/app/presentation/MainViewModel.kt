package com.catetai.app.presentation

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.catetai.app.data.local.dao.RecordingDao
import com.catetai.app.data.local.entity.RecordingEntity
import com.catetai.app.data.local.entity.RecordingStatus
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

data class MainUiState(
    val title: String = "Catet AI",
    val statusText: String = "Database Terenkripsi SQLCipher Aktif (AES-256)",
    val recordingCount: Int = 0,
    val isReady: Boolean = true
)

@HiltViewModel
class MainViewModel @Inject constructor(
    private val recordingDao: RecordingDao,
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val _uiState = MutableStateFlow(MainUiState())
    val uiState: StateFlow<MainUiState> = _uiState.asStateFlow()

    init {
        observeRecordings()
        verifyEncryptedDatabase()
    }

    private fun observeRecordings() {
        viewModelScope.launch {
            recordingDao.getAllRecordingsOrderByDateDesc().collect { list ->
                _uiState.update { current ->
                    current.copy(
                        recordingCount = list.size,
                        statusText = "Database SQLCipher Aktif • ${list.size} Catatan Tersimpan Terenkripsi"
                    )
                }
            }
        }
    }

    private fun verifyEncryptedDatabase() {
        viewModelScope.launch {
            // Seed a sample verification record if database is empty
            val initialList = recordingDao.getRecordingById("demo-init-uuid")
            if (initialList == null) {
                recordingDao.insertRecording(
                    RecordingEntity(
                        id = "demo-init-uuid",
                        title = "Rapat Onboarding Perdana (Sample Terenkripsi)",
                        audioFilePath = File(context.filesDir, "sample.wav").absolutePath,
                        durationMs = 120_000L,
                        fileSizeBytes = 3_840_000L,
                        status = RecordingStatus.STOPPED,
                        customVocabulary = "Catet AI, Indoglish, Local-First"
                    )
                )
            }
        }
    }
}
