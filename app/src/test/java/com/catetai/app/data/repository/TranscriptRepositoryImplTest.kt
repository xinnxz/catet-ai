package com.catetai.app.data.repository

import com.catetai.app.data.local.dao.TranscriptDao
import com.catetai.app.data.local.entity.TranscriptSegmentEntity
import com.catetai.app.domain.model.TranscriptSegment
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import org.mockito.kotlin.any

/**
 * Unit test untuk [TranscriptRepositoryImpl].
 */
class TranscriptRepositoryImplTest {

    private lateinit var mockDao: TranscriptDao
    private lateinit var repository: TranscriptRepositoryImpl

    @Before
    fun setUp() {
        mockDao = mock(TranscriptDao::class.java)
        repository = TranscriptRepositoryImpl(mockDao)
    }

    @Test
    fun `getSegmentsByRecordingId maps entities to domain models`() = runTest {
        val sampleEntities = listOf(
            TranscriptSegmentEntity(id = 1, recordingId = "rec-1", startMs = 0, endMs = 2000, text = "Halo"),
            TranscriptSegmentEntity(id = 2, recordingId = "rec-1", startMs = 2000, endMs = 4000, text = "Dunia")
        )
        `when`(mockDao.getSegmentsByRecordingId("rec-1")).thenReturn(flowOf(sampleEntities))

        val result = repository.getSegmentsByRecordingId("rec-1").first()

        assertEquals(2, result.size)
        assertEquals("Halo", result[0].text)
        assertEquals("Dunia", result[1].text)
    }

    @Test
    fun `getSegmentsByRecordingIdOnce returns domain list`() = runTest {
        val sampleEntities = listOf(
            TranscriptSegmentEntity(id = 10, recordingId = "rec-2", startMs = 100, endMs = 500, text = "Rapat")
        )
        `when`(mockDao.getSegmentsByRecordingIdOnce("rec-2")).thenReturn(sampleEntities)

        val result = repository.getSegmentsByRecordingIdOnce("rec-2")

        assertEquals(1, result.size)
        assertEquals("Rapat", result[0].text)
    }

    @Test
    fun `insertSegments delegates to dao`() = runTest {
        val domainSegments = listOf(
            TranscriptSegment(id = 0, recordingId = "rec-1", startMs = 0, endMs = 1000, text = "Teks")
        )

        val result = repository.insertSegments(domainSegments)

        assertTrue(result.isSuccess)
        verify(mockDao).insertSegments(any())
    }

    @Test
    fun `deleteSegmentsByRecordingId delegates to dao`() = runTest {
        val result = repository.deleteSegmentsByRecordingId("rec-1")

        assertTrue(result.isSuccess)
        verify(mockDao).deleteSegmentsByRecordingId("rec-1")
    }

    @Test
    fun `getSegmentCount delegates to dao`() = runTest {
        `when`(mockDao.getSegmentCount("rec-1")).thenReturn(5)

        val count = repository.getSegmentCount("rec-1")

        assertEquals(5, count)
        verify(mockDao).getSegmentCount("rec-1")
    }
}
