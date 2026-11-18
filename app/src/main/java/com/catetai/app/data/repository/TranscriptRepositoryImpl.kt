package com.catetai.app.data.repository

import com.catetai.app.data.local.dao.TranscriptDao
import com.catetai.app.data.mapper.toDomain
import com.catetai.app.data.mapper.toEntity
import com.catetai.app.domain.model.TranscriptSegment
import com.catetai.app.domain.repository.TranscriptRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Implementasi konkret dari [TranscriptRepository] yang menghubungkan Room [TranscriptDao]
 * dengan model domain [TranscriptSegment].
 */
@Singleton
class TranscriptRepositoryImpl @Inject constructor(
    private val transcriptDao: TranscriptDao
) : TranscriptRepository {

    override fun getSegmentsByRecordingId(recordingId: String): Flow<List<TranscriptSegment>> =
        transcriptDao.getSegmentsByRecordingId(recordingId).map { entities ->
            entities.map { it.toDomain() }
        }

    override suspend fun getSegmentsByRecordingIdOnce(recordingId: String): List<TranscriptSegment> =
        transcriptDao.getSegmentsByRecordingIdOnce(recordingId).map { it.toDomain() }

    override suspend fun insertSegments(segments: List<TranscriptSegment>): Result<Unit> = runCatching {
        transcriptDao.insertSegments(segments.map { it.toEntity() })
    }

    override suspend fun deleteSegmentsByRecordingId(recordingId: String): Result<Unit> = runCatching {
        transcriptDao.deleteSegmentsByRecordingId(recordingId)
    }

    override suspend fun getSegmentCount(recordingId: String): Int =
        transcriptDao.getSegmentCount(recordingId)
}
