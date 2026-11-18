package com.catetai.app.domain.usecase

import android.content.Context
import com.catetai.app.data.di.IoDispatcher
import com.catetai.app.domain.model.ModelInfo
import com.catetai.app.domain.repository.ModelRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Use case untuk mengunduh model AI Whisper ke penyimpanan internal perangkat (F2-05).
 *
 * Tanggung Jawab:
 * 1. Memeriksa kecukupan memori internal sebelum mengunduh (PRD EC-05: butuh ukuran model + buffer 100 MB).
 * 2. Mengoordinasikan proses pengunduhan via [ModelRepository].
 * 3. Mendukung pembatalan dan pelaporan kemajuan progres ke UI.
 */
@Singleton
class DownloadModelUseCase @Inject constructor(
    @ApplicationContext private val context: Context,
    private val modelRepository: ModelRepository,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) {

    /**
     * Memulai proses pengunduhan model AI.
     *
     * @param modelInfo Metadata model yang akan diunduh.
     * @param onProgress Callback kemajuan unduhan (bytesDownloaded, totalBytes, progress: 0.0f - 1.0f).
     * @return [Result.success] dengan [File] model yang siap pakai, atau [Result.failure] jika ada kesalahan.
     */
    suspend operator fun invoke(
        modelInfo: ModelInfo = ModelInfo.DEFAULT,
        onProgress: (bytesDownloaded: Long, totalBytes: Long, progress: Float) -> Unit = { _, _, _ -> }
    ): Result<File> = withContext(ioDispatcher) {
        // 1. Jika model sudah ada, langsung kembalikan
        if (modelRepository.isModelAvailable(modelInfo)) {
            val file = modelRepository.getModelFile(modelInfo)
            if (file != null) return@withContext Result.success(file)
        }

        // 2. Validasi ruang penyimpanan internal perangkat (PRD EC-05)
        val usableSpace = context.filesDir.usableSpace
        val requiredSpace = modelInfo.sizeBytes + MIN_BUFFER_STORAGE_BYTES
        if (usableSpace < requiredSpace) {
            return@withContext Result.failure(
                IOException(
                    "Ruang penyimpanan internal tidak mencukupi untuk mengunduh model AI. " +
                            "Dibutuhkan setidaknya ${requiredSpace / 1024 / 1024} MB, " +
                            "tersisa ${usableSpace / 1024 / 1024} MB."
                )
            )
        }

        // 3. Eksekusi pengunduhan
        modelRepository.downloadModel(modelInfo, onProgress)
    }

    /**
     * Membatalkan proses pengunduhan model yang sedang berjalan.
     */
    fun cancel() {
        modelRepository.cancelDownload()
    }

    companion object {
        private const val MIN_BUFFER_STORAGE_BYTES = 100 * 1024 * 1024L // Buffer 100 MB
    }
}
