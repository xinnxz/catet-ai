package com.catetai.app.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.catetai.app.data.local.entity.RecordingEntity
import com.catetai.app.data.local.entity.RecordingStatus
import kotlinx.coroutines.flow.Flow

/**
 * RecordingDao — Data Access Object untuk tabel `recordings` di Room (SQLCipher).
 *
 * # Panduan Coroutine & Flow
 * - `suspend fun` → Cocok untuk operasi one-shot (insert, update, delete, query tunggal).
 *   Memanggil fungsi ini harus dilakukan dari coroutine scope.
 * - `fun getAllRecordingsOrderByDateDesc(): Flow<List<RecordingEntity>>` → Room mengobservasi
 *   tabel secara reaktif. Setiap kali ada INSERT/UPDATE/DELETE di tabel `recordings`, Flow
 *   akan secara otomatis memancarkan list terbaru ke semua kolektornya (ViewModel, UI).
 *   Ini adalah pola "single source of truth" Room — tidak perlu refresh manual.
 *
 * # Strategi Conflict Resolution
 * `@Insert(onConflict = OnConflictStrategy.REPLACE)` → Jika id sudah ada, seluruh baris
 * diganti. Berguna untuk upsert (insert-or-update) yang aman.
 *
 * # Pemisahan updateStatus vs updateRecording
 * Query UPDATE parsial (`UPDATE ... SET status = ? WHERE id = ?`) jauh lebih efisien daripada
 * `@Update` yang mengganti seluruh baris. Ini penting karena operasi update status terjadi
 * berkali-kali selama siklus hidup rekaman.
 */
@Dao
interface RecordingDao {

    // ================================================================================
    // INSERT
    // ================================================================================

    /**
     * Memasukkan entitas rekaman baru ke database.
     * Jika id sudah ada (konflik), seluruh baris akan digantikan ([OnConflictStrategy.REPLACE]).
     *
     * @param recording Entitas yang akan disimpan.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRecording(recording: RecordingEntity)

    // ================================================================================
    // SELECT (Queries)
    // ================================================================================

    /**
     * Mengembalikan semua rekaman sebagai [Flow] reaktif, diurutkan dari terbaru ke terlama.
     *
     * Flow ini otomatis memancarkan data terbaru setiap kali ada perubahan di tabel.
     * Gunakan di ViewModel dengan `stateIn()` untuk diobservasi oleh Compose UI.
     *
     * @return Flow yang memancarkan list rekaman terbaru setiap kali ada perubahan.
     */
    @Query("SELECT * FROM recordings ORDER BY created_at DESC")
    fun getAllRecordingsOrderByDateDesc(): Flow<List<RecordingEntity>>

    /**
     * Alias untuk [getAllRecordingsOrderByDateDesc].
     */
    fun getAllRecordings(): Flow<List<RecordingEntity>> = getAllRecordingsOrderByDateDesc()

    /**
     * Mengambil satu rekaman berdasarkan ID uniknya.
     *
     * @param id UUID string rekaman yang dicari.
     * @return [RecordingEntity] jika ditemukan, null jika tidak ada.
     */
    @Query("SELECT * FROM recordings WHERE id = :id LIMIT 1")
    suspend fun getRecordingById(id: String): RecordingEntity?

    /**
     * Mengambil semua rekaman yang memiliki status tertentu.
     * Berguna untuk dashboard (mis. "berapa rekaman yang belum diproses").
     *
     * @param status Status yang difilter.
     * @return Flow rekaman dengan status yang diminta.
     */
    @Query("SELECT * FROM recordings WHERE status = :status ORDER BY created_at DESC")
    fun getRecordingsByStatus(status: RecordingStatus): Flow<List<RecordingEntity>>

    // ================================================================================
    // UPDATE (Partial — efisien daripada mengganti seluruh baris)
    // ================================================================================

    /**
     * Memperbarui status rekaman dan mencatat waktu perubahan.
     *
     * Ini adalah query paling sering dipanggil sepanjang siklus hidup rekaman
     * (RECORDING → STOPPED → PROCESSING → TRANSCRIBED → SUMMARIZED).
     *
     * @param id UUID rekaman yang statusnya akan diperbarui.
     * @param newStatus Status baru yang akan diterapkan.
     * @param updatedAt Epoch ms waktu perubahan (default: sekarang).
     */
    @Query("UPDATE recordings SET status = :newStatus, updated_at = :updatedAt WHERE id = :id")
    suspend fun updateStatus(
        id: String,
        newStatus: RecordingStatus,
        updatedAt: Long = System.currentTimeMillis()
    )

    /**
     * Memperbarui judul rekaman (untuk fitur rename oleh pengguna).
     *
     * @param id UUID rekaman yang judulnya akan diubah.
     * @param newTitle Judul baru.
     * @param updatedAt Epoch ms waktu perubahan (default: sekarang).
     */
    @Query("UPDATE recordings SET title = :newTitle, updated_at = :updatedAt WHERE id = :id")
    suspend fun updateTitle(
        id: String,
        newTitle: String,
        updatedAt: Long = System.currentTimeMillis()
    )

    /**
     * Memperbarui metadata durasi dan ukuran file setelah rekaman dihentikan dan WAV difinalisasi.
     *
     * @param id UUID rekaman.
     * @param durationMs Durasi final rekaman dalam milidetik.
     * @param fileSizeBytes Ukuran fisik file WAV dalam byte.
     * @param wasSilencedBySystem True jika mikrofon sempat dibisukan oleh OS selama sesi.
     * @param updatedAt Epoch ms waktu perubahan (default: sekarang).
     */
    @Query("""
        UPDATE recordings 
        SET duration_ms = :durationMs,
            file_size_bytes = :fileSizeBytes,
            was_silenced_by_system = :wasSilencedBySystem,
            updated_at = :updatedAt
        WHERE id = :id
    """)
    suspend fun updateAudioMetadata(
        id: String,
        durationMs: Long,
        fileSizeBytes: Long,
        wasSilencedBySystem: Boolean,
        updatedAt: Long = System.currentTimeMillis()
    )

    /**
     * Memperbarui seluruh entitas (untuk perubahan kompleks yang melibatkan banyak kolom).
     *
     * Gunakan [updateStatus], [updateTitle], atau [updateAudioMetadata] untuk perubahan
     * parsial yang lebih efisien. Cadangkan fungsi ini hanya untuk kasus pembaruan lengkap.
     *
     * @param recording Entitas rekaman dengan nilai baru. Cocokkan berdasarkan primary key [RecordingEntity.id].
     */
    @Update
    suspend fun updateRecording(recording: RecordingEntity)

    // ================================================================================
    // DELETE
    // ================================================================================

    /**
     * Menghapus entitas rekaman dari database.
     *
     * PERINGATAN: Ini hanya menghapus metadata dari DB. File WAV di disk harus dihapus
     * secara terpisah oleh [RecordingRepository.deleteRecording] agar tidak ada orphan file.
     *
     * @param recording Entitas yang akan dihapus.
     */
    @Delete
    suspend fun deleteRecording(recording: RecordingEntity)

    /**
     * Menghapus rekaman berdasarkan ID, tanpa perlu mengambil entitas terlebih dahulu.
     *
     * @param id UUID rekaman yang akan dihapus.
     */
    @Query("DELETE FROM recordings WHERE id = :id")
    suspend fun deleteRecordingById(id: String)
}
