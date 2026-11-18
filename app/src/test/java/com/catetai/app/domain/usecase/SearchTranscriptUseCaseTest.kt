package com.catetai.app.domain.usecase

import com.catetai.app.domain.model.SearchResultItem
import com.catetai.app.domain.repository.TranscriptRepository
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class SearchTranscriptUseCaseTest {

    private val transcriptRepository: TranscriptRepository = mock()
    private val useCase = SearchTranscriptUseCase(transcriptRepository)

    @Test
    fun `invoke with blank query returns empty list without calling repository`() = runTest {
        val result = useCase("   ")

        assertTrue(result.isSuccess)
        assertTrue(result.getOrNull()?.isEmpty() == true)
        verify(transcriptRepository, never()).searchTranscripts(org.mockito.kotlin.any())
    }

    @Test
    fun `invoke with valid query calls repository and returns results`() = runTest {
        val sampleResults = listOf(
            SearchResultItem(
                recordingId = "rec-1",
                recordingTitle = "Rapat Q4",
                segmentId = 1,
                startMs = 1000L,
                endMs = 4000L,
                snippet = "Target omzet tercapai"
            )
        )
        whenever(transcriptRepository.searchTranscripts("omzet")).thenReturn(Result.success(sampleResults))

        val result = useCase("omzet")

        assertTrue(result.isSuccess)
        assertEquals(1, result.getOrNull()?.size)
        assertEquals("Target omzet tercapai", result.getOrNull()?.first()?.snippet)
        verify(transcriptRepository).searchTranscripts("omzet")
    }
}
