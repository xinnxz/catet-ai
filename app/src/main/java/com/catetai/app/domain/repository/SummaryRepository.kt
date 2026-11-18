package com.catetai.app.domain.repository

import com.catetai.app.domain.model.Summary
import kotlinx.coroutines.flow.Flow

/**
 * Kontrak repositori domain untuk notulensi rapat (Summary).
 *
 * Menjembatani lapisan domain (use case) dengan lapisan data (Room / SummaryDao)
 * sesuai prinsip Clean Architecture — domain layer tidak mengenal Room atau SQL.
 *
 * # Relasi dengan Recording
 * Setiap [Summary] terikat 1:1 dengan Recording melalui [recordingId].
 * - Jika rekaman dihapus, notulensi ikut terhapus (CASCADE di level Room).
 * - Jika pengguna "Proses Ulang", notulensi lama diganti (REPLACE).
 */
interface SummaryRepository {

    /**
     * Mengamati notulensi secara reaktif untuk rekaman tertentu.
     *
     * Flow memancarkan null jika belum ada notulensi, lalu memancarkan
     * data terbaru saat notulensi baru disimpan (cocok untuk Tab Ringkasan
     * di DetailScreen yang perlu langsung berubah saat proses selesai).
     *
     * @param recordingId ID unik rekaman induk.
     * @return Flow reaktif yang memancarkan [Summary] atau null.
     */
    fun getSummaryByRecordingId(recordingId: String): Flow<Summary?>

    /**
     * Mengambil notulensi sekali jalan (one-shot) tanpa berlangganan.
     * Digunakan untuk proses ekspor PDF atau cek ketersediaan notulensi.
     *
     * @param recordingId ID unik rekaman induk.
     * @return [Summary] atau null jika belum ada.
     */
    suspend fun getSummaryByRecordingIdOnce(recordingId: String): Summary?

    /**
     * Menyimpan (atau menggantikan) notulensi rapat.
     *
     * Jika sudah ada notulensi untuk [summary.recordingId] yang sama,
     * notulensi lama otomatis ditimpa (strategi REPLACE di Room).
     *
     * @param summary Objek domain notulensi yang akan disimpan.
     * @return [Result.success] dengan ID baris, atau [Result.failure] jika gagal.
     */
    suspend fun insertSummary(summary: Summary): Result<Long>

    /**
     * Menghapus notulensi untuk rekaman tertentu.
     * Digunakan saat pengguna memilih "Proses Ulang" mode berbeda.
     *
     * @param recordingId ID rekaman yang notulensinya akan dihapus.
     */
    suspend fun deleteSummaryByRecordingId(recordingId: String): Result<Unit>
}
