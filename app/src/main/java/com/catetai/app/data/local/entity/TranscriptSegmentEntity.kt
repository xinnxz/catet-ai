package com.catetai.app.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * TranscriptSegmentEntity — Skema tabel `transcript_segments`.
 *
 * Setiap baris mewakili satu segmen kalimat hasil transkripsi Whisper.
 * Inilah yang ditampilkan di Tab Transkrip — teks verbatim (persis apa yang
 * diucapkan pembicara) lengkap dengan tanda waktu mulai dan akhir.
 *
 * # ForeignKey dengan CASCADE
 * Kolom `recording_id` adalah foreign key ke tabel `recordings`.
 * `onDelete = CASCADE` artinya: jika sebuah rekaman dihapus dari tabel `recordings`,
 * SEMUA segmen transkrip yang merujuk ke rekaman tersebut otomatis ikut terhapus.
 * Ini mencegah data "yatim" (orphan) yang membuang ruang penyimpanan.
 *
 * # Indeks pada `recording_id`
 * Room memerlukan indeks eksplisit di kolom foreign key untuk performa.
 * Tanpa indeks ini, Room akan mengeluarkan peringatan saat kompilasi.
 * Query "ambil semua segmen untuk rekaman X" akan berjalan lebih cepat dengan indeks.
 *
 * # Kolom `text`
 * Teks disimpan apa adanya — verbatim dari output Whisper, tanpa koreksi ejaan
 * atau pemformatan. Tanggung jawab tampilan ada di lapisan UI.
 */
@Entity(
    tableName = "transcript_segments",
    foreignKeys = [
        ForeignKey(
            entity = RecordingEntity::class,
            parentColumns = ["id"],
            childColumns = ["recording_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["recording_id"], name = "idx_segments_recording_id")
    ]
)
data class TranscriptSegmentEntity(

    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "id")
    val id: Long = 0,

    /** ID rekaman induk (foreign key ke tabel `recordings`). */
    @ColumnInfo(name = "recording_id")
    val recordingId: String,

    /**
     * Waktu mulai segmen ini dari awal audio, dalam milidetik.
     * Contoh: 15000 = segmen dimulai di detik ke-15.
     * Digunakan oleh pemutar audio untuk lompat ke posisi yang tepat.
     */
    @ColumnInfo(name = "start_ms")
    val startMs: Long,

    /**
     * Waktu akhir segmen ini, dalam milidetik.
     * Selisih (endMs - startMs) adalah durasi segmen.
     */
    @ColumnInfo(name = "end_ms")
    val endMs: Long,

    /**
     * Teks verbatim dari pembicara — persis apa yang diucapkan, tanpa perubahan.
     * Ini yang ditampilkan baris demi baris di Tab Transkrip.
     */
    @ColumnInfo(name = "text")
    val text: String,

    /** Epoch ms saat segmen ini disimpan ke database. */
    @ColumnInfo(name = "created_at")
    val createdAt: Long = System.currentTimeMillis()
)
