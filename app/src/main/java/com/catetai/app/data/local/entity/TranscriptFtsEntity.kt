package com.catetai.app.data.local.entity

import androidx.room.Entity
import androidx.room.Fts4

/**
 * TranscriptFtsEntity — Tabel Virtual FTS4 untuk Pencarian Teks Transkrip Cepat.
 *
 * # Apa itu FTS4?
 * Full-Text Search 4 (FTS4) adalah ekstensi SQLite yang membuat indeks teks terbalik
 * (inverted index) — seperti indeks di belakang buku — atas kolom yang ditentukan.
 * Query `MATCH :kata` mencari semua baris yang mengandung kata tersebut dalam waktu
 * O(log n) alih-alih O(n) scan linier biasa yang lambat.
 *
 * # contentEntity
 * `contentEntity = TranscriptSegmentEntity::class` berarti FTS4 adalah "shadow table"
 * dari tabel `transcript_segments`. Tabel FTS ini TIDAK menyimpan data fisik —
 * ia hanya menyimpan indeks. Data aslinya tetap di `transcript_segments`.
 *
 * # Cara Kerja JOIN di Query
 * Karena FTS4 adalah virtual table, cara kerjanya:
 * 1. FTS4 MATCH menemukan rowid yang cocok dalam milidetik (dari indeks teks).
 * 2. JOIN dengan `transcript_segments` menggunakan `rowid = id` untuk mendapatkan
 *    data lengkap (recording_id, start_ms, text, dll).
 *
 * # Pembaruan Indeks (Trigger Otomatis)
 * Room secara otomatis membuat trigger INSERT/UPDATE/DELETE pada tabel content
 * sehingga indeks FTS selalu sinkron dengan data di `transcript_segments`.
 */
@Fts4(contentEntity = TranscriptSegmentEntity::class)
@Entity(tableName = "transcript_segments_fts")
data class TranscriptFtsEntity(
    val text: String
)
