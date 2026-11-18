package com.catetai.app.engine.summarizer

import com.catetai.app.domain.model.TranscriptSegment
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class ExtractiveSummarizerTest {

    private lateinit var summarizer: ExtractiveSummarizer

    @Before
    fun setUp() {
        summarizer = ExtractiveSummarizer()
    }

    @Test
    fun summarize_withEmptySegments_returnsCleanEmptyState() = runTest {
        val result = summarizer.summarize(emptyList()).getOrThrow()

        assertEquals("extractive", result.mode)
        assertEquals("Tidak ada teks percakapan yang dapat dirangkum.", result.summaryText)
        assertEquals("", result.decisions)
        assertEquals("[]", result.actionItemsJson)
    }

    @Test
    fun summarize_extractsRepresentativeBulletsInChronologicalOrder() = runTest {
        val segments = listOf(
            TranscriptSegment(1, "rec-1", 0, 4000, "Halo selamat pagi rekan-rekan semua."),
            TranscriptSegment(2, "rec-1", 5000, 12000, "Agenda penting pertemuan kita hari ini adalah membahas arsitektur rilis aplikasi."),
            TranscriptSegment(3, "rec-1", 13000, 18000, "Fokus utama kita adalah efisiensi memori pada perangkat RAM 8 GB."),
            TranscriptSegment(4, "rec-1", 19000, 25000, "Target rilis MVP kita jadwalkan pada kuartal pertama tahun depan.")
        )

        val result = summarizer.summarize(segments).getOrThrow()

        assertEquals("extractive", result.mode)
        assertTrue(result.summaryText.contains("•"))
        assertTrue(result.summaryText.contains("Agenda penting pertemuan kita hari ini"))
    }

    @Test
    fun summarize_detectsDecisionsFromKeywords() = runTest {
        val segments = listOf(
            TranscriptSegment(1, "rec-1", 0, 4000, "Setelah diskusi panjang, tim sepakat menggunakan Whisper Large v3 Turbo."),
            TranscriptSegment(2, "rec-1", 5000, 9000, "Kita juga memutuskan untuk menunda fitur perekam bawaan ke rilis berikutnya."),
            TranscriptSegment(3, "rec-1", 10000, 14000, "Cuaca hari ini cukup cerah.")
        )

        val result = summarizer.summarize(segments).getOrThrow()

        assertFalse(result.decisions.isBlank())
        assertTrue(result.decisions.contains("1. Setelah diskusi panjang, tim sepakat menggunakan Whisper Large v3 Turbo."))
        assertTrue(result.decisions.contains("2. Kita juga memutuskan untuk menunda fitur perekam bawaan ke rilis berikutnya."))
        assertFalse(result.decisions.contains("Cuaca hari ini"))
    }

    @Test
    fun summarize_detectsActionItemsWithPicAndDeadline() = runTest {
        val segments = listOf(
            TranscriptSegment(1, "rec-1", 0, 5000, "Tolong siapkan dokumentasi API sebelum hari jumat."),
            TranscriptSegment(2, "rec-1", 6000, 10000, "Nanti saya akan follow up hasil pengujian ke klien."),
            TranscriptSegment(3, "rec-1", 11000, 15000, "Terima kasih semuanya.")
        )

        val result = summarizer.summarize(segments).getOrThrow()

        assertTrue(result.actionItemsJson.contains("Tolong siapkan dokumentasi API"))
        assertTrue(result.actionItemsJson.contains("hari jumat"))
        assertTrue(result.actionItemsJson.contains("follow up"))
    }

    @Test
    fun summarize_whenNoDecisionsFound_returnsEmptyDecisionsString() = runTest {
        val segments = listOf(
            TranscriptSegment(1, "rec-1", 0, 5000, "Ini hanyalah obrolan santai tanpa kesepakatan apa pun."),
            TranscriptSegment(2, "rec-1", 6000, 10000, "Membahas film kemarin malam.")
        )

        val result = summarizer.summarize(segments).getOrThrow()

        assertEquals("", result.decisions)
    }
}
