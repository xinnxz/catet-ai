package com.catetai.app.domain.usecase

import android.content.Context
import android.util.Log
import com.catetai.app.core.crypto.AudioEncryptor
import com.catetai.app.data.di.IoDispatcher
import com.catetai.app.domain.model.RecordingStatus
import com.catetai.app.domain.model.TranscriptSegment
import com.catetai.app.domain.repository.RecordingRepository
import com.catetai.app.domain.repository.TranscriptRepository
import com.catetai.app.engine.decoder.AudioDecoder
import com.catetai.app.engine.whisper.WhisperConfig
import com.catetai.app.engine.whisper.WhisperEngine
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Use case orkestrator pipeline transkripsi audio Catet AI (F2-04).
 *
 * Mengoordinasikan seluruh rantai pemrosesan audio:
 * 1. Mendekripsi berkas audio `.enc` (Google Tink Streaming AEAD).
 * 2. Mendekode format audio asli ke PCM 16kHz 16-bit Mono ([AudioDecoder]).
 * 3. Menjalankan inferensi AI suara-ke-teks menggunakan [WhisperEngine].
 *    - Memproses dalam chunk per 30 menit jika durasi rekaman panjang untuk menjaga batas memori RAM.
 *    - Menggabungkan kosakata kustom (prompt biasing) dari entitas rekaman.
 * 4. Menyimpan segmen transkrip bertanda waktu secara inkremental ke [TranscriptRepository].
 * 5. Memperbarui status siklus hidup rekaman ([RecordingStatus]):
 *    `DECODING` -> `TRANSCRIBING` -> `COMPLETED` (atau `FAILED` / `INTERRUPTED`).
 * 6. Menghapus berkas sementara (decrypted buffer & PCM buffer) secara otomatis.
 */
