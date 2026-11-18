package com.catetai.app.legacy.recorder

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * Unit test untuk PcmBufferWriter.
 *
 * Menguji penulisan buffer audio PCM ke disk, otomatisasi penulisan dan finalisasi header WAV,
 * kalkulasi durasi/byte aktual, periodic flush, dan penanganan error.
 */
class PcmBufferWriterTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    // ================================================================================
    // 1. TEST ALUR UTAMA (3 BUFFER → CLOSE → VALIDASI HEADER WAV)
    // ================================================================================

    @Test
    fun `menulis 3 buffer lalu close menghasilkan file WAV valid dengan header dan data yang akurat`() {
        val testFile = tempFolder.newFile("recording_test.wav")
        val buffer1 = ByteArray(1_024) { 1 }
        val buffer2 = ByteArray(2_048) { 2 }
        val buffer3 = ByteArray(4_096) { 3 }
        val expectedTotalBytes = 1_024L + 2_048L + 4_096L // 7.168 byte

        PcmBufferWriter(testFile).use { writer ->
            writer.write(buffer1)
            assertEquals(1_024L, writer.bytesWritten)

            writer.write(buffer2)
            assertEquals(3_072L, writer.bytesWritten)

            writer.write(buffer3)
            assertEquals(expectedTotalBytes, writer.bytesWritten)

            assertFalse("Writer tidak boleh dalam keadaan tertutup sebelum use block berakhir", writer.isClosed)
        }

        // Setelah block use berakhir, writer harus sudah tertutup dan difinalisasi
        assertTrue("File WAV harus ada di disk", testFile.exists())
        assertEquals(
            "Ukuran file di disk harus 44 byte header + 7.168 byte audio",
            44L + expectedTotalBytes,
            testFile.length()
        )

        // Verifikasi melalui parser header WAV
        val headerInfo = WavHeaderUtil.readHeader(testFile)
        assertEquals("Sample rate harus 16kHz", 16_000, headerInfo.sampleRate)
        assertEquals("Channels harus 1 (Mono)", 1, headerInfo.channels)
        assertEquals("Bits per sample harus 16", 16, headerInfo.bitsPerSample)
        assertEquals("Data size di header harus sesuai total bytes ditulis", expectedTotalBytes, headerInfo.dataSizeBytes)
        assertEquals("Total file size di header harus sesuai panjang file fisik", testFile.length(), headerInfo.totalFileSizeBytes)

        // Verifikasi integritas byte audio PCM
        testFile.inputStream().use { fis ->
            fis.skip(44) // Lewati header
            val readBuffer = ByteArray(expectedTotalBytes.toInt())
            fis.read(readBuffer)

            // Periksa segmen 1 (1024 byte berisi angka 1)
            for (i in 0 until 1_024) {
                assertEquals(1.toByte(), readBuffer[i])
            }
            // Periksa segmen 2 (2048 byte berisi angka 2)
            for (i in 1_024 until 3_072) {
                assertEquals(2.toByte(), readBuffer[i])
            }
            // Periksa segmen 3 (4096 byte berisi angka 3)
            for (i in 3_072 until 7_168) {
                assertEquals(3.toByte(), readBuffer[i])
            }
        }
    }

    // ================================================================================
    // 2. TEST PROPERTI DURASI & TRACKING
    // ================================================================================

    @Test
    fun `durationMs dihitung akurat berdasarkan byte audio yang ditulis`() {
        val testFile = tempFolder.newFile("duration_test.wav")
        val writer = PcmBufferWriter(testFile)

        assertEquals(0L, writer.durationMs)

        // Tulis 32.000 byte = tepat 1.000 ms (1 detik) pada 16kHz 16-bit mono
        val oneSecondBuffer = ByteArray(32_000)
        writer.write(oneSecondBuffer)

        assertEquals(32_000L, writer.bytesWritten)
        assertEquals(1_000L, writer.durationMs)

        writer.close()
        assertTrue(writer.isClosed)
    }

    // ================================================================================
    // 3. TEST PERIODIC FLUSH LOGIC
    // ================================================================================

    @Test
    fun `write memicu flush berkala saat interval waktu tercapai`() {
        val testFile = tempFolder.newFile("flush_test.wav")
        var fakeCurrentTime = 1_000_000L

        val writer = PcmBufferWriter(
            targetFile = testFile,
            flushIntervalMs = 5_000L,
            timeProvider = { fakeCurrentTime }
        )

        val smallChunk = ByteArray(512)

        // Tulis chunk awal pada t = 1.000.000 (belum flush)
        writer.write(smallChunk)

        // Majukan waktu sebesar 4.999 ms (belum mencapai interval 5.000 ms)
        fakeCurrentTime += 4_999L
        writer.write(smallChunk)

        // Majukan waktu melewati 5.000 ms (mencapai interval periodic flush)
        fakeCurrentTime += 2L
        writer.write(smallChunk) // Memicu flushInternal otomatis

        writer.close()

        val info = WavHeaderUtil.readHeader(testFile)
        assertEquals(1_536L, info.dataSizeBytes) // 3 * 512 = 1.536 byte
    }

    // ================================================================================
    // 4. TEST ERROR HANDLING & IDEMPOTENSI
    // ================================================================================

    @Test
    fun `close aman dipanggil berulang kali tanpa melempar exception`() {
        val testFile = tempFolder.newFile("idempotent_close.wav")
        val writer = PcmBufferWriter(testFile)
        writer.write(ByteArray(100))

        writer.close()
        assertTrue(writer.isClosed)

        // Panggilan kedua dan ketiga harus aman
        writer.close()
        writer.close()
        assertTrue(writer.isClosed)
    }

    @Test(expected = IllegalStateException::class)
    fun `menulis data ke writer yang sudah ditutup melempar IllegalStateException`() {
        val testFile = tempFolder.newFile("closed_write.wav")
        val writer = PcmBufferWriter(testFile)
        writer.close()

        writer.write(ByteArray(10))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `menulis dengan offset di luar batas melempar IllegalArgumentException`() {
        val testFile = tempFolder.newFile("bounds_test.wav")
        PcmBufferWriter(testFile).use { writer ->
            writer.write(ByteArray(10), offset = 5, length = 10) // 5 + 10 = 15 > 10
        }
    }
}
