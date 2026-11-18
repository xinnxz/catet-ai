package com.catetai.app.engine.whisper

/**
 * Konfigurasi parameter inferensi mesin transkripsi Whisper On-Device.
 *
 * Mengusung prinsip "Zero Configuration" untuk pengguna:
 * Pengguna tidak perlu mengatur bahasa atau mengetik kosakata secara manual.
 * Secara otomatis backend menyuntikkan [language] = "id" (Bahasa Indonesia)
 * dan [DEFAULT_MEETING_VOCABULARY] sebagai prompt biasing cerdas agar istilah rapat umum
 * langsung dikenali dengan akurasi tinggi.
 *
 * @param language Kode bahasa ISO 639-1 ("id" untuk Bahasa Indonesia, "en" untuk Bahasa Inggris).
 * @param customVocabulary Daftar kata/frasa untuk prompt biasing (otomatis diisi istilah rapat jika kosong).
 * @param numThreads Jumlah thread CPU yang dialokasikan (optimal 4 thread pada mobile SoC).
 * @param translateToEnglish Jika true, menerjemahkan ucapan langsung ke Bahasa Inggris.
 */
data class WhisperConfig(
    val language: String = "id",
    val customVocabulary: List<String> = DEFAULT_MEETING_VOCABULARY,
    val numThreads: Int = 4,
    val translateToEnglish: Boolean = false
) {
    companion object {
        /**
         * Kosakata rapat bawaan di backend untuk prompt biasing otomatis (Zero-Config).
         * Berisi istilah rapat, singkatan kerja, dan kata kunci notulensi umum di Indonesia.
         */
        val DEFAULT_MEETING_VOCABULARY = listOf(
            "notulensi",
            "rapat",
            "agenda",
            "pembahasan",
            "kesepakatan",
            "tindak lanjut",
            "deadline",
            "PIC",
            "evaluasi",
            "koordinasi",
            "anggaran",
            "proyek",
            "sprint",
            "standup",
            "retrospektif",
            "WFO",
            "WFH",
            "Catet AI"
        )
    }
}
