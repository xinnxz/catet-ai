package com.catetai.app.legacy.recorder

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

/**
 * Unit test untuk WavHeaderUtil.
 *
 * Menguji kepatuhan spesifikasi RIFF WAV, operasi in-place header update,
 * validasi byte layout, serta pemulihan (recovery) dari crash skenario.
 */
class WavHeaderUtilTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    // ================================================================================
    // 1. STRUKTUR & FIELD HEADER WAV
    // ================================================================================

    @Test
    fun `createHeaderByteArray menghasilkan tepat 44 byte dengan semua field valid`() {
        val dataSize = 32_000L // 1 detik audio 16kHz 16-bit mono
        val header = WavHeaderUtil.createHeaderByteArray(
            dataSizeBytes = dataSize,
            sampleRate = 16_000,
            channels = 1,
            bitsPerSample = 16
        )

        assertEquals("Ukuran header harus tepat 44 byte", 44, header.size)

        // Validasi string magic tags
        val riffTag = String(header, 0, 4, Charsets.US_ASCII)
        val waveTag = String(header, 8, 4, Charsets.US_ASCII)
        val fmtTag = String(header, 12, 4, Charsets.US_ASCII)
        val dataTag = String(header, 36, 4, Charsets.US_ASCII)

        assertEquals("RIFF", riffTag)
        assertEquals("WAVE", waveTag)
        assertEquals("fmt ", fmtTag)
        assertEquals("data", dataTag)
    }

    @Test
    fun `writeHeader ke OutputStream menulis 44 byte yang dapat diparsing kembali`() {
        val testFile = tempFolder.newFile("test_sample.wav")
        val dataSize = 32_000L

        FileOutputStream(testFile).use { fos ->
            WavHeaderUtil.writeHeader(
                outputStream = fos,
                dataSizeBytes = dataSize,
                sampleRate = AudioConfig.SAMPLE_RATE,
                channels = AudioConfig.CHANNEL_COUNT,
                bitsPerSample = AudioConfig.BYTES_PER_SAMPLE * 8
            )
            // Tulis 32.000 byte dummy PCM data
            fos.write(ByteArray(dataSize.toInt()))
        }

        val info = WavHeaderUtil.readHeader(testFile)

        assertEquals(16_000, info.sampleRate)
        assertEquals(1, info.channels)
        assertEquals(16, info.bitsPerSample)
        assertEquals(32_000, info.byteRate)
        assertEquals(2, info.blockAlign)
        assertEquals(32_000L, info.dataSizeBytes)
        assertEquals(32_044L, info.totalFileSizeBytes)
    }

    // ================================================================================
    // 2. IN-PLACE HEADER DATA SIZE UPDATE
    // ================================================================================

    @Test
    fun `updateHeaderDataSize memperbarui offset 4 dan 40 tanpa merusak data PCM audio`() {
        val testFile = tempFolder.newFile("update_test.wav")
        val actualDataSize = 64_000L // 2 detik

        // Langkah 1: Buat file dengan placeholder dataSize = 0 (skenario awal rekam)
        FileOutputStream(testFile).use { fos ->
            WavHeaderUtil.writeHeader(fos, dataSizeBytes = 0L)
            // Isi dengan pola byte unik untuk memastikan konten tidak tertimpa
            val dummyAudio = ByteArray(actualDataSize.toInt()) { (it % 127).toByte() }
            fos.write(dummyAudio)
        }

        // Verifikasi awal: dataSizeBytes masih 0
        var info = WavHeaderUtil.readHeader(testFile)
        assertEquals(0L, info.dataSizeBytes)

        // Langkah 2: Panggil updateHeaderDataSize saat rekaman selesai
        WavHeaderUtil.updateHeaderDataSize(testFile, actualDataSize)

        // Verifikasi akhir: dataSizeBytes sudah 64.000
        info = WavHeaderUtil.readHeader(testFile)
        assertEquals(64_000L, info.dataSizeBytes)
        assertEquals(64_044L, info.totalFileSizeBytes)

        // Langkah 3: Verifikasi data audio PCM di byte 44 dst TIDAK korup/berubah
        testFile.inputStream().use { fis ->
            fis.skip(44)
            val audioRead = ByteArray(actualDataSize.toInt())
            fis.read(audioRead)
            for (i in 0 until 100) {
                assertEquals((i % 127).toByte(), audioRead[i])
            }
        }
    }

    // ================================================================================
    // 3. CRASH RECOVERY (Auto-Repair Unfinalized Audio)
    // ================================================================================

    @Test
    fun `recoverUnfinalizedHeader memperbaiki file yang terputus dengan dataSize 0`() {
        val testFile = tempFolder.newFile("crashed_recording.wav")
        val audioBytes = 96_000L // 3 detik

        // Simulasikan app crash mendadak saat rekaman:
        // Header ditulis dengan dataSize = 0, audio ditulis, tapi updateHeaderDataSize TIDAK sempat dipanggil
        FileOutputStream(testFile).use { fos ->
            WavHeaderUtil.writeHeader(fos, dataSizeBytes = 0L)
            fos.write(ByteArray(audioBytes.toInt()))
        }

        // Jalankan pemulihan
        val repaired = WavHeaderUtil.recoverUnfinalizedHeader(testFile)
        assertTrue("Harus mengembalikan true karena perbaikan berhasil", repaired)

        // Verifikasi hasil perbaikan
        val info = WavHeaderUtil.readHeader(testFile)
        assertEquals("Data size harus pulih ke ukuran aktual", audioBytes, info.dataSizeBytes)
    }

    @Test
    fun `recoverUnfinalizedHeader mengembalikan false jika file sudah sinkron dan sehat`() {
        val testFile = tempFolder.newFile("healthy.wav")
        val audioBytes = 32_000L

        FileOutputStream(testFile).use { fos ->
            WavHeaderUtil.writeHeader(fos, dataSizeBytes = audioBytes)
            fos.write(ByteArray(audioBytes.toInt()))
        }

        val repaired = WavHeaderUtil.recoverUnfinalizedHeader(testFile)
        assertFalse("Tidak perlu repair untuk file yang sudah sinkron", repaired)
    }

    @Test
    fun `recoverUnfinalizedHeader memotong odd byte trailing jika proses terputus di tengah 16-bit sample`() {
        val testFile = tempFolder.newFile("odd_orphan_byte.wav")
        val oddAudioBytes = 32_001 // 32.000 byte genap + 1 byte sisa (separuh sampel 16-bit)

        FileOutputStream(testFile).use { fos ->
            WavHeaderUtil.writeHeader(fos, dataSizeBytes = 0L)
            fos.write(ByteArray(oddAudioBytes))
        }

        assertEquals("Total ukuran awal sebelum recovery", 44L + 32_001L, testFile.length())

        val repaired = WavHeaderUtil.recoverUnfinalizedHeader(testFile)
        assertTrue("Harus mengembalikan true karena perbaikan dan pemotongan berhasil", repaired)

        val info = WavHeaderUtil.readHeader(testFile)
        assertEquals("Data size harus dipangkas ke kelipatan 2 byte genap", 32_000L, info.dataSizeBytes)
        assertEquals("File di disk harus dipangkas tepat 32044 byte", 44L + 32_000L, testFile.length())
    }

    // ================================================================================
    // 4. ERROR HANDLING & VALIDASI INPUT
    // ================================================================================

    @Test(expected = IllegalArgumentException::class)
    fun `createHeaderByteArray menolak dataSizeBytes negatif`() {
        WavHeaderUtil.createHeaderByteArray(dataSizeBytes = -1L)
    }

    @Test(expected = IOException::class)
    fun `readHeader melempar IOException jika file kurang dari 44 byte`() {
        val truncatedFile = tempFolder.newFile("truncated.wav")
        truncatedFile.writeBytes(ByteArray(20))
        WavHeaderUtil.readHeader(truncatedFile)
    }

    @Test(expected = IOException::class)
    fun `readHeader melempar IOException jika signature bukan RIFF`() {
        val badFile = tempFolder.newFile("bad_magic.wav")
        val corruptedBytes = WavHeaderUtil.createHeaderByteArray(0L)
        corruptedBytes[0] = 'N'.code.toByte() // Rusak signature
        badFile.writeBytes(corruptedBytes)
        WavHeaderUtil.readHeader(badFile)
    }
}
