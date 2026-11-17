package com.catetai.app.core.audio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit test untuk AudioAmplitudeUtil.
 * Menguji kalkulasi RMS amplitude, penanganan sinyal hening, sinyal penuh, dan batas error.
 */
class AudioAmplitudeUtilTest {

    @Test
    fun `sinyal hening (semua byte 0) menghasilkan amplitudo 0_0f`() {
        val silentBuffer = ByteArray(1_024) { 0 }
        val amplitude = AudioAmplitudeUtil.calculateRms(silentBuffer)
        assertEquals(0.0f, amplitude, 0.0001f)
    }

    @Test
    fun `sinyal puncak maksimum konstan (+32767) menghasilkan amplitudo mendekati 1_0f`() {
        // Sampel bernilai +32767 (0x7FFF dalam Little-Endian: low=0xFF, high=0x7F)
        val maxBuffer = ByteArray(1_024)
        for (i in 0 until 1_024 step 2) {
            maxBuffer[i] = 0xFF.toByte()
            maxBuffer[i + 1] = 0x7F.toByte()
        }

        val amplitude = AudioAmplitudeUtil.calculateRms(maxBuffer)
        assertEquals(1.0f, amplitude, 0.001f)
    }

    @Test
    fun `sinyal separuh amplitudo (+16384) menghasilkan amplitudo mendekati 0_5f`() {
        // Sampel bernilai 16384 (0x4000 dalam Little-Endian: low=0x00, high=0x40)
        val halfBuffer = ByteArray(1_024)
        for (i in 0 until 1_024 step 2) {
            halfBuffer[i] = 0x00.toByte()
            halfBuffer[i + 1] = 0x40.toByte()
        }

        val amplitude = AudioAmplitudeUtil.calculateRms(halfBuffer)
        assertEquals(0.5f, amplitude, 0.01f)
    }

    @Test
    fun `buffer kosong atau kurang dari 2 byte menghasilkan 0_0f`() {
        assertEquals(0.0f, AudioAmplitudeUtil.calculateRms(ByteArray(0)), 0.0001f)
        assertEquals(0.0f, AudioAmplitudeUtil.calculateRms(ByteArray(1) { 10 }), 0.0001f)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `offset dan length di luar batas melempar IllegalArgumentException`() {
        val buffer = ByteArray(10)
        AudioAmplitudeUtil.calculateRms(buffer, offset = 5, length = 10)
    }
}
