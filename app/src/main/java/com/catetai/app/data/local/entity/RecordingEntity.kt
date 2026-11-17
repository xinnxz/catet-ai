package com.catetai.app.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.util.UUID

/**
 * RecordingEntity v2 — Skema tabel `recordings` untuk alur impor Catet AI v2.
 *
 * # Perubahan dari v1 (skema recorder-based):
 * - DIHAPUS : audioFilePath (path absolut) → diganti audioFileName (nama relatif)
 * - DIHAPUS : sampleRate (selalu 16000, tidak perlu disimpan)
 * - DIHAPUS : wasSilencedBySystem (hanya relevan untuk fitur recorder)
 * - DIHAPUS : processingEngine (dipindah ke SummaryEntity.mode)
 * - DITAMBAH: originalFileName — nama berkas asli yang diimpor (hanya referensi)
 * - DITAMBAH: mimeType — MIME type berkas asli (audio/mp4, audio/mpeg, dsb.)
 * - DITAMBAH: importedAt — epoch ms saat berkas berhasil diimpor
 *
 * # Kolom `audioFileName` (nama relatif, bukan path absolut)
 * Menyimpan nama berkas relatif (misal: "audio_1700000000000.enc") bukan path absolut.
 * Alasan: path absolut berubah jika pengguna melakukan backup-restore ke perangkat lain.
 * Untuk mendapatkan path lengkap: File(context.filesDir, "audio/$audioFileName").
 *
 * # Indeks
 * - `idx_recordings_imported_at` : Mempercepat query urutan terbaru-ke-terlama (HomeScreen).
 * - `idx_recordings_status`      : Mempercepat filter berdasarkan status pemrosesan.
 */
@Entity(
    tableName = "recordings",
    indices = [
        Index(value = ["imported_at"], name = "idx_recordings_imported_at"),
        Index(value = ["status"],      name = "idx_recordings_status")
    ]
)
data class RecordingEntity(

    @PrimaryKey
    @ColumnInfo(name = "id")
    val id: String = UUID.randomUUID().toString(),

    /** Judul yang dapat diubah pengguna. Default: nama berkas asli tanpa ekstensi. */
    @ColumnInfo(name = "title")
    val title: String,

    /** Nama berkas asli yang diimpor — hanya untuk referensi, tidak berubah. */
    @ColumnInfo(name = "original_file_name")
    val originalFileName: String,

    /**
     * Nama berkas audio di penyimpanan internal app (relatif, bukan path absolut).
     * Setelah enkripsi Tink: "audio_1700000000000.enc"
     * Path lengkap: File(context.filesDir, "audio/$audioFileName")
     */
    @ColumnInfo(name = "audio_file_name")
    val audioFileName: String,

    /** Durasi audio dalam milidetik (diisi dari MediaMetadataRetriever saat impor). */
    @ColumnInfo(name = "duration_ms")
    val durationMs: Long = 0L,

    /** Ukuran berkas asli dalam byte. */
    @ColumnInfo(name = "file_size_bytes")
    val fileSizeBytes: Long = 0L,

    /** MIME type berkas asli, misalnya "audio/mp4", "audio/mpeg", "audio/ogg". */
    @ColumnInfo(name = "mime_type")
    val mimeType: String = "audio/mp4",

    /**
     * Status pemrosesan saat ini.
     * Disimpan sebagai TEXT via [RecordingStatusConverter].
     * Nilai: IMPORTED, DECODING, TRANSCRIBING, SUMMARIZING, COMPLETED, FAILED, INTERRUPTED.
     */
    @ColumnInfo(name = "status")
    val status: String = "IMPORTED",

    /** Epoch ms saat berkas berhasil diimpor dari SAF ke penyimpanan internal. */
    @ColumnInfo(name = "imported_at")
    val importedAt: Long = System.currentTimeMillis(),

    /** Kosakata kustom untuk prompt biasing Whisper (dipisahkan koma, opsional). */
    @ColumnInfo(name = "custom_vocabulary")
    val customVocabulary: String? = null,

    /** Epoch ms saat entitas pertama kali dibuat. */
    @ColumnInfo(name = "created_at")
    val createdAt: Long = System.currentTimeMillis(),

    /** Epoch ms saat entitas terakhir diperbarui. */
    @ColumnInfo(name = "updated_at")
    val updatedAt: Long = System.currentTimeMillis()
)
