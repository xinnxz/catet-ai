package com.catetai.app.domain.model

/**
 * Hasil satu item pencarian teks transkrip.
 *
 * Menggabungkan informasi dari dua tabel (transcript_segments dan recordings)
 * sehingga UI dapat menampilkan:
 * - Judul rekaman (dari recordings)
 * - Potongan teks (snippet) yang cocok (dari transcript_segments)
 * - Tanda waktu segmen (untuk navigasi langsung ke momen tersebut)
 * - ID rekaman (untuk navigasi ke DetailScreen)
 *
 * @param recordingId   ID rekaman induk.
 * @param recordingTitle Judul rekaman (judul yang tampil di HomeScreen).
 * @param segmentId     ID segmen transkrip (primary key).
 * @param startMs       Waktu mulai segmen dalam milidetik (untuk seek audio).
 * @param endMs         Waktu akhir segmen dalam milidetik.
 * @param snippet       Teks asli segmen yang cocok dengan query pencarian.
 */
data class SearchResultItem(
    val recordingId: String,
    val recordingTitle: String,
    val segmentId: Long,
    val startMs: Long,
    val endMs: Long,
    val snippet: String
)
