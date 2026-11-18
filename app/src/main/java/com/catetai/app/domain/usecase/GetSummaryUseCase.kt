package com.catetai.app.domain.usecase

import com.catetai.app.domain.model.Summary
import com.catetai.app.domain.repository.SummaryRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Use case untuk mengamati data notulensi rapat secara reaktif.
 *
 * Digunakan oleh Tab Ringkasan di DetailScreen agar antarmuka
 * langsung terbarui begitu proses perangkuman selesai.
 */
@Singleton
class GetSummaryUseCase @Inject constructor(
    private val summaryRepository: SummaryRepository
) {

    /**
     * Mengalirkan notulensi untuk [recordingId].
     *
     * @param recordingId ID unik rekaman induk.
     * @return [Flow] yang memancarkan [Summary] atau null jika belum ada notulensi.
     */
    operator fun invoke(recordingId: String): Flow<Summary?> =
        summaryRepository.getSummaryByRecordingId(recordingId)

    /**
     * Mengambil notulensi sekali jalan (one-shot).
     */
    suspend fun getOnce(recordingId: String): Summary? =
        summaryRepository.getSummaryByRecordingIdOnce(recordingId)
}
