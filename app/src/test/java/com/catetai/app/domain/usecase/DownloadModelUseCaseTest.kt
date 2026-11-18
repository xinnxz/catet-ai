package com.catetai.app.domain.usecase

import android.content.Context
import com.catetai.app.domain.model.ModelInfo
import com.catetai.app.domain.repository.ModelRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
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
import java.io.File
import java.io.IOException

/**
 * Unit test untuk [DownloadModelUseCase].
 */
@OptIn(ExperimentalCoroutinesApi::class)
class DownloadModelUseCaseTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var mockContext: Context
    private lateinit var mockRepository: ModelRepository
    private lateinit var useCase: DownloadModelUseCase
    private lateinit var filesDir: File

    @Before
    fun setUp() {
        mockContext = mock(Context::class.java)
        mockRepository = mock(ModelRepository::class.java)

        filesDir = tempFolder.newFolder("files")
        `when`(mockContext.filesDir).thenReturn(filesDir)

        useCase = DownloadModelUseCase(
            context = mockContext,
            modelRepository = mockRepository,
            ioDispatcher = testDispatcher
        )
    }

    @Test
    fun `invoke when model already available returns existing file immediately`() = runTest(testDispatcher) {
        val dummyFile = File(filesDir, "model.bin").apply { writeBytes(ByteArray(100)) }
        val modelInfo = ModelInfo(fileName = "model.bin")

        `when`(mockRepository.isModelAvailable(modelInfo)).thenReturn(true)
        `when`(mockRepository.getModelFile(modelInfo)).thenReturn(dummyFile)

        val result = useCase(modelInfo)

        assertTrue(result.isSuccess)
        assertEquals(dummyFile, result.getOrThrow())
        verify(mockRepository, never()).downloadModel(any(), any())
    }

    @Test
    fun `invoke when space is sufficient delegates to repository downloadModel`() = runTest(testDispatcher) {
        // model 1 KB, filesDir di hard drive punya banyak GB usableSpace
        val modelInfo = ModelInfo(sizeBytes = 1024L)
        val expectedFile = File(filesDir, "downloaded.bin")

        `when`(mockRepository.isModelAvailable(modelInfo)).thenReturn(false)
        `when`(mockRepository.downloadModel(any(), any())).thenReturn(Result.success(expectedFile))

        val result = useCase(modelInfo)

        assertTrue(result.isSuccess)
        assertEquals(expectedFile, result.getOrThrow())
        verify(mockRepository).downloadModel(any(), any())
    }

    @Test
    fun `cancel delegates to repository cancelDownload`() {
        useCase.cancel()
        verify(mockRepository).cancelDownload()
    }
}
