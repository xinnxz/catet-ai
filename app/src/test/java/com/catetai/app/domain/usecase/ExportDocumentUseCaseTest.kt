package com.catetai.app.domain.usecase

import android.content.Context
import com.catetai.app.domain.model.ExportFormat
import com.catetai.app.domain.model.Recording
import com.catetai.app.domain.model.RecordingStatus
import com.catetai.app.domain.repository.RecordingRepository
import com.catetai.app.domain.repository.SummaryRepository
import com.catetai.app.domain.repository.TranscriptRepository
import com.catetai.app.engine.export.MarkdownExporter
import com.catetai.app.engine.export.PdfExporter
import com.catetai.app.engine.export.TextExporter
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.io.File

@OptIn(ExperimentalCoroutinesApi::class)
class ExportDocumentUseCaseTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private val testDispatcher = StandardTestDispatcher()

    private lateinit var mockContext: Context
    private lateinit var recordingRepository: RecordingRepository
    private lateinit var transcriptRepository: TranscriptRepository
    private lateinit var summaryRepository: SummaryRepository
    private lateinit var pdfExporter: PdfExporter
    private lateinit var markdownExporter: MarkdownExporter
    private lateinit var textExporter: TextExporter
    private lateinit var useCase: ExportDocumentUseCase

    private val testRecordingId = "rec-export-1"
    private val testRecording = Recording(
        id = testRecordingId,
        title = "Sprint Review",
        originalFileName = "audio.m4a",
        audioFileName = "audio.enc",
        durationMs = 60_000L,
        fileSizeBytes = 1_000_000L,
        status = RecordingStatus.COMPLETED
    )

    @Before
    fun setUp() {
        mockContext = mock()
        recordingRepository = mock()
        transcriptRepository = mock()
        summaryRepository = mock()
        pdfExporter = mock()
        markdownExporter = mock()
        textExporter = mock()

        val cacheDir = tempFolder.newFolder("cache")
        whenever(mockContext.cacheDir).thenReturn(cacheDir)

        useCase = ExportDocumentUseCase(
            context = mockContext,
            recordingRepository = recordingRepository,
            transcriptRepository = transcriptRepository,
            summaryRepository = summaryRepository,
            pdfExporter = pdfExporter,
            markdownExporter = markdownExporter,
            textExporter = textExporter,
            ioDispatcher = testDispatcher
        )
    }

    @Test
    fun invoke_whenRecordingNotFound_returnsFailure() = runTest(testDispatcher) {
        whenever(recordingRepository.getRecordingById(testRecordingId)).thenReturn(null)

        val result = useCase(testRecordingId, ExportFormat.MARKDOWN)

        assertTrue(result.isFailure)
    }

    @Test
    fun invoke_withMarkdownFormat_callsMarkdownExporter() = runTest(testDispatcher) {
        whenever(recordingRepository.getRecordingById(testRecordingId)).thenReturn(testRecording)
        whenever(transcriptRepository.getSegmentsByRecordingIdOnce(testRecordingId)).thenReturn(emptyList())
        whenever(summaryRepository.getSummaryByRecordingIdOnce(testRecordingId)).thenReturn(null)

        val dummyExportedFile = File(tempFolder.root, "CatetAI_Sprint_Review.md")
        whenever(markdownExporter.export(any(), any(), anyOrNull(), any())).thenReturn(Result.success(dummyExportedFile))

        val result = useCase(testRecordingId, ExportFormat.MARKDOWN)

        assertTrue(result.isSuccess)
        verify(markdownExporter).export(any(), any(), anyOrNull(), any())
    }

    @Test
    fun invoke_withTextFormat_callsTextExporter() = runTest(testDispatcher) {
        whenever(recordingRepository.getRecordingById(testRecordingId)).thenReturn(testRecording)
        whenever(transcriptRepository.getSegmentsByRecordingIdOnce(testRecordingId)).thenReturn(emptyList())
        whenever(summaryRepository.getSummaryByRecordingIdOnce(testRecordingId)).thenReturn(null)

        val dummyExportedFile = File(tempFolder.root, "CatetAI_Sprint_Review.txt")
        whenever(textExporter.export(any(), any(), anyOrNull(), any())).thenReturn(Result.success(dummyExportedFile))

        val result = useCase(testRecordingId, ExportFormat.TXT)

        assertTrue(result.isSuccess)
        verify(textExporter).export(any(), any(), anyOrNull(), any())
    }
}
