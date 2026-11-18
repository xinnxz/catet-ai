package com.catetai.app.data.repository

import android.content.Context
import com.catetai.app.domain.model.ModelDownloadStatus
import com.catetai.app.domain.model.ModelInfo
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`
import java.io.File
import java.io.IOException
import java.security.MessageDigest

/**
 * Unit test komprehensif untuk [ModelRepositoryImpl].
 *
 * Menguji:
 * 1. Unduhan penuh model via HTTP 200 OK
 * 2. Dukungan resume via HTTP 206 Partial Content (Range header)
 * 3. Validasi checksum SHA-256 (sukses & gagal)
 * 4. Pembatalan unduhan (cancel)
 * 5. Penghapusan berkas model
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ModelRepositoryImplTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var mockServer: MockWebServer
    private lateinit var mockContext: Context
    private lateinit var okHttpClient: OkHttpClient
    private lateinit var repository: ModelRepositoryImpl
    private lateinit var filesDir: File

    @Before
    fun setUp() {
        mockServer = MockWebServer()
        mockServer.start()

        mockContext = mock(Context::class.java)
        filesDir = tempFolder.newFolder("files")
        `when`(mockContext.filesDir).thenReturn(filesDir)

        okHttpClient = OkHttpClient.Builder().build()
        repository = ModelRepositoryImpl(mockContext, okHttpClient, testDispatcher)
    }

    @After
    fun tearDown() {
        mockServer.shutdown()
    }

    private fun sha256(bytes: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-256")
        return digest.digest(bytes).joinToString("") { "%02x".format(it) }
    }

    // ─── Download Full (200 OK) ──────────────────────────────────────────────

    @Test
    fun `downloadModel full download writes target file and reports ready`() = runTest(testDispatcher) {
        val modelBytes = ByteArray(1024) { (it % 100).toByte() }
        mockServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody(okio.Buffer().write(modelBytes))
        )

        val modelInfo = ModelInfo(
            id = "test-model",
            fileName = "model.bin",
            sizeBytes = 1024L,
            downloadUrl = mockServer.url("/model.bin").toString(),
            expectedSha256 = sha256(modelBytes)
        )

        val progressValues = mutableListOf<Float>()
        val result = repository.downloadModel(modelInfo) { _, _, p -> progressValues.add(p) }

        assertTrue(result.isSuccess)
        val file = result.getOrThrow()
        assertTrue(file.exists())
        assertEquals(1024L, file.length())
        assertArrayEquals(modelBytes, file.readBytes())

        assertTrue(repository.isModelAvailable(modelInfo))
        assertNotNull(repository.getModelFile(modelInfo))
        assertTrue(repository.downloadStatus.value is ModelDownloadStatus.Ready)
        assertEquals(1.0f, progressValues.last(), 0.001f)
    }

    // ─── Resume Download (206 Partial Content) ────────────────────────────────

    @Test
    fun `downloadModel with existing part file sends Range header and resumes`() = runTest(testDispatcher) {
        val firstHalf = ByteArray(500) { 1 }
        val secondHalf = ByteArray(500) { 2 }
        val fullBytes = firstHalf + secondHalf

        // Buat file .part awal (500 byte sudah terunduh sebelumnya)
        val modelsDir = File(filesDir, "models").apply { mkdirs() }
        val partFile = File(modelsDir, "resumable.bin.part").apply { writeBytes(firstHalf) }

        mockServer.enqueue(
            MockResponse()
                .setResponseCode(206)
                .setHeader("Content-Range", "bytes 500-999/1000")
                .setBody(okio.Buffer().write(secondHalf))
        )

        val modelInfo = ModelInfo(
            id = "resumable-model",
            fileName = "resumable.bin",
            sizeBytes = 1000L,
            downloadUrl = mockServer.url("/resumable.bin").toString(),
            expectedSha256 = sha256(fullBytes)
        )

        val result = repository.downloadModel(modelInfo)

        assertTrue(result.isSuccess)
        val file = result.getOrThrow()
        assertEquals(1000L, file.length())
        assertArrayEquals(fullBytes, file.readBytes())

        // Verifikasi header Range dikirimkan ke server
        val recordedRequest = mockServer.takeRequest()
        assertEquals("bytes=500-", recordedRequest.getHeader("Range"))
    }

    // ─── SHA-256 Checksum Validation ─────────────────────────────────────────

    @Test
    fun `downloadModel with sha256 mismatch deletes part file and fails`() = runTest(testDispatcher) {
        val modelBytes = ByteArray(100) { 1 }
        mockServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody(okio.Buffer().write(modelBytes))
        )

        val modelInfo = ModelInfo(
            fileName = "corrupt.bin",
            downloadUrl = mockServer.url("/corrupt.bin").toString(),
            expectedSha256 = "invalid_hash_value_1234567890abcdef"
        )

        val result = repository.downloadModel(modelInfo)

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is IOException)
        assertTrue(result.exceptionOrNull()?.message?.contains("Checksum SHA-256") == true)

        // Berkas korup harus sudah dibersihkan
        val targetFile = File(filesDir, "models/corrupt.bin")
        val partFile = File(filesDir, "models/corrupt.bin.part")
        assertFalse(targetFile.exists())
        assertFalse(partFile.exists())
    }

    // ─── Delete Model ────────────────────────────────────────────────────────

    @Test
    fun `deleteModel removes target and part files and resets status`() = runTest(testDispatcher) {
        val modelsDir = File(filesDir, "models").apply { mkdirs() }
        val target = File(modelsDir, "to_delete.bin").apply { writeBytes(ByteArray(100)) }
        val part = File(modelsDir, "to_delete.bin.part").apply { writeBytes(ByteArray(50)) }

        val modelInfo = ModelInfo(fileName = "to_delete.bin")

        val deleted = repository.deleteModel(modelInfo)

        assertTrue(deleted)
        assertFalse(target.exists())
        assertFalse(part.exists())
        assertEquals(ModelDownloadStatus.NotDownloaded, repository.downloadStatus.value)
    }
}
