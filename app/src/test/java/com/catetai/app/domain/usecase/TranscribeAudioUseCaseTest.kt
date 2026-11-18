package com.catetai.app.domain.usecase

import android.content.Context
import com.catetai.app.core.crypto.AudioEncryptor
import com.catetai.app.domain.model.Recording
import com.catetai.app.domain.model.RecordingStatus
import com.catetai.app.domain.model.TranscriptSegment
import com.catetai.app.domain.repository.RecordingRepository
import com.catetai.app.domain.repository.TranscriptRepository
import com.catetai.app.engine.decoder.AudioDecoder
import com.catetai.app.engine.whisper.WhisperConfig
import com.catetai.app.engine.whisper.WhisperEngine
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.argThat
import org.mockito.kotlin.check
import org.mockito.kotlin.eq
import java.io.File
import java.io.OutputStream

/**
 * Unit test komprehensif untuk [TranscribeAudioUseCase] (F2-04).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class TranscribeAudioUseCaseTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private val testDispatcher = StandardTestDispatcher()

    private lateinit var mockContext: Context
    private lateinit var mockRecordingRepo: RecordingRepository
    private lateinit var mockTranscriptRepo: TranscriptRepository
    private lateinit var mockEncryptor: AudioEncryptor
    private lateinit var mockDecoder: AudioDecoder
    private lateinit var mockWhisperEngine: WhisperEngine

    private lateinit var useCase: TranscribeAudioUseCase

    private lateinit var filesDir: File
    private lateinit var cacheDir: File
    private lateinit var audioDir: File

    @Before
    fun setUp() {
        mockContext = mock(Context::class.java)
        mockRecordingRepo = mock(RecordingRepository::class.java)
        mockTranscriptRepo = mock(TranscriptRepository::class.java)
        mockEncryptor = mock(AudioEncryptor::class.java)
        mockDecoder = mock(AudioDecoder::class.java)
        mockWhisperEngine = mock(WhisperEngine::class.java)

        filesDir = tempFolder.newFolder("files")
        cacheDir = tempFolder.newFolder("cache")
        audioDir = File(filesDir, "audio").apply { mkdirs() }

        `when`(mockContext.filesDir).thenReturn(filesDir)
        `when`(mockContext.cacheDir).thenReturn(cacheDir)

        useCase = TranscribeAudioUseCase(
            context = mockContext,
            recordingRepository = mockRecordingRepo,
            transcriptRepository = mockTranscriptRepo,
            audioEncryptor = mockEncryptor,
            audioDecoder = mockDecoder,
            whisperEngine = mockWhisperEngine,
            ioDispatcher = testDispatcher
        )
    }

    private fun createSampleRecording(
        id: String = "rec-001",
        audioFileName: String = "audio_123.enc",
        customVocabulary: String? = null
    ): Recording {
        // Buat file terenkripsi palsu di audioDir
        File(audioDir, audioFileName).apply {
            writeBytes(ByteArray(1024) { 1 })
        }

        return Recording(
            id = id,
            title = "Rapat Q3",
            originalFileName = "recording.m4a",
            audioFileName = audioFileName,
            durationMs = 60_000L,
            customVocabulary = customVocabulary,
            status = RecordingStatus.IMPORTED
        )
    }

    // ─── Input Validation Tests ──────────────────────────────────────────────

    @Test
    fun `invoke when recording not found returns failure`() = runTest(testDispatcher) {
        `when`(mockRecordingRepo.getRecordingById("ghost-id")).thenReturn(null)

        val result = useCase("ghost-id")

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is IllegalArgumentException)
        verify(mockRecordingRepo, never()).updateStatus(any(), any())
    }

    @Test
    fun `invoke when encrypted file does not exist returns failure`() = runTest(testDispatcher) {
        val recording = Recording(
            id = "rec-missing",
            title = "Missing",
            originalFileName = "rec.m4a",
            audioFileName = "non_existent.enc",
            status = RecordingStatus.IMPORTED
        )
        `when`(mockRecordingRepo.getRecordingById("rec-missing")).thenReturn(recording)

        val result = useCase("rec-missing")

        assertTrue(result.isFailure)
        verify(mockRecordingRepo, never()).updateStatus(any(), any())
    }

    @Test
    fun `invoke when model not loaded and no modelFile provided returns failure`() = runTest(testDispatcher) {
        val recording = createSampleRecording()
        `when`(mockRecordingRepo.getRecordingById(recording.id)).thenReturn(recording)
        `when`(mockWhisperEngine.isModelLoaded).thenReturn(false)

        val result = useCase(recording.id)

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is IllegalStateException)
    }

    // ─── Happy Path (Audio <= 30 mins) ───────────────────────────────────────

    @Test
    fun `invoke happy path under 30 mins decrypts decodes transcribes and saves segments`() = runTest(testDispatcher) {
        val recording = createSampleRecording(customVocabulary = "Catet AI, Android")
        `when`(mockRecordingRepo.getRecordingById(recording.id)).thenReturn(recording)
        `when`(mockWhisperEngine.isModelLoaded).thenReturn(true)
        `when`(mockTranscriptRepo.deleteSegmentsByRecordingId(recording.id)).thenReturn(Result.success(Unit))
        `when`(mockTranscriptRepo.insertSegments(any())).thenReturn(Result.success(Unit))

        // Mock dekripsi: tulis byte dummy
        `when`(mockEncryptor.decrypt(any(), any(), any())).thenAnswer { invocation ->
            val out = invocation.arguments[1] as OutputStream
            out.write(ByteArray(500) { 1 })
            Result.success(Unit)
        }

        // Mock decoder: hasilkan file PCM 16kHz
        `when`(mockDecoder.decode(any(), any(), any())).thenAnswer { invocation ->
            val pcmOut = invocation.arguments[1] as File
            pcmOut.writeBytes(ByteArray(64000) { 1 }) // 2 detik PCM
            Result.success(pcmOut)
        }

        // Mock whisperEngine: kembalikan 2 segmen transkrip
        val mockSegments = listOf(
            TranscriptSegment(id = 0, recordingId = recording.id, startMs = 0, endMs = 1500, text = "Halo Catet AI"),
            TranscriptSegment(id = 0, recordingId = recording.id, startMs = 1500, endMs = 2000, text = "Siap digunakan")
        )
        `when`(mockWhisperEngine.transcribe(eq(recording.id), any(), any(), any()))
            .thenReturn(Result.success(mockSegments))

        val progressList = mutableListOf<Float>()
        val result = useCase(recording.id, onProgress = { progressList.add(it) })

        assertTrue(result.isSuccess)
        val segments = result.getOrThrow()
        assertEquals(2, segments.size)
        assertEquals("Halo Catet AI", segments[0].text)

        // Verifikasi transisi status: DECODING -> TRANSCRIBING -> COMPLETED
        verify(mockRecordingRepo).updateStatus(recording.id, RecordingStatus.DECODING)
        verify(mockRecordingRepo).updateStatus(recording.id, RecordingStatus.TRANSCRIBING)
        verify(mockRecordingRepo).updateStatus(recording.id, RecordingStatus.COMPLETED)

        // Verifikasi segmen disimpan ke repository
        verify(mockTranscriptRepo).insertSegments(mockSegments)

        // Verifikasi progres mencapai 1.0f
        assertTrue(progressList.isNotEmpty())
        assertEquals(1.0f, progressList.last(), 0.001f)
    }

    // ─── Custom Vocabulary Integration Test ──────────────────────────────────

    @Test
    fun `invoke combines vocabulary from config and recording entity`() = runTest(testDispatcher) {
        val recording = createSampleRecording(customVocabulary = "Catet AI, Kotlin")
        `when`(mockRecordingRepo.getRecordingById(recording.id)).thenReturn(recording)
        `when`(mockWhisperEngine.isModelLoaded).thenReturn(true)
        `when`(mockTranscriptRepo.deleteSegmentsByRecordingId(recording.id)).thenReturn(Result.success(Unit))
        `when`(mockTranscriptRepo.insertSegments(any())).thenReturn(Result.success(Unit))

        `when`(mockEncryptor.decrypt(any(), any(), any())).thenAnswer {
            (it.arguments[1] as OutputStream).write(ByteArray(10) { 1 })
            Result.success(Unit)
        }
        `when`(mockDecoder.decode(any(), any(), any())).thenAnswer {
            val f = it.arguments[1] as File
            f.writeBytes(ByteArray(100) { 1 })
            Result.success(f)
        }

        `when`(mockWhisperEngine.transcribe(eq(recording.id), any(), any(), any()))
            .thenReturn(Result.success(emptyList()))

        val config = WhisperConfig(customVocabulary = listOf("Whisper", "Jetpack Compose"))
        useCase(recording.id, config = config)

        // Verifikasi bahwa konfigurasi yang diteruskan ke WhisperEngine menggabungkan keduanya
        verify(mockWhisperEngine).transcribe(
            eq(recording.id),
            any(),
            argThat {
                customVocabulary.contains("Catet AI") &&
                        customVocabulary.contains("Kotlin") &&
                        customVocabulary.contains("Whisper") &&
                        customVocabulary.contains("Jetpack Compose")
            },
            any()
        )
    }

    // ─── Error Handling & Cancellation Tests ─────────────────────────────────

    @Test
    fun `invoke when decoder fails updates status to FAILED`() = runTest(testDispatcher) {
        val recording = createSampleRecording()
        `when`(mockRecordingRepo.getRecordingById(recording.id)).thenReturn(recording)
        `when`(mockWhisperEngine.isModelLoaded).thenReturn(true)

        `when`(mockEncryptor.decrypt(any(), any(), any())).thenAnswer {
            (it.arguments[1] as OutputStream).write(ByteArray(10) { 1 })
            Result.success(Unit)
        }
        `when`(mockDecoder.decode(any(), any(), any())).thenReturn(Result.failure(RuntimeException("Decoder error")))

        val result = useCase(recording.id)

        assertTrue(result.isFailure)
        verify(mockRecordingRepo).updateStatus(recording.id, RecordingStatus.FAILED)
    }

    @Test
    fun `invoke when cancelled updates status to INTERRUPTED and cancels whisper`() = runTest(testDispatcher) {
        val recording = createSampleRecording()
        `when`(mockRecordingRepo.getRecordingById(recording.id)).thenReturn(recording)
        `when`(mockWhisperEngine.isModelLoaded).thenReturn(true)

        `when`(mockEncryptor.decrypt(any(), any(), any())).thenAnswer {
            (it.arguments[1] as OutputStream).write(ByteArray(10) { 1 })
            Result.success(Unit)
        }
        `when`(mockDecoder.decode(any(), any(), any())).thenAnswer {
            val f = it.arguments[1] as File
            f.writeBytes(ByteArray(100) { 1 })
            Result.success(f)
        }
        `when`(mockWhisperEngine.transcribe(any(), any(), any(), any())).thenAnswer {
            throw CancellationException("Transkripsi dibatalkan oleh pengguna")
        }

        try {
            useCase(recording.id)
        } catch (e: Exception) {
            assertTrue(e is CancellationException)
        }

        verify(mockWhisperEngine).cancel()
        verify(mockRecordingRepo).updateStatus(recording.id, RecordingStatus.INTERRUPTED)
    }

    @Test
    fun `cancel method delegates to whisperEngine cancel`() {
        useCase.cancel()
        verify(mockWhisperEngine).cancel()
    }
}
