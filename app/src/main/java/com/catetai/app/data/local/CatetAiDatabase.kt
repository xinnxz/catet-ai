package com.catetai.app.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.catetai.app.data.local.converter.RecordingStatusConverter
import com.catetai.app.data.local.dao.RecordingDao
import com.catetai.app.data.local.dao.SummaryDao
import com.catetai.app.data.local.dao.TranscriptDao
import com.catetai.app.data.local.entity.RecordingEntity
import com.catetai.app.data.local.entity.SummaryEntity
import com.catetai.app.data.local.entity.TranscriptFtsEntity
import com.catetai.app.data.local.entity.TranscriptSegmentEntity

/**
 * CatetAiDatabase v4 — Database Room terenkripsi (SQLCipher AES-256) untuk Catet AI v2.
 *
 * # Riwayat Versi Skema
 * - v1 : Skema awal (F0-04) — RecordingEntity tanpa anotasi @ColumnInfo, tanpa indeks.
 * - v2 : F1-06 v1 — Tambah was_silenced_by_system, ubah nama kolom ke snake_case,
 *         tambah indeks status & created_at, tambah TypeConverter, tambah status TRUNCATED.
 * - v3 : F1-01 v2 — Pivot ke alur impor: tabel recordings baru, transcript_segments, summaries.
 * - v4 : F4-03 (SAAT INI) — Tambah tabel virtual FTS4 `transcript_segments_fts` untuk
 *         pencarian cepat berbasis teks (Full-Text Search).
 */
@Database(
    entities = [
        RecordingEntity::class,
        TranscriptSegmentEntity::class,
        SummaryEntity::class,
        TranscriptFtsEntity::class
    ],
    version = 4,
    exportSchema = true
)
@TypeConverters(RecordingStatusConverter::class)
abstract class CatetAiDatabase : RoomDatabase() {

    abstract fun recordingDao(): RecordingDao
    abstract fun transcriptDao(): TranscriptDao
    abstract fun summaryDao(): SummaryDao

