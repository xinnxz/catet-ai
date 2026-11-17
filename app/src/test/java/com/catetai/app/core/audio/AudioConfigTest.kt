package com.catetai.app.core.audio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Unit test untuk AudioConfig.
 *
 * Mengapa unit test ini penting?
 * ==============================
 * AudioConfig adalah "kontrak matematis" seluruh pipeline audio Catet AI.
 * Jika kalkulasi konversi byte ↔ durasi salah, dampaknya sangat serius:
 *   - Timestamp transkrip bisa meleset (pengguna klik detik ke-30, audio loncat ke tempat lain).
 *   - Estimasi ukuran file storage bisa terlalu kecil, menyebabkan overflow disk.
 *   - Silero VAD bisa menerima chunk berukuran salah, menghasilkan false positive.
 *
 * Semua test di sini adalah pure logic test (tidak butuh Android framework/emulator),
 * sehingga bisa dijalankan cepat di JVM lokal via: ./gradlew testDebugUnitTest
 *
 * CATATAN: AudioRecord.getMinBufferSize() adalah Android framework API yang tidak tersedia
 * di JVM unit test. Kita gunakan AudioConfig.bufferSizeProvider untuk meng-inject nilai
 * dummy (8192) agar test bisa berjalan tanpa Android emulator. Teknik ini disebut
 * "Dependency Injection via Function Parameter" — ringan, efektif, tanpa library mock tambahan.
 */
class AudioConfigTest {

    @Before
    fun setUp() {
        // Inject stub provider agar BUFFER_SIZE_BYTES tidak memanggil AudioRecord.getMinBufferSize()
        // yang tidak tersedia di JVM test runner (hanya tersedia di Android runtime / emulator).
        AudioConfig.bufferSizeProvider = { AudioConfig.DEFAULT_BUFFER_SIZE }
    }

    // ================================================================================
    // 1. TEST KONSTANTA INTI
    // ================================================================================

    @Test
    fun `sample rate harus 16000 Hz untuk kompatibilitas Whisper dan Silero VAD`() {
        // Whisper dan Silero VAD keduanya dilatih pada 16kHz.
        // Jika nilai ini berubah, seluruh pipeline AI akan rusak tanpa error yang jelas.
        assertEquals(16_000, AudioConfig.SAMPLE_RATE)
    }

    @Test
    fun `bytes per sample harus 2 untuk encoding PCM 16-bit`() {
        // PCM 16-bit = 2 byte per sampel (short integer -32768 hingga +32767)
        assertEquals(2, AudioConfig.BYTES_PER_SAMPLE)
    }

    @Test
    fun `channel count harus 1 untuk mono recording`() {
        // Whisper mengharapkan audio mono. Stereo akan membuat ukuran file 2x lebih besar
        // dan butuh downmix sebelum bisa diproses.
        assertEquals(1, AudioConfig.CHANNEL_COUNT)
    }

    @Test
    fun `flush interval harus 5 detik sebagai batas aman kehilangan data`() {
        // 5 detik adalah trade-off antara frekuensi I/O disk (makin sering makin aman tapi
        // makin boros baterai) dan risiko kehilangan data saat proses dimatikan paksa oleh OS.
        assertEquals(5_000L, AudioConfig.FLUSH_INTERVAL_MS)
    }

    @Test
    fun `WAV header size harus 44 byte sesuai standar RIFF format`() {
        // Format WAV standar RIFF memiliki header 44 byte tetap.
        // Referensi: http://soundfile.sapp.org/doc/WaveFormat/
        assertEquals(44, AudioConfig.WAV_HEADER_SIZE_BYTES)
    }

    @Test
    fun `buffer size harus positif dan merupakan kelipatan 2 untuk 16-bit PCM alignment`() {
        // setUp() meng-inject DEFAULT_BUFFER_SIZE = 8192 melalui bufferSizeProvider.
        assertTrue(
            "BUFFER_SIZE_BYTES harus > 0, actual: ${AudioConfig.BUFFER_SIZE_BYTES}",
            AudioConfig.BUFFER_SIZE_BYTES > 0
        )
        assertEquals(
            "BUFFER_SIZE_BYTES harus genap untuk alignment 16-bit PCM",
            0,
            AudioConfig.BUFFER_SIZE_BYTES % 2
        )
    }

    @Test
    fun `default buffer size harus 8192 byte sebagai nilai fallback aman`() {
        // DEFAULT_BUFFER_SIZE = 8192 byte = 256 ms audio pada 16kHz 16-bit mono.
        // Ini adalah nilai fallback saat AudioRecord.getMinBufferSize gagal di hardware tertentu.
        assertEquals(8_192, AudioConfig.DEFAULT_BUFFER_SIZE)
    }

    // ================================================================================
    // 2. TEST KONVERSI DURASI → BYTE (durationMsToBytes)
    // ================================================================================

    @Test
    fun `durationMsToBytes - 0 ms harus menghasilkan 0 byte`() {
        assertEquals(0L, AudioConfig.durationMsToBytes(0L))
    }

