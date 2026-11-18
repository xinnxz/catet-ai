package com.catetai.app.domain.usecase

import android.content.Context
import android.net.Uri
import android.util.Log
import com.catetai.app.core.crypto.AudioEncryptor
import com.catetai.app.core.util.AudioUriReader
import com.catetai.app.data.di.IoDispatcher
import com.catetai.app.domain.model.Recording
import com.catetai.app.domain.model.RecordingStatus
import com.catetai.app.domain.repository.RecordingRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Use case untuk alur impor berkas rekaman audio ke dalam Catet AI.
 *
 * # Tanggung Jawab (Single Responsibility):
 * 1. Menerima [Uri] audio dari Storage Access Framework (SAF) atau Android Share Intent.
 * 2. Memvalidasi format berkas (hanya berkas audio yang didukung).
 * 3. Memverifikasi kecukupan ruang penyimpanan perangkat (PRD EC-04).
 * 4. Mendeteksi duplikasi berkas audio (PRD EC-10).
 * 5. Menyalin berkas mentah ke buffer sementara (`cacheDir/temp_import/`).
 * 6. Memvalidasi durasi audio minimum (>= 3 detik).
 * 7. Mengenkripsi berkas audio dengan Google Tink StreamingAead (AES256-GCM-HKDF-4KB)
 *    ke direktori permanen internal (`filesDir/audio/xxx.enc`) (PRD F-10, Tech Spec Bagian 10).
 * 8. Menghapus buffer sementara segera setelah enkripsi selesai.
 * 9. Menyimpan entitas awal [Recording] ke Room Database terenkripsi dengan status [RecordingStatus.IMPORTED].
 *
 * @param context Konteks aplikasi untuk mengakses direktori penyimpanan internal.
 * @param audioUriReader Abstraksi untuk membaca metadata dan aliran byte dari Uri.
 * @param recordingRepository Repositori untuk menyimpan entitas ke Room Database.
 * @param audioEncryptor Utilitas enkripsi berkas audio menggunakan Streaming AEAD.
 * @param ioDispatcher Coroutine dispatcher untuk operasi I/O.
 */
