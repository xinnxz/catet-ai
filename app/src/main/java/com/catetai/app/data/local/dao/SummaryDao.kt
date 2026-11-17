package com.catetai.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.catetai.app.data.local.entity.SummaryEntity
import kotlinx.coroutines.flow.Flow

/**
 * SummaryDao — Data Access Object untuk tabel `summaries`.
 *
 * Menyimpan dan mengambil notulensi rapat yang dihasilkan oleh perangkum.
 *
 * # Relasi 1:1 dengan recordings
 * Setiap rekaman hanya boleh punya SATU notulensi aktif. Ini dijaga oleh
 * indeks UNIQUE pada kolom `recording_id` di SummaryEntity.
 *
 * Jika pengguna memilih "Proses Ulang" dengan mode berbeda, notulensi lama
 * dihapus dulu (via deleteSummaryByRecordingId), lalu yang baru disisipkan.
 *
 * # Strategi REPLACE
 * insertSummary menggunakan OnConflictStrategy.REPLACE — jika sudah ada notulensi
 * untuk recording_id yang sama, notulensi lama otomatis digantikan. Ini lebih
 * sederhana dan aman daripada delete + insert manual.
 */
@Dao
interface SummaryDao {

    // ================================================================================
    // INSERT
    // ================================================================================

    /**
     * Menyimpan (atau menggantikan) notulensi untuk sebuah rekaman.
     * Jika sudah ada notulensi dengan recording_id yang sama, notulensi lama digantikan.
     *
     * @param summary Entitas notulensi yang akan disimpan.
     * @return ID baris yang baru disisipkan.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSummary(summary: SummaryEntity): Long

    // ================================================================================
    // SELECT
    // ================================================================================

    /**
     * Mengambil notulensi untuk rekaman tertentu sebagai [Flow] reaktif.
     * Memancarkan null jika belum ada notulensi, dan memancarkan data saat tersedia.
     * Digunakan di Tab Notulensi pada DetailScreen.
     *
     * @param recordingId UUID rekaman.
     * @return Flow yang memancarkan SummaryEntity atau null.
     */
    @Query("SELECT * FROM summaries WHERE recording_id = :recordingId LIMIT 1")
    fun getSummaryByRecordingId(recordingId: String): Flow<SummaryEntity?>

    /**
     * Versi suspend (one-shot) dari [getSummaryByRecordingId].
     * Digunakan untuk proses ekspor PDF yang butuh notulensi sekali jalan.
     *
     * @param recordingId UUID rekaman.
     * @return SummaryEntity atau null jika belum ada notulensi.
     */
    @Query("SELECT * FROM summaries WHERE recording_id = :recordingId LIMIT 1")
    suspend fun getSummaryByRecordingIdOnce(recordingId: String): SummaryEntity?

    // ================================================================================
    // DELETE
    // ================================================================================

    /**
     * Menghapus notulensi untuk rekaman tertentu.
     * Dipanggil saat pengguna memilih "Proses Ulang" agar notulensi lama digantikan.
     * Catatan: Cascade delete dari RecordingDao sudah menangani ini secara otomatis
     * saat rekaman dihapus — fungsi ini hanya untuk reset manual.
     *
     * @param recordingId UUID rekaman yang notulensinya akan dihapus.
     */
    @Query("DELETE FROM summaries WHERE recording_id = :recordingId")
    suspend fun deleteSummaryByRecordingId(recordingId: String)
}
