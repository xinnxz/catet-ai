package com.catetai.app.legacy.recorder

/**
 * AudioConfig — Konstanta pipeline audio Catet AI.
 *
 * File ini hanya berisi `const val` murni — tidak ada Android API, tidak ada state,
 * tidak ada fungsi yang perlu di-mock. Dapat diuji sepenuhnya di JVM tanpa emulator.
 *
 * Tanggung jawab yang TIDAK ada di sini (sengaja dipindahkan):
 *   • AudioRecord.getMinBufferSize()  → AudioRecorderManager (F1-04), di-inject via Hilt
 *   • AudioFormat.CHANNEL_IN_MONO    → AudioRecorderManager, dipakai langsung saat membuat AudioRecord
 *   • AudioFormat.ENCODING_PCM_16BIT → AudioRecorderManager
 *   • MediaRecorder.AudioSource.MIC  → AudioRecorderManager
 *
 * Prinsip: file konstanta murni tidak boleh bergantung pada Android framework.
 * Ini memastikan AudioConfig selalu 100% testable di JVM tanpa mock apapun.
 *
 * ======================================================================================
 * PENJELASAN TEKNIS FORMAT PCM (Pulse-Code Modulation)
 * ======================================================================================
 * PCM adalah cara merepresentasikan sinyal audio analog sebagai urutan angka digital.
 * Setiap "sampel" adalah pengukuran amplitudo sinyal pada satu titik waktu.
 *
 *   • SAMPLE_RATE = 16.000 Hz: Kita mengambil 16.000 pengukuran per detik.
 *     Berdasarkan Teorema Nyquist-Shannon, ini cukup untuk merepresentasikan frekuensi
 *     suara hingga 8.000 Hz — jauh di atas rentang vokal manusia (85 Hz–3.400 Hz).
 *
 *   • BYTES_PER_SAMPLE = 2: Setiap sampel adalah bilangan bulat 16-bit signed
 *     (rentang −32.768 hingga +32.767), memberikan dynamic range 96 dB.
 *
 *   • CHANNEL_COUNT = 1 (Mono): Satu saluran. Ini menghemat penyimpanan setengahnya
 *     dan sesuai dengan format input Whisper dan Silero VAD.
 *
 * KALKULASI UKURAN FILE AUDIO MENTAH (PCM, tanpa header):
 *   Bytes/detik  = 16.000 × 2 × 1 = 32.000 byte/detik
 *   1 menit rapat  = 32.000 × 60       = 1.920.000 byte  ≈ 1,83 MB
 *   1 jam rapat    = 32.000 × 3.600    = 115.200.000 byte ≈ 109,9 MB
 *   2 jam rapat    = 32.000 × 7.200    = 230.400.000 byte ≈ 219,7 MB
 * ======================================================================================
 *
 * UKURAN CHUNK SILERO VAD v5 (16 kHz):
 *   Silero VAD v5 pada 16 kHz mendukung dua ukuran chunk tetap:
 *     • 512 sampel (32 ms)  — digunakan Catet AI (VAD_CHUNK_SAMPLES)
 *     • 256 sampel (16 ms)  — alternatif untuk latensi lebih rendah, akurasi sedikit turun
 *   Referensi: https://github.com/snakers4/silero-vad (README, bagian "ONNX Usage")
 *
 *   Catatan: 480 sampel (30 ms) BUKAN ukuran chunk yang didukung Silero VAD v5.
 *   480 sampel berasal dari WebRTC VAD — model yang berbeda. Jangan tertukar.
 * ======================================================================================
 */
object AudioConfig {

    // ================================================================================
    // 1. FORMAT AUDIO INTI
    // (Gunakan konstanta ini sebagai referensi saat membuat AudioRecord di AudioRecorderManager)
    // ================================================================================

    /** Frekuensi sampling: 16.000 Hz — standar Whisper dan Silero VAD. */
    const val SAMPLE_RATE: Int = 16_000

    /** Jumlah byte per sampel audio. PCM 16-bit = 2 byte (short int signed). */
    const val BYTES_PER_SAMPLE: Int = 2

    /** Jumlah channel audio. Mono = 1; dipilih karena Whisper dan VAD mengharapkan input mono. */
    const val CHANNEL_COUNT: Int = 1

    /** Jumlah byte yang dihasilkan per detik rekaman. Diturunkan dari 3 konstanta di atas. */
    const val BYTES_PER_SECOND: Int = SAMPLE_RATE * BYTES_PER_SAMPLE * CHANNEL_COUNT // 32.000

    // ================================================================================
    // 2. KONFIGURASI BUFFER & FLUSH
    // ================================================================================

    /**
     * Ukuran buffer default dalam byte — dipakai sebagai fallback saat
     * AudioRecord.getMinBufferSize() gagal, dan sebagai referensi di test instrumented.
     * 8.192 byte = 256 ms audio pada 16 kHz PCM 16-bit mono.
     * AudioRecord.getMinBufferSize() sendiri dipanggil di AudioRecorderManager (F1-04).
     */
    const val DEFAULT_BUFFER_SIZE_BYTES: Int = 8_192

    /**
     * Interval waktu flush buffer ke disk, dalam milidetik.
     * Setiap 5 detik, data audio di memori dipaksa ditulis ke file untuk mencegah
     * kehilangan data saat proses latar belakang dimatikan paksa oleh OS
     * (penting untuk perangkat Transsion/HiOS yang agresif membunuh background process).
     */
    const val FLUSH_INTERVAL_MS: Long = 5_000L

    // ================================================================================
    // 3. FORMAT FILE WAV
    // ================================================================================

