package com.catetai.app.engine.decoder

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream

/**
 * Unit test murni pada JVM untuk [AudioResampler].
 * Memverifikasi logika downmixing kanal, konversi byte LE, dan algoritma resampling linier.
 */
class AudioResamplerTest {

    // ─── Downmixing Stereo ke Mono ──────────────────────────────────────────

    @Test
    fun downmixToMono_stereoEqualChannels_returnsExactAverage() {
        // Sampel Stereo: Kiri = 2000, Kanan = 4000 -> Rata-rata = 3000
        val left = 2000.toShort()
        val right = 4000.toShort()

        val bytes = ByteArray(4)
        // Kiri (LE)
        bytes[0] = (left.toInt() and 0xFF).toByte()
        bytes[1] = ((left.toInt() shr 8) and 0xFF).toByte()
        // Kanan (LE)
        bytes[2] = (right.toInt() and 0xFF).toByte()
        bytes[3] = ((right.toInt() shr 8) and 0xFF).toByte()

        val mono = AudioResampler.downmixToMono(bytes, 0, bytes.size, channels = 2)

        assertEquals(1, mono.size)
        assertEquals(3000.toShort(), mono[0])
    }

    @Test
    fun downmixToMono_stereoOppositePolarity_cancelsToZero() {
        val left = (-5000).toShort()
        val right = 5000.toShort()

        val bytes = ByteArray(4)
        bytes[0] = (left.toInt() and 0xFF).toByte()
        bytes[1] = ((left.toInt() shr 8) and 0xFF).toByte()
        bytes[2] = (right.toInt() and 0xFF).toByte()
        bytes[3] = ((right.toInt() shr 8) and 0xFF).toByte()

        val mono = AudioResampler.downmixToMono(bytes, 0, bytes.size, channels = 2)

        assertEquals(1, mono.size)
        assertEquals(0.toShort(), mono[0])
    }

    @Test
    fun downmixToMono_emptyInput_returnsEmptyArray() {
        val mono = AudioResampler.downmixToMono(ByteArray(0), 0, 0, channels = 2)
        assertEquals(0, mono.size)
    }

    // ─── Resampling 48kHz ke 16kHz (Rasio 3:1) ──────────────────────────────

    @Test
    fun processChunk_resamples48kHzTo16kHz_producesExactSampleRatio() {
        val outStream = ByteArrayOutputStream()
        val context = AudioResampler.StreamContext(
            srcSampleRate = 48_000,
            targetSampleRate = 16_000,
            channels = 1
        )

        // Buat 300 sampel Mono pada 48kHz (setara 100 sampel pada 16kHz)
        val srcSampleCount = 300
        val inputBytes = ByteArray(srcSampleCount * 2)
        for (i in 0 until srcSampleCount) {
            val sample = (i * 50).toShort()
            inputBytes[i * 2] = (sample.toInt() and 0xFF).toByte()
            inputBytes[i * 2 + 1] = ((sample.toInt() shr 8) and 0xFF).toByte()
        }

        AudioResampler.processChunk(inputBytes, 0, inputBytes.size, context, outStream)

        val outputBytes = outStream.toByteArray()
        val outSampleCount = outputBytes.size / 2

        // Rasio 48k -> 16k adalah 3.0, jadi 300 input sampel harus menghasilkan ~100 output sampel
        assertEquals(100, outSampleCount)
    }

    // ─── Resampling 44.1kHz ke 16kHz ────────────────────────────────────────

    @Test
    fun processChunk_resamples44100HzTo16kHz_producesExpectedDuration() {
        val outStream = ByteArrayOutputStream()
        val context = AudioResampler.StreamContext(
            srcSampleRate = 44_100,
            targetSampleRate = 16_000,
            channels = 1
        )

        // 44.100 sampel = persis 1.0 detik audio
        val srcSampleCount = 44_100
        val inputBytes = ByteArray(srcSampleCount * 2)
        for (i in 0 until srcSampleCount) {
            // Gelombang konstan bernilai 1000
            val sample = 1000.toShort()
            inputBytes[i * 2] = (sample.toInt() and 0xFF).toByte()
            inputBytes[i * 2 + 1] = ((sample.toInt() shr 8) and 0xFF).toByte()
        }

        AudioResampler.processChunk(inputBytes, 0, inputBytes.size, context, outStream)

        val outputBytes = outStream.toByteArray()
        val outSampleCount = outputBytes.size / 2

        // 1.0 detik pada 16kHz harus menghasilkan tepat 16.000 sampel (+/- toleransi rounding)
        assertTrue(
            "Hasil sampel ($outSampleCount) harus mendekati 16.000 untuk 1 detik audio",
            outSampleCount in 15995..16005
        )
    }

    // ─── Passthrough 16kHz Mono ──────────────────────────────────────────────

    @Test
    fun processChunk_already16kHzMono_passesThroughByteForByte() {
        val outStream = ByteArrayOutputStream()
        val context = AudioResampler.StreamContext(
            srcSampleRate = 16_000,
            targetSampleRate = 16_000,
            channels = 1
        )

        val inputBytes = byteArrayOf(1, 2, 3, 4, 5, 6, 7, 8)

        AudioResampler.processChunk(inputBytes, 0, inputBytes.size, context, outStream)

        val outputBytes = outStream.toByteArray()
        assertEquals(inputBytes.size, outputBytes.size)
        for (i in inputBytes.indices) {
            assertEquals(inputBytes[i], outputBytes[i])
        }
    }
}
