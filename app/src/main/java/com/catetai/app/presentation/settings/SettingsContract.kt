package com.catetai.app.presentation.settings

/**
 * State antarmuka untuk Layar Pengaturan (SettingsScreen).
 *
 * Mengikuti filosofi "Zero Configuration":
 * Pengaturan bahasa dan prompt biasing diatur secara otomatis di backend (Bahasa Indonesia & kosakata rapat),
 * sehingga state UI tetap bersih dan fokus pada manajemen perangkat keras & penyimpanan.
 */
data class SettingsUiState(
    val isModelAvailable: Boolean = false,
    val modelFileName: String = "ggml-large-v3-turbo-q5_0.bin",
    val modelSizeBytesFormatted: String = "~574 MB",
    val isModelDownloading: Boolean = false,
    val modelDownloadProgress: Float = 0f,
    val cacheSizeFormatted: String = "0 MB",
    val appVersion: String = "1.0.0",
    val showDeleteModelDialog: Boolean = false
)

/**
 * Aksi / Niat pengguna di Layar Pengaturan.
 */
sealed interface SettingsIntent {
    data object LoadSettings : SettingsIntent
    data object StartModelDownload : SettingsIntent
    data object OpenDeleteModelDialog : SettingsIntent
    data object DismissDeleteModelDialog : SettingsIntent
    data object ConfirmDeleteModel : SettingsIntent
    data object ClearPlaybackCache : SettingsIntent
}

/**
 * Efek samping satu kali untuk notifikasi UI.
 */
sealed interface SettingsEffect {
    data class ShowSnackbar(val message: String) : SettingsEffect
}
