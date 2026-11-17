package com.catetai.app.domain.model

/**
 * Status siklus hidup rekaman audio di domain layer Catet AI.
 */
enum class RecordingStatus {
    /** Audio sedang direkam aktif oleh service latar belakang. */
    RECORDING,

    /** Rekaman dihentikan secara normal dan file WAV telah difinalisasi. */
    STOPPED,

    /** Rekaman terputus mendadak oleh sistem (LMK/daya habis), header berhasil dipulihkan. */
    TRUNCATED,

    /** Transkripsi AI atau proses inferensi sedang berjalan di latar belakang. */
    PROCESSING,

    /** Transkripsi selesai menghasilkan teks ber-timestamp. */
    TRANSCRIBED,

    /** Notulensi rapat (ringkasan, keputusan, action items) berhasil dibuat. */
    SUMMARIZED,

    /** Terjadi kegagalan pada proses perekaman atau inferensi AI. */
    ERROR
}

/**
 * Entitas domain yang merepresentasikan satu sesi rapat di Catet AI.
 *
 * Bersifat murni Kotlin (Plain Old Kotlin Object), bebas dari anotasi Room atau dependensi Android.
 */
data class Recording(
    val id: String,
    val title: String,
    val audioFilePath: String,
    val durationMs: Long = 0L,
    val fileSizeBytes: Long = 0L,
    val sampleRate: Int = 16_000,
    val status: RecordingStatus = RecordingStatus.STOPPED,
    val wasSilencedBySystem: Boolean = false,
    val customVocabulary: String? = null,
    val processingEngine: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)
