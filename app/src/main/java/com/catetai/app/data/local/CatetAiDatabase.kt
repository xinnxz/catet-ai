package com.catetai.app.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.catetai.app.data.local.converter.RecordingStatusConverter
import com.catetai.app.data.local.dao.RecordingDao
import com.catetai.app.data.local.entity.RecordingEntity

/**
 * CatetAiDatabase — Database Room terenkripsi (SQLCipher AES-256) untuk Catet AI.
 *
 * # Desain Versi
 * Setiap kali skema tabel berubah (tambah kolom, ubah tipe, tambah index), versi wajib dinaikkan.
 * Karena saat ini masih tahap awal pengembangan dan belum ada data pengguna nyata di production,
 * kita menggunakan `fallbackToDestructiveMigration()` di [DatabaseModule] — database dihapus dan
 * dibuat ulang jika versi tidak cocok.
 *
 * PENTING: Sebelum rilis production pertama (MVP), ganti ke migrasi eksplisit:
 *   `.addMigrations(MIGRATION_1_2, MIGRATION_2_3, ...)`
 *
 * # TypeConverters
 * Room tidak bisa menyimpan tipe data custom (enum, List, dll) secara langsung ke kolom SQLite.
 * [RecordingStatusConverter] mengajarkan Room cara mengubah [RecordingStatus] ↔ String.
 *
 * # Enkripsi
 * Enkripsi diatur di layer [DatabaseModule] melalui SQLCipher [SupportFactory] dengan kunci
 * yang dikelola oleh [DatabaseKeyManager] (Android Keystore). CatetAiDatabase sendiri tidak
 * tahu tentang enkripsi — ini adalah Separation of Concerns yang baik.
 *
 * # Versi Saat Ini: 2
 * - v1: Skema awal (F0-04) — RecordingEntity tanpa anotasi @ColumnInfo, tanpa indeks.
 * - v2: F1-06 — Tambah kolom was_silenced_by_system, ubah nama kolom ke snake_case,
 *   tambah indeks (idx_recordings_status, idx_recordings_created_at),
 *   tambah RecordingStatusConverter, tambah status TRUNCATED.
 */
@Database(
    entities = [RecordingEntity::class],
    version = 2,
    exportSchema = false
)
@TypeConverters(RecordingStatusConverter::class)
abstract class CatetAiDatabase : RoomDatabase() {

    abstract fun recordingDao(): RecordingDao

    companion object {
        const val DATABASE_NAME = "catet_ai_encrypted.db"
    }
}
