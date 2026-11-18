package com.catetai.app.engine.whisper

/**
 * Konfigurasi parameter inferensi mesin transkripsi Whisper.
 *
 * @param language Kode bahasa ISO 639-1 ("id" untuk Bahasa Indonesia, "en" untuk Bahasa Inggris).
 * @param customVocabulary Daftar kata atau frasa khusus untuk prompt biasing (nama orang, istilah teknis, dsb.).
 * @param numThreads Jumlah thread CPU yang dialokasikan untuk inferensi (disarankan 4 pada perangkat mobile).
 * @param translateToEnglish Jika true, menerjemahkan ucapan langsung ke Bahasa Inggris.
 */
data class WhisperConfig(
    val language: String = "id",
    val customVocabulary: List<String> = emptyList(),
    val numThreads: Int = 4,
    val translateToEnglish: Boolean = false
)