@Singleton
class TranscribeAudioUseCase @Inject constructor(
    @ApplicationContext private val context: Context,
    private val recordingRepository: RecordingRepository,
    private val transcriptRepository: TranscriptRepository,
    private val audioEncryptor: AudioEncryptor,
    private val audioDecoder: AudioDecoder,
    private val whisperEngine: WhisperEngine,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) {

    /**
     * Menjalankan transkripsi penuh untuk rekaman audio dengan [recordingId].
     *
     * @param recordingId ID rekaman yang akan ditranskrip.
     * @param modelFile Berkas model GGUF (opsional jika model sudah dimuat sebelumnya di [WhisperEngine]).
     * @param config Konfigurasi inferensi (bahasa, thread, kosakata kustom).
     * @param onProgress Callback pemantau progres transkripsi (0.0f - 1.0f).
     * @return [Result.success] dengan daftar segmen transkrip lengkap, atau [Result.failure] jika gagal.
     */
    suspend operator fun invoke(
        recordingId: String,
        modelFile: File? = null,
        config: WhisperConfig = WhisperConfig(),
        onProgress: (Float) -> Unit = {}
    ): Result<List<TranscriptSegment>> = withContext(ioDispatcher) {
        var tempDecryptedFile: File? = null
        var tempPcmFile: File? = null
        val tempChunkFiles = mutableListOf<File>()

        try {
            // 1. Validasi entitas rekaman
            val recording = recordingRepository.getRecordingById(recordingId)
                ?: return@withContext Result.failure(
                    IllegalArgumentException("Rekaman dengan ID '$recordingId' tidak ditemukan di basis data.")
                )

            val audioDir = File(context.filesDir, "audio")
            val encryptedAudioFile = File(audioDir, recording.audioFileName)
            if (!encryptedAudioFile.exists()) {
                return@withContext Result.failure(
                    IOException("Berkas rekaman terenkripsi tidak ditemukan di penyimpanan: ${encryptedAudioFile.absolutePath}")
                )
            }

            // 2. Inisialisasi model Whisper jika disediakan
            if (modelFile != null && (!whisperEngine.isModelLoaded)) {
                whisperEngine.loadModel(modelFile).getOrThrow()
            }
            check(whisperEngine.isModelLoaded) {
                "Model Whisper belum dimuat. Sediakan parameter modelFile atau panggil loadModel() terlebih dahulu."
            }

            // 3. Dekripsi berkas audio ke buffer sementara
            val tempDir = File(context.cacheDir, "temp_transcribe").apply { mkdirs() }
            val rawExt = recording.originalFileName.substringAfterLast('.', "m4a")
            val decryptedFile = File(tempDir, "dec_${recordingId}_${System.currentTimeMillis()}.$rawExt")
            tempDecryptedFile = decryptedFile

            FileOutputStream(decryptedFile).use { outputStream ->
                audioEncryptor.decrypt(encryptedAudioFile, outputStream).getOrThrow()
            }
            onProgress(0.05f)
            currentCoroutineContext().ensureActive()

            // 4. Dekode berkas audio ke format mentah PCM 16kHz 16-bit Mono
            recordingRepository.updateStatus(recordingId, RecordingStatus.DECODING)
            val pcmFile = File(tempDir, "pcm_${recordingId}_${System.currentTimeMillis()}.pcm")
            tempPcmFile = pcmFile

            audioDecoder.decode(decryptedFile, pcmFile).getOrThrow()

            // Hapus berkas decrypted audio segera setelah PCM terbentuk untuk hemat disk
            decryptedFile.delete()
            tempDecryptedFile = null

            if (!pcmFile.exists() || pcmFile.length() == 0L) {
                return@withContext Result.failure(
                    IOException("Gagal mendekode audio: berkas PCM hasil dekode kosong (0 byte).")
                )
            }
            onProgress(0.15f)
            currentCoroutineContext().ensureActive()

            // 5. Persiapan konfigurasi inferensi & prompt biasing
            recordingRepository.updateStatus(recordingId, RecordingStatus.TRANSCRIBING)

            // Gabungkan kosakata kustom dari konfigurasi dan entitas rekaman
            val entityVocab = recording.customVocabulary
                ?.split(",")
                ?.map { it.trim() }
                ?.filter { it.isNotBlank() }
                ?: emptyList()

            val combinedVocab = (config.customVocabulary + entityVocab).distinct()
            val effectiveConfig = config.copy(customVocabulary = combinedVocab)

            // Hapus segmen lama jika sebelumnya pernah diproses parsial ("Proses Ulang")
            transcriptRepository.deleteSegmentsByRecordingId(recordingId)

            val totalPcmBytes = pcmFile.length()
            val allSegments = mutableListOf<TranscriptSegment>()

            // 6. Transkripsi dengan strategi chunking (30 menit per chunk)
            if (totalPcmBytes <= CHUNK_SIZE_BYTES) {
                // Rekaman <= 30 menit: proses langsung satu berkas penuh
                val segments = whisperEngine.transcribe(
                    recordingId = recordingId,
                    pcmFile = pcmFile,
                    config = effectiveConfig,
                    onProgress = { p ->
                        val overall = 0.15f + (p * 0.80f)
                        onProgress(overall.coerceIn(0.15f, 0.95f))
                    }
                ).getOrThrow()

                transcriptRepository.insertSegments(segments).getOrThrow()
                allSegments.addAll(segments)
            } else {
                // Rekaman panjang > 30 menit: potong PCM menjadi chunk berukuran 30 menit
                val totalChunks = ((totalPcmBytes + CHUNK_SIZE_BYTES - 1) / CHUNK_SIZE_BYTES).toInt()
                Log.i(TAG, "Audio panjang (${totalPcmBytes / 1024 / 1024} MB) dipecah menjadi $totalChunks chunk @ 30 menit.")

                FileInputStream(pcmFile).use { pcmInput ->
                    val buffer = ByteArray(128 * 1024)
                    for (chunkIndex in 0 until totalChunks) {
                        currentCoroutineContext().ensureActive()

                        val chunkFile = File(tempDir, "chunk_${recordingId}_$chunkIndex.pcm")
                        tempChunkFiles.add(chunkFile)

                        // Tulis tepat CHUNK_SIZE_BYTES ke file chunk
                        var bytesRemainingForChunk = CHUNK_SIZE_BYTES
                        FileOutputStream(chunkFile).use { chunkOutput ->
                            while (bytesRemainingForChunk > 0L) {
                                val toRead = minOf(buffer.size.toLong(), bytesRemainingForChunk).toInt()
                                val bytesRead = pcmInput.read(buffer, 0, toRead)
                                if (bytesRead == -1) break
                                chunkOutput.write(buffer, 0, bytesRead)
                                bytesRemainingForChunk -= bytesRead
                            }
                        }

                        val chunkStartMs = chunkIndex * CHUNK_DURATION_MS
                        val chunkProgressWeight = 0.80f / totalChunks
                        val chunkBaseProgress = 0.15f + (chunkIndex * chunkProgressWeight)

                        val rawChunkSegments = whisperEngine.transcribe(
                            recordingId = recordingId,
                            pcmFile = chunkFile,
                            config = effectiveConfig,
                            onProgress = { p ->
                                val overall = chunkBaseProgress + (p * chunkProgressWeight)
                                onProgress(overall.coerceIn(0.15f, 0.95f))
                            }
                        ).getOrThrow()

                        // Sesuaikan penanda waktu segmen dengan offset chunk
                        val adjustedSegments = rawChunkSegments.map { seg ->
                            seg.copy(
                                startMs = chunkStartMs + seg.startMs,
                                endMs = chunkStartMs + seg.endMs
                            )
                        }

                        // Simpan hasil chunk secara progresif ke database Room
                        transcriptRepository.insertSegments(adjustedSegments).getOrThrow()
                        allSegments.addAll(adjustedSegments)

                        // Hapus file chunk sementara segera setelah selesai
                        chunkFile.delete()
                        tempChunkFiles.remove(chunkFile)
                    }
                }
            }

            // 7. Bersihkan PCM buffer
            pcmFile.delete()
            tempPcmFile = null

            // 8. Perbarui status rekaman ke COMPLETED
            recordingRepository.updateStatus(recordingId, RecordingStatus.COMPLETED)
            onProgress(1.0f)

            Log.i(TAG, "Transkripsi sukses untuk rekaman '$recordingId': ${allSegments.size} segmen teks dihasilkan.")
            Result.success(allSegments)
        } catch (e: CancellationException) {
            Log.w(TAG, "Transkripsi dibatalkan untuk rekaman '$recordingId'.")
            whisperEngine.cancel()
            recordingRepository.updateStatus(recordingId, RecordingStatus.INTERRUPTED)
            cleanupTempFiles(tempDecryptedFile, tempPcmFile, tempChunkFiles)
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Gagal menjalankan pipeline transkripsi untuk rekaman '$recordingId'", e)
            recordingRepository.updateStatus(recordingId, RecordingStatus.FAILED)
            cleanupTempFiles(tempDecryptedFile, tempPcmFile, tempChunkFiles)
            Result.failure(e)
        }
    }

    /**
     * Membatalkan proses transkripsi yang sedang berjalan.
     */
    fun cancel() {
        whisperEngine.cancel()
    }

    private fun cleanupTempFiles(
        tempDecrypted: File?,
        tempPcm: File?,
        chunks: List<File>
    ) {
        tempDecrypted?.delete()
        tempPcm?.delete()
        chunks.forEach { it.delete() }
    }

    companion object {
        private const val TAG = "TranscribeAudioUseCase"

        // 1 detik PCM 16kHz 16-bit Mono = 32.000 byte
        // 30 menit = 1800 detik * 32.000 byte = 57.600.000 byte
        const val CHUNK_DURATION_MS = 30 * 60 * 1000L // 30 menit (1.800.000 ms)
        const val CHUNK_SIZE_BYTES = 30 * 60 * 32_000L // 57.600.000 bytes
    }
}
