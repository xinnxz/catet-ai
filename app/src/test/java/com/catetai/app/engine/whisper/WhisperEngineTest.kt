package com.catetai.app.engine.whisper

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
import org.mockito.kotlin.eq
import java.io.File

/**
 * Unit test untuk [WhisperEngine].
 *
 * Menguji seluruh alur siklus hidup model Whisper:
 * 1. Pemuatan model (loadModel)
 * 2. Transkripsi audio PCM (transcribe)
 * 3. Pembatalan (cancel)
 * 4. Pembebasan memori (release)
 */
@OptIn(ExperimentalCoroutinesApi::class)
class WhisperEngineTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var mockBridge: WhisperBridge
    private lateinit var engine: WhisperEngine

    @Before
    fun setUp() {
        mockBridge = mock(WhisperBridge::class.java)
        engine = WhisperEngine(mockBridge, testDispatcher)
    }

    // ─── loadModel Tests ─────────────────────────────────────────────────────

    @Test
    fun `loadModel with valid file succeeds and sets isModelLoaded true`() = runTest(testDispatcher) {
        val modelFile = File(tempFolder.root, "ggml-model.bin").apply {
            writeBytes(ByteArray(1024) { 1 })
        }
        `when`(mockBridge.nativeInit(modelFile.absolutePath)).thenReturn(12345L)

        val result = engine.loadModel(modelFile)

        assertTrue(result.isSuccess)
        assertTrue(engine.isModelLoaded)
        verify(mockBridge).nativeInit(modelFile.absolutePath)
    }

    @Test
    fun `loadModel with nonexistent file fails`() = runTest(testDispatcher) {
        val ghostFile = File(tempFolder.root, "not_exist.bin")

        val result = engine.loadModel(ghostFile)

        assertTrue(result.isFailure)
        assertFalse(engine.isModelLoaded)
        verify(mockBridge, never()).nativeInit(any())
    }

    @Test
    fun `loadModel with empty file fails`() = runTest(testDispatcher) {
        val emptyFile = File(tempFolder.root, "empty.bin").apply {
            writeBytes(ByteArray(0))
        }

        val result = engine.loadModel(emptyFile)

        assertTrue(result.isFailure)
        assertFalse(engine.isModelLoaded)
        verify(mockBridge, never()).nativeInit(any())
    }

    @Test
    fun `loadModel when nativeInit returns 0 fails`() = runTest(testDispatcher) {
        val modelFile = File(tempFolder.root, "corrupt.bin").apply {
            writeBytes(ByteArray(512) { 1 })
        }
        `when`(mockBridge.nativeInit(modelFile.absolutePath)).thenReturn(0L)

        val result = engine.loadModel(modelFile)

        assertTrue(result.isFailure)
        assertFalse(engine.isModelLoaded)
    }

    @Test
    fun `loadModel when already loaded releases previous model first`() = runTest(testDispatcher) {
        val modelFile1 = File(tempFolder.root, "model1.bin").apply { writeBytes(ByteArray(100) { 1 }) }
        val modelFile2 = File(tempFolder.root, "model2.bin").apply { writeBytes(ByteArray(100) { 2 }) }

        `when`(mockBridge.nativeInit(modelFile1.absolutePath)).thenReturn(111L)
        `when`(mockBridge.nativeInit(modelFile2.absolutePath)).thenReturn(222L)

        engine.loadModel(modelFile1)
        assertTrue(engine.isModelLoaded)

        engine.loadModel(modelFile2)
        verify(mockBridge).nativeRelease(111L)
        verify(mockBridge).nativeInit(modelFile2.absolutePath)
        assertTrue(engine.isModelLoaded)
    }

    // ─── transcribe Tests ────────────────────────────────────────────────────

    @Test
    fun `transcribe before loading model fails with IllegalStateException`() = runTest(testDispatcher) {
        val pcmFile = File(tempFolder.root, "audio.pcm").apply { writeBytes(ByteArray(100) { 1 }) }

        val result = engine.transcribe(recordingId = "rec-1", pcmFile = pcmFile)

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is IllegalStateException)
    }

    @Test
    fun `transcribe with valid pcm file maps segments accurately`() = runTest(testDispatcher) {
        val modelFile = File(tempFolder.root, "model.bin").apply { writeBytes(ByteArray(100) { 1 }) }
        val pcmFile = File(tempFolder.root, "sample.pcm").apply { writeBytes(ByteArray(32000) { 1 }) }

        `when`(mockBridge.nativeInit(modelFile.absolutePath)).thenReturn(999L)
        engine.loadModel(modelFile)

        // Mock output JNI array: [[startMs, endMs, text], ...]
        val mockRawOutput: Array<Array<Any>> = arrayOf(
            arrayOf(0L, 2500L, " Selamat pagi rekan-rekan. "),
            arrayOf(2500L, 5000L, " Mari kita mulai rapat sinkronisasi hari ini. ")
        )
        `when`(mockBridge.nativeTranscribe(
            eq(999L),
            eq(pcmFile.absolutePath),
            eq("id"),
            eq("Catet AI, Android, Kotlin"),
            eq(4),
            any()
        )).thenReturn(mockRawOutput)

        val config = WhisperConfig(
            language = "id",
            customVocabulary = listOf("Catet AI", "Android", "Kotlin"),
            numThreads = 4
        )

        val result = engine.transcribe(recordingId = "rec-rapat-1", pcmFile = pcmFile, config = config)

        assertTrue(result.isSuccess)
        val segments = result.getOrThrow()
        assertEquals(2, segments.size)

        assertEquals("rec-rapat-1", segments[0].recordingId)
        assertEquals(0L, segments[0].startMs)
        assertEquals(2500L, segments[0].endMs)
        assertEquals("Selamat pagi rekan-rekan.", segments[0].text)

        assertEquals("rec-rapat-1", segments[1].recordingId)
        assertEquals(2500L, segments[1].startMs)
        assertEquals(5000L, segments[1].endMs)
        assertEquals("Mari kita mulai rapat sinkronisasi hari ini.", segments[1].text)
    }

    @Test
    fun `transcribe when nativeTranscribe returns null returns failure`() = runTest(testDispatcher) {
        val modelFile = File(tempFolder.root, "model.bin").apply { writeBytes(ByteArray(100) { 1 }) }
        val pcmFile = File(tempFolder.root, "sample.pcm").apply { writeBytes(ByteArray(100) { 1 }) }

        `when`(mockBridge.nativeInit(modelFile.absolutePath)).thenReturn(999L)
        engine.loadModel(modelFile)

        `when`(mockBridge.nativeTranscribe(eq(999L), any(), any(), any(), any(), any())).thenReturn(null)

        val result = engine.transcribe(recordingId = "rec-1", pcmFile = pcmFile)

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is IllegalStateException)
    }

    // ─── cancel & release Tests ──────────────────────────────────────────────

    @Test
    fun `cancel calls bridge cancel with active handle`() = runTest(testDispatcher) {
        val modelFile = File(tempFolder.root, "model.bin").apply { writeBytes(ByteArray(100) { 1 }) }
        `when`(mockBridge.nativeInit(modelFile.absolutePath)).thenReturn(777L)
        engine.loadModel(modelFile)

        engine.cancel()

        verify(mockBridge).nativeCancel(777L)
    }

    @Test
    fun `cancel when not loaded does not call bridge`() {
        engine.cancel()
        verify(mockBridge, never()).nativeCancel(any())
    }

    @Test
    fun `release frees memory and resets isModelLoaded to false`() = runTest(testDispatcher) {
        val modelFile = File(tempFolder.root, "model.bin").apply { writeBytes(ByteArray(100) { 1 }) }
        `when`(mockBridge.nativeInit(modelFile.absolutePath)).thenReturn(555L)
        engine.loadModel(modelFile)
        assertTrue(engine.isModelLoaded)

        engine.release()

        assertFalse(engine.isModelLoaded)
        verify(mockBridge).nativeRelease(555L)
    }
}