@Singleton
class ImportAudioUseCase @Inject constructor(
    @ApplicationContext private val context: Context,
    private val audioUriReader: AudioUriReader,
    private val recordingRepository: RecordingRepository,
    private val audioEncryptor: AudioEncryptor,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) {

    /**
     * Mengeksekusi impor berkas audio dari [uri].
     *
     * @param uri URI berkas audio yang dipilih dari SAF atau Share Intent.
     * @param customTitle Judul kustom pilihan pengguna (opsional; jika kosong, menggunakan nama berkas asli).
     * @param onProgress Callback pemantau progres penyalinan dan enkripsi berkas (rentang 0.0f hingga 1.0f).
     * @return [Result.success] dengan entitas [Recording] yang baru diimpor, atau [Result.failure] jika ada kesalahan.
     */
    suspend operator fun invoke(
        uri: Uri,
        customTitle: String? = null,
        onProgress: (Float) -> Unit = {}
    ): Result<Recording> = withContext(ioDispatcher) {
        var tempBuffer: File? = null
        var targetEncFile: File? = null

        try {
            // 1. Ekstraksi metadata awal dari URI
            val rawName = audioUriReader.getDisplayName(uri) ?: "audio_${System.currentTimeMillis()}.m4a"
            val displayName = rawName.trim()
            val mimeType = audioUriReader.getMimeType(uri) ?: "audio/mp4"
            val extension = displayName.substringAfterLast('.', "").lowercase()

            // 2. Validasi format: tolak jika bukan berkas audio
            val isAudioMime = mimeType.startsWith("audio/", ignoreCase = true)
            val isSupportedExt = SUPPORTED_EXTENSIONS.contains(extension)
            if (!isAudioMime && !isSupportedExt) {
                return@withContext Result.failure(
                    IllegalArgumentException("Berkas bukan audio yang didukung ($mimeType, .$extension).")
                )
            }

            // 3. Validasi kapasitas ruang penyimpanan internal (PRD EC-04)
            val reportedFileSize = audioUriReader.getFileSize(uri)
            val usableSpace = audioUriReader.getUsableStorageSpace()
            val requiredSpace = reportedFileSize * 2 + MIN_BUFFER_STORAGE_BYTES
            if (reportedFileSize > 0L && usableSpace < requiredSpace) {
                return@withContext Result.failure(
                    IOException(
                        "Ruang penyimpanan tidak cukup. Butuh setidaknya ${requiredSpace / 1024 / 1024} MB, " +
                                "tersisa ${usableSpace / 1024 / 1024} MB."
                    )
                )
            }

            // 4. Deteksi duplikasi berkas audio (PRD EC-10 / F1-05)
            if (reportedFileSize > 0L) {
                val duplicate = recordingRepository.findDuplicate(displayName, reportedFileSize)
                if (duplicate != null) {
                    return@withContext Result.failure(
                        IllegalArgumentException("Berkas '$displayName' dengan ukuran yang sama sudah pernah diimpor.")
                    )
                }
            }

            // 5. Salin berkas fisik ke buffer sementara di cacheDir/temp_import/
            val recordingId = UUID.randomUUID().toString()
            val safeExtension = if (extension.isNotBlank()) extension else "m4a"
            val tempDir = File(context.cacheDir, "temp_import").apply { mkdirs() }
            val tempFile = File(tempDir, "temp_${recordingId.take(8)}.$safeExtension")
            tempBuffer = tempFile

            val inputStream = audioUriReader.openInputStream(uri)
                ?: return@withContext Result.failure(
                    IOException("Tidak dapat membuka aliran data dari berkas yang dipilih.")
                )

            // Salin secara streaming dengan pelaporan progres (PRD AC-01.4 / F1-06)
            val buffer = ByteArray(128 * 1024) // Buffer 128 KB
            var bytesCopied = 0L
            inputStream.use { input ->
                FileOutputStream(tempFile).use { output ->
                    var bytesRead: Int
                    while (input.read(buffer).also { bytesRead = it } != -1) {
                        output.write(buffer, 0, bytesRead)
                        bytesCopied += bytesRead
                        if (reportedFileSize > 0L) {
                            val copyProgress = (bytesCopied.toFloat() / reportedFileSize).coerceIn(0.0f, 1.0f)
                            onProgress(copyProgress * 0.8f)
                        }
                    }
                }
            }

            val actualPlainSize = tempFile.length()
            if (actualPlainSize == 0L) {
                tempFile.delete()
                return@withContext Result.failure(
                    IOException("Berkas audio yang disalin memiliki ukuran 0 byte (kosong).")
                )
            }

            // 6. Ekstraksi dan validasi durasi audio via MediaMetadataRetriever
            val durationMs = audioUriReader.getAudioDurationMs(tempFile)
            if (durationMs > 0L && durationMs < MIN_DURATION_MS) {
                tempFile.delete()
                return@withContext Result.failure(
                    IllegalArgumentException(
                        "Durasi audio terlalu pendek (${durationMs / 1000} detik). " +
                                "Minimal durasi rekaman untuk diproses adalah 3 detik."
                    )
                )
            }

            // 7. Enkripsi berkas sementara ke direktori permanen internal (context.filesDir/audio/xxx.enc)
            // Sesuai Tech Spec Bagian 10.2: Berkas terenkripsi Google Tink StreamingAead AES256-GCM-HKDF-4KB
            val audioDir = File(context.filesDir, "audio").apply { mkdirs() }
            val encFileName = "audio_${System.currentTimeMillis()}_${recordingId.take(8)}.enc"
            val encFile = File(audioDir, encFileName)
            targetEncFile = encFile

            audioEncryptor.encrypt(inputFile = tempFile, outputFile = encFile).getOrThrow()
            onProgress(0.95f)

            // 8. Hapus buffer sementara segera setelah enkripsi berhasil
            tempFile.delete()
            tempBuffer = null
            onProgress(1.0f)

            // 9. Tentukan judul rekaman
            val finalTitle = if (!customTitle.isNullOrBlank()) {
                customTitle.trim()
            } else {
                displayName.substringBeforeLast('.').ifBlank { "Rekaman Tanpa Judul" }
            }

            val now = System.currentTimeMillis()
            val recording = Recording(
                id = recordingId,
                title = finalTitle,
                originalFileName = displayName,
                audioFileName = encFileName,
                durationMs = durationMs,
                fileSizeBytes = if (reportedFileSize > 0L) reportedFileSize else actualPlainSize,
                mimeType = mimeType,
                status = RecordingStatus.IMPORTED,
                importedAt = now,
                createdAt = now,
                updatedAt = now
            )

            // 10. Simpan entitas ke basis data Room
            recordingRepository.insertRecording(recording).getOrThrow()

            Log.i(TAG, "Berhasil mengimpor & mengenkripsi audio [id=$recordingId]: '$finalTitle', ukuran=${encFile.length()}B, durasi=${durationMs}ms")
            Result.success(recording)
        } catch (e: Exception) {
            tempBuffer?.delete()
            targetEncFile?.delete()
            Log.e(TAG, "Gagal mengimpor audio", e)
            Result.failure(e)
        }
    }

    companion object {
        private const val TAG = "ImportAudioUseCase"
        private const val MIN_DURATION_MS = 3_000L // Minimal 3 detik (PRD F-05)
        private const val MIN_BUFFER_STORAGE_BYTES = 100 * 1024 * 1024L // Buffer 100 MB (PRD EC-04)

        val SUPPORTED_EXTENSIONS = setOf(
            "m4a", "mp3", "ogg", "opus", "flac", "wav", "aac", "wma", "amr"
        )
    }
}
