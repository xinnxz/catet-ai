package com.catetai.app.domain.usecase

import android.content.Context
import android.net.Uri
import android.util.Log
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
 * 2. Memvalidasi format berkas (hanya berkas audio yang diizinkan).
 * 3. Memverifikasi kecukupan ruang penyimpanan perangkat (mencegah kehabisan memori disk).
 * 4. Menyalin berkas secara aman ke direktori internal aplikasi (`filesDir/audio/`).
 * 5. Mengekstrak durasi pemutaran dan memvalidasi batas minimum durasi (>= 3 detik).
 * 6. Menyimpan entitas awal [Recording] ke Room Database dengan status [RecordingStatus.IMPORTED].
 *
 * @param context Konteks aplikasi untuk mengakses direktori penyimpanan internal.
 * @param audioUriReader Abstraksi untuk membaca metadata dan aliran byte dari Uri.
 * @param recordingRepository Repositori untuk menyimpan entitas ke Room Database.
 * @param ioDispatcher Coroutine dispatcher untuk operasi I/O.
 */
@Singleton
class ImportAudioUseCase @Inject constructor(
    @ApplicationContext private val context: Context,
    private val audioUriReader: AudioUriReader,
    private val recordingRepository: RecordingRepository,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) {

    /**
     * Mengeksekusi impor berkas audio dari [uri].
     *
     * @param uri URI berkas audio yang dipilih dari SAF atau Share Intent.
     * @param customTitle Judul kustom pilihan pengguna (opsional; jika kosong, menggunakan nama berkas asli).
     * @return [Result.success] dengan entitas [Recording] yang baru diimpor, atau [Result.failure] jika ada kesalahan.
     */
    suspend operator fun invoke(
        uri: Uri,
        customTitle: String? = null
    ): Result<Recording> = withContext(ioDispatcher) {
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

            // 5. Salin berkas fisik ke direktori internal aplikasi (context.filesDir/audio/)
            val recordingId = UUID.randomUUID().toString()
            val safeExtension = if (extension.isNotBlank()) extension else "m4a"
            val audioFileName = "audio_${System.currentTimeMillis()}_${recordingId.take(8)}.$safeExtension"

            val audioDir = File(context.filesDir, "audio").apply { mkdirs() }
            val targetFile = File(audioDir, audioFileName)

            val inputStream = audioUriReader.openInputStream(uri)
                ?: return@withContext Result.failure(
                    IOException("Tidak dapat membuka aliran data dari berkas yang dipilih.")
                )

            inputStream.use { input ->
                FileOutputStream(targetFile).use { output ->
                    input.copyTo(output)
                }
            }

            val actualFileSize = targetFile.length()
            if (actualFileSize == 0L) {
                targetFile.delete()
                return@withContext Result.failure(
                    IOException("Berkas audio yang disalin memiliki ukuran 0 byte (kosong).")
                )
            }

            // 5. Ekstraksi dan validasi durasi audio via MediaMetadataRetriever
            val durationMs = audioUriReader.getAudioDurationMs(targetFile)
            if (durationMs > 0L && durationMs < MIN_DURATION_MS) {
                targetFile.delete()
                return@withContext Result.failure(
                    IllegalArgumentException(
                        "Durasi audio terlalu pendek (${durationMs / 1000} detik). " +
                                "Minimal durasi rekaman untuk diproses adalah 3 detik."
                    )
                )
            }

            // 6. Tentukan judul rekaman
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
                audioFileName = audioFileName,
                durationMs = durationMs,
                fileSizeBytes = actualFileSize,
                mimeType = mimeType,
                status = RecordingStatus.IMPORTED,
                importedAt = now,
                createdAt = now,
                updatedAt = now
            )

            // 7. Simpan entitas ke basis data Room
            recordingRepository.insertRecording(recording).getOrThrow()

            Log.i(TAG, "Berhasil mengimpor audio [id=$recordingId]: '$finalTitle', ukuran=${actualFileSize}B, durasi=${durationMs}ms")
            Result.success(recording)
        } catch (e: Exception) {
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
