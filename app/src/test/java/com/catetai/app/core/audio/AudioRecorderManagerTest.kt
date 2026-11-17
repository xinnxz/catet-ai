package com.catetai.app.core.audio

import android.media.AudioRecord
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/**
 * Fake AudioRecordSource untuk mensimulasikan penangkapan mikrofon secara deterministik
 * di unit test JVM murni tanpa memerlukan hardware Android fisik atau emulator.
 */
class FakeAudioRecordSource(
    private val chunksToEmit: List<ByteArray>,
    private val delayPerChunkMs: Long = 5L
) : AudioRecordSource {

    override val audioSessionId: Int = 1001
    override val state: Int = AudioRecord.STATE_INITIALIZED

    private var chunkIndex = 0
    private var isRecording = false

    override fun startRecording() {
        isRecording = true
    }

    override fun stop() {
        isRecording = false
    }

    override fun read(audioData: ByteArray, offsetInBytes: Int, sizeInBytes: Int): Int {
        if (!isRecording) return 0
        if (chunkIndex >= chunksToEmit.size) {
            Thread.sleep(10)
            return 0
        }

        val chunk = chunksToEmit[chunkIndex++]
        val bytesToCopy = minOf(chunk.size, sizeInBytes)
        System.arraycopy(chunk, 0, audioData, offsetInBytes, bytesToCopy)

        if (delayPerChunkMs > 0) {
            Thread.sleep(delayPerChunkMs)
        }
        return bytesToCopy
    }

    override fun close() {
        stop()
    }
}

class FakeAudioSourceFactory(
    private val sourceProvider: () -> AudioRecordSource
) : AudioSourceFactory {
    override fun createSource(
        sampleRate: Int,
        channelConfig: Int,
        audioEncoding: Int,
        audioSource: Int
    ): AudioRecordSource = sourceProvider()
}

/**
 * Unit test untuk AudioRecorderManager.
 *
 * Menguji pola antrean Producer-Consumer, pemancaran amplitudo RMS,
 * kalkulasi durasi, pembuatan file WAV, dan penanganan error lifecycle.
 */
class AudioRecorderManagerTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    @Test
    fun `perekaman berhasil merekam buffer dari producer ke consumer dan menghasilkan file WAV valid`() {
        val testFile = tempFolder.newFile("recorder_integration_test.wav")
        val chunkSize = AudioConfig.VAD_CHUNK_BYTES // 1024 byte
        val chunkCount = 5
        val totalExpectedBytes = (chunkSize * chunkCount).toLong()

        // 5 chunk data audio sintetis
        val chunks = List(chunkCount) { ByteArray(chunkSize) { (it + 1).toByte() } }
        val fakeSource = FakeAudioRecordSource(chunks, delayPerChunkMs = 10L)
        val factory = FakeAudioSourceFactory { fakeSource }

        val recorder = AudioRecorderManager(audioSourceFactory = factory)

        assertFalse(recorder.isRecording.value)

        // 1. Mulai rekam
        val startResult = recorder.startRecording(testFile)
        assertTrue("startRecording harus berhasil", startResult.isSuccess)
        assertTrue("isRecording harus bernilai true", recorder.isRecording.value)

        // Tunggu sejenak agar coroutine producer & consumer memproses seluruh chunk
        Thread.sleep(200)

        // 2. Hentikan rekam
        val stopResult = recorder.stopRecording()
        assertTrue("stopRecording harus berhasil", stopResult.isSuccess)
        assertFalse("isRecording harus bernilai false", recorder.isRecording.value)

        val recordingResult = stopResult.getOrThrow()
        assertEquals(testFile.absolutePath, recordingResult.file.absolutePath)
        assertEquals(totalExpectedBytes, recordingResult.fileSizeBytes - WavHeaderUtil.HEADER_SIZE)
        assertFalse(recordingResult.wasSilencedBySystem)

        // 3. Validasi struktur file WAV hasil rekaman
        val headerInfo = WavHeaderUtil.readHeader(testFile)
        assertEquals(16_000, headerInfo.sampleRate)
        assertEquals(1, headerInfo.channels)
        assertEquals(16, headerInfo.bitsPerSample)
        assertEquals(totalExpectedBytes, headerInfo.dataSizeBytes)
        assertEquals(44L + totalExpectedBytes, headerInfo.totalFileSizeBytes)
    }

    @Test
    fun `amplitudeFlow memancarkan nilai amplitudo RMS saat audio masuk`() {
        runBlocking {
            val testFile = tempFolder.newFile("amplitude_test.wav")
            val chunkSize = AudioConfig.VAD_CHUNK_BYTES

            // Sampel gelombang bernilai 16384 (amplitudo ~0.5f)
            val halfVolumeChunk = ByteArray(chunkSize)
            for (i in 0 until chunkSize step 2) {
                halfVolumeChunk[i] = 0x00.toByte()
                halfVolumeChunk[i + 1] = 0x40.toByte() // 0x4000 = 16384
            }

            val fakeSource = FakeAudioRecordSource(listOf(halfVolumeChunk), delayPerChunkMs = 5L)
            val factory = FakeAudioSourceFactory { fakeSource }
            val recorder = AudioRecorderManager(audioSourceFactory = factory)

            recorder.startRecording(testFile)

            // Ambil emisi amplitudo pertama dari producer loop
            val emittedAmplitude = recorder.amplitudeFlow.first { it > 0f }
            assertTrue("Amplitudo harus > 0.4 dan < 0.6", emittedAmplitude in 0.4f..0.6f)

            recorder.stopRecording()
        }
    }

    @Test
    fun `perekaman ganda melempar failure IllegalStateException`() {
        val testFile1 = tempFolder.newFile("double_test1.wav")
        val testFile2 = tempFolder.newFile("double_test2.wav")

        val fakeSource = FakeAudioRecordSource(listOf(ByteArray(1024)), delayPerChunkMs = 100L)
        val factory = FakeAudioSourceFactory { fakeSource }
        val recorder = AudioRecorderManager(audioSourceFactory = factory)

        val firstStart = recorder.startRecording(testFile1)
        assertTrue(firstStart.isSuccess)

        // Perekaman kedua saat perekaman pertama masih berjalan harus gagal
        val secondStart = recorder.startRecording(testFile2)
        assertTrue(secondStart.isFailure)
        assertTrue(secondStart.exceptionOrNull() is IllegalStateException)

        recorder.stopRecording()
    }

    @Test
    fun `stopRecording saat tidak merekam melempar failure IllegalStateException`() {
        val factory = FakeAudioSourceFactory { FakeAudioRecordSource(emptyList()) }
        val recorder = AudioRecorderManager(audioSourceFactory = factory)

        val stopResult = recorder.stopRecording()
        assertTrue(stopResult.isFailure)
        assertTrue(stopResult.exceptionOrNull() is IllegalStateException)
    }

    @Test
    fun `producer consumer tahan terhadap lonjakan burst audio tanpa data loss`() {
        val testFile = tempFolder.newFile("burst_test.wav")
        val chunkSize = AudioConfig.VAD_CHUNK_BYTES
        val burstCount = 20 // 20 chunk × 1024 = 20.480 byte
        val totalExpectedBytes = (chunkSize * burstCount).toLong()

        // 20 chunk dipompa sangat cepat (delay 0 ms)
        val chunks = List(burstCount) { ByteArray(chunkSize) { 99 } }
        val fakeSource = FakeAudioRecordSource(chunks, delayPerChunkMs = 0L)
        val factory = FakeAudioSourceFactory { fakeSource }

        val recorder = AudioRecorderManager(audioSourceFactory = factory)
        recorder.startRecording(testFile)

        // Beri waktu coroutine consumer menguras antrean channel
        Thread.sleep(150)

        val stopResult = recorder.stopRecording()
        assertTrue(stopResult.isSuccess)

        val info = WavHeaderUtil.readHeader(testFile)
        assertEquals("Seluruh 20 chunk burst harus tersimpan utuh", totalExpectedBytes, info.dataSizeBytes)
    }
}
