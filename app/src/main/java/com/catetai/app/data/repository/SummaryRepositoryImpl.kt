package com.catetai.app.data.repository

import com.catetai.app.data.local.dao.SummaryDao
import com.catetai.app.data.mapper.toDomain
import com.catetai.app.data.mapper.toEntity
import com.catetai.app.domain.model.Summary
import com.catetai.app.domain.repository.SummaryRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Implementasi konkret [SummaryRepository] yang menghubungkan Room [SummaryDao]
 * dengan model domain [Summary] melalui mapper.
 *
 * # Strategi REPLACE
 * [SummaryDao.insertSummary] menggunakan OnConflictStrategy.REPLACE.
 * Jika pengguna menjalankan "Proses Ulang" (re-summarize), notulensi lama
 * dengan recording_id yang sama otomatis ditimpa tanpa harus menghapus dulu.
 */
@Singleton
class SummaryRepositoryImpl @Inject constructor(
    private val summaryDao: SummaryDao
) : SummaryRepository {

    override fun getSummaryByRecordingId(recordingId: String): Flow<Summary?> =
        summaryDao.getSummaryByRecordingId(recordingId).map { entity ->
            entity?.toDomain()
        }

    override suspend fun getSummaryByRecordingIdOnce(recordingId: String): Summary? =
        summaryDao.getSummaryByRecordingIdOnce(recordingId)?.toDomain()

    override suspend fun insertSummary(summary: Summary): Result<Long> = runCatching {
        summaryDao.insertSummary(summary.toEntity())
    }

    override suspend fun deleteSummaryByRecordingId(recordingId: String): Result<Unit> = runCatching {
        summaryDao.deleteSummaryByRecordingId(recordingId)
    }
}
