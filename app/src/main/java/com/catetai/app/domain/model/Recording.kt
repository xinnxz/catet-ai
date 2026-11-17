package com.catetai.app.domain.model

/**
 * Status siklus hidup sebuah rekaman audio di Catet AI v2 (alur impor).
 *
 * Alur utama (happy path):
 *   IMPORTED → DECODING → TRANSCRIBING → SUMMARIZING → COMPLETED
 *
 * Alur error / gangguan:
 *   Setiap status bisa berpindah ke FAILED atau INTERRUPTED
 *
 * Penjelasan per status:
 * - [IMPORTED]      : Berkas audio berhasil disalin dari SAF ke penyimpanan internal.
 *                     Siap untuk didekode dan ditranskrip.
 * - [DECODING]      : MediaCodec/FFmpeg sedang mengubah format audio asli ke PCM 16kHz Mono.
 * - [TRANSCRIBING]  : Whisper Large v3 Turbo Q5 sedang memproses audio → teks bertanda waktu.
 * - [SUMMARIZING]   : Perangkum (Ekstraktif / BYOK Cloud) sedang mengolah transkrip → notulensi.
 * - [COMPLETED]     : Semua proses selesai. Transkrip dan notulensi tersedia.
 * - [FAILED]        : Proses gagal permanen (berkas rusak, model crash, ruang disk penuh).
 * - [INTERRUPTED]   : Proses terganggu (force stop, OS kill). Data parsial tersimpan.
 *                     Pengguna bisa memilih "Proses Ulang".
 */
enum class RecordingStatus {
    IMPORTED,
    DECODING,
    TRANSCRIBING,
    SUMMARIZING,
    COMPLETED,
    FAILED,
    INTERRUPTED
}

/**
 * Model domain untuk satu rekaman audio yang diimpor di Catet AI v2.
 *
 * Ini adalah Plain Old Kotlin Object (POKO) — bebas dari anotasi Room atau
 * dependensi Android apa pun. Domain layer tidak boleh tahu tentang implementasi
 * penyimpanan data (Clean Architecture).
 *
 * @param id UUID string sebagai identifier unik.
 * @param title Judul yang bisa diubah pengguna. Default: nama berkas asli.
 * @param originalFileName Nama berkas asli yang diimpor (tidak berubah, hanya referensi).
 * @param audioFileName Nama berkas di penyimpanan internal app (relatif, bukan path absolut).
 *                      Contoh: "audio_1700000000000.enc". Gunakan bersama context.filesDir.
 * @param durationMs Durasi audio dalam milidetik.
 * @param fileSizeBytes Ukuran berkas asli dalam byte.
 * @param mimeType MIME type berkas asli, misalnya "audio/mp4", "audio/mpeg".
 * @param status Status pemrosesan saat ini. Lihat [RecordingStatus].
 * @param importedAt Epoch ms saat berkas berhasil diimpor.
 * @param customVocabulary Kosakata kustom untuk prompt biasing Whisper (opsional).
 * @param createdAt Epoch ms saat entitas pertama kali dibuat.
 * @param updatedAt Epoch ms saat entitas terakhir diperbarui.
 */
data class Recording(
    val id: String,
    val title: String,
    val originalFileName: String,
    val audioFileName: String,
    val durationMs: Long = 0L,
    val fileSizeBytes: Long = 0L,
    val mimeType: String = "audio/mp4",
    val status: RecordingStatus = RecordingStatus.IMPORTED,
    val importedAt: Long = System.currentTimeMillis(),
    val customVocabulary: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

/**
 * Model domain untuk satu segmen kalimat hasil transkripsi.
 *
 * Setiap segmen merupakan satu kalimat (atau penggalan ujaran) lengkap dengan
 * tanda waktu mulai dan akhir dari audio asli. Inilah yang ditampilkan baris demi
 * baris di Tab Transkrip — persis kata-kata yang diucapkan pembicara.
 *
 * @param id Identifier unik segmen.
 * @param recordingId ID rekaman induk (foreign key).
 * @param startMs Waktu mulai segmen dari awal audio (dalam milidetik).
 * @param endMs Waktu akhir segmen (dalam milidetik).
 * @param text Teks verbatim — persis apa yang diucapkan pembicara, tanpa perubahan.
 */
data class TranscriptSegment(
    val id: Long = 0,
    val recordingId: String,
    val startMs: Long,
    val endMs: Long,
    val text: String
)

/**
 * Model domain untuk notulensi rapat yang dihasilkan oleh perangkum.
 *
 * @param id Identifier unik.
 * @param recordingId ID rekaman induk (foreign key).
 * @param summaryText Poin-poin ringkasan rapat (format Markdown).
 * @param decisions Daftar keputusan rapat (format Markdown, bernomor).
 * @param actionItemsJson Daftar tugas dalam format JSON array.
 *                        Skema tiap item: {"task":"...", "pic":"...", "deadline":"..."}
 * @param mode Mode perangkum yang digunakan: "extractive" atau "byok_cloud".
 */
data class Summary(
    val id: Long = 0,
    val recordingId: String,
    val summaryText: String,
    val decisions: String,
    val actionItemsJson: String,
    val mode: String,
    val createdAt: Long = System.currentTimeMillis()
)
