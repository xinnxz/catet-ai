package com.catetai.app.data.mapper

import com.catetai.app.data.local.entity.RecordingEntity
import com.catetai.app.domain.model.Recording
import com.catetai.app.domain.model.RecordingStatus
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Unit test untuk [toDomain] dan [toEntity] mapper.
 */
class RecordingMapperTest {

    @Test
    fun toDomain_mapsAllFieldsAccurately() {
        val entity = RecordingEntity(
            id = "rec-123",
            title = "Rapat Sinkronisasi",
            audioFilePath = "/path/to/audio.wav",
            durationMs = 60_000L,
            fileSizeBytes = 1_920_044L,
            sampleRate = 16_000,
            status = RecordingStatus.TRANSCRIBED,
            wasSilencedBySystem = true,
            customVocabulary = "Catet AI, Jetpack Compose",
            processingEngine = "whisper-base",
            createdAt = 1_000L,
            updatedAt = 2_000L
        )

        val domain = entity.toDomain()

        assertEquals(entity.id, domain.id)
        assertEquals(entity.title, domain.title)
        assertEquals(entity.audioFilePath, domain.audioFilePath)
        assertEquals(entity.durationMs, domain.durationMs)
        assertEquals(entity.fileSizeBytes, domain.fileSizeBytes)
        assertEquals(entity.sampleRate, domain.sampleRate)
        assertEquals(entity.status, domain.status)
        assertEquals(entity.wasSilencedBySystem, domain.wasSilencedBySystem)
        assertEquals(entity.customVocabulary, domain.customVocabulary)
        assertEquals(entity.processingEngine, domain.processingEngine)
        assertEquals(entity.createdAt, domain.createdAt)
        assertEquals(entity.updatedAt, domain.updatedAt)
    }

    @Test
    fun toEntity_mapsAllFieldsAccurately() {
        val domain = Recording(
            id = "rec-456",
            title = "Diskusi Desain",
            audioFilePath = "/data/audio.wav",
            durationMs = 120_000L,
            fileSizeBytes = 3_840_044L,
            sampleRate = 16_000,
            status = RecordingStatus.SUMMARIZED,
            wasSilencedBySystem = false,
            customVocabulary = null,
            processingEngine = "qwen-2.5-3b",
            createdAt = 5_000L,
            updatedAt = 6_000L
        )

        val entity = domain.toEntity()

        assertEquals(domain.id, entity.id)
        assertEquals(domain.title, entity.title)
        assertEquals(domain.audioFilePath, entity.audioFilePath)
        assertEquals(domain.durationMs, entity.durationMs)
        assertEquals(domain.fileSizeBytes, entity.fileSizeBytes)
        assertEquals(domain.sampleRate, entity.sampleRate)
        assertEquals(domain.status, entity.status)
        assertEquals(domain.wasSilencedBySystem, entity.wasSilencedBySystem)
        assertEquals(domain.customVocabulary, entity.customVocabulary)
        assertEquals(domain.processingEngine, entity.processingEngine)
        assertEquals(domain.createdAt, entity.createdAt)
        assertEquals(domain.updatedAt, entity.updatedAt)
    }

    @Test
    fun roundTrip_entityToDomainToEntity_preservesExactData() {
        val original = RecordingEntity(
            id = "rec-roundtrip",
            title = "Round Trip Test",
            audioFilePath = "/test.wav",
            durationMs = 30_000L,
            fileSizeBytes = 960_044L,
            sampleRate = 16_000,
            status = RecordingStatus.STOPPED,
            wasSilencedBySystem = false,
            customVocabulary = "Alpha, Beta",
            processingEngine = null,
            createdAt = 10_000L,
            updatedAt = 20_000L
        )

        val result = original.toDomain().toEntity()

        assertEquals(original, result)
    }
}
