package com.catetai.app.data.mapper

import com.catetai.app.data.local.entity.RecordingEntity
import com.catetai.app.data.local.entity.SummaryEntity
import com.catetai.app.data.local.entity.TranscriptSegmentEntity
import com.catetai.app.domain.model.Recording
import com.catetai.app.domain.model.RecordingStatus
import com.catetai.app.domain.model.Summary
import com.catetai.app.domain.model.TranscriptSegment

// ================================================================================
// Recording Mappers
// ================================================================================

/**
 * Mengubah [RecordingEntity] (lapisan data Room) menjadi [Recording] (lapisan domain).
 *
 * Konversi RecordingStatus dari String ke enum dilakukan di sini:
 * - Jika nilai status di DB tidak dikenal (mis. dari versi lama), fallback ke FAILED
 *   agar pengguna tahu ada masalah, daripada crash.
 */
fun RecordingEntity.toDomain(): Recording = Recording(
    id = id,
    title = title,
    originalFileName = originalFileName,
    audioFileName = audioFileName,
    durationMs = durationMs,
    fileSizeBytes = fileSizeBytes,
    mimeType = mimeType,
    status = RecordingStatus.entries.firstOrNull { it.name == status }
        ?: RecordingStatus.FAILED,
    importedAt = importedAt,
    customVocabulary = customVocabulary,
    createdAt = createdAt,
    updatedAt = updatedAt
)

/**
 * Mengubah [Recording] (lapisan domain) menjadi [RecordingEntity] (lapisan data Room).
 *
 * RecordingStatus enum dikonversi ke String karena Room tidak bisa menyimpan enum
 * secara langsung (TypeConverter menangani ini di level query, tapi mapper ini
 * memastikan konsistensi saat menggunakan @Update dengan objek penuh).
 */
fun Recording.toEntity(): RecordingEntity = RecordingEntity(
    id = id,
    title = title,
    originalFileName = originalFileName,
    audioFileName = audioFileName,
    durationMs = durationMs,
    fileSizeBytes = fileSizeBytes,
    mimeType = mimeType,
    status = status.name,
    importedAt = importedAt,
    customVocabulary = customVocabulary,
    createdAt = createdAt,
    updatedAt = updatedAt
)

// ================================================================================
// TranscriptSegment Mappers
// ================================================================================

/** Mengubah [TranscriptSegmentEntity] menjadi [TranscriptSegment] domain model. */
fun TranscriptSegmentEntity.toDomain(): TranscriptSegment = TranscriptSegment(
    id = id,
    recordingId = recordingId,
    startMs = startMs,
    endMs = endMs,
    text = text
)

/** Mengubah [TranscriptSegment] domain menjadi [TranscriptSegmentEntity] untuk disimpan ke Room. */
fun TranscriptSegment.toEntity(): TranscriptSegmentEntity = TranscriptSegmentEntity(
    id = id,
    recordingId = recordingId,
    startMs = startMs,
    endMs = endMs,
    text = text
)

// ================================================================================
// Summary Mappers
// ================================================================================

/** Mengubah [SummaryEntity] menjadi [Summary] domain model. */
fun SummaryEntity.toDomain(): Summary = Summary(
    id = id,
    recordingId = recordingId,
    summaryText = summaryText,
    decisions = decisions,
    actionItemsJson = actionItems,
    mode = mode,
    createdAt = createdAt
)

/** Mengubah [Summary] domain menjadi [SummaryEntity] untuk disimpan ke Room. */
fun Summary.toEntity(): SummaryEntity = SummaryEntity(
    id = id,
    recordingId = recordingId,
    summaryText = summaryText,
    decisions = decisions,
    actionItems = actionItemsJson,
    mode = mode,
    createdAt = createdAt
)
