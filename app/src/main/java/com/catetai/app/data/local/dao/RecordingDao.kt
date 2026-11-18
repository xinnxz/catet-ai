package com.catetai.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.catetai.app.data.local.entity.RecordingEntity
import kotlinx.coroutines.flow.Flow

/**
 * RecordingDao v2 — Data Access Object untuk tabel `recordings` (alur impor).
 *
 * # Panduan Coroutine & Flow
 * - `suspend fun`                     : Operasi one-shot (insert, update, delete, query tunggal).
 * - `fun ...: Flow<...>`              : Observasi reaktif — Room memancarkan data terbaru
 *                                       otomatis setiap kali ada perubahan di tabel.
 *
 * # Strategi Update Parsial
 * Query UPDATE parsial (`SET status = ?`) jauh lebih efisien daripada @Update yang
 * mengganti seluruh baris. Status diupdate berkali-kali selama pipeline transkripsi.
 *
 * # Pemilihan Kolom Sort Key
 * Daftar diurutkan berdasarkan `imported_at` (bukan `created_at`) karena:
 * - `imported_at` mencerminkan kapan rekaman masuk ke app — lebih relevan bagi pengguna.
 * - `created_at` bisa sama dengan `imported_at` dalam implementasi sederhana,
 *   tapi dipisahkan untuk fleksibilitas di masa depan.
 */
@Dao
interface RecordingDao {

    // ================================================================================
    // INSERT
    // ================================================================================

    /**
     * Memasukkan entitas rekaman baru ke database.
     * Jika id sudah ada (konflik), seluruh baris digantikan (REPLACE).
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRecording(recording: RecordingEntity)

    // ================================================================================
    // SELECT (Queries)
    // ================================================================================

    /**
     * Mengembalikan semua rekaman sebagai [Flow] reaktif, diurutkan dari terbaru ke terlama.
     * Ini adalah query utama HomeScreen — dipancarkan ulang setiap kali ada perubahan di tabel.
     */
    @Query("SELECT * FROM recordings ORDER BY imported_at DESC")
    fun getAllRecordingsOrderByDateDesc(): Flow<List<RecordingEntity>>

    /** Alias untuk [getAllRecordingsOrderByDateDesc]. */
    fun getAllRecordings(): Flow<List<RecordingEntity>> = getAllRecordingsOrderByDateDesc()

    /**
     * Mengambil satu rekaman berdasarkan ID uniknya.
     * @param id UUID string rekaman yang dicari.
     * @return [RecordingEntity] jika ditemukan, null jika tidak ada.
     */
    @Query("SELECT * FROM recordings WHERE id = :id LIMIT 1")
    suspend fun getRecordingById(id: String): RecordingEntity?

    /**
     * Memeriksa apakah berkas audio dengan nama asli dan ukuran yang sama persis sudah ada di database.
     * Digunakan untuk deteksi duplikasi saat impor (PRD EC-10 / F1-05).
     */
    @Query("SELECT * FROM recordings WHERE original_file_name = :fileName AND file_size_bytes = :fileSizeBytes LIMIT 1")
    suspend fun findDuplicateRecording(fileName: String, fileSizeBytes: Long): RecordingEntity?

    /**
     * Mengambil semua rekaman yang memiliki status tertentu.
     * @param status Nama status (string), misalnya "IMPORTED" atau "COMPLETED".
     */
    @Query("SELECT * FROM recordings WHERE status = :status ORDER BY imported_at DESC")
    fun getRecordingsByStatus(status: String): Flow<List<RecordingEntity>>

    // ================================================================================
    // UPDATE (Partial — efisien)
    // ================================================================================

    /**
     * Memperbarui status pemrosesan rekaman.
     * Dipanggil di setiap transisi status selama pipeline transkripsi.
     *
     * @param id UUID rekaman.
     * @param newStatus Nama status baru (string), misalnya "TRANSCRIBING".
     * @param updatedAt Epoch ms waktu perubahan.
     */
    @Query("UPDATE recordings SET status = :newStatus, updated_at = :updatedAt WHERE id = :id")
    suspend fun updateStatus(
        id: String,
        newStatus: String,
        updatedAt: Long = System.currentTimeMillis()
    )

    /**
     * Memperbarui judul rekaman (fitur rename oleh pengguna).
     *
     * @param id UUID rekaman.
     * @param newTitle Judul baru.
     * @param updatedAt Epoch ms waktu perubahan.
     */
    @Query("UPDATE recordings SET title = :newTitle, updated_at = :updatedAt WHERE id = :id")
    suspend fun updateTitle(
        id: String,
        newTitle: String,
        updatedAt: Long = System.currentTimeMillis()
    )

    /**
     * Memperbarui kosakata kustom untuk rekaman tertentu.
     * Dipanggil saat pengguna mengedit kosakata sebelum transkripsi.
     *
     * @param id UUID rekaman.
     * @param vocabulary Kosakata dipisahkan koma, atau null untuk menghapus.
     */
    @Query("UPDATE recordings SET custom_vocabulary = :vocabulary, updated_at = :updatedAt WHERE id = :id")
    suspend fun updateCustomVocabulary(
        id: String,
        vocabulary: String?,
        updatedAt: Long = System.currentTimeMillis()
    )

    /**
     * Memulihkan status rekaman yang terhenti karena force close, crash, atau aplikasi ditutup.
     * Mengubah status DECODING, TRANSCRIBING, atau SUMMARIZING menjadi INTERRUPTED saat startup.
     */
    @Query("""
        UPDATE recordings 
        SET status = 'INTERRUPTED', updated_at = :updatedAt 
        WHERE status IN ('DECODING', 'TRANSCRIBING', 'SUMMARIZING')
    """)
    suspend fun resetInterruptedRecordings(updatedAt: Long = System.currentTimeMillis())

    /** Memperbarui seluruh entitas (untuk perubahan kompleks multi-kolom). */
    @Update
    suspend fun updateRecording(recording: RecordingEntity)

    // ================================================================================
    // DELETE
    // ================================================================================

    /**
     * Menghapus rekaman berdasarkan ID.
     * CATATAN: Ini hanya menghapus metadata dari DB. Berkas audio terenkripsi
     * di penyimpanan internal harus dihapus secara terpisah oleh Repository
     * agar tidak ada berkas "yatim" (orphan) yang membuang ruang penyimpanan.
     * Karena ForeignKey CASCADE aktif, semua segmen transkrip dan notulensi
     * terkait akan otomatis terhapus oleh Room.
     *
     * @param id UUID rekaman yang akan dihapus.
     */
    @Query("DELETE FROM recordings WHERE id = :id")
    suspend fun deleteRecordingById(id: String)
}
