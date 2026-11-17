package com.catetai.app.engine.decoder

import java.io.OutputStream

/**
 * Utilitas pemrosesan audio PCM tingkat rendah untuk dekoder Catet AI.
 *
 * Menangani dua transformasi kunci yang dibutuhkan sebelum audio dimasukkan ke Whisper:
 * 1. **Downmixing Stereo / Multi-channel ke Mono**:
 *    Menggabungkan sampel kiri dan kanan menjadi satu kanal mono dengan rata-rata berbobot.
 * 2. **Resampling Linier ke 16.000 Hz**:
 *    Mengubah laju sampel (misal 44.100 Hz atau 48.000 Hz) ke 16.000 Hz standar Whisper.
 *
 * Dirancang hemat memori dengan pendekatan streaming (chunk-by-chunk) langsung ke [OutputStream],
 * sehingga audio berdurasi panjang (hingga 1–2 jam) tidak membebani heap memory perangkat.
 */
object AudioResampler {

    const val TARGET_SAMPLE_RATE: Int = 16_000
    const val BYTES_PER_SAMPLE: Int = 2 // 16-bit PCM Little Endian

    /**
     * Menyimpan status interpolasi fraksional antar-potongan buffer (chunk).
     * Mencegah distorsi klik/pop pada sambungan batas buffer audio.
     */
    class StreamContext(
        val srcSampleRate: Int,
        val targetSampleRate: Int = TARGET_SAMPLE_RATE,
        val channels: Int = 1
    ) {
        var phase: Double = 0.0
        var lastSample: Short = 0
        var isFirstChunk: Boolean = true
    }

    /**
     * Memproses satu potongan (chunk) buffer PCM 16-bit mentah dari MediaCodec,
     * melakukan downmixing ke mono (jika kanal > 1), resampling ke 16kHz (jika laju ≠ 16kHz),
     * dan menuliskan hasilnya langsung ke [outputStream].
     *
     * @param inputBytes Buffer byte PCM masukan dari MediaCodec.
     * @param offset Posisi awal data valid di buffer masukan.
     * @param length Panjang byte data valid.
     * @param context Objek state streaming untuk menjaga kontinuitas interpolasi.
     * @param outputStream Aliran berkas tujuan (misal FileOutputStream).
     */
    fun processChunk(
        inputBytes: ByteArray,
        offset: Int,
        length: Int,
        context: StreamContext,
        outputStream: OutputStream
    ) {
        if (length <= 0) return

        val channels = context.channels
        val srcRate = context.srcSampleRate
        val targetRate = context.targetSampleRate

        // Kasus 1: Sudah 16kHz dan Mono — tulis langsung tanpa alokasi objek
        if (channels == 1 && srcRate == targetRate) {
            outputStream.write(inputBytes, offset, length)
            return
        }

        // 1. Ekstraksi dan downmixing ke sampel Mono ShortArray
        val monoSamples = downmixToMono(inputBytes, offset, length, channels)
        if (monoSamples.isEmpty()) return

        // 2. Resampling jika sample rate berbeda
        if (srcRate == targetRate) {
            writeShortsLittleEndian(monoSamples, outputStream)
        } else {
            resampleAndWrite(monoSamples, context, outputStream)
        }
    }

    /**
     * Mengubah byte interleaved multi-channel 16-bit PCM Little-Endian menjadi
     * array sampel 16-bit Mono.
     */
    fun downmixToMono(
        bytes: ByteArray,
        offset: Int,
        length: Int,
        channels: Int
    ): ShortArray {
        val bytesPerFrame = channels * BYTES_PER_SAMPLE
        val frameCount = length / bytesPerFrame
        if (frameCount <= 0) return ShortArray(0)

        val monoSamples = ShortArray(frameCount)
        var byteIdx = offset

        for (i in 0 until frameCount) {
            var sum = 0
            for (ch in 0 until channels) {
                // Baca 16-bit Little-Endian bertanda (signed short)
                val low = bytes[byteIdx].toInt() and 0xFF
                val high = bytes[byteIdx + 1].toInt()
                val sample = ((high shl 8) or low).toShort()
                sum += sample
                byteIdx += 2
            }
            // Rata-rata dari semua kanal
            monoSamples[i] = (sum / channels).coerceIn(-32768, 32767).toShort()
        }

        return monoSamples
    }

    /**
     * Melakukan resampling linier berkelanjutan dari laju sampel sumber ke laju sampel target,
     * lalu menuliskan byte 16-bit LE langsung ke [outputStream].
     */
    private fun resampleAndWrite(
        input: ShortArray,
        context: StreamContext,
        outputStream: OutputStream
    ) {
        val step = context.srcSampleRate.toDouble() / context.targetSampleRate.toDouble()
        var pos = context.phase
        val inputSize = input.size

        // Buffer tulis lokal per-chunk untuk mengurangi I/O syscall
        val writeBuffer = ByteArray(4096)
        var writePos = 0

        fun flushBuffer() {
            if (writePos > 0) {
                outputStream.write(writeBuffer, 0, writePos)
                writePos = 0
            }
        }

        while (pos < inputSize) {
            val i0 = pos.toInt()
            val frac = (pos - i0).toFloat()

            val s0: Float = if (i0 == 0 && !context.isFirstChunk) {
                // Gunakan sampel terakhir dari chunk sebelumnya jika ada interpolasi di batas
                context.lastSample.toFloat()
            } else {
                input[i0].toFloat()
            }

            val s1: Float = if (i0 + 1 < inputSize) {
                input[i0 + 1].toFloat()
            } else {
                input[i0].toFloat()
            }

            val interpolated = (s0 * (1f - frac) + s1 * frac).toInt().coerceIn(-32768, 32767).toShort()

            if (writePos + 2 > writeBuffer.size) {
                flushBuffer()
            }

            // Tulis Little Endian
            val sampleInt = interpolated.toInt()
            writeBuffer[writePos++] = (sampleInt and 0xFF).toByte()
            writeBuffer[writePos++] = ((sampleInt shr 8) and 0xFF).toByte()

            pos += step
        }

        flushBuffer()

        // Simpan sisa fase dan sampel terakhir untuk chunk berikutnya
        context.phase = pos - inputSize
        context.lastSample = input.last()
        context.isFirstChunk = false
    }

    /**
     * Menuliskan [ShortArray] ke [outputStream] dalam format 16-bit Little-Endian.
     */
    fun writeShortsLittleEndian(samples: ShortArray, outputStream: OutputStream) {
        val buffer = ByteArray(4096)
        var bufIdx = 0

        for (sample in samples) {
            if (bufIdx + 2 > buffer.size) {
                outputStream.write(buffer, 0, bufIdx)
                bufIdx = 0
            }
            val v = sample.toInt()
            buffer[bufIdx++] = (v and 0xFF).toByte()
            buffer[bufIdx++] = ((v shr 8) and 0xFF).toByte()
        }

        if (bufIdx > 0) {
            outputStream.write(buffer, 0, bufIdx)
        }
    }
}
