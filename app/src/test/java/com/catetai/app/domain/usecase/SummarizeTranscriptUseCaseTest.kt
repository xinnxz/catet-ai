package com.catetai.app.domain.usecase

import com.catetai.app.domain.model.RecordingStatus
import com.catetai.app.domain.model.Summary
import com.catetai.app.domain.model.TranscriptSegment
import com.catetai.app.domain.repository.RecordingRepository
import com.catetai.app.domain.repository.SummaryRepository
import com.catetai.app.domain.repository.TranscriptRepository
import com.catetai.app.engine.summarizer.SummarizerEngine
import com.catetai.app.engine.summarizer.SummaryResult
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

@OptIn(ExperimentalCoroutinesApi::class)
class SummarizeTranscriptUseCaseTest {

    private val testDispatcher = StandardTestDispatcher()

    private lateinit var recordingRepository: RecordingRepository
    private lateinit var transcriptRepository: TranscriptRepository
    private lateinit var summaryRepository: SummaryRepository
    private lateinit var summarizerEngine: SummarizerEngine
    private lateinit var useCase: SummarizeTranscriptUseCase

    private val testRecordingId = "rec-test-101"

    @Before
    fun setUp() {
        recordingRepository = mock()
        transcriptRepository = mock()
        summaryRepository = mock()
        summarizerEngine = mock()

        useCase = SummarizeTranscriptUseCase(
            recordingRepository = recordingRepository,
            transcriptRepository = transcriptRepository,
            summaryRepository = summaryRepository,
            summarizerEngine = summarizerEngine,
            ioDispatcher = testDispatcher
        )
    }

    @Test
    fun invoke_whenSegmentsEmpty_returnsFailureWithoutRunningEngine() = runTest(testDispatcher) {
        whenever(transcriptRepository.getSegmentsByRecordingIdOnce(testRecordingId))
            .thenReturn(emptyList())

        val result = useCase(testRecordingId)

        assertTrue(result.isFailure)
        verify(summarizerEngine, never()).summarize(any())
        verify(summaryRepository, never()).insertSummary(any())
    }

    @Test
    fun invoke_whenSuccessful_updatesStatusToSummarizingThenCompletedAndSavesSummary() = runTest(testDispatcher) {
        val segments = listOf(
            TranscriptSegment(1, testRecordingId, 0, 5000, "Diskusi rapat tim engineering."),
            TranscriptSegment(2, testRecordingId, 6000, 12000, "Sepakat merilis versi MVP.")
        )
        whenever(transcriptRepository.getSegmentsByRecordingIdOnce(testRecordingId))
            .thenReturn(segments)

        val summaryResult = SummaryResult(
            summaryText = "• Diskusi rapat tim engineering.",
            decisions = "1. Sepakat merilis versi MVP.",
            actionItemsJson = "[]",
            mode = "extractive"
        )
        whenever(summarizerEngine.summarize(segments))
            .thenReturn(Result.success(summaryResult))
        whenever(summaryRepository.insertSummary(any()))
            .thenReturn(Result.success(1L))
        whenever(recordingRepository.updateStatus(any(), any()))
            .thenReturn(Result.success(Unit))

        val result = useCase(testRecordingId)

        assertTrue(result.isSuccess)
        val summary = result.getOrNull()
        assertNotNull(summary)
        assertEquals(testRecordingId, summary?.recordingId)
        assertEquals("• Diskusi rapat tim engineering.", summary?.summaryText)
        assertEquals("1. Sepakat merilis versi MVP.", summary?.decisions)
        assertEquals("extractive", summary?.mode)

        verify(recordingRepository).updateStatus(testRecordingId, RecordingStatus.SUMMARIZING)
        verify(recordingRepository).updateStatus(testRecordingId, RecordingStatus.COMPLETED)
        verify(summaryRepository).insertSummary(any())
    }

    @Test
    fun invoke_whenSummarizerEngineFails_updatesStatusToFailedAndReturnsFailure() = runTest(testDispatcher) {
        val segments = listOf(
            TranscriptSegment(1, testRecordingId, 0, 5000, "Diskusi rapat tim.")
        )
        whenever(transcriptRepository.getSegmentsByRecordingIdOnce(testRecordingId))
            .thenReturn(segments)
        whenever(summarizerEngine.summarize(segments))
            .thenReturn(Result.failure(RuntimeException("Engine error")))

        val result = useCase(testRecordingId)

        assertTrue(result.isFailure)
        verify(recordingRepository).updateStatus(testRecordingId, RecordingStatus.SUMMARIZING)
        verify(recordingRepository).updateStatus(testRecordingId, RecordingStatus.FAILED)
        verify(summaryRepository, never()).insertSummary(any())
    }
}
