package com.catetai.app.presentation.settings

import android.content.Context
import com.catetai.app.domain.repository.ModelRepository
import com.catetai.app.domain.usecase.DownloadModelUseCase
import com.catetai.app.domain.usecase.GetModelStatusUseCase
import com.catetai.app.testutil.MainDispatcherRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.io.File

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val context: Context = mock()
    private val modelRepository: ModelRepository = mock()
    private val getModelStatusUseCase: GetModelStatusUseCase = mock()
    private val downloadModelUseCase: DownloadModelUseCase = mock()

    private lateinit var cacheDir: File

    @Before
    fun setUp() {
        cacheDir = File(System.getProperty("java.io.tmpdir"), "catet_test_cache_${System.currentTimeMillis()}")
        cacheDir.mkdirs()

        whenever(context.cacheDir).thenReturn(cacheDir)
        whenever(getModelStatusUseCase.isModelAvailable(any())).thenReturn(false)
        whenever(getModelStatusUseCase.getModelFile(any())).thenReturn(null)
    }

    private fun createViewModel(): SettingsViewModel {
        return SettingsViewModel(
            context = context,
            modelRepository = modelRepository,
            getModelStatusUseCase = getModelStatusUseCase,
            downloadModelUseCase = downloadModelUseCase
        )
    }

    @Test
    fun `init loads settings state correctly`() = runTest {
        val viewModel = createViewModel()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertFalse(state.isModelAvailable)
        assertEquals("1.0.0", state.appVersion)
    }

    @Test
    fun `open and dismiss delete model dialog updates state correctly`() = runTest {
        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.processIntent(SettingsIntent.OpenDeleteModelDialog)
        assertTrue(viewModel.uiState.value.showDeleteModelDialog)

        viewModel.processIntent(SettingsIntent.DismissDeleteModelDialog)
        assertFalse(viewModel.uiState.value.showDeleteModelDialog)
    }

    @Test
    fun `clearPlaybackCache deletes cache directories and updates cacheSizeFormatted`() = runTest {
        val playbackDir = File(cacheDir, "playback").also { it.mkdirs() }
        File(playbackDir, "temp.tmp").writeText("sample cache content")

        val viewModel = createViewModel()
        viewModel.processIntent(SettingsIntent.ClearPlaybackCache)
        advanceUntilIdle()

        assertFalse(File(playbackDir, "temp.tmp").exists())
        val effect = viewModel.effects.first()
        assertTrue(effect is SettingsEffect.ShowSnackbar)
    }
}
