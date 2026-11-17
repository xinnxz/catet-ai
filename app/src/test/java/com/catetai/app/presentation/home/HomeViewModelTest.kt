package com.catetai.app.presentation.home

import com.catetai.app.domain.model.Recording
import com.catetai.app.domain.model.RecordingStatus
import com.catetai.app.domain.repository.RecordingRepository
import com.catetai.app.testutil.MainDispatcherRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * Fake implementation of [RecordingRepository] untuk unit test [HomeViewModel].
 * Disesuaikan untuk skema v2 (alur impor).
 */
class FakeHomeRecordingRepository : RecordingRepository {
    val recordingsFlow = MutableStateFlow<List<Recording>>(emptyList())
    val deletedIds = mutableListOf<String>()
    var shouldFailDelete = false
    var shouldThrowOnGetRecordings = false

    override fun getAllRecordings(): Flow<List<Recording>> {
        if (shouldThrowOnGetRecordings) {
            return flow { throw RuntimeException("Database error") }
        }
        return recordingsFlow
    }

    override suspend fun getRecordingById(id: String): Recording? =
        recordingsFlow.value.find { it.id == id }

    override suspend fun deleteRecording(id: String): Result<Unit> =
        if (shouldFailDelete) {
            Result.failure(RuntimeException("Gagal menghapus"))
        } else {
            deletedIds.add(id)
            recordingsFlow.value = recordingsFlow.value.filter { it.id != id }
            Result.success(Unit)
        }

    override suspend fun updateTitle(id: String, newTitle: String): Result<Unit> {
        recordingsFlow.value = recordingsFlow.value.map {
            if (it.id == id) it.copy(title = newTitle) else it
        }
        return Result.success(Unit)
    }
}

/** Helper untuk membuat Recording domain model dengan nilai default v2. */
private fun buildRecording(
    id: String = "rec-${System.nanoTime()}",
    title: String = "Rekaman Test",
    originalFileName: String = "recording.m4a",
    audioFileName: String = "audio_${System.nanoTime()}.enc",
    durationMs: Long = 60_000L,
    status: RecordingStatus = RecordingStatus.IMPORTED,
    importedAt: Long = System.currentTimeMillis()
) = Recording(
    id = id,
    title = title,
    originalFileName = originalFileName,
    audioFileName = audioFileName,
    durationMs = durationMs,
    status = status,
    importedAt = importedAt
)

@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val fakeRepository = FakeHomeRecordingRepository()

    @Test
    fun `init loads recordings and updates uiState`() = runTest {
        val sampleList = listOf(
            buildRecording(id = "rec-1", title = "Rapat Tim Tech",
                durationMs = 120_000L, status = RecordingStatus.COMPLETED),
            buildRecording(id = "rec-2", title = "1-on-1 Sync",
                durationMs = 60_000L, status = RecordingStatus.IMPORTED)
        )
        fakeRepository.recordingsFlow.value = sampleList

        val viewModel = HomeViewModel(fakeRepository)

        val state = viewModel.uiState.value
        assertFalse(state.isLoading)
        assertEquals(2, state.recordings.size)
        assertEquals("rec-1", state.recordings[0].id)
        assertEquals("rec-2", state.recordings[1].id)
    }

    @Test
    fun `deleteRecording intent calls repository and updates state`() = runTest {
        val sample = buildRecording(id = "rec-to-delete", title = "Akan Dihapus")
        fakeRepository.recordingsFlow.value = listOf(sample)

        val viewModel = HomeViewModel(fakeRepository)
        assertEquals(1, viewModel.uiState.value.recordings.size)

        viewModel.processIntent(HomeIntent.DeleteRecording("rec-to-delete"))
        advanceUntilIdle()

        assertTrue(fakeRepository.deletedIds.contains("rec-to-delete"))
        assertEquals(0, viewModel.uiState.value.recordings.size)
    }

    @Test
    fun `navigateToDetail intent emits NavigateToDetail effect`() = runTest {
        val viewModel = HomeViewModel(fakeRepository)

        viewModel.processIntent(HomeIntent.NavigateToDetail("rec-detail-123"))

        val effect = viewModel.effects.first()
        assertTrue(effect is HomeEffect.NavigateToDetail)
        assertEquals("rec-detail-123", (effect as HomeEffect.NavigateToDetail).recordingId)
    }

    @Test
    fun `repository error produces errorMessage in uiState`() = runTest {
        fakeRepository.shouldThrowOnGetRecordings = true
        val viewModel = HomeViewModel(fakeRepository)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertFalse(state.isLoading)
        assertEquals("Database error", state.errorMessage)
    }
}
