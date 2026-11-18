package com.catetai.app.legacy.recorder

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Unit test untuk AudioConfig.
 *
 * Yang DIUJI di sini:
 *   • Nilai-nilai turunan yang tidak trivial: BYTES_PER_SECOND, VAD_CHUNK_BYTES,
 *     batas disk, dan persamaan-persamaan konversi matematika.
 *   • Fungsi konversi durationMsToBytes, bytesToDurationMs, durationMsToSamples.
 *   • Konsistensi internal: VAD_CHUNK_SAMPLES harus selaras dengan durationMsToSamples(32).
 *
 * Yang TIDAK diuji di sini (dan kenapa):
 *   • `SAMPLE_RATE == 16_000` — itu hanya membuktikan kita bisa membaca literal.
 *     Jika nilai itu berubah, test roundtrip byte/durasi akan langsung gagal.
 *   • `BYTES_PER_SAMPLE == 2` — sama, sudah tercakup oleh test BYTES_PER_SECOND.
 *   • AudioRecord.getMinBufferSize() — Android API, diuji di instrumented test F1-04.
 *
 * Semua test berjalan di JVM murni tanpa Android dependency (tidak butuh emulator).
 */
class AudioConfigTest {

    // ================================================================================
    // 1. KONSISTENSI NILAI TURUNAN
    // ================================================================================

    @Test
    fun `BYTES_PER_SECOND harus 32000 — diturunkan dari sample rate dan format PCM`() {
        // 16.000 sampel/detik × 2 byte/sampel × 1 channel = 32.000 byte/detik
        // Test ini lebih berharga dari sekadar mengecek SAMPLE_RATE == 16000,
        // karena memvalidasi bahwa tiga konstanta inti konsisten satu sama lain.
        assertEquals(32_000, AudioConfig.BYTES_PER_SECOND)
    }

    @Test
    fun `VAD_CHUNK_BYTES harus 1024 — 512 sampel × 2 byte per sampel`() {
        // Silero VAD v5 pada 16 kHz meminta chunk 512 sampel (32 ms).
        // 512 × 2 byte × 1 channel = 1.024 byte per chunk VAD.
        // Jika nilai ini salah, model ONNX akan menolak input atau menghasilkan prediksi acak.
        assertEquals(1_024, AudioConfig.VAD_CHUNK_BYTES)
    }

    @Test
    fun `VAD_CHUNK_SAMPLES harus selaras dengan durationMsToSamples(32)`() {
        // Silero VAD v5 menggunakan window 32 ms @ 16 kHz = 512 sampel.
        // Test ini memastikan konstanta VAD_CHUNK_SAMPLES tidak berbeda dari
        // apa yang dihasilkan fungsi konversi untuk 32 ms.
        val samplesFor32ms = AudioConfig.durationMsToSamples(32L)
        assertEquals(
            "VAD_CHUNK_SAMPLES (${ AudioConfig.VAD_CHUNK_SAMPLES}) harus sama dengan " +
                "durationMsToSamples(32) = $samplesFor32ms",
            AudioConfig.VAD_CHUNK_SAMPLES,
            samplesFor32ms
        )
    }

    @Test
    fun `LOW_DISK_WARNING_BYTES harus lebih besar dari MIN_DISK_SPACE_BYTES`() {
        // Peringatan (100 MB) harus lebih awal dari auto-stop (50 MB).
        // Jika kedua nilai ini tertukar, pengguna tidak akan pernah mendapat peringatan
        // sebelum rekaman dihentikan paksa.
        assert(AudioConfig.LOW_DISK_WARNING_BYTES > AudioConfig.MIN_DISK_SPACE_BYTES) {
            "LOW_DISK_WARNING_BYTES (${AudioConfig.LOW_DISK_WARNING_BYTES}) harus > " +
                "MIN_DISK_SPACE_BYTES (${AudioConfig.MIN_DISK_SPACE_BYTES})"
        }
    }

