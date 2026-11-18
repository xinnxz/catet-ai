package com.catetai.app.domain.model

import java.io.File

/**
 * Informasi metadata model kecerdasan buatan (AI) yang digunakan untuk transkripsi lokal.
 *
 * @param id Identifikasi unik model.
 * @param name Nama tampilan model (misal: "Whisper Large v3 Turbo (Q5_0)").
 * @param fileName Nama berkas model saat disimpan di direktori internal (`filesDir/models/`).
 * @param sizeBytes Estimasi ukuran berkas model dalam byte (~574 MB).
 * @param downloadUrl Tautan unduhan resmi (default: Hugging Face repositori ggerganov/whisper.cpp).
 * @param expectedSha256 Nilai hash SHA-256 berkas untuk memvalidasi integritas dari kerusakan berkas.
 */
data class ModelInfo(
    val id: String = DEFAULT_MODEL_ID,
    val name: String = "Whisper Large v3 Turbo (Q5_0)",
    val fileName: String = DEFAULT_MODEL_FILE_NAME,
    val sizeBytes: Long = DEFAULT_MODEL_SIZE_BYTES,
    val downloadUrl: String = DEFAULT_DOWNLOAD_URL,
    val expectedSha256: String? = null
) {
    companion object {
        const val DEFAULT_MODEL_ID = "whisper-large-v3-turbo-q5"
        const val DEFAULT_MODEL_FILE_NAME = "ggml-large-v3-turbo-q5_0.bin"
        const val DEFAULT_MODEL_SIZE_BYTES = 574_453_000L // ~574 MB
        const val DEFAULT_DOWNLOAD_URL =
            "https://huggingface.co/ggerganov/whisper.cpp/resolve/main/ggml-large-v3-turbo-q5_0.bin"

        val DEFAULT = ModelInfo()
    }
}

/**
 * Status kondisi unduhan model AI di perangkat.
 */
sealed interface ModelDownloadStatus {
    /** Model belum ada di penyimpanan lokal. */
    data object NotDownloaded : ModelDownloadStatus

    /** Proses unduhan sedang berlangsung. */
    data class Downloading(
        val bytesDownloaded: Long,
        val totalBytes: Long,
        val progress: Float
    ) : ModelDownloadStatus

    /** Berkas selesai diunduh dan sedang divalidasi checksum SHA-256. */
    data object Validating : ModelDownloadStatus

    /** Model sudah terunduh lengkap, valid, dan siap digunakan. */
    data class Ready(val modelFile: File) : ModelDownloadStatus

    /** Terjadi kesalahan saat pengunduhan atau validasi berkas. */
    data class Failed(val error: String, val throwable: Throwable? = null) : ModelDownloadStatus
}
