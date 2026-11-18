package com.catetai.app.domain.repository

import com.catetai.app.domain.model.ModelDownloadStatus
import com.catetai.app.domain.model.ModelInfo
import kotlinx.coroutines.flow.StateFlow
import java.io.File

/**
 * Kontrak repositori untuk mengelola ketersediaan, unduhan, dan validasi berkas model AI lokal.
 */
interface ModelRepository {

    /**
     * Aliran status unduhan model terkini secara reaktif.
     */
    val downloadStatus: StateFlow<ModelDownloadStatus>

    /**
     * Memeriksa apakah berkas model yang valid sudah tersimpan di direktori lokal.
     *
     * @param modelInfo Metadata model yang diperiksa.
     */
    fun isModelAvailable(modelInfo: ModelInfo = ModelInfo.DEFAULT): Boolean

    /**
     * Mengambil referensi [File] model jika sudah tersedia di disk, atau null jika belum diunduh.
     *
     * @param modelInfo Metadata model.
     */
    fun getModelFile(modelInfo: ModelInfo = ModelInfo.DEFAULT): File?

    /**
     * Mengunduh berkas model AI dari internet dengan dukungan resume (HTTP Range Request)
     * dan validasi integritas berkas.
     *
     * @param modelInfo Metadata model yang akan diunduh.
     * @param onProgress Callback pemantau progres (bytesDownloaded, totalBytes, progress: 0.0f - 1.0f).
     * @return [Result.success] dengan [File] model yang valid, atau [Result.failure] jika ada kesalahan jaringan/validasi.
     */
    suspend fun downloadModel(
        modelInfo: ModelInfo = ModelInfo.DEFAULT,
        onProgress: (bytesDownloaded: Long, totalBytes: Long, progress: Float) -> Unit = { _, _, _ -> }
    ): Result<File>

    /**
     * Membatalkan proses pengunduhan model yang sedang berjalan.
     */
    fun cancelDownload()

    /**
     * Menghapus berkas model dari penyimpanan lokal (misal untuk membersihkan memori perangkat).
     *
     * @param modelInfo Metadata model yang akan dihapus.
     */
    fun deleteModel(modelInfo: ModelInfo = ModelInfo.DEFAULT): Boolean
}
