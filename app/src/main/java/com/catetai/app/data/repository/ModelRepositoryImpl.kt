package com.catetai.app.data.repository

import android.content.Context
import android.util.Log
import com.catetai.app.data.di.IoDispatcher
import com.catetai.app.domain.model.ModelDownloadStatus
import com.catetai.app.domain.model.ModelInfo
import com.catetai.app.domain.repository.ModelRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Implementasi konkret dari [ModelRepository] untuk mengelola unduhan model AI lokal.
 *
 * Fitur Utama:
 * 1. Penyimpanan terisolasi di `context.filesDir/models/`.
 * 2. Dukungan HTTP Range Header (Resume): Jika koneksi terputus, unduhan dilanjutkan dari byte terakhir.
 * 3. Validasi integritas berkas via Checksum SHA-256.
 * 4. Pemantauan progres real-time untuk komponen UI.
 */
@Singleton
class ModelRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val okHttpClient: OkHttpClient,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) : ModelRepository {

    private val _downloadStatus = MutableStateFlow<ModelDownloadStatus>(ModelDownloadStatus.NotDownloaded)
    override val downloadStatus: StateFlow<ModelDownloadStatus> = _downloadStatus.asStateFlow()

    private var activeCall: Call? = null

    private val modelsDir: File
        get() = File(context.filesDir, "models").apply { mkdirs() }

    init {
        // Inisialisasi status awal berdasarkan ketersediaan berkas default di disk
        val defaultFile = File(modelsDir, ModelInfo.DEFAULT_MODEL_FILE_NAME)
        if (defaultFile.exists() && defaultFile.length() > 0L) {
            _downloadStatus.value = ModelDownloadStatus.Ready(defaultFile)
        }
    }

    override fun isModelAvailable(modelInfo: ModelInfo): Boolean {
        val file = File(modelsDir, modelInfo.fileName)
        return file.exists() && file.length() > 0L
    }

    override fun getModelFile(modelInfo: ModelInfo): File? {
        val file = File(modelsDir, modelInfo.fileName)
        return if (file.exists() && file.length() > 0L) file else null
    }