    companion object {
        const val DATABASE_NAME = "catet_ai_encrypted.db"

        /**
         * Migrasi dari skema v2 (recorder-based) ke skema v3 (import-based).
         *
         * Strategi: DROP + CREATE karena perubahan terlalu fundamental.
         * - Hapus tabel recordings lama (skema v2 tidak kompatibel dengan v3).
         * - Buat ulang tabel recordings dengan skema baru.
         * - Buat tabel transcript_segments dan summaries yang sebelumnya tidak ada.
         *
         * Penjelasan SQL per bagian:
         *
         * 1. `DROP TABLE IF EXISTS recordings` — Hapus tabel lama beserta semua datanya.
         *    Aman karena belum ada data pengguna nyata di production.
         *
         * 2. `CREATE TABLE recordings (...)` — Buat ulang tabel dengan skema v3:
         *    - id TEXT PRIMARY KEY : UUID string, bukan INTEGER autoincrement.
         *    - audio_file_name     : Nama berkas relatif (bukan path absolut).
         *    - original_file_name  : Nama berkas asli yang diimpor (hanya referensi).
         *    - mime_type           : MIME type berkas asli.
         *    - imported_at         : Kapan berkas diimpor.
         *    - DEFAULT 'IMPORTED'  : Status awal saat rekaman baru diimpor.
         *
         * 3. `CREATE TABLE transcript_segments (...)` — Tabel baru untuk segmen teks Whisper.
         *    - FOREIGN KEY ... ON DELETE CASCADE : Jika rekaman dihapus, segmen ikut terhapus.
         *
         * 4. `CREATE TABLE summaries (...)` — Tabel baru untuk notulensi perangkum.
         *    - UNIQUE pada recording_id : Satu rekaman hanya boleh punya satu notulensi.
         *    - FOREIGN KEY ... ON DELETE CASCADE : Jika rekaman dihapus, notulensi ikut terhapus.
         *
         * 5. `CREATE INDEX ...` — Buat indeks untuk performa query yang sering digunakan.
         */
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // 1. Hapus tabel recordings lama (skema v2 tidak kompatibel)
                db.execSQL("DROP TABLE IF EXISTS recordings")

                // 2. Buat tabel recordings baru (skema v3 — import-based)
                db.execSQL("""
                    CREATE TABLE recordings (
                        id TEXT NOT NULL PRIMARY KEY,
                        title TEXT NOT NULL,
                        original_file_name TEXT NOT NULL,
                        audio_file_name TEXT NOT NULL,
                        duration_ms INTEGER NOT NULL DEFAULT 0,
                        file_size_bytes INTEGER NOT NULL DEFAULT 0,
                        mime_type TEXT NOT NULL DEFAULT 'audio/mp4',
                        status TEXT NOT NULL DEFAULT 'IMPORTED',
                        imported_at INTEGER NOT NULL,
                        custom_vocabulary TEXT,
                        created_at INTEGER NOT NULL,
                        updated_at INTEGER NOT NULL
                    )
                """.trimIndent())

                // 3. Buat tabel transcript_segments (baru di v3)
                db.execSQL("""
                    CREATE TABLE transcript_segments (
                        id INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT,
                        recording_id TEXT NOT NULL,
                        start_ms INTEGER NOT NULL,
                        end_ms INTEGER NOT NULL,
                        text TEXT NOT NULL,
                        created_at INTEGER NOT NULL,
                        FOREIGN KEY (recording_id) REFERENCES recordings(id) ON DELETE CASCADE
                    )
                """.trimIndent())

                // 4. Buat tabel summaries (baru di v3)
                db.execSQL("""
                    CREATE TABLE summaries (
                        id INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT,
                        recording_id TEXT NOT NULL UNIQUE,
                        summary_text TEXT NOT NULL,
                        decisions TEXT NOT NULL,
                        action_items TEXT NOT NULL,
                        mode TEXT NOT NULL,
                        created_at INTEGER NOT NULL,
                        FOREIGN KEY (recording_id) REFERENCES recordings(id) ON DELETE CASCADE
                    )
                """.trimIndent())

                // 5. Buat indeks untuk performa query
                db.execSQL("CREATE INDEX idx_recordings_imported_at ON recordings(imported_at)")
                db.execSQL("CREATE INDEX idx_recordings_status ON recordings(status)")
                db.execSQL("CREATE INDEX idx_segments_recording_id ON transcript_segments(recording_id)")
                db.execSQL("CREATE UNIQUE INDEX idx_summaries_recording_id ON summaries(recording_id)")
            }
        }

        /**
         * Migrasi dari skema v3 ke skema v4:
         * Menambahkan virtual table FTS4 `transcript_segments_fts` untuk pencarian transkrip cepat.
         */
        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE VIRTUAL TABLE IF NOT EXISTS transcript_segments_fts 
                    USING fts4(content='transcript_segments', text)
                """.trimIndent())
                db.execSQL("""
                    CREATE TRIGGER IF NOT EXISTS room_fts_content_sync_transcript_segments_fts_BEFORE_UPDATE 
                    BEFORE UPDATE ON transcript_segments BEGIN 
                        DELETE FROM transcript_segments_fts WHERE docid=old.id; 
                    END
                """.trimIndent())
                db.execSQL("""
                    CREATE TRIGGER IF NOT EXISTS room_fts_content_sync_transcript_segments_fts_BEFORE_DELETE 
                    BEFORE DELETE ON transcript_segments BEGIN 
                        DELETE FROM transcript_segments_fts WHERE docid=old.id; 
                    END
                """.trimIndent())
                db.execSQL("""
                    CREATE TRIGGER IF NOT EXISTS room_fts_content_sync_transcript_segments_fts_AFTER_UPDATE 
                    AFTER UPDATE ON transcript_segments BEGIN 
                        INSERT INTO transcript_segments_fts(docid, text) VALUES (new.id, new.text); 
                    END
                """.trimIndent())
                db.execSQL("""
                    CREATE TRIGGER IF NOT EXISTS room_fts_content_sync_transcript_segments_fts_AFTER_INSERT 
                    AFTER INSERT ON transcript_segments BEGIN 
                        INSERT INTO transcript_segments_fts(docid, text) VALUES (new.id, new.text); 
                    END
                """.trimIndent())
            }
        }
    }
}
