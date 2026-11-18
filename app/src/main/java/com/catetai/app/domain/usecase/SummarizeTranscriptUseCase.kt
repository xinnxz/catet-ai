package com.catetai.app.domain.usecase

import android.util.Log
import com.catetai.app.data.di.IoDispatcher
import com.catetai.app.domain.model.RecordingStatus
import com.catetai.app.domain.model.Summary
import com.catetai.app.domain.repository.RecordingRepository
import com.catetai.app.domain.repository.SummaryRepository
import com.catetai.app.domain.repository.TranscriptRepository
import com.catetai.app.engine.summarizer.SummarizerEngine
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Use case orkestrator pembuatan notulensi rapat cerdas (Fase 3 / FR-06).
 *
 * Alur Proses:
 * 1. Mengambil seluruh segmen teks transkrip dari [TranscriptRepository].
 * 2. Memastikan transkrip tidak kosong (jika kosong, kembalikan [Result.failure]).
 * 3. Memperbarui status rekaman menjadi [RecordingStatus.SUMMARIZING].
 * 4. Menjalankan inferensi perangkuman via [SummarizerEngine].
 * 5. Menyimpan notulensi yang dihasilkan ke [SummaryRepository].
 * 6. Memperbarui status rekaman menjadi [RecordingStatus.COMPLETED].
 */
@Singleton
class SummarizeTranscriptUseCase @Inject constructor(
    private val recordingRepository: RecordingRepository,
    private val transcriptRepository: TranscriptRepository,
    private val summaryRepository: SummaryRepository,
    private val summarizerEngine: SummarizerEngine,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher
) {

    /** Konstruktor sekunder untuk instansiasi unit test tanpa Hilt */
    constructor(
        recordingRepository: RecordingRepository,
        transcriptRepository: TranscriptRepository,
        summaryRepository: SummaryRepository,
        summarizerEngine: SummarizerEngine
    ) : this(
        recordingRepository,
        transcriptRepository,
        summaryRepository,
        summarizerEngine,
        Dispatchers.IO
    )

    /**
     * Menjalankan proses perangkuman untuk rekaman dengan [recordingId].
     *
     * @param recordingId ID unik rekaman yang transkripnya akan dirangkum.
     * @return [Result.success] dengan objek [Summary], atau [Result.failure] jika ada kesalahan.
     */
    suspend operator fun invoke(recordingId: String): Result<Summary> = withContext(ioDispatcher) {
        try {
            // 1. Ambil segmen transkrip bertanda waktu
            val segments = transcriptRepository.getSegmentsByRecordingIdOnce(recordingId)
            if (segments.isEmpty()) {
                return@withContext Result.failure(
                    IllegalStateException("Tidak ada teks transkrip untuk dirangkum. Jalankan transkripsi terlebih dahulu.")
                )
            }

            // 2. Perbarui status sesi rekaman menjadi SUMMARIZING
            recordingRepository.updateStatus(recordingId, RecordingStatus.SUMMARIZING)

            // 3. Eksekusi perangkuman
            val summaryResult = summarizerEngine.summarize(segments).getOrThrow()

            // 4. Bangun entitas domain Summary
            val summary = Summary(
                recordingId = recordingId,
                summaryText = summaryResult.summaryText,
                decisions = summaryResult.decisions,
                actionItemsJson = summaryResult.actionItemsJson,
                mode = summaryResult.mode
            )

            // 5. Simpan ke database Room
            summaryRepository.insertSummary(summary).getOrThrow()

            // 6. Tandai rekaman sebagai selesai
            recordingRepository.updateStatus(recordingId, RecordingStatus.COMPLETED)

            Log.i(TAG, "Notulensi rapat untuk rekaman [$recordingId] berhasil dibuat dan disimpan.")
            Result.success(summary)
        } catch (e: Exception) {
            Log.e(TAG, "Gagal membuat notulensi untuk rekaman [$recordingId]", e)
            recordingRepository.updateStatus(recordingId, RecordingStatus.FAILED)
            Result.failure(e)
        }
    }

    companion object {
        private const val TAG = "SummarizeUseCase"
    }
}
