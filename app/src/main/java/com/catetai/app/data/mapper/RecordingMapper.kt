package com.catetai.app.data.mapper

import com.catetai.app.data.local.entity.RecordingEntity
import com.catetai.app.domain.model.Recording

/**
 * Pemetaan dua arah antara [RecordingEntity] (lapisan basis data Room)
 * dan [Recording] (lapisan model domain Clean Architecture).
 */
fun RecordingEntity.toDomain(): Recording {
    return Recording(
        id = id,
        title = title,
        audioFilePath = audioFilePath,
        durationMs = durationMs,
        fileSizeBytes = fileSizeBytes,
        sampleRate = sampleRate,
        status = status,
        wasSilencedBySystem = wasSilencedBySystem,
        customVocabulary = customVocabulary,
        processingEngine = processingEngine,
        createdAt = createdAt,
        updatedAt = updatedAt
    )
}

fun Recording.toEntity(): RecordingEntity {
    return RecordingEntity(
        id = id,
        title = title,
        audioFilePath = audioFilePath,
        durationMs = durationMs,
        fileSizeBytes = fileSizeBytes,
        sampleRate = sampleRate,
        status = status,
        wasSilencedBySystem = wasSilencedBySystem,
        customVocabulary = customVocabulary,
        processingEngine = processingEngine,
        createdAt = createdAt,
        updatedAt = updatedAt
    )
}
