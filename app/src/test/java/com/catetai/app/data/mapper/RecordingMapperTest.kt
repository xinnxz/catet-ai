package com.catetai.app.data.mapper

import com.catetai.app.data.local.entity.RecordingEntity
import com.catetai.app.data.local.entity.SummaryEntity
import com.catetai.app.data.local.entity.TranscriptSegmentEntity
import com.catetai.app.domain.model.Recording
import com.catetai.app.domain.model.RecordingStatus
import com.catetai.app.domain.model.Summary
import com.catetai.app.domain.model.TranscriptSegment
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Unit test untuk semua fungsi mapper di [RecordingMapper.kt].
 *
 * Mencakup tiga pasang mapper:
 * 1. [RecordingEntity] ↔ [Recording]
 * 2. [TranscriptSegmentEntity] ↔ [TranscriptSegment]
 * 3. [SummaryEntity] ↔ [Summary]
 *
 * Setiap pasang diuji dari arah data→domain, domain→data,
 * dan round-trip (data→domain→data harus menghasilkan objek yang sama).
 */
class RecordingMapperTest {

    // ════════════════════════════════════════════════════════════════════════════
    // Recording Mappers
    // ════════════════════════════════════════════════════════════════════════════

    @Test
    fun recordingEntity_toDomain_mapsAllFieldsAccurately() {
        val entity = RecordingEntity(
            id = "rec-123",
            title = "Rapat Sinkronisasi",
            originalFileName = "meeting_2024.m4a",
            audioFileName = "audio_1700000000000.enc",
            durationMs = 60_000L,
            fileSizeBytes = 1_920_044L,
            mimeType = "audio/mp4",
            status = "TRANSCRIBING",
            importedAt = 1_700_000_000_000L,
            customVocabulary = "Catet AI, Jetpack Compose",
            createdAt = 1_000L,
            updatedAt = 2_000L
        )

        val domain = entity.toDomain()

        assertEquals(entity.id, domain.id)
        assertEquals(entity.title, domain.title)
        assertEquals(entity.originalFileName, domain.originalFileName)
        assertEquals(entity.audioFileName, domain.audioFileName)
        assertEquals(entity.durationMs, domain.durationMs)
        assertEquals(entity.fileSizeBytes, domain.fileSizeBytes)
        assertEquals(entity.mimeType, domain.mimeType)
        assertEquals(RecordingStatus.TRANSCRIBING, domain.status)
        assertEquals(entity.importedAt, domain.importedAt)
        assertEquals(entity.customVocabulary, domain.customVocabulary)
        assertEquals(entity.createdAt, domain.createdAt)
        assertEquals(entity.updatedAt, domain.updatedAt)
    }

    @Test
    fun recordingEntity_toDomain_unknownStatus_fallsBackToFailed() {
        // Jika status di DB berisi nilai tidak dikenal (misal: migrasi dari versi lama),
        // mapper harus fallback ke FAILED agar tidak crash.
        val entity = RecordingEntity(
            id = "rec-456",
            title = "Rapat Rusak",
            originalFileName = "broken.mp3",
            audioFileName = "audio_broken.enc",
            status = "NILAI_TIDAK_DIKENAL" // Nilai korup / tidak valid
        )

        val domain = entity.toDomain()

        assertEquals(RecordingStatus.FAILED, domain.status)
    }

    @Test
    fun recording_toEntity_mapsAllFieldsAccurately() {
        val domain = Recording(
            id = "rec-789",
            title = "Diskusi Desain",
            originalFileName = "design_review.ogg",
            audioFileName = "audio_1700000001000.enc",
            durationMs = 120_000L,
            fileSizeBytes = 3_840_044L,
            mimeType = "audio/ogg",
            status = RecordingStatus.COMPLETED,
            importedAt = 1_700_000_001_000L,
            customVocabulary = null,
            createdAt = 5_000L,
            updatedAt = 6_000L
        )

        val entity = domain.toEntity()

        assertEquals(domain.id, entity.id)
        assertEquals(domain.title, entity.title)
        assertEquals(domain.originalFileName, entity.originalFileName)
        assertEquals(domain.audioFileName, entity.audioFileName)
        assertEquals(domain.durationMs, entity.durationMs)
        assertEquals(domain.fileSizeBytes, entity.fileSizeBytes)
        assertEquals(domain.mimeType, entity.mimeType)
        assertEquals("COMPLETED", entity.status) // Status disimpan sebagai String di DB
        assertEquals(domain.importedAt, entity.importedAt)
        assertEquals(domain.customVocabulary, entity.customVocabulary)
        assertEquals(domain.createdAt, entity.createdAt)
        assertEquals(domain.updatedAt, entity.updatedAt)
    }

