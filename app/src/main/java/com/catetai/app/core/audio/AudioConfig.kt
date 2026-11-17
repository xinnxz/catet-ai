package com.catetai.app.core.audio

import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder

/**
 * AudioConfig — Singleton konfigurasi audio untuk seluruh pipeline Catet AI.
 *
 * Semua konstanta di sini mengikuti persyaratan teknis dari dua komponen hilir:
 *   1. Silero VAD  — Model PyTorch Voice Activity Detection; dilatih pada 16kHz mono.
 *   2. whisper.cpp — Implementasi C++ model Whisper OpenAI; menerima input 16kHz mono PCM.
 *
 * Jika salah satu nilai diubah (misalnya SAMPLE_RATE menjadi 44100), kedua engine di atas
 * harus melakukan resampling yang mahal. Oleh karena itu, nilai-nilai ini tidak boleh diubah
 * tanpa mempertimbangkan dampak ke seluruh pipeline audio.
 *
 * ======================================================================================
 * PENJELASAN TEKNIS FORMAT PCM (Pulse-Code Modulation)
 * ======================================================================================
 * PCM adalah cara merepresentasikan sinyal audio analog sebagai urutan angka digital.
 * Setiap "sampel" adalah pengukuran amplitudo sinyal pada satu titik waktu.
 *
 *   • SAMPLE_RATE = 16.000: Artinya kita mengambil 16.000 pengukuran per detik.
 *     Berdasarkan Teorema Nyquist-Shannon, ini cukup untuk merepresentasikan frekuensi
 *     suara hingga 8.000 Hz — jauh di atas rentang vokal manusia (85 Hz – 3.400 Hz).
 *
 *   • ENCODING_PCM_16BIT: Setiap sampel direpresentasikan sebagai bilangan bulat 16-bit
 *     bertanda (signed), rentang -32.768 hingga +32.767. Ini menghasilkan resolusi
 *     amplitude 96dB, lebih dari cukup untuk merekam suara rapat.
 *
 *   • CHANNEL_IN_MONO: Hanya satu saluran (tidak stereo). Ini menghemat penyimpanan
 *     setengahnya dan memperlancar proses Whisper.
 *
 * KALKULASI UKURAN FILE:
 *   Bytes per detik = 16.000 sampel × 2 byte/sampel × 1 channel = 32.000 byte/detik
 *   1 menit rapat  = 32.000 × 60 = 1.920.000 byte ≈ 1,83 MB
 *   1 jam rapat    = 1.920.000 × 60 = 115.200.000 byte ≈ 109,9 MB
 *   2 jam rapat    = ≈ 219,7 MB
 * ======================================================================================
 */
object AudioConfig {

    // ================================================================================
    // 1. FORMAT AUDIO INTI
    // ================================================================================

    /**
     * Frekuensi sampling dalam satuan Hertz (Hz).
     * 16.000 Hz = standar universal untuk Speech-to-Text (STT) model Whisper & Silero VAD.
     */
    const val SAMPLE_RATE: Int = 16_000

    /**
     * Konfigurasi channel audio.
     * CHANNEL_IN_MONO = 1 channel; hemat bandwidth dan memori, cocok untuk rekaman suara rapat.
     */
    const val CHANNEL_CONFIG: Int = AudioFormat.CHANNEL_IN_MONO

    /**
     * Format encoding per sampel audio.
     * ENCODING_PCM_16BIT = 2 byte (16-bit) per sampel, memberikan dynamic range 96dB.
     */
    const val AUDIO_ENCODING: Int = AudioFormat.ENCODING_PCM_16BIT

    /**
     * Jumlah byte per sampel audio (diturunkan dari ENCODING_PCM_16BIT = 2 byte).
     * Digunakan dalam kalkulasi konversi durasi ↔ byte.
     */
    const val BYTES_PER_SAMPLE: Int = 2

    /**
     * Jumlah channel audio (diturunkan dari CHANNEL_IN_MONO = 1).
     */
    const val CHANNEL_COUNT: Int = 1

    // ================================================================================
    // 2. KONFIGURASI BUFFER & FLUSH
    // ================================================================================

    /**
     * Ukuran buffer AudioRecord dalam byte.
     *
     * AudioRecord.getMinBufferSize(...) mengembalikan ukuran minimum yang dijamin oleh
     * Android HAL (Hardware Abstraction Layer). Kita kalikan 2 (double-buffering) untuk
     * menghindari overrun — kondisi di mana data baru masuk sebelum buffer lama sempat
     * dibaca, yang akan menyebabkan keheningan atau artefak dalam audio.
     *
     * CATATAN TESTABILITY: AudioRecord.getMinBufferSize() adalah Android framework call yang
     * tidak tersedia di JVM unit test biasa (hanya mock stub yang akan crash).
     * Untuk itu kita memakai lazy + bufferSizeProvider yang bisa di-override dari luar
     * sehingga unit test bisa inject nilai dummy tanpa butuh Android emulator.
     *
     * Pattern ini disebut "dependency injection via function parameter" — salah satu teknik
     * paling ringan untuk membuat kode Android testable di JVM.
     */
    val BUFFER_SIZE_BYTES: Int by lazy { bufferSizeProvider() }

    /**
     * Provider fungsi untuk menghitung buffer size.
     * Default: panggil AudioRecord.getMinBufferSize (membutuhkan Android runtime).
     * Di unit test: di-replace dengan { DEFAULT_BUFFER_SIZE } agar tidak crash.
     */
    var bufferSizeProvider: () -> Int = { calculateBufferSize() }

