package com.catetai.app.engine.decoder

import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.util.Log
import com.catetai.app.data.di.IoDispatcher
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.coroutineContext

/**
 * Implementasi [AudioDecoder] berbasis Android [MediaCodec] dan [MediaExtractor] perangkat keras.
 *
 * Mengubah berkas audio berformat populer (M4A, AAC, MP3, OGG, OPUS, FLAC, WAV)
 * menjadi PCM 16kHz 16-bit Mono (Little-Endian) secara efisien dengan akselerasi perangkat keras ponsel.
 *
 * # Alur Kerja Pemrosesan
 * 1. [MediaExtractor] membaca berkas masukan dan mengekstrak trek audio.
 * 2. [MediaCodec] mendekode paket audio terkompresi menjadi buffer PCM mentah.
 * 3. [AudioResampler] menangani konversi kanal (stereo ke mono) dan resampling (mis. 44.1kHz / 48kHz ke 16kHz).
 * 4. Data PCM 16kHz Mono ditulis secara streaming langsung ke [outputFile] via [BufferedOutputStream].
 *
 * @param ioDispatcher Coroutine dispatcher untuk operasi I/O dan dekoding audio.
 */
@Singleton
class MediaCodecDecoder @Inject constructor(
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher
) : AudioDecoder {

    /** Konstruktor sekunder untuk instansiasi manual atau unit test tanpa DI */
    constructor() : this(Dispatchers.IO)

    override fun supportsFormat(mimeType: String): Boolean {
        val normalized = mimeType.trim().lowercase()
        return SUPPORTED_MIME_TYPES.any { normalized.contains(it) }
    }

    override suspend fun decode(
        inputFile: File,
        outputFile: File,
        onProgress: (Float) -> Unit
    ): Result<File> = withContext(ioDispatcher) {
        if (!inputFile.exists() || inputFile.length() == 0L) {
            return@withContext Result.failure(
                IllegalArgumentException("Berkas masukan tidak ditemukan atau kosong: ${inputFile.absolutePath}")
            )
        }

        val extractor = MediaExtractor()
        var codec: MediaCodec? = null
        var outputStream: BufferedOutputStream? = null

        try {
            extractor.setDataSource(inputFile.absolutePath)

            // 1. Temukan trek audio di berkas kontainer
            var audioTrackIndex = -1
            var inputFormat: MediaFormat? = null

            for (i in 0 until extractor.trackCount) {
                val format = extractor.getTrackFormat(i)
                val mime = format.getString(MediaFormat.KEY_MIME) ?: ""
                if (mime.startsWith("audio/")) {
                    audioTrackIndex = i
                    inputFormat = format
                    break
                }
            }

            if (audioTrackIndex < 0 || inputFormat == null) {
                return@withContext Result.failure(
                    IllegalArgumentException("Tidak ditemukan trek audio yang valid di dalam berkas.")
                )
            }

            val mime = inputFormat.getString(MediaFormat.KEY_MIME)
                ?: return@withContext Result.failure(
                    IllegalArgumentException("MIME type trek audio tidak terdefinisi.")
                )

            val durationUs = if (inputFormat.containsKey(MediaFormat.KEY_DURATION)) {
                inputFormat.getLong(MediaFormat.KEY_DURATION)
            } else {
                0L
            }

            var currentSampleRate = if (inputFormat.containsKey(MediaFormat.KEY_SAMPLE_RATE)) {
                inputFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE)
            } else {
                AudioResampler.TARGET_SAMPLE_RATE
            }

            var currentChannels = if (inputFormat.containsKey(MediaFormat.KEY_CHANNEL_COUNT)) {
                inputFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
            } else {
                1
            }

            Log.i(
                TAG,
                "Memulai dekoding: mime=$mime, rate=$currentSampleRate Hz, channels=$currentChannels, durationUs=$durationUs"
            )

            // 2. Inisialisasi MediaCodec decoder
            codec = MediaCodec.createDecoderByType(mime)
            codec.configure(inputFormat, null, null, 0)
            codec.start()
            extractor.selectTrack(audioTrackIndex)

            // 3. Siapkan berkas dan stream output PCM
            outputFile.parentFile?.mkdirs()
            outputStream = BufferedOutputStream(FileOutputStream(outputFile))

            var streamContext = AudioResampler.StreamContext(
                srcSampleRate = currentSampleRate,
                targetSampleRate = AudioResampler.TARGET_SAMPLE_RATE,
                channels = currentChannels
            )

            val bufferInfo = MediaCodec.BufferInfo()
            var sawInputEOS = false
            var sawOutputEOS = false
            var consecutiveTimeoutsAfterInputEOS = 0

            // 4. Loop dekoding streaming
            while (!sawOutputEOS && coroutineContext.isActive) {
                // Alirkan data input ke decoder jika belum mencapai akhir berkas (EOS)
                if (!sawInputEOS) {
                    val inputBufferIndex = codec.dequeueInputBuffer(TIMEOUT_US)
                    if (inputBufferIndex >= 0) {
                        val inputBuffer = codec.getInputBuffer(inputBufferIndex)
                        if (inputBuffer != null) {
                            val sampleSize = extractor.readSampleData(inputBuffer, 0)
                            if (sampleSize < 0) {
                                codec.queueInputBuffer(
                                    inputBufferIndex,
                                    0,
                                    0,
                                    0L,
                                    MediaCodec.BUFFER_FLAG_END_OF_STREAM
                                )
                                sawInputEOS = true
                            } else {
                                val presentationTimeUs = extractor.sampleTime
                                codec.queueInputBuffer(
                                    inputBufferIndex,
                                    0,
                                    sampleSize,
                                    presentationTimeUs,
                                    0
                                )
                                extractor.advance()

                                if (durationUs > 0L) {
                                    val progress = (presentationTimeUs.toFloat() / durationUs).coerceIn(0.0f, 0.99f)
                                    onProgress(progress)
                                }
                            }
                        }
                    }
                }

                // Ambil buffer PCM hasil dekode dari output decoder
                val outputBufferIndex = codec.dequeueOutputBuffer(bufferInfo, TIMEOUT_US)
                when {
                    outputBufferIndex == MediaCodec.INFO_TRY_AGAIN_LATER -> {
                        if (sawInputEOS) {
                            consecutiveTimeoutsAfterInputEOS++
                            // Jika sudah EOS input dan decoder 50x berturut-turut (~500ms) tidak memancarkan data lagi,
                            // hentikan loop untuk mencegah hang/infinite loop pada file audio pendek
                            if (consecutiveTimeoutsAfterInputEOS >= 50) {
                                Log.w(TAG, "Mencapai batas tunggu output buffer setelah input EOS (50x timeouts). Mengakhiri dekoding.")
                                sawOutputEOS = true
                            }
                        }
                    }

                    outputBufferIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                        consecutiveTimeoutsAfterInputEOS = 0
                        val newFormat = codec.outputFormat
                        Log.d(TAG, "Format output berubah: $newFormat")

                        if (newFormat.containsKey(MediaFormat.KEY_SAMPLE_RATE)) {
                            currentSampleRate = newFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                        }
                        if (newFormat.containsKey(MediaFormat.KEY_CHANNEL_COUNT)) {
                            currentChannels = newFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
                        }

                        // Perbarui konfigurasi resampling sesuai format aktual dari decoder
                        streamContext = AudioResampler.StreamContext(
                            srcSampleRate = currentSampleRate,
                            targetSampleRate = AudioResampler.TARGET_SAMPLE_RATE,
                            channels = currentChannels
                        )
                    }

                    outputBufferIndex >= 0 -> {
                        val outputBuffer = codec.getOutputBuffer(outputBufferIndex)
                        if (outputBuffer != null && bufferInfo.size > 0) {
                            outputBuffer.position(bufferInfo.offset)
                            outputBuffer.limit(bufferInfo.offset + bufferInfo.size)

                            val chunk = ByteArray(bufferInfo.size)
                            outputBuffer.get(chunk)

                            // Downmix & resample ke PCM 16kHz Mono
                            AudioResampler.processChunk(
                                inputBytes = chunk,
                                offset = 0,
                                length = chunk.size,
                                context = streamContext,
                                outputStream = outputStream
                            )
                        }

                        codec.releaseOutputBuffer(outputBufferIndex, false)

                        if ((bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                            sawOutputEOS = true
                        }
                    }
                }
            }

            // Periksa apakah operasi dibatalkan oleh coroutine cancellation
            if (!coroutineContext.isActive) {
                Log.w(TAG, "Dekoding dibatalkan oleh coroutine.")
                outputFile.delete()
                return@withContext Result.failure(
                    kotlinx.coroutines.CancellationException("Dekoding dibatalkan.")
                )
            }

            outputStream.flush()
            onProgress(1.0f)
            Log.i(TAG, "Dekoding selesai sukses: ${outputFile.length()} byte ditulis.")

            Result.success(outputFile)
        } catch (e: Exception) {
            Log.e(TAG, "Gagal saat mendekode berkas audio", e)
            outputFile.delete()
            Result.failure(e)
        } finally {
            try {
                outputStream?.close()
            } catch (e: IOException) {
                Log.w(TAG, "Gagal menutup outputStream", e)
            }
            try {
                codec?.stop()
            } catch (ignored: Exception) {}
            try {
                codec?.release()
            } catch (ignored: Exception) {}
            try {
                extractor.release()
            } catch (ignored: Exception) {}
        }
    }

    companion object {
        private const val TAG = "MediaCodecDecoder"
        private const val TIMEOUT_US = 10_000L // 10 milidetik batas tunggu antrean buffer

        val SUPPORTED_MIME_TYPES = setOf(
            "audio/mp4",
            "audio/mp4a-latm",
            "audio/m4a",
            "audio/aac",
            "audio/mpeg",
            "audio/mp3",
            "audio/ogg",
            "audio/opus",
            "audio/vorbis",
            "audio/flac",
            "audio/x-flac",
            "audio/wav",
            "audio/x-wav",
            "audio/raw"
        )
    }
}
