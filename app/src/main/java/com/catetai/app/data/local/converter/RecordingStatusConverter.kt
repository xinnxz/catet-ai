package com.catetai.app.data.local.converter

import androidx.room.TypeConverter
import com.catetai.app.data.local.entity.RecordingStatus

/**
 * TypeConverter Room untuk mengubah enum [RecordingStatus] ↔ String di SQLite.
 *
 * # Mengapa Diperlukan?
 * SQLite adalah database relasional berbasis teks dan angka. Ia tidak mengenal konsep "enum"
 * dari bahasa pemrograman. Room membutuhkan konverter eksplisit agar tahu cara:
 *   - Menulis: Kotlin `RecordingStatus.RECORDING` → String `"RECORDING"` di kolom TEXT SQLite.
 *   - Membaca: String `"RECORDING"` dari SQLite → Kotlin `RecordingStatus.RECORDING`.
 *
 * # Strategi Penyimpanan: String vs Ordinal (Int)
 * Kita menyimpan NAMA enum (string), bukan posisi ordinal (int). Alasannya:
 *   - **Keamanan migrasi**: Jika urutan enum berubah di masa depan (mis. menambah status baru
 *     di tengah daftar), ordinal-based storage akan menghasilkan data korup secara diam-diam.
 *   - **Keterbacaan**: Kolom di DB akan berisi `"RECORDING"`, bukan `"0"` — mudah di-debug.
 *   - **SQLCipher tetap bekerja**: TypeConverter beroperasi di layer JVM sebelum data ditulis
 *     ke SQLite, jadi enkripsi SQLCipher tidak terpengaruh.
 *
 * # Lokasi Registrasi
 * Konverter ini harus didaftarkan di [CatetAiDatabase] via `@TypeConverters(RecordingStatusConverter::class)`.
 */
class RecordingStatusConverter {

    /**
     * Mengubah enum [RecordingStatus] → String untuk disimpan di kolom TEXT SQLite.
     */
    @TypeConverter
    fun fromStatus(status: RecordingStatus): String {
        return status.name
    }

    /**
     * Mengubah String dari kolom SQLite → enum [RecordingStatus].
     *
     * Menggunakan [RecordingStatus.entries] dan [firstOrNull] dengan fallback ke
     * [RecordingStatus.ERROR] jika nilai di DB tidak dikenal — lebih aman daripada
     * [RecordingStatus.valueOf] yang akan melempar [IllegalArgumentException] jika
     * ada nilai invalid (mis. data korup atau dari versi DB lama yang belum dimigrasikan).
     */
    @TypeConverter
    fun toStatus(value: String): RecordingStatus {
        return RecordingStatus.entries.firstOrNull { it.name == value }
            ?: RecordingStatus.ERROR
    }
}