    /**
     * Nilai default buffer jika AudioRecord.getMinBufferSize gagal atau dijalankan di JVM.
     * 8192 byte = 256 ms audio pada 16kHz 16-bit mono — ukuran aman untuk semua perangkat.
     */
    const val DEFAULT_BUFFER_SIZE: Int = 8_192

    /**
     * Interval waktu flush buffer ke disk, dalam milidetik.
     * Setiap 5 detik, data audio yang sudah terekam di memori dipaksa ditulis ke file.
     * Ini mencegah kehilangan audio jika aplikasi crash atau proses latar belakang dimatikan
     * secara paksa oleh OS (terutama pada HiOS Tecno yang agresif membunuh background process).
     */
    const val FLUSH_INTERVAL_MS: Long = 5_000L

    /**
     * Ukuran minimum buffer untuk operasi baca yang aman (dalam bytes).
     * Dipakai oleh WavHeaderUtil saat menghitung posisi seek untuk update header WAV.
     */
    const val WAV_HEADER_SIZE_BYTES: Int = 44

    // ================================================================================
    // 3. KONSTANTA TEKNIS AUDIO RECORD
    // ================================================================================

    /**
     * Sumber audio yang digunakan untuk rekaman.
     * MIC = mikrofon built-in perangkat.
     * VOICE_COMMUNICATION adalah alternatif yang mengaktifkan noise cancellation bawaan
     * Android, namun kita pakai MIC murni agar VAD dan Whisper mendapat sinyal asli
     * tanpa pre-processing dari OS yang bisa merusak akurasi transkripsi.
     */
    const val AUDIO_SOURCE: Int = MediaRecorder.AudioSource.MIC

    // ================================================================================
    // 4. FUNGSI UTILITAS KONVERSI WAKTU ↔ BYTE
    // ================================================================================

    /**
     * Menghitung berapa BYTE yang dibutuhkan untuk menyimpan audio dengan durasi
     * sejumlah [durationMs] milidetik.
     *
     * Rumus:
     *   bytes = (durationMs / 1000) × SAMPLE_RATE × BYTES_PER_SAMPLE × CHANNEL_COUNT
     *
     * Contoh: 1.000 ms (1 detik) = 1 × 16.000 × 2 × 1 = 32.000 byte
     * Contoh: 60.000 ms (1 menit) = 60 × 16.000 × 2 × 1 = 1.920.000 byte (≈ 1,83 MB)
     *
     * @param durationMs Durasi audio dalam milidetik (harus positif).
     * @return Jumlah byte PCM yang dibutuhkan (tidak termasuk 44-byte WAV header).
     */
    fun durationMsToBytes(durationMs: Long): Long {
        require(durationMs >= 0) { "durationMs tidak boleh negatif: $durationMs" }
        return (durationMs.toDouble() / 1_000.0 * SAMPLE_RATE * BYTES_PER_SAMPLE * CHANNEL_COUNT).toLong()
    }

    /**
     * Menghitung berapa MILIDETIK durasi audio yang tersimpan dalam sejumlah [bytes] byte PCM.
     *
     * Rumus (kebalikan dari durationMsToBytes):
     *   durationMs = bytes / (SAMPLE_RATE × BYTES_PER_SAMPLE × CHANNEL_COUNT) × 1000
     *
     * Contoh: 32.000 byte = 32.000 / (16.000 × 2 × 1) × 1000 = 1.000 ms (1 detik)
     * Contoh: 1.920.000 byte = 1.920.000 / 32.000 × 1000 = 60.000 ms (1 menit)
     *
     * @param bytes Jumlah byte PCM (tidak termasuk WAV header). Harus non-negatif.
     * @return Durasi dalam milidetik.
     */
    fun bytesToDurationMs(bytes: Long): Long {
        require(bytes >= 0) { "bytes tidak boleh negatif: $bytes" }
        val bytesPerSecond = SAMPLE_RATE.toLong() * BYTES_PER_SAMPLE * CHANNEL_COUNT
        return if (bytesPerSecond == 0L) 0L else (bytes * 1_000L / bytesPerSecond)
    }

    /**
     * Menghitung berapa SAMPEL (bukan byte) yang dihasilkan selama [durationMs] milidetik.
     * Berguna untuk Silero VAD yang menerima input dalam satuan jumlah sampel, bukan byte.
     *
     * Rumus: samples = (durationMs / 1000) × SAMPLE_RATE
     * Contoh: 30 ms = 0,030 × 16.000 = 480 sampel (ukuran chunk VAD yang umum)
     *
     * @param durationMs Durasi dalam milidetik.
     * @return Jumlah sampel audio (integer).
     */
    fun durationMsToSamples(durationMs: Long): Int {
        require(durationMs >= 0) { "durationMs tidak boleh negatif: $durationMs" }
        return (durationMs.toDouble() / 1_000.0 * SAMPLE_RATE).toInt()
    }

    // ================================================================================
    // 5. FUNGSI INTERNAL
    // ================================================================================

    /**
     * Menghitung ukuran buffer AudioRecord yang aman untuk perangkat saat ini.
     * Dipanggil via bufferSizeProvider (lazy) saat AudioConfig pertama kali diakses.
     */
    private fun calculateBufferSize(): Int {
        val minBuffer = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_ENCODING)
        // Jika getMinBufferSize mengembalikan ERROR (-1 atau -2), pakai default 8192 byte.
        return if (minBuffer > 0) minBuffer * 2 else DEFAULT_BUFFER_SIZE
    }
}
