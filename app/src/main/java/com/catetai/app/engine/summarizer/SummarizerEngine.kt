package com.catetai.app.engine.summarizer

import com.catetai.app.domain.model.TranscriptSegment

/**
 * Hasil keluaran dari proses perangkuman rapat.
 *
 * Sesuai spesifikasi PRD Catet AI v2, notulensi memiliki 3 bagian utama:
 * 1. [summaryText]: Poin-poin ringkasan rapat dalam format Markdown (maksimal 3-5 poin).
 * 2. [decisions]: Daftar keputusan eksplisit/implisit rapat dalam format Markdown bernomor.
 * 3. [actionItemsJson]: Daftar tugas tindak lanjut dalam format JSON array terstruktur.
 * 4. [mode]: Mode perangkum yang digunakan ("extractive" atau "byok_cloud").
 */
data class SummaryResult(
    val summaryText: String,
    val decisions: String,
    val actionItemsJson: String,
    val mode: String
)

/**
 * Kontrak mesin perangkum transkrip rapat (Summarizer Engine).
 *
 * Memisahkan algoritma perangkum (Ekstraktif luring vs Cloud generatif)
 * dari use case dan antarmuka pengguna (Dependency Inversion Principle).
 */
interface SummarizerEngine {

    /**
     * Menghasilkan notulensi rapat terstruktur dari daftar segmen teks transkrip.
     *
     * @param segments Daftar segmen transkrip bertanda waktu.
     * @return [Result.success] dengan [SummaryResult], atau [Result.failure] jika ada kesalahan.
     */
    suspend fun summarize(segments: List<TranscriptSegment>): Result<SummaryResult>
}
