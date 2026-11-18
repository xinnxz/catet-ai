package com.catetai.app.presentation.detail

import androidx.lifecycle.SavedStateHandle
import com.catetai.app.domain.model.ModelInfo
import com.catetai.app.domain.model.Recording
import com.catetai.app.domain.model.RecordingStatus
import com.catetai.app.domain.model.Summary
import com.catetai.app.domain.model.TranscriptSegment
import com.catetai.app.domain.repository.RecordingRepository
import com.catetai.app.domain.repository.TranscriptRepository
import com.catetai.app.domain.usecase.DownloadModelUseCase
import com.catetai.app.domain.usecase.GetModelStatusUseCase
import com.catetai.app.domain.usecase.GetSummaryUseCase
import com.catetai.app.domain.usecase.SummarizeTranscriptUseCase
import com.catetai.app.domain.usecase.TranscribeAudioUseCase
import com.catetai.app.testutil.MainDispatcherRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.io.File

@OptIn(ExperimentalCoroutinesApi::class)
class DetailViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val recordingRepository: RecordingRepository = mock()
    private val transcriptRepository: TranscriptRepository = mock()
    private val transcribeAudioUseCase: TranscribeAudioUseCase = mock()
    private val getModelStatusUseCase: GetModelStatusUseCase = mock()
    private val downloadModelUseCase: DownloadModelUseCase = mock()
    private val getSummaryUseCase: GetSummaryUseCase = mock()
    private val summarizeTranscriptUseCase: SummarizeTranscriptUseCase = mock()

    private val testRecordingId = "rec-test-123"
    private val testRecording = Recording(
        id = testRecordingId,
        title = "Rapat Tim Catet AI",
        originalFileName = "audio.m4a",
        audioFileName = "audio.enc",
        durationMs = 120_000L,
        fileSizeBytes = 2_000_000L,
        status = RecordingStatus.IMPORTED,
        customVocabulary = "Catet AI, Jetpack Compose"
    )

    private val segmentsFlow = MutableStateFlow<List<TranscriptSegment>>(emptyList())
    private val summaryFlow = MutableStateFlow<Summary?>(null)

    @Before
    fun setUp() = runTest {
        whenever(recordingRepository.getRecordingById(testRecordingId)).thenReturn(testRecording)
        whenever(transcriptRepository.getSegmentsByRecordingId(testRecordingId)).thenReturn(segmentsFlow)
        whenever(getSummaryUseCase.invoke(testRecordingId)).thenReturn(summaryFlow)
    }

    private fun createViewModel(recordingId: String? = testRecordingId): DetailViewModel {
        val savedStateHandle = if (recordingId != null) {
            SavedStateHandle(mapOf("recordingId" to recordingId))
        } else {
            SavedStateHandle()
        }
        return DetailViewModel(
            savedStateHandle = savedStateHandle,
            recordingRepository = recordingRepository,
            transcriptRepository = transcriptRepository,
            transcribeAudioUseCase = transcribeAudioUseCase,
            getModelStatusUseCase = getModelStatusUseCase,
            downloadModelUseCase = downloadModelUseCase,
            getSummaryUseCase = getSummaryUseCase,
            summarizeTranscriptUseCase = summarizeTranscriptUseCase
        )
    }

    @Test
    fun init_withRecordingId_loadsRecordingAndCollectsSegments() = runTest {
        val segment1 = TranscriptSegment(
            id = 1,
            recordingId = testRecordingId,
            startMs = 0L,
            endMs = 4000L,
            text = "Halo selamat pagi semuanya."
        )
        segmentsFlow.value = listOf(segment1)

        val viewModel = createViewModel()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertFalse(state.isLoading)
        assertEquals(testRecording, state.recording)
        assertEquals(1, state.segments.size)
        assertEquals("Halo selamat pagi semuanya.", state.segments.first().text)
        assertEquals("Catet AI, Jetpack Compose", state.vocabularyInput)
    }

    @Test
    fun processIntent_selectTab_updatesSelectedTab() = runTest {
        val viewModel = createViewModel()
        advanceUntilIdle()

        assertEquals(DetailTab.TRANSCRIPT, viewModel.uiState.value.selectedTab)

        viewModel.processIntent(DetailIntent.SelectTab(DetailTab.SUMMARY))
        assertEquals(DetailTab.SUMMARY, viewModel.uiState.value.selectedTab)

        viewModel.processIntent(DetailIntent.SelectTab(DetailTab.TRANSCRIPT))
        assertEquals(DetailTab.TRANSCRIPT, viewModel.uiState.value.selectedTab)
    }

    @Test
    fun processIntent_vocabularyDialog_opensDismissesAndUpdatesInput() = runTest {
        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.processIntent(DetailIntent.OpenVocabularyDialog)
        assertTrue(viewModel.uiState.value.showVocabularyDialog)
        assertEquals("Catet AI, Jetpack Compose", viewModel.uiState.value.vocabularyInput)

        viewModel.processIntent(DetailIntent.UpdateVocabularyInput("Whisper, Kotlin"))
        assertEquals("Whisper, Kotlin", viewModel.uiState.value.vocabularyInput)

        viewModel.processIntent(DetailIntent.DismissVocabularyDialog)
        assertFalse(viewModel.uiState.value.showVocabularyDialog)
    }

    @Test
    fun processIntent_saveVocabulary_updatesRepositoryAndState() = runTest {
        whenever(recordingRepository.updateCustomVocabulary(testRecordingId, "Kotlin, Android"))
            .thenReturn(Result.success(Unit))

        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.processIntent(DetailIntent.OpenVocabularyDialog)
        viewModel.processIntent(DetailIntent.UpdateVocabularyInput("Kotlin, Android"))
        viewModel.processIntent(DetailIntent.SaveVocabulary)
        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.showVocabularyDialog)
        assertEquals("Kotlin, Android", viewModel.uiState.value.recording?.customVocabulary)
        verify(recordingRepository).updateCustomVocabulary(testRecordingId, "Kotlin, Android")
    }

    @Test
    fun processIntent_startTranscription_whenModelNotAvailable_showsDownloadDialog() = runTest {
        whenever(getModelStatusUseCase.isModelAvailable(any())).thenReturn(false)

        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.processIntent(DetailIntent.StartTranscription)
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.showModelDownloadDialog)
        assertFalse(viewModel.uiState.value.isTranscribing)
    }

    @Test
    fun processIntent_startTranscription_whenModelAvailable_executesTranscriptionSuccessfully() = runTest {
        val dummyModel = File("dummy-model.bin")
        whenever(getModelStatusUseCase.isModelAvailable(any())).thenReturn(true)
        whenever(getModelStatusUseCase.getModelFile(any())).thenReturn(dummyModel)

        val resultSegments = listOf(
            TranscriptSegment(
                id = 10,
                recordingId = testRecordingId,
                startMs = 0L,
                endMs = 3000L,
                text = "Rapat dimulai sekarang."
            )
        )
        whenever(transcribeAudioUseCase.invoke(any(), anyOrNull(), any(), any()))
            .thenReturn(Result.success(resultSegments))
        whenever(summarizeTranscriptUseCase.invoke(any()))
            .thenReturn(Result.success(mock()))

        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.processIntent(DetailIntent.StartTranscription)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertFalse(state.isTranscribing)
        assertEquals(1f, state.transcriptionProgress)
        assertEquals(RecordingStatus.COMPLETED, state.recording?.status)
    }

    @Test
    fun processIntent_confirmModelDownload_downloadsModelAndStartsTranscription() = runTest {
        val dummyModel = File("dummy-model.bin")
        whenever(getModelStatusUseCase.getModelFile(any())).thenReturn(dummyModel)
        whenever(downloadModelUseCase.invoke(any(), any())).thenReturn(Result.success(dummyModel))
        whenever(transcribeAudioUseCase.invoke(any(), anyOrNull(), any(), any())).thenReturn(Result.success(emptyList()))
        whenever(summarizeTranscriptUseCase.invoke(any())).thenReturn(Result.success(mock()))

        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.processIntent(DetailIntent.ConfirmModelDownload)
        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.isModelDownloading)
        assertFalse(viewModel.uiState.value.showModelDownloadDialog)
        verify(downloadModelUseCase).invoke(any(), any())
        verify(transcribeAudioUseCase).invoke(any(), anyOrNull(), any(), any())
    }

    @Test
    fun processIntent_cancelTranscription_cancelsJobAndSetsStatusInterrupted() = runTest {
        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.processIntent(DetailIntent.CancelTranscription)
        advanceUntilIdle()

        verify(transcribeAudioUseCase).cancel()
        assertFalse(viewModel.uiState.value.isTranscribing)
        assertEquals(RecordingStatus.INTERRUPTED, viewModel.uiState.value.recording?.status)
    }

    @Test
    fun init_withRecordingId_observesSummaryFlow() = runTest {
        val expectedSummary = Summary(
            id = 1L,
            recordingId = testRecordingId,
            summaryText = "• Poin penting rapat.",
            decisions = "1. Sepakat rilis.",
            actionItemsJson = "[]",
            mode = "extractive"
        )
        summaryFlow.value = expectedSummary

        val viewModel = createViewModel()
        advanceUntilIdle()

        assertEquals(expectedSummary, viewModel.uiState.value.summary)
    }

    @Test
    fun processIntent_generateSummary_callsSummarizeTranscriptUseCaseSuccessfully() = runTest {
        val expectedSummary = Summary(
            id = 2L,
            recordingId = testRecordingId,
            summaryText = "• Ringkasan baru.",
            decisions = "",
            actionItemsJson = "[]",
            mode = "extractive"
        )
        whenever(summarizeTranscriptUseCase.invoke(testRecordingId))
            .thenReturn(Result.success(expectedSummary))

        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.processIntent(DetailIntent.GenerateSummary)
        advanceUntilIdle()

        verify(summarizeTranscriptUseCase).invoke(testRecordingId)
        assertFalse(viewModel.uiState.value.isSummarizing)
        assertEquals(expectedSummary, viewModel.uiState.value.summary)
    }
}
