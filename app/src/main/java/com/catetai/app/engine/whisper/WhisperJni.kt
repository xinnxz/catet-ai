package com.catetai.app.engine.whisper

/**
 * Ikatan JNI ke pustaka native whisper_jni (whisper.cpp).
 *
 * Catatan Arsitektur:
 * Objek ini berstatus `internal` dan tidak boleh diakses langsung dari luar modul engine.
 * Gunakan [WhisperEngine] sebagai wrapper tingkat tinggi.
 */
internal object WhisperJni {

    private var isLibraryLoaded = false

    init {
        loadLibrarySilently()
    }

    /**
     * Memuat pustaka `libwhisper_jni.so` secara aman.
     * Mengabaikan UnsatisfiedLinkError saat dijalankan di lingkungan JVM murni (misal: Unit Test di Host).
     */
    fun loadLibrarySilently(): Boolean {
        if (!isLibraryLoaded) {
            try {
                System.loadLibrary("whisper_jni")
                isLibraryLoaded = true
            } catch (e: UnsatisfiedLinkError) {
                // Di unit test JVM (komputer host Windows/Linux/Mac), library Android .so
                // tidak dapat dimuat langsung kecuali ada mock atau wrapper.
                isLibraryLoaded = false
            }
        }
        return isLibraryLoaded
    }

    /**
     * Memuat model GGUF ke memori perangkat.
     *
     * @param modelPath Path absolut ke berkas model GGUF di penyimpanan internal.
     * @return Handle pointer (Long) jika berhasil, atau 0L jika gagal.
     */
    external fun nativeInit(modelPath: String): Long

    /**
     * Menjalankan transkripsi inferensi audio pada berkas PCM 16kHz 16-bit Mono.
     *
     * @param contextHandle Pointer context hasil dari [nativeInit].
     * @param pcmPath Path berkas PCM 16kHz 16-bit Mono mentah.
     * @param language Kode bahasa ISO (misal: "id" untuk Bahasa Indonesia, "en" untuk Bahasa Inggris).
     * @param initialPrompt Kosakata kustom atau petunjuk awalan (prompt biasing) untuk meningkatkan akurasi istilah teknis.
     * @param numThreads Jumlah thread CPU yang digunakan untuk inferensi (disarankan 4 pada perangkat mobile).
     * @param progressCallback Callback progres berkala (0.0f - 1.0f).
     * @return Array 2 dimensi berformat `[[startMs: Long, endMs: Long, text: String], ...]` atau null jika gagal/dibatalkan.
     */
    external fun nativeTranscribe(
        contextHandle: Long,
        pcmPath: String,
        language: String,
        initialPrompt: String,
        numThreads: Int,
        progressCallback: (Float) -> Unit
    ): Array<Array<Any>>?

    /**
     * Membatalkan proses inferensi transkripsi yang sedang berjalan secara aman via callback abort.
     *
     * @param contextHandle Pointer context hasil dari [nativeInit].
     */
    external fun nativeCancel(contextHandle: Long)

    /**
     * Membebaskan alokasi memori model C++ di heap. Wajib dipanggil saat sesi transkripsi selesai.
     *
     * @param contextHandle Pointer context hasil dari [nativeInit].
     */
    external fun nativeRelease(contextHandle: Long)
}