    @Test
    fun `LOW_BATTERY_WARNING_PERCENT harus lebih besar dari CRITICAL_BATTERY_PERCENT`() {
        // Peringatan baterai (5%) harus muncul sebelum auto-stop kritis (2%).
        assert(AudioConfig.LOW_BATTERY_WARNING_PERCENT > AudioConfig.CRITICAL_BATTERY_PERCENT) {
            "LOW_BATTERY_WARNING_PERCENT (${AudioConfig.LOW_BATTERY_WARNING_PERCENT}) " +
                "harus > CRITICAL_BATTERY_PERCENT (${AudioConfig.CRITICAL_BATTERY_PERCENT})"
        }
    }

    // ================================================================================
    // 2. FUNGSI KONVERSI durationMsToBytes
    // ================================================================================

    @Test
    fun `durationMsToBytes - 0 ms menghasilkan 0 byte`() {
        assertEquals(0L, AudioConfig.durationMsToBytes(0L))
    }

    @Test
    fun `durationMsToBytes - 1 detik menghasilkan 32000 byte`() {
        // 1 detik = 1.000 ms × 32.000 byte/detik / 1.000 = 32.000 byte
        assertEquals(32_000L, AudioConfig.durationMsToBytes(1_000L))
    }

    @Test
    fun `durationMsToBytes - 2 jam menghasilkan 230400000 byte (skenario rapat maksimum)`() {
        // 2 jam = 7.200 detik × 32.000 = 230.400.000 byte ≈ 219,7 MB
        assertEquals(230_400_000L, AudioConfig.durationMsToBytes(7_200_000L))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `durationMsToBytes - input negatif dilempar IllegalArgumentException`() {
        AudioConfig.durationMsToBytes(-1L)
    }

    // ================================================================================
    // 3. FUNGSI KONVERSI bytesToDurationMs
    // ================================================================================

    @Test
    fun `bytesToDurationMs - 0 byte menghasilkan 0 ms`() {
        assertEquals(0L, AudioConfig.bytesToDurationMs(0L))
    }

    @Test
    fun `bytesToDurationMs - 32000 byte menghasilkan 1000 ms`() {
        assertEquals(1_000L, AudioConfig.bytesToDurationMs(32_000L))
    }

    @Test
    fun `bytesToDurationMs - 230400000 byte menghasilkan 7200000 ms (2 jam)`() {
        assertEquals(7_200_000L, AudioConfig.bytesToDurationMs(230_400_000L))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `bytesToDurationMs - input negatif dilempar IllegalArgumentException`() {
        AudioConfig.bytesToDurationMs(-1L)
    }

    // ================================================================================
    // 4. ROUND-TRIP: konversi bolak-balik harus idempoten
    // ================================================================================

    @Test
    fun `round-trip durationMs ke bytes lalu kembali ke ms harus akurat`() {
        // Test simetri: bytesToDurationMs(durationMsToBytes(x)) == x
        // untuk nilai kelipatan penuh (tidak ada sisa pembagian integer)
        listOf(1_000L, 30_000L, 300_000L, 3_600_000L, 7_200_000L).forEach { originalMs ->
            val recovered = AudioConfig.bytesToDurationMs(AudioConfig.durationMsToBytes(originalMs))
            assertEquals("Round-trip gagal untuk $originalMs ms", originalMs, recovered)
        }
    }

    // ================================================================================
    // 5. FUNGSI KONVERSI durationMsToSamples
    // ================================================================================

    @Test
    fun `durationMsToSamples - 32 ms menghasilkan 512 sampel (ukuran chunk Silero VAD v5)`() {
        // Silero VAD v5 pada 16 kHz meminta tepat 512 sampel per chunk.
        // 32 ms × 16.000 Hz / 1.000 = 512 sampel
        assertEquals(512, AudioConfig.durationMsToSamples(32L))
    }

    @Test
    fun `durationMsToSamples - 1000 ms menghasilkan 16000 sampel`() {
        assertEquals(16_000, AudioConfig.durationMsToSamples(1_000L))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `durationMsToSamples - input negatif dilempar IllegalArgumentException`() {
        AudioConfig.durationMsToSamples(-1L)
    }
}