    override suspend fun downloadModel(
        modelInfo: ModelInfo,
        onProgress: (bytesDownloaded: Long, totalBytes: Long, progress: Float) -> Unit
    ): Result<File> = withContext(ioDispatcher) {
        val targetFile = File(modelsDir, modelInfo.fileName)
        val partFile = File(modelsDir, "${modelInfo.fileName}.part")

        // 1. Cek jika sudah terunduh sempurna
        if (targetFile.exists() && targetFile.length() > 0L) {
            _downloadStatus.value = ModelDownloadStatus.Ready(targetFile)
            onProgress(targetFile.length(), targetFile.length(), 1.0f)
            return@withContext Result.success(targetFile)
        }

        try {
            // 2. Tentukan offset byte untuk dukungan resume
            val existingBytes = if (partFile.exists()) partFile.length() else 0L

            val requestBuilder = Request.Builder()
                .url(modelInfo.downloadUrl)

            if (existingBytes > 0L) {
                requestBuilder.header("Range", "bytes=$existingBytes-")
                Log.i(TAG, "Melanjutkan unduhan model dari byte: $existingBytes")
            }

            val request = requestBuilder.build()
            val call = okHttpClient.newCall(request)
            activeCall = call

            _downloadStatus.value = ModelDownloadStatus.Downloading(
                bytesDownloaded = existingBytes,
                totalBytes = modelInfo.sizeBytes,
                progress = if (modelInfo.sizeBytes > 0L) (existingBytes.toFloat() / modelInfo.sizeBytes).coerceIn(0f, 1f) else 0f
            )

            val response = call.execute()

            if (!response.isSuccessful && response.code != 206) {
                if (response.code == 416) {
                    // Range Not Satisfiable: file part kemungkinan korup, hapus dan mulai ulang
                    partFile.delete()
                    throw IOException("HTTP 416: Berkas parsial tidak valid, silakan ulangi unduhan.")
                }
                throw IOException("Gagal mengunduh model dari server: HTTP ${response.code} ${response.message}")
            }

            val isPartial = (response.code == 206)
            val body = response.body ?: throw IOException("Respons server tidak memiliki body data.")
            val contentLength = body.contentLength()

            val totalBytes = if (isPartial) {
                existingBytes + contentLength
            } else {
                if (contentLength > 0L) contentLength else modelInfo.sizeBytes
            }

            // 3. Tulis aliran data ke berkas .part (append jika partial)
            var currentBytes = if (isPartial) existingBytes else 0L
            val appendMode = isPartial

            body.byteStream().use { input ->
                FileOutputStream(partFile, appendMode).use { output ->
                    val buffer = ByteArray(128 * 1024) // Buffer streaming 128 KB
                    var bytesRead: Int
                    while (input.read(buffer).also { bytesRead = it } != -1) {
                        currentCoroutineContext().ensureActive()
                        output.write(buffer, 0, bytesRead)
                        currentBytes += bytesRead

                        val progress = if (totalBytes > 0L) {
                            (currentBytes.toFloat() / totalBytes).coerceIn(0.0f, 1.0f)
                        } else 0.0f

                        _downloadStatus.value = ModelDownloadStatus.Downloading(currentBytes, totalBytes, progress)
                        onProgress(currentBytes, totalBytes, progress)
                    }
                }
            }

            // 4. Validasi integritas berkas (Checksum SHA-256 jika disediakan)
            if (!modelInfo.expectedSha256.isNullOrBlank()) {
                _downloadStatus.value = ModelDownloadStatus.Validating
                val actualSha256 = calculateSha256(partFile)
                if (!actualSha256.equals(modelInfo.expectedSha256, ignoreCase = true)) {
                    partFile.delete()
                    throw IOException("Validasi integritas model gagal: Checksum SHA-256 tidak cocok. Berkas korup telah dihapus.")
                }
                Log.i(TAG, "Validasi SHA-256 model berhasil: $actualSha256")
            }

            // 5. Ganti nama dari .part ke berkas model permanen
            if (partFile.renameTo(targetFile)) {
                _downloadStatus.value = ModelDownloadStatus.Ready(targetFile)
                onProgress(targetFile.length(), targetFile.length(), 1.0f)
                Log.i(TAG, "Model berhasil diunduh dan siap digunakan: ${targetFile.absolutePath}")
                Result.success(targetFile)
            } else {
                throw IOException("Gagal memindahkan berkas sementara ${partFile.name} ke ${targetFile.name}")
            }
        } catch (e: CancellationException) {
            Log.w(TAG, "Unduhan model dibatalkan oleh pengguna/sistem.")
            activeCall?.cancel()
            _downloadStatus.value = ModelDownloadStatus.NotDownloaded
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Terjadi kesalahan saat mengunduh model AI", e)
            _downloadStatus.value = ModelDownloadStatus.Failed(
                error = e.localizedMessage ?: "Kesalahan tidak diketahui saat mengunduh model.",
                throwable = e
            )
            Result.failure(e)
        } finally {
            activeCall = null
        }
    }

    override fun cancelDownload() {
        activeCall?.cancel()
        activeCall = null
    }

    override fun deleteModel(modelInfo: ModelInfo): Boolean {
        val targetFile = File(modelsDir, modelInfo.fileName)
        val partFile = File(modelsDir, "${modelInfo.fileName}.part")

        val deletedTarget = if (targetFile.exists()) targetFile.delete() else false
        val deletedPart = if (partFile.exists()) partFile.delete() else false

        _downloadStatus.value = ModelDownloadStatus.NotDownloaded
        return deletedTarget || deletedPart
    }

    private fun calculateSha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        FileInputStream(file).use { input ->
            val buffer = ByteArray(128 * 1024)
            var bytesRead: Int
            while (input.read(buffer).also { bytesRead = it } != -1) {
                digest.update(buffer, 0, bytesRead)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    companion object {
        private const val TAG = "ModelRepositoryImpl"
    }
}
