package com.catetai.app.engine.whisper

import com.catetai.app.data.di.IoDispatcher
import com.catetai.app.domain.model.TranscriptSegment
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Pembungkus (wrapper) Kotlin tingkat tinggi untuk mesin transkripsi Whisper.
 *
 * Mengelola siklus hidup model C++ (muat model -> inferensi -> lepas) dan
 * mengonversi hasil mentah JNI menjadi model domain [TranscriptSegment].
 *
 * @param jniBridge Jembatan JNI ke kode native C++ (dapat di-mock untuk testing).
 * @param ioDispatcher Coroutine dispatcher untuk operasi I/O dan inferensi intensif CPU.
 */
@Singleton
class WhisperEngine internal constructor(
    private val jniBridge: WhisperBridge,
    private val ioDispatcher: CoroutineDispatcher
) {

    @Inject
    constructor(
        @IoDispatcher ioDispatcher: CoroutineDispatcher = Dispatchers.IO
    ) : this(DefaultWhisperBridge, ioDispatcher)

    private var contextHandle: Long = 0L

    /**
     * Memeriksa apakah model Whisper sedang dimuat aktif di memori RAM.
     */
    val isModelLoaded: Boolean
        get() = contextHandle != 0L

    /**
     * Memuat model GGUF ke dalam memori perangkat.
     *
     * @param modelFile Berkas model GGUF (.bin) dari penyimpanan lokal.
     * @return [Result.success] jika model berhasil diinisialisasi, atau [Result.failure] jika ada kesalahan.
     */
    suspend fun loadModel(modelFile: File): Result<Unit> = withContext(ioDispatcher) {
        runCatching {
            require(modelFile.exists()) { "Berkas model tidak ditemukan: ${modelFile.absolutePath}" }
            require(modelFile.length() > 0) { "Berkas model kosong (0 byte): ${modelFile.absolutePath}" }

            // Lepaskan model lama jika sebelumnya sudah dimuat
            if (contextHandle != 0L) {
                release()
            }

            contextHandle = jniBridge.nativeInit(modelFile.absolutePath)
            if (contextHandle == 0L) {
                error("Gagal memuat model Whisper (nativeInit mengembalikan pointer 0)")
            }
        }
    }

    /**
     * Menjalankan transkripsi audio pada berkas PCM 16kHz 16-bit Mono.
     *
     * @param recordingId ID rekaman induk untuk mengaitkan setiap segmen kalimat.
     * @param pcmFile Berkas audio mentah PCM 16kHz 16-bit Mono.
     * @param config Konfigurasi inferensi (bahasa, thread, kosakata kustom).
     * @param onProgress Callback pemantau progres inferensi (0.0f hingga 1.0f).
     * @return [Result.success] dengan daftar [TranscriptSegment], atau [Result.failure] jika gagal.
     */
    suspend fun transcribe(
        recordingId: String,
        pcmFile: File,
        config: WhisperConfig = WhisperConfig(),
        onProgress: (Float) -> Unit = {}
    ): Result<List<TranscriptSegment>> = withContext(ioDispatcher) {
        runCatching {
            check(contextHandle != 0L) { "Model Whisper belum dimuat. Panggil loadModel() terlebih dahulu." }
            require(pcmFile.exists()) { "Berkas PCM tidak ditemukan: ${pcmFile.absolutePath}" }
            require(pcmFile.length() > 0) { "Berkas PCM kosong (0 byte): ${pcmFile.absolutePath}" }

            val prompt = config.customVocabulary
                .map { it.trim() }
                .filter { it.isNotBlank() }
                .joinToString(", ")

            val rawResult = jniBridge.nativeTranscribe(
                contextHandle = contextHandle,
                pcmPath = pcmFile.absolutePath,
                language = config.language,
                initialPrompt = prompt,
                numThreads = config.numThreads,
                progressCallback = onProgress
            ) ?: error("Inferensi transkripsi whisper menghasilkan null (gagal atau dibatalkan)")

            rawResult.map { segment ->
                val startMs = (segment[0] as Number).toLong()
                val endMs = (segment[1] as Number).toLong()
                val text = (segment[2] as String).trim()

                TranscriptSegment(
                    id = 0L,
                    recordingId = recordingId,
                    startMs = startMs,
                    endMs = endMs,
                    text = text
                )
            }
        }
    }

    /**
     * Membatalkan proses inferensi transkripsi yang sedang berjalan.
     */
    fun cancel() {
        if (contextHandle != 0L) {
            jniBridge.nativeCancel(contextHandle)
        }
    }

    /**
     * Membebaskan alokasi memori RAM yang digunakan oleh model C++ di heap.
     */
    fun release() {
        if (contextHandle != 0L) {
            jniBridge.nativeRelease(contextHandle)
            contextHandle = 0L
        }
    }
}
