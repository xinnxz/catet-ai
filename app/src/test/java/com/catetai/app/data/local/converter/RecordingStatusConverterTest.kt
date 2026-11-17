package com.catetai.app.data.local.converter

import com.catetai.app.data.local.entity.RecordingStatus
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Unit test untuk [RecordingStatusConverter].
 *
 * Menguji round-trip konversi enum ↔ String secara murni di JVM.
 * Tidak memerlukan emulator atau framework Android.
 */
class RecordingStatusConverterTest {

    private val converter = RecordingStatusConverter()

    // ---- fromStatus: Enum → String ----

    @Test
    fun fromStatus_recording_returnsCorrectString() {
        assertEquals("RECORDING", converter.fromStatus(RecordingStatus.RECORDING))
    }

    @Test
    fun fromStatus_stopped_returnsCorrectString() {
        assertEquals("STOPPED", converter.fromStatus(RecordingStatus.STOPPED))
    }

    @Test
    fun fromStatus_truncated_returnsCorrectString() {
        assertEquals("TRUNCATED", converter.fromStatus(RecordingStatus.TRUNCATED))
    }

    @Test
    fun fromStatus_processing_returnsCorrectString() {
        assertEquals("PROCESSING", converter.fromStatus(RecordingStatus.PROCESSING))
    }

    @Test
    fun fromStatus_transcribed_returnsCorrectString() {
        assertEquals("TRANSCRIBED", converter.fromStatus(RecordingStatus.TRANSCRIBED))
    }

    @Test
    fun fromStatus_summarized_returnsCorrectString() {
        assertEquals("SUMMARIZED", converter.fromStatus(RecordingStatus.SUMMARIZED))
    }

    @Test
    fun fromStatus_error_returnsCorrectString() {
        assertEquals("ERROR", converter.fromStatus(RecordingStatus.ERROR))
    }

    // ---- toStatus: String → Enum ----

    @Test
    fun toStatus_validString_returnsCorrectEnum() {
        assertEquals(RecordingStatus.RECORDING, converter.toStatus("RECORDING"))
        assertEquals(RecordingStatus.STOPPED, converter.toStatus("STOPPED"))
        assertEquals(RecordingStatus.TRUNCATED, converter.toStatus("TRUNCATED"))
        assertEquals(RecordingStatus.PROCESSING, converter.toStatus("PROCESSING"))
        assertEquals(RecordingStatus.TRANSCRIBED, converter.toStatus("TRANSCRIBED"))
        assertEquals(RecordingStatus.SUMMARIZED, converter.toStatus("SUMMARIZED"))
        assertEquals(RecordingStatus.ERROR, converter.toStatus("ERROR"))
    }

    @Test
    fun toStatus_unknownString_fallsBackToError() {
        // Data korup atau dari versi lama yang belum dimigrasi — harus aman, tidak crash
        assertEquals(RecordingStatus.ERROR, converter.toStatus("UNKNOWN_STATUS"))
        assertEquals(RecordingStatus.ERROR, converter.toStatus(""))
        assertEquals(RecordingStatus.ERROR, converter.toStatus("recording")) // case-sensitive
    }

    // ---- Round-trip: Enum → String → Enum ----

    @Test
    fun roundTrip_allStatuses_surviveConversionIntact() {
        RecordingStatus.entries.forEach { originalStatus ->
            val serialized = converter.fromStatus(originalStatus)
            val deserialized = converter.toStatus(serialized)
            assertEquals(
                "Round-trip gagal untuk status: $originalStatus",
                originalStatus,
                deserialized
            )
        }
    }
}
