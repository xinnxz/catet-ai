package com.catetai.app.domain.model

/**
 * Pilihan format ekspor dokumen rapat Catet AI (FR-11).
 *
 * @param extension Ekstensi berkas tanpa titik (misal: "pdf", "md", "txt").
 * @param mimeType MIME type standar untuk berbagi berkas via Android Intent.
 * @param displayName Nama tampilan ramah pengguna di dialog UI.
 */
enum class ExportFormat(
    val extension: String,
    val mimeType: String,
    val displayName: String
) {
    /**
     * Dokumen PDF formal dua bagian: (A) Notulensi & Ringkasan, (B) Transkrip Verbatim Lengkap.
     * Menggunakan Android Graphics PdfDocument API tanpa dependensi pihak ketiga.
     */
    PDF("pdf", "application/pdf", "Dokumen PDF (.pdf)"),

    /**
     * Format Markdown terstruktur, cocok untuk Notion, GitHub, Obsidian, atau dokumentasi tim.
     */
    MARKDOWN("md", "text/markdown", "Dokumen Markdown (.md)"),

    /**
     * Berkas teks polos ringan tanpa format, mudah dibuka di aplikasi catatan apa pun.
     */
    TXT("txt", "text/plain", "Teks Polos (.txt)")
}
