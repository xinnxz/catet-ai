package com.catetai.app.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.util.UUID

/**
 * Status siklus hidup sebuah rekaman audio di Catet AI.
 *
 * Status ini mengikuti alur pipeline pemrosesan rekaman dari awal hingga akhir:
 *
 *   RECORDING → STOPPED → PROCESSING → TRANSCRIBED → SUMMARIZED
 *                   ↘                                      ↗
 *                    ERROR  ←←←←←←←←←←←←←←←←←←←←←←←←←←←
 *
 * - [RECORDING]   : Audio sedang direkam aktif oleh [RecordingForegroundService].
 * - [STOPPED]     : Rekaman dihentikan (normal, baterai kritis, disk penuh, atau force-stop oleh OS).
 *                   File WAV sudah difinalisasi dan dapat diputar ulang.
 * - [TRUNCATED]   : Rekaman terpotong akibat kill mendadak (LMK/power loss). Header WAV sudah
 *                   dipulihkan via [WavHeaderUtil.recoverUnfinalizedHeader], tapi mungkin ada gap audio.
 * - [PROCESSING]  : Pipeline Whisper atau local SLM sedang memproses audio di background.
 * - [TRANSCRIBED] : Transkripsi teks berhasil dihasilkan. Belum ada notulensi.
 * - [SUMMARIZED]  : Notulensi / ringkasan sudah dihasilkan oleh local SLM. Status final sukses.
 * - [ERROR]       : Proses gagal (mis. file audio korup, model AI crash, disk penuh saat inferensi).
 */
enum class RecordingStatus {
    RECORDING,
    STOPPED,
    TRUNCATED,
    PROCESSING,
    TRANSCRIBED,
    SUMMARIZED,
    ERROR
}

/**
 * RecordingEntity — Skema tabel `recordings` di Room (SQLCipher AES-256).
 *
 * Mewakili satu sesi rapat yang direkam dan metadata pemrosesannya.
 *
 * # Desain Kolom
 * - [id]: UUID string sebagai primary key — lebih aman dari integer auto-increment karena
 *   tidak bocorkan jumlah total rekaman (security-by-design).
 * - [audioFilePath]: Path absolut ke file WAV di internal storage. File TIDAK di-backup
 *   (android:allowBackup=false) — hanya ada di perangkat lokal pengguna.
 * - [durationMs]: Diisi 0L saat rekaman baru dimulai, diperbarui ketika rekaman berhenti.
 * - [fileSizeBytes]: Diisi 0L saat rekaman baru dimulai, diperbarui saat WAV header difinalisasi.
 * - [sampleRate]: Disimpan eksplisit (16000 Hz) agar file dapat diputar ulang dengan benar
 *   bahkan jika konstanta AudioConfig berubah di masa depan.
 * - [status]: Enum, disimpan sebagai TEXT via [RecordingStatusConverter] untuk keterbacaan DB.
 * - [wasSilencedBySystem]: True jika selama sesi ini mikrofon sempat dibisukan oleh OS
 *   (panggilan telepon masuk atau asisten suara). Berguna untuk memperingatkan user saat
 *   membuka Detail layar bahwa mungkin ada bagian audio yang kosong/senyap.
 * - [customVocabulary]: Kosakata kustom yang di-inject ke prompt Whisper (nama orang, produk, dll).
 * - [processingEngine]: Identifier model yang digunakan untuk transkripsi (mis. "whisper-tiny",
 *   "whisper-base"). Dicatat untuk keperluan audit dan debugging kualitas transkripsi.
 * - [createdAt], [updatedAt]: Epoch milidetik (System.currentTimeMillis()).
 *   [updatedAt] harus selalu diperbarui saat ada perubahan status.
 *
 * # Indeks
 * - `idx_recordings_status`: Mempercepat query filter berdasarkan status (mis. "tampilkan semua
 *   yang belum ditranskripsi"). Tanpa indeks, Room harus full-scan tabel — mahal saat ada 1000+ rekaman.
 * - `idx_recordings_created_at`: Mempercepat query urutan terbaru-ke-terlama untuk HomeScreen list.
 *   Ini query paling sering dijalankan di aplikasi.
 */
@Entity(
    tableName = "recordings",
    indices = [
        Index(value = ["status"], name = "idx_recordings_status"),
        Index(value = ["created_at"], name = "idx_recordings_created_at")
    ]
)
data class RecordingEntity(

    @PrimaryKey
    @ColumnInfo(name = "id")
    val id: String = UUID.randomUUID().toString(),

    /** Judul rekaman yang dapat diedit pengguna. Default: tanggal & jam rekaman. */
    @ColumnInfo(name = "title")
    val title: String,

    /** Path absolut ke file WAV di internal storage. */
    @ColumnInfo(name = "audio_file_path")
    val audioFilePath: String,

    /** Durasi rekaman dalam milidetik. 0 selama rekaman berlangsung. */
    @ColumnInfo(name = "duration_ms")
    val durationMs: Long = 0L,

    /** Ukuran fisik file WAV dalam byte. 0 selama rekaman berlangsung. */
    @ColumnInfo(name = "file_size_bytes")
    val fileSizeBytes: Long = 0L,

    /** Frekuensi sampling audio (selalu 16000 Hz untuk Catet AI). */
    @ColumnInfo(name = "sample_rate")
    val sampleRate: Int = 16_000,

    /** Status siklus hidup rekaman ini. Lihat [RecordingStatus]. */
    @ColumnInfo(name = "status")
    val status: RecordingStatus = RecordingStatus.RECORDING,

    /**
     * True jika mikrofon sempat dibisukan oleh OS selama sesi ini.
     * Mungkin ada gap senyap di file audio, pengguna perlu diperingatkan.
     */
    @ColumnInfo(name = "was_silenced_by_system")
    val wasSilencedBySystem: Boolean = false,

    /** Kosakata kustom untuk prompt Whisper (opsional). */
    @ColumnInfo(name = "custom_vocabulary")
    val customVocabulary: String? = null,

    /** Identifier model inferensi yang digunakan (opsional, diisi setelah transkripsi). */
    @ColumnInfo(name = "processing_engine")
    val processingEngine: String? = null,

    /** Epoch ms saat rekaman ini pertama kali dibuat. */
    @ColumnInfo(name = "created_at")
    val createdAt: Long = System.currentTimeMillis(),

    /** Epoch ms saat entitas ini terakhir diperbarui (status, judul, durasi, dll). */
    @ColumnInfo(name = "updated_at")
    val updatedAt: Long = System.currentTimeMillis()
)
