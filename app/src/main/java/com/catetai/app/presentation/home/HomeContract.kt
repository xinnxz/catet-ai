package com.catetai.app.presentation.home

import android.net.Uri
import com.catetai.app.domain.model.Recording
import com.catetai.app.domain.model.SearchResultItem

/**
 * State antarmuka untuk Layar Utama (HomeScreen).
 *
 * Mengikuti arsitektur MVI (Model-View-Intent) unidirectional data flow:
 * [HomeUiState] bersifat immutable dan hanya diubah oleh [HomeViewModel].
 */
data class HomeUiState(
    val recordings: List<Recording> = emptyList(),
    val isLoading: Boolean = false,
    val isImporting: Boolean = false,
    val importProgress: Float = 0f,
    val errorMessage: String? = null,
    // ── F4-03: State Pencarian Teks Transkrip (FTS4) ──
    val searchQuery: String = "",
    val isSearching: Boolean = false,
    val searchResults: List<SearchResultItem> = emptyList()
)

/**
 * Intent (aksi / niat pengguna atau event UI) yang dikirim ke [HomeViewModel].
 */
sealed interface HomeIntent {
    /** Mengambil dan mengamati daftar rekaman dari repositori. */
    data object LoadRecordings : HomeIntent

    /** Mengimpor berkas rekaman audio dari URI pemilih berkas (SAF) atau Share Intent. */
    data class ImportAudio(val uri: Uri, val customTitle: String? = null) : HomeIntent

    /** Menghapus rekaman audio beserta file WAV/audio terkait berdasarkan ID. */
    data class DeleteRecording(val recordingId: String) : HomeIntent

    /** Membuka halaman detail sesi rapat. */
    data class NavigateToDetail(val recordingId: String) : HomeIntent

    /** Memperbarui query pencarian transkrip (F4-03). */
    data class UpdateSearchQuery(val query: String) : HomeIntent

    /** Mengosongkan kolom pencarian. */
    data object ClearSearch : HomeIntent
}

/**
 * Efek samping satu kali (one-shot effect) seperti navigasi atau pesan notifikasi.
 */
sealed interface HomeEffect {
    data class NavigateToDetail(val recordingId: String) : HomeEffect
    data class ShowSnackbar(val message: String) : HomeEffect
}

