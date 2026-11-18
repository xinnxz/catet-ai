package com.catetai.app.domain.repository

import com.catetai.app.domain.model.SearchResultItem
import com.catetai.app.domain.model.TranscriptSegment
import kotlinx.coroutines.flow.Flow

/**
 * Kontrak repositori untuk penyimpanan dan pembacaan segmen transkrip verbatim ucapan.
 *
 * Mengikuti Clean Architecture: Interface didefinisikan di lapisan domain,
 * sedangkan implementasinya berada di lapisan data ([com.catetai.app.data.repository.TranscriptRepositoryImpl]).
 */
interface TranscriptRepository {

    /**
     * Mengalirkan seluruh segmen transkrip untuk satu rekaman secara reaktif.
     * Segmen otomatis diurutkan berdasarkan [TranscriptSegment.startMs].
     *
     * @param recordingId ID unik rekaman induk.
     */
    fun getSegmentsByRecordingId(recordingId: String): Flow<List<TranscriptSegment>>

    /**
     * Mengambil seluruh segmen transkrip untuk rekaman tertentu dalam satu pemanggilan (one-shot).
     *
     * @param recordingId ID unik rekaman induk.
     */
    suspend fun getSegmentsByRecordingIdOnce(recordingId: String): List<TranscriptSegment>

    /**
     * Menyimpan daftar segmen transkrip ke database.
     *
     * @param segments Daftar segmen transkrip yang akan disimpan.
     */
    suspend fun insertSegments(segments: List<TranscriptSegment>): Result<Unit>

    /**
     * Menghapus semua segmen transkrip milik suatu rekaman (digunakan saat "Proses Ulang").
     *
     * @param recordingId ID unik rekaman induk.
     */
    suspend fun deleteSegmentsByRecordingId(recordingId: String): Result<Unit>

    /**
     * Mengambil jumlah total segmen yang sudah tersimpan untuk rekaman tertentu.
     *
     * @param recordingId ID unik rekaman induk.
     */
    suspend fun getSegmentCount(recordingId: String): Int

    /**
     * Mencari kalimat transkrip di seluruh rekaman menggunakan FTS4 (F4-03).
     *
     * @param query Kata kunci yang dicari.
     */
    suspend fun searchTranscripts(query: String): Result<List<SearchResultItem>>
}
