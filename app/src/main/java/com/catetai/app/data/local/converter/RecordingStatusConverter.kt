package com.catetai.app.data.local.converter

import androidx.room.TypeConverter
import com.catetai.app.domain.model.RecordingStatus

/**
 * TypeConverter Room untuk mengubah enum [RecordingStatus] ↔ String di SQLite.
 *
 * # Mengapa Diperlukan?
 * SQLite tidak mengenal konsep "enum" dari Kotlin. Room membutuhkan konverter eksplisit:
 * - Menulis : Kotlin `RecordingStatus.IMPORTED` → String `"IMPORTED"` di kolom TEXT SQLite.
 * - Membaca : String `"IMPORTED"` dari SQLite → Kotlin `RecordingStatus.IMPORTED`.
 *
 * # Strategi: Nama String, Bukan Ordinal (Int)
 * Kita simpan NAMA enum, bukan posisi ordinal. Alasan:
 * - Jika urutan enum berubah (mis. menambah status baru di tengah), ordinal-based storage
 *   akan menghasilkan data korup secara diam-diam.
 * - Kolom di DB berisi "IMPORTED", bukan "0" — mudah dibaca saat debugging dengan SQLite browser.
 *
 * # Fallback ke FAILED
 * Jika nilai string di DB tidak dikenal (mis. data dari versi lama yang belum dimigrasikan),
 * konverter mengembalikan FAILED sebagai sinyal bahwa ada masalah, daripada melempar Exception
 * yang bisa menyebabkan crash.
 *
 * # Status v2 yang Valid
 * IMPORTED, DECODING, TRANSCRIBING, SUMMARIZING, COMPLETED, FAILED, INTERRUPTED
 */
class RecordingStatusConverter {

    /**
     * Mengubah enum [RecordingStatus] → String untuk disimpan di kolom TEXT SQLite.
     */
    @TypeConverter
    fun fromStatus(status: RecordingStatus): String = status.name

    /**
     * Mengubah String dari kolom SQLite → enum [RecordingStatus].
     * Fallback ke [RecordingStatus.FAILED] jika nilai tidak dikenal.
     */
    @TypeConverter
    fun toStatus(value: String): RecordingStatus =
        RecordingStatus.entries.firstOrNull { it.name == value }
            ?: RecordingStatus.FAILED
}
