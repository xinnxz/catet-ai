package com.catetai.app.presentation.home

import com.catetai.app.domain.model.Recording

/**
 * State antarmuka untuk Layar Utama (HomeScreen).
 *
 * Mengikuti arsitektur MVI (Model-View-Intent) unidirectional data flow:
 * [HomeUiState] bersifat immutable dan hanya diubah oleh [HomeViewModel].
 */
data class HomeUiState(
    val recordings: List<Recording> = emptyList(),
    val isLoading: Boolean = false,
    val errorMessage: String? = null
)

/**
 * Intent (aksi / niat pengguna atau event UI) yang dikirim ke [HomeViewModel].
 */
sealed interface HomeIntent {
    /** Mengambil dan mengamati daftar rekaman dari repositori. */
    data object LoadRecordings : HomeIntent

    /** Menghapus rekaman audio beserta file WAV terkait berdasarkan ID. */
    data class DeleteRecording(val recordingId: String) : HomeIntent

    /** Membuka halaman detail sesi rapat. */
    data class NavigateToDetail(val recordingId: String) : HomeIntent
}

/**
 * Efek samping satu kali (one-shot effect) seperti navigasi atau pesan notifikasi.
 */
sealed interface HomeEffect {
    data class NavigateToDetail(val recordingId: String) : HomeEffect
    data class ShowSnackbar(val message: String) : HomeEffect
}
