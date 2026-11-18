package com.catetai.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.catetai.app.data.local.entity.TranscriptSegmentEntity
import com.catetai.app.domain.model.SearchResultItem
import kotlinx.coroutines.flow.Flow

/**
 * TranscriptDao — Data Access Object untuk tabel `transcript_segments`.
 *
 * Menyimpan dan mengambil segmen teks hasil transkripsi Whisper.
 * Setiap segmen adalah satu kalimat verbatim lengkap dengan tanda waktu.
 *
 * # Strategi Insert
 * Segmen disimpan satu per satu (atau dalam batch) saat Whisper selesai memproses
 * setiap chunk 30 detik. Ini memungkinkan tampilan progresif di UI — pengguna
 * bisa mulai membaca transkrip bahkan sebelum keseluruhan audio selesai diproses.
 *
 * # Urutan Segmen
 * Selalu diurutkan berdasarkan `start_ms` (waktu mulai) agar tampil berurutan
 * sesuai alur percakapan di Tab Transkrip.
 */
@Dao
interface TranscriptDao {

    // ================================================================================
    // INSERT
    // ================================================================================

    /**
     * Menyimpan satu segmen transkrip ke database.
     * Digunakan saat Whisper memproses chunk per chunk.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSegment(segment: TranscriptSegmentEntity): Long

    /**
     * Menyimpan banyak segmen sekaligus (batch insert).
     * Lebih efisien daripada memanggil insertSegment() satu per satu
     * karena Room menggabungkan semuanya dalam satu transaksi database.
     *
     * @param segments Daftar segmen yang akan disimpan.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSegments(segments: List<TranscriptSegmentEntity>)

    // ================================================================================
    // SELECT
    // ================================================================================

    /**
     * Mengambil semua segmen transkrip untuk rekaman tertentu sebagai [Flow] reaktif.
     * Diurutkan berdasarkan waktu mulai agar tampil berurutan di Tab Transkrip.
     *
     * Flow memancarkan ulang setiap kali ada segmen baru yang ditambahkan —
     * ini memungkinkan tampilan progresif saat transkripsi masih berjalan.
     *
     * @param recordingId UUID rekaman induk.
     * @return Flow yang memancarkan daftar segmen terkini.
     */
    @Query("SELECT * FROM transcript_segments WHERE recording_id = :recordingId ORDER BY start_ms ASC")
    fun getSegmentsByRecordingId(recordingId: String): Flow<List<TranscriptSegmentEntity>>

    /**
     * Versi suspend (one-shot) dari [getSegmentsByRecordingId].
     * Digunakan saat membutuhkan semua segmen sekali jalan (misalnya untuk proses perangkuman
     * atau ekspor PDF) tanpa perlu observasi reaktif.
     *
     * @param recordingId UUID rekaman induk.
     * @return Daftar semua segmen transkrip, diurutkan berdasarkan waktu mulai.
     */
    @Query("SELECT * FROM transcript_segments WHERE recording_id = :recordingId ORDER BY start_ms ASC")
    suspend fun getSegmentsByRecordingIdOnce(recordingId: String): List<TranscriptSegmentEntity>

    /**
     * Menghitung jumlah segmen yang sudah tersimpan untuk rekaman tertentu.
     * Berguna untuk menampilkan kemajuan transkripsi di UI.
     *
     * @param recordingId UUID rekaman.
     * @return Jumlah segmen yang sudah tersimpan.
     */
    @Query("SELECT COUNT(*) FROM transcript_segments WHERE recording_id = :recordingId")
    suspend fun getSegmentCount(recordingId: String): Int

    // ================================================================================
    // SEARCH (F4-03)
    // ================================================================================

    /**
     * Pencarian cepat Full-Text Search (FTS4) pada teks transkrip di seluruh rekaman.
     * Menggunakan index inverted SQLite FTS4 untuk latensi < 1 detik.
     */
    @Query("""
        SELECT 
            s.recording_id AS recordingId,
            r.title AS recordingTitle,
            s.id AS segmentId,
            s.start_ms AS startMs,
            s.end_ms AS endMs,
            s.text AS snippet
        FROM transcript_segments s
        JOIN recordings r ON s.recording_id = r.id
        JOIN transcript_segments_fts fts ON fts.docid = s.id
        WHERE transcript_segments_fts MATCH :query
        ORDER BY s.created_at DESC
    """)
    suspend fun searchTranscriptsFts(query: String): List<SearchResultItem>

    /**
     * Fallback pencarian teks transkrip berbasis LIKE bila query FTS mengalami syntax issue.
     */
    @Query("""
        SELECT 
            s.recording_id AS recordingId,
            r.title AS recordingTitle,
            s.id AS segmentId,
            s.start_ms AS startMs,
            s.end_ms AS endMs,
            s.text AS snippet
        FROM transcript_segments s
        JOIN recordings r ON s.recording_id = r.id
        WHERE s.text LIKE '%' || :query || '%'
        ORDER BY s.created_at DESC
    """)
    suspend fun searchTranscriptsLike(query: String): List<SearchResultItem>

    // ================================================================================
    // DELETE
    // ================================================================================

    /**
     * Menghapus semua segmen transkrip untuk rekaman tertentu.
     * Berguna saat pengguna ingin "Proses Ulang" dari awal.
     * Catatan: Jika rekaman dihapus via RecordingDao, cascade delete otomatis
     * menghapus semua segmen terkait — tidak perlu memanggil fungsi ini.
     *
     * @param recordingId UUID rekaman yang segmennya akan dihapus.
     */
    @Query("DELETE FROM transcript_segments WHERE recording_id = :recordingId")
    suspend fun deleteSegmentsByRecordingId(recordingId: String)
}
