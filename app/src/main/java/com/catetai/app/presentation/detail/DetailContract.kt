package com.catetai.app.presentation.detail

import com.catetai.app.domain.model.ExportFormat
import com.catetai.app.domain.model.Recording
import com.catetai.app.domain.model.Summary
import com.catetai.app.domain.model.TranscriptSegment
import java.io.File

/**
 * Pilihan tab tampilan di Layar Detail sesi rekaman.
 */
enum class DetailTab {
    /**
     * Teks asli murni hasil ucapan suara pembicara (verbatim) lengkap dengan penanda waktu.
     */
    TRANSCRIPT,

    /**
     * Ringkasan keputusan dan tindak lanjut rapat (Fase 3).
     */
    SUMMARY
}

/**
 * State antarmuka (UI State) untuk Layar Detail sesi rekaman.
 */
data class DetailUiState(
    val recording: Recording? = null,
    val segments: List<TranscriptSegment> = emptyList(),
    val summary: Summary? = null,
    val isLoading: Boolean = true,
    val isTranscribing: Boolean = false,
    val transcriptionProgress: Float = 0f,
    val isModelDownloading: Boolean = false,
    val modelDownloadProgress: Float = 0f,
    val isSummarizing: Boolean = false,
    val isExporting: Boolean = false,
    val showVocabularyDialog: Boolean = false,
    val showModelDownloadDialog: Boolean = false,
    val showExportDialog: Boolean = false,
    val vocabularyInput: String = "",
    val selectedTab: DetailTab = DetailTab.TRANSCRIPT,
    val errorMessage: String? = null
)

/**
 * Niat (Intent / Aksi pengguna) yang diproses oleh [DetailViewModel].
 */
sealed interface DetailIntent {
    data class LoadRecording(val recordingId: String) : DetailIntent
    data object StartTranscription : DetailIntent
    data object CancelTranscription : DetailIntent
    data object RetryTranscription : DetailIntent

    data object GenerateSummary : DetailIntent

    data object OpenExportDialog : DetailIntent
    data object DismissExportDialog : DetailIntent
    data class ExportDocument(val format: ExportFormat) : DetailIntent

    data object OpenVocabularyDialog : DetailIntent
    data object DismissVocabularyDialog : DetailIntent
    data class UpdateVocabularyInput(val input: String) : DetailIntent
    data object SaveVocabulary : DetailIntent

    data object ConfirmModelDownload : DetailIntent
    data object DismissModelDownloadDialog : DetailIntent

    data class SelectTab(val tab: DetailTab) : DetailIntent
}

/**
 * Efek samping satu kali (one-shot effect) untuk navigasi atau notifikasi pengguna.
 */
sealed interface DetailEffect {
    data class ShowSnackbar(val message: String) : DetailEffect
    data object NavigateBack : DetailEffect
    data class ShareExportedDocument(val file: File, val format: ExportFormat, val title: String) : DetailEffect
}