    @Test
    fun `durationMsToBytes - 1 detik (1000 ms) harus menghasilkan 32000 byte`() {
        // Verifikasi formula inti:
        // 1 detik × 16.000 sampel/detik × 2 byte/sampel × 1 channel = 32.000 byte
        assertEquals("1 detik harus = 32.000 byte", 32_000L, AudioConfig.durationMsToBytes(1_000L))
    }

    @Test
    fun `durationMsToBytes - 1 menit (60000 ms) harus menghasilkan 1920000 byte`() {
        // 60 × 32.000 = 1.920.000 byte ≈ 1,83 MB
        assertEquals("1 menit harus = 1.920.000 byte", 1_920_000L, AudioConfig.durationMsToBytes(60_000L))
    }

    @Test
    fun `durationMsToBytes - 2 jam (7200000 ms) harus menghasilkan 230400000 byte`() {
        // 2 jam = 7.200 detik × 32.000 = 230.400.000 byte ≈ 219,7 MB
        // Ini adalah skenario rapat terpanjang di PRD (R-01).
        assertEquals("2 jam harus = 230.400.000 byte", 230_400_000L, AudioConfig.durationMsToBytes(7_200_000L))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `durationMsToBytes - nilai negatif harus melempar IllegalArgumentException`() {
        // Durasi negatif tidak memiliki makna fisik — harus ditolak secara eksplisit.
        // Menggunakan annotation @Test(expected=...) untuk cara penulisan test yang lebih idiomatic.
        AudioConfig.durationMsToBytes(-1L)
    }

    // ================================================================================
    // 3. TEST KONVERSI BYTE → DURASI (bytesToDurationMs)
    // ================================================================================

    @Test
    fun `bytesToDurationMs - 0 byte harus menghasilkan 0 ms`() {
        assertEquals(0L, AudioConfig.bytesToDurationMs(0L))
    }

    @Test
    fun `bytesToDurationMs - 32000 byte harus menghasilkan 1000 ms (1 detik)`() {
        // 32.000 byte / (16.000 × 2 × 1) × 1.000 = 1.000 ms
        assertEquals("32.000 byte harus = 1.000 ms", 1_000L, AudioConfig.bytesToDurationMs(32_000L))
    }

    @Test
    fun `bytesToDurationMs - 1920000 byte harus menghasilkan 60000 ms (1 menit)`() {
        assertEquals("1.920.000 byte harus = 60.000 ms", 60_000L, AudioConfig.bytesToDurationMs(1_920_000L))
    }

    @Test
    fun `bytesToDurationMs - 230400000 byte harus menghasilkan 7200000 ms (2 jam)`() {
        assertEquals("230.400.000 byte harus = 7.200.000 ms", 7_200_000L, AudioConfig.bytesToDurationMs(230_400_000L))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `bytesToDurationMs - nilai negatif harus melempar IllegalArgumentException`() {
        AudioConfig.bytesToDurationMs(-1L)
    }

    // ================================================================================
    // 4. TEST ROUND-TRIP (konversi bolak-balik harus idempoten untuk nilai bersih)
    // ================================================================================

    @Test
    fun `konversi round-trip - durationMs ke bytes lalu kembali ke ms harus akurat`() {
        // Test simetri: bytesToDurationMs(durationMsToBytes(x)) == x
        // untuk nilai-nilai kelipatan penuh (1 detik, 30 detik, 5 menit, 1 jam, 2 jam)
        val testCasesMs = listOf(1_000L, 30_000L, 300_000L, 3_600_000L, 7_200_000L)
        testCasesMs.forEach { originalMs ->
            val bytes = AudioConfig.durationMsToBytes(originalMs)
            val recoveredMs = AudioConfig.bytesToDurationMs(bytes)
            assertEquals(
                "Round-trip gagal untuk $originalMs ms: dapat $recoveredMs ms",
                originalMs,
                recoveredMs
            )
        }
    }

    // ================================================================================
    // 5. TEST KONVERSI DURASI → SAMPEL (durationMsToSamples) untuk Silero VAD
    // ================================================================================

    @Test
    fun `durationMsToSamples - 0 ms harus menghasilkan 0 sampel`() {
        assertEquals(0, AudioConfig.durationMsToSamples(0L))
    }

    @Test
    fun `durationMsToSamples - 30 ms harus menghasilkan 480 sampel (ukuran chunk VAD standar)`() {
        // Silero VAD umumnya menerima chunk 30 ms atau 10 ms.
        // 30 ms × 16.000 sampel/detik / 1.000 ms = 480 sampel
        assertEquals("30 ms harus = 480 sampel VAD", 480, AudioConfig.durationMsToSamples(30L))
    }

    @Test
    fun `durationMsToSamples - 1000 ms harus menghasilkan 16000 sampel`() {
        // 1 detik = tepat 1 × SAMPLE_RATE sampel
        assertEquals(16_000, AudioConfig.durationMsToSamples(1_000L))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `durationMsToSamples - nilai negatif harus melempar IllegalArgumentException`() {
        AudioConfig.durationMsToSamples(-1L)
    }
}