    @Test
    fun recording_roundTrip_entityToDomainToEntity_preservesExactData() {
        val original = RecordingEntity(
            id = "rec-roundtrip",
            title = "Round Trip Test",
            originalFileName = "audio.m4a",
            audioFileName = "audio_rt.enc",
            durationMs = 30_000L,
            fileSizeBytes = 960_044L,
            mimeType = "audio/mp4",
            status = "IMPORTED",
            importedAt = 10_000L,
            customVocabulary = "Alpha, Beta",
            createdAt = 10_000L,
            updatedAt = 20_000L
        )

        val result = original.toDomain().toEntity()

        assertEquals(original, result)
    }

    // ════════════════════════════════════════════════════════════════════════════
    // TranscriptSegment Mappers
    // ════════════════════════════════════════════════════════════════════════════

    @Test
    fun transcriptSegmentEntity_toDomain_mapsAllFieldsAccurately() {
        val entity = TranscriptSegmentEntity(
            id = 1L,
            recordingId = "rec-ts-001",
            startMs = 15_000L,
            endMs = 18_500L,
            text = "Halo selamat pagi, saya ingin membahas roadmap kuartal ini.",
            createdAt = 9_000L
        )

        val domain = entity.toDomain()

        assertEquals(entity.id, domain.id)
        assertEquals(entity.recordingId, domain.recordingId)
        assertEquals(entity.startMs, domain.startMs)
        assertEquals(entity.endMs, domain.endMs)
        assertEquals(entity.text, domain.text)
    }

    @Test
    fun transcriptSegment_toEntity_mapsAllFieldsAccurately() {
        val domain = TranscriptSegment(
            id = 2L,
            recordingId = "rec-ts-002",
            startMs = 30_000L,
            endMs = 35_000L,
            text = "Deadline untuk fitur ini adalah akhir bulan."
        )

        val entity = domain.toEntity()

        assertEquals(domain.id, entity.id)
        assertEquals(domain.recordingId, entity.recordingId)
        assertEquals(domain.startMs, entity.startMs)
        assertEquals(domain.endMs, entity.endMs)
        assertEquals(domain.text, entity.text)
    }

    @Test
    fun transcriptSegment_roundTrip_entityToDomainToEntity_preservesExactData() {
        val original = TranscriptSegmentEntity(
            id = 99L,
            recordingId = "rec-ts-rt",
            startMs = 0L,
            endMs = 5_000L,
            text = "Selamat datang di rapat hari ini.",
            createdAt = 1_000L
        )

        // round-trip: entity → domain → entity
        // Catatan: `createdAt` TIDAK ada di domain model TranscriptSegment,
        // sehingga setelah round-trip field ini akan menggunakan nilai default
        // (System.currentTimeMillis()). Round-trip TIDAK menjamin kesamaan `createdAt`.
        val domainSeg = original.toDomain()
        val entityBack = domainSeg.toEntity()

        assertEquals(original.id, entityBack.id)
        assertEquals(original.recordingId, entityBack.recordingId)
        assertEquals(original.startMs, entityBack.startMs)
        assertEquals(original.endMs, entityBack.endMs)
        assertEquals(original.text, entityBack.text)
    }

    // ════════════════════════════════════════════════════════════════════════════
    // Summary Mappers
    // ════════════════════════════════════════════════════════════════════════════

    @Test
    fun summaryEntity_toDomain_mapsAllFieldsAccurately() {
        val entity = SummaryEntity(
            id = 1L,
            recordingId = "rec-sum-001",
            summaryText = "• Tim sepakat deadline adalah 30 November\n• Anggaran disetujui",
            decisions = "1. Gunakan Kotlin Multiplatform\n2. Rilis Q2",
            actionItems = """[{"task":"Kirim laporan","pic":"Rania","deadline":"Senin"}]""",
            mode = "extractive",
            createdAt = 7_000L
        )

        val domain = entity.toDomain()

        assertEquals(entity.id, domain.id)
        assertEquals(entity.recordingId, domain.recordingId)
        assertEquals(entity.summaryText, domain.summaryText)
        assertEquals(entity.decisions, domain.decisions)
        assertEquals(entity.actionItems, domain.actionItemsJson)
        assertEquals(entity.mode, domain.mode)
        assertEquals(entity.createdAt, domain.createdAt)
    }

    @Test
    fun summary_toEntity_mapsAllFieldsAccurately() {
        val domain = Summary(
            id = 2L,
            recordingId = "rec-sum-002",
            summaryText = "Ringkasan singkat rapat sprint planning.",
            decisions = "1. Sprint 3 dimulai Senin",
            actionItemsJson = "[]",
            mode = "byok_cloud",
            createdAt = 8_000L
        )

        val entity = domain.toEntity()

        assertEquals(domain.id, entity.id)
        assertEquals(domain.recordingId, entity.recordingId)
        assertEquals(domain.summaryText, entity.summaryText)
        assertEquals(domain.decisions, entity.decisions)
        assertEquals(domain.actionItemsJson, entity.actionItems)
        assertEquals(domain.mode, entity.mode)
        assertEquals(domain.createdAt, entity.createdAt)
    }
}
