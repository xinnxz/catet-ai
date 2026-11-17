package com.catetai.app.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * SummaryEntity — Skema tabel `summaries`.
 *
 * Menyimpan hasil notulensi yang dihasilkan oleh perangkum otomatis (Ekstraktif atau BYOK Cloud).
 * Setiap rekaman memiliki paling banyak satu notulensi aktif (relasi 1:1 dengan recordings).
 *
 * # Struktur Notulensi (3 Bagian Wajib)
 * - `summary_text` : Poin-poin ringkasan rapat (Markdown, maks 5 butir).
 * - `decisions`    : Daftar keputusan eksplisit/implisit (Markdown bernomor).
 * - `action_items` : Daftar tugas dalam JSON — contoh:
 *                    [{"task":"Kirim laporan","pic":"Rania","deadline":"Senin"},
 *                     {"task":"Setup API","pic":"Belum teridentifikasi","deadline":null}]
 *
 * # Kolom `mode`
 * Mencatat perangkum mana yang menghasilkan notulensi ini:
 * - "extractive" : Perangkum Ekstraktif berbasis TextRank/TF-IDF (Kotlin murni, luring).
 * - "byok_cloud" : API cloud (Groq/OpenAI) menggunakan kunci API pengguna.
 *
 * # ForeignKey dengan CASCADE
 * Jika rekaman dihapus, notulensinya otomatis ikut terhapus.
 *
 * # Kenapa action_items disimpan sebagai JSON String?
 * Room tidak mendukung penyimpanan List<ActionItem> secara langsung. Ada dua pilihan:
 * (1) TypeConverter: mengubah List → JSON String saat tulis, JSON String → List saat baca.
 * (2) Tabel relasional terpisah: lebih kompleks untuk MVP.
 * Kita pilih opsi (1) untuk kesederhanaan MVP. Data tidak terlalu besar (< 10 item per rapat).
 */
@Entity(
    tableName = "summaries",
    foreignKeys = [
        ForeignKey(
            entity = RecordingEntity::class,
            parentColumns = ["id"],
            childColumns = ["recording_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["recording_id"], name = "idx_summaries_recording_id", unique = true)
    ]
)
data class SummaryEntity(

    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "id")
    val id: Long = 0,

    /** ID rekaman induk (foreign key ke tabel `recordings`). */
    @ColumnInfo(name = "recording_id")
    val recordingId: String,

    /**
     * Ringkasan rapat dalam format Markdown.
     * Contoh: "• Tim sepakat deadline proyek adalah 30 November\n• Anggaran direvisi..."
     */
    @ColumnInfo(name = "summary_text")
    val summaryText: String,

    /**
     * Daftar keputusan rapat dalam format Markdown bernomor.
     * Contoh: "1. Gunakan teknologi X untuk modul Y\n2. Jadwal rilis diundur ke Q2"
     * Kosong ("") jika tidak ada keputusan eksplisit yang terdeteksi.
     */
    @ColumnInfo(name = "decisions")
    val decisions: String,

    /**
     * Daftar tugas / tindak lanjut dalam format JSON array.
     * Skema: [{"task":"string","pic":"string","deadline":"string|null"}]
     * PIC diisi "Belum teridentifikasi (Pembicara)" jika tidak disebutkan namanya.
     * Kosong ("[]") jika tidak ada tugas yang terdeteksi.
     */
    @ColumnInfo(name = "action_items")
    val actionItems: String,

    /**
     * Mode perangkum yang menghasilkan notulensi ini.
     * Nilai: "extractive" atau "byok_cloud".
     */
    @ColumnInfo(name = "mode")
    val mode: String,

    /** Epoch ms saat notulensi ini dibuat. */
    @ColumnInfo(name = "created_at")
    val createdAt: Long = System.currentTimeMillis()
)
