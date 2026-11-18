package com.catetai.app.domain.usecase

import com.catetai.app.domain.model.SearchResultItem
import com.catetai.app.domain.repository.TranscriptRepository
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Use case untuk melakukan pencarian kalimat transkrip di seluruh rekaman rapat (F4-03).
 *
 * Menggunakan Full-Text Search (FTS4) pada SQLite dengan fallback aman ke pencarian LIKE.
 * Mengembalikan daftar [SearchResultItem] yang berisi judul rekaman, timestamp, dan potongan kalimat.
 */
@Singleton
class SearchTranscriptUseCase @Inject constructor(
    private val transcriptRepository: TranscriptRepository
) {
    suspend operator fun invoke(query: String): Result<List<SearchResultItem>> {
        val trimmed = query.trim()
        if (trimmed.isBlank()) {
            return Result.success(emptyList())
        }
        return transcriptRepository.searchTranscripts(trimmed)
    }
}