    /**
     * Ukuran header RIFF WAV dalam byte — selalu 44 byte sesuai standar.
     * Referensi: http://soundfile.sapp.org/doc/WaveFormat/
     * Dipakai oleh WavHeaderUtil untuk menghitung posisi seek saat update data size.
     */
    const val WAV_HEADER_SIZE_BYTES: Int = 44

    // ================================================================================
    // 4. KONSTANTA SILERO VAD
    // ================================================================================

    /**
     * Jumlah sampel per chunk yang diminta oleh Silero VAD v5 pada 16 kHz.
     * 512 sampel = 32 ms audio.
     *
     * Ini adalah satu-satunya ukuran chunk yang direkomendasikan untuk Silero VAD v5
     * di 16 kHz. Mengirim chunk berukuran berbeda (mis. 480) akan menyebabkan error
     * atau akurasi VAD yang sangat buruk.
     *
     * Referensi: https://github.com/snakers4/silero-vad (ONNX Usage Guide)
     */
    const val VAD_CHUNK_SAMPLES: Int = 512 // 32 ms @ 16 kHz

    /**
     * Ukuran chunk VAD dalam byte (512 sampel × 2 byte/sampel × 1 channel = 1.024 byte).
     * Ini yang dikirimkan ke model ONNX Silero VAD setiap iterasi.
     */
    const val VAD_CHUNK_BYTES: Int = VAD_CHUNK_SAMPLES * BYTES_PER_SAMPLE * CHANNEL_COUNT // 1.024

    // ================================================================================
    // 5. AMBANG BATAS PENYIMPANAN DISK
    // ================================================================================

    /**
     * Batas peringatan ruang disk tersisa (100 MB).
     * Saat ruang tersisa turun di bawah nilai ini, tampilkan notifikasi peringatan
     * di layar "sedang merekam" agar pengguna bisa mengakhiri rapat lebih awal
     * atau menghapus rekaman lama (F1-05).
     */
    const val LOW_DISK_WARNING_BYTES: Long = 100L * 1024 * 1024  // 100 MB

    /**
     * Batas auto-stop perekaman karena disk hampir penuh (50 MB).
     * Saat ruang tersisa turun di bawah nilai ini, perekaman dihentikan otomatis
     * untuk mencegah file WAV terpotong di tengah (yang akan menghasilkan file korup).
     * Menyisakan 50 MB memastikan ada cukup ruang untuk finalisasi WAV header dan
     * enkripsi EncryptedFile setelah rekaman berhenti.
     */
    const val MIN_DISK_SPACE_BYTES: Long = 50L * 1024 * 1024    // 50 MB

    // ================================================================================
    // 6. AMBANG BATAS BATERAI
    // ================================================================================

    /**
     * Persentase baterai untuk peringatan dini (5%).
     * Tampilkan notifikasi ringan agar pengguna sadar baterai hampir habis.
     */
    const val LOW_BATTERY_WARNING_PERCENT: Int = 5

    /**
     * Persentase baterai untuk auto-stop kritis (2%).
     * Di bawah 2%, risiko HP mati mendadak sangat tinggi, yang akan memotong file WAV.
     * Auto-stop memastikan file di-finalisasi dengan benar sebelum HP kehabisan daya.
     */
    const val CRITICAL_BATTERY_PERCENT: Int = 2

    // ================================================================================
    // 7. FUNGSI UTILITAS KONVERSI WAKTU ↔ BYTE
    // ================================================================================

    /**
     * Konversi durasi [durationMs] milidetik → jumlah byte PCM.
     *
     * Rumus: bytes = (durationMs / 1.000) × SAMPLE_RATE × BYTES_PER_SAMPLE × CHANNEL_COUNT
     *
     * Contoh:
     *   1.000 ms (1 detik) = 1 × 16.000 × 2 × 1 = 32.000 byte
     *   60.000 ms (1 menit) = 60 × 32.000 = 1.920.000 byte (≈ 1,83 MB)
     *
     * @param durationMs Durasi audio dalam milidetik. Harus ≥ 0.
     * @return Jumlah byte PCM (tidak termasuk 44-byte WAV header).
     */
    fun durationMsToBytes(durationMs: Long): Long {
        require(durationMs >= 0) { "durationMs tidak boleh negatif: $durationMs" }
        return durationMs * BYTES_PER_SECOND / 1_000L
    }

    /**
     * Konversi [bytes] byte PCM → durasi milidetik.
     *
     * Rumus: durationMs = bytes / BYTES_PER_SECOND × 1.000
     *
     * @param bytes Jumlah byte PCM (tidak termasuk WAV header). Harus ≥ 0.
     * @return Durasi dalam milidetik.
     */
    fun bytesToDurationMs(bytes: Long): Long {
        require(bytes >= 0) { "bytes tidak boleh negatif: $bytes" }
        return bytes * 1_000L / BYTES_PER_SECOND
    }

    /**
     * Konversi durasi [durationMs] milidetik → jumlah sampel audio.
     * Berguna saat membagi audio menjadi chunk untuk dikirim ke Silero VAD.
     *
     * Rumus: samples = durationMs × SAMPLE_RATE / 1.000
     * Contoh: 32 ms × 16.000 / 1.000 = 512 sampel (= VAD_CHUNK_SAMPLES)
     *
     * @param durationMs Durasi dalam milidetik. Harus ≥ 0.
     * @return Jumlah sampel audio (integer, dibulatkan ke bawah).
     */
    fun durationMsToSamples(durationMs: Long): Int {
        require(durationMs >= 0) { "durationMs tidak boleh negatif: $durationMs" }
        return (durationMs * SAMPLE_RATE / 1_000L).toInt()
    }
}
