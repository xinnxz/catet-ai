package com.catetai.app.data.repository

import com.catetai.app.data.local.dao.SummaryDao
import com.catetai.app.data.local.entity.SummaryEntity
import com.catetai.app.domain.model.Summary
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class SummaryRepositoryImplTest {

    private lateinit var summaryDao: SummaryDao
    private lateinit var repository: SummaryRepositoryImpl

    private val testRecordingId = "rec-123"
    private val testEntity = SummaryEntity(
        id = 1L,
        recordingId = testRecordingId,
        summaryText = "• Poin 1\n• Poin 2",
        decisions = "1. Keputusan A",
        actionItems = """[{"task":"Buat laporan","pic":"Budi","deadline":"Jumat"}]""",
        mode = "extractive",
        createdAt = 1000L
    )

    @Before
    fun setUp() {
        summaryDao = mock()
        repository = SummaryRepositoryImpl(summaryDao)
    }

    @Test
    fun getSummaryByRecordingId_emitsMappedDomainModel() = runTest {
        whenever(summaryDao.getSummaryByRecordingId(testRecordingId)).thenReturn(flowOf(testEntity))

        val result = repository.getSummaryByRecordingId(testRecordingId).first()

        assertNotNull(result)
        assertEquals(1L, result?.id)
        assertEquals(testRecordingId, result?.recordingId)
        assertEquals("• Poin 1\n• Poin 2", result?.summaryText)
        assertEquals("1. Keputusan A", result?.decisions)
        assertEquals("""[{"task":"Buat laporan","pic":"Budi","deadline":"Jumat"}]""", result?.actionItemsJson)
        assertEquals("extractive", result?.mode)
    }

    @Test
    fun getSummaryByRecordingId_whenNull_emitsNull() = runTest {
        whenever(summaryDao.getSummaryByRecordingId(testRecordingId)).thenReturn(flowOf(null))

        val result = repository.getSummaryByRecordingId(testRecordingId).first()

        assertNull(result)
    }

    @Test
    fun getSummaryByRecordingIdOnce_returnsMappedDomainModel() = runTest {
        whenever(summaryDao.getSummaryByRecordingIdOnce(testRecordingId)).thenReturn(testEntity)

        val result = repository.getSummaryByRecordingIdOnce(testRecordingId)

        assertNotNull(result)
        assertEquals(testRecordingId, result?.recordingId)
        assertEquals("1. Keputusan A", result?.decisions)
    }

    @Test
    fun insertSummary_callsDaoAndReturnsSuccessId() = runTest {
        whenever(summaryDao.insertSummary(any())).thenReturn(10L)

        val domainSummary = Summary(
            recordingId = testRecordingId,
            summaryText = "Ringkasan rapat",
            decisions = "Sepakat rilis MVP",
            actionItemsJson = "[]",
            mode = "extractive"
        )

        val result = repository.insertSummary(domainSummary)

        assertTrue(result.isSuccess)
        assertEquals(10L, result.getOrNull())
        verify(summaryDao).insertSummary(any())
    }

    @Test
    fun deleteSummaryByRecordingId_callsDaoSuccessfully() = runTest {
        val result = repository.deleteSummaryByRecordingId(testRecordingId)

        assertTrue(result.isSuccess)
        verify(summaryDao).deleteSummaryByRecordingId(testRecordingId)
    }
}
