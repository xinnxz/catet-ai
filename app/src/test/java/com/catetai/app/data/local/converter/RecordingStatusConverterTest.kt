package com.catetai.app.data.local.converter

import com.catetai.app.domain.model.RecordingStatus
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Unit test untuk [RecordingStatusConverter].
 *
 * Memverifikasi bahwa konversi dua arah antara String (disimpan di Room DB)
 * dan enum [RecordingStatus] (digunakan di domain model) berjalan benar untuk
 * semua status v2 (alur impor audio).
 *
 * Metode converter:
 * - [RecordingStatusConverter.toStatus]  : String → RecordingStatus (baca dari DB)
 * - [RecordingStatusConverter.fromStatus]: RecordingStatus → String (tulis ke DB)
 *
 * Prinsip pengujian:
 * - **toStatus**: String yang valid harus menghasilkan enum yang tepat.
 * - **fromStatus**: Enum harus dikonversi ke String dengan nama yang sama persis.
 * - **fallback**: String yang tidak dikenal harus jatuh ke [RecordingStatus.FAILED].
 */
class RecordingStatusConverterTest {

    private val converter = RecordingStatusConverter()

    // ─── toStatus: semua status v2 yang valid ─────────────────────────────────

    @Test
    fun toStatus_imported_returnsImported() {
        assertEquals(RecordingStatus.IMPORTED, converter.toStatus("IMPORTED"))
    }

    @Test
    fun toStatus_decoding_returnsDecoding() {
        assertEquals(RecordingStatus.DECODING, converter.toStatus("DECODING"))
    }

    @Test
    fun toStatus_transcribing_returnsTranscribing() {
        assertEquals(RecordingStatus.TRANSCRIBING, converter.toStatus("TRANSCRIBING"))
    }

    @Test
    fun toStatus_summarizing_returnsSummarizing() {
        assertEquals(RecordingStatus.SUMMARIZING, converter.toStatus("SUMMARIZING"))
    }

    @Test
    fun toStatus_completed_returnsCompleted() {
        assertEquals(RecordingStatus.COMPLETED, converter.toStatus("COMPLETED"))
    }

    @Test
    fun toStatus_failed_returnsFailed() {
        assertEquals(RecordingStatus.FAILED, converter.toStatus("FAILED"))
    }

    @Test
    fun toStatus_interrupted_returnsInterrupted() {
        assertEquals(RecordingStatus.INTERRUPTED, converter.toStatus("INTERRUPTED"))
    }

    // ─── toStatus: nilai tidak dikenal harus fallback ke FAILED ───────────────

    @Test
    fun toStatus_unknownValue_returnsFailed() {
        // Nilai lama atau nilai korup harus di-fallback ke FAILED agar tidak crash
        assertEquals(RecordingStatus.FAILED, converter.toStatus("UNKNOWN_VALUE"))
    }

    @Test
    fun toStatus_legacyRecording_returnsFailed() {
        // Status lama "RECORDING" dari skema v1 tidak ada di v2 → fallback FAILED
        assertEquals(RecordingStatus.FAILED, converter.toStatus("RECORDING"))
    }

    @Test
    fun toStatus_legacyStopped_returnsFailed() {
        assertEquals(RecordingStatus.FAILED, converter.toStatus("STOPPED"))
    }

    @Test
    fun toStatus_legacyProcessing_returnsFailed() {
        assertEquals(RecordingStatus.FAILED, converter.toStatus("PROCESSING"))
    }

    @Test
    fun toStatus_legacyError_returnsFailed() {
        assertEquals(RecordingStatus.FAILED, converter.toStatus("ERROR"))
    }

    @Test
    fun toStatus_emptyString_returnsFailed() {
        assertEquals(RecordingStatus.FAILED, converter.toStatus(""))
    }

    // ─── fromStatus: enum harus dikonversi ke nama String yang benar ──────────

    @Test
    fun fromStatus_imported_returnsImportedString() {
        assertEquals("IMPORTED", converter.fromStatus(RecordingStatus.IMPORTED))
    }

    @Test
    fun fromStatus_decoding_returnsDecodingString() {
        assertEquals("DECODING", converter.fromStatus(RecordingStatus.DECODING))
    }

    @Test
    fun fromStatus_transcribing_returnsTranscribingString() {
        assertEquals("TRANSCRIBING", converter.fromStatus(RecordingStatus.TRANSCRIBING))
    }

    @Test
    fun fromStatus_summarizing_returnsSummarizingString() {
        assertEquals("SUMMARIZING", converter.fromStatus(RecordingStatus.SUMMARIZING))
    }

    @Test
    fun fromStatus_completed_returnsCompletedString() {
        assertEquals("COMPLETED", converter.fromStatus(RecordingStatus.COMPLETED))
    }

    @Test
    fun fromStatus_failed_returnsFailedString() {
        assertEquals("FAILED", converter.fromStatus(RecordingStatus.FAILED))
    }

    @Test
    fun fromStatus_interrupted_returnsInterruptedString() {
        assertEquals("INTERRUPTED", converter.fromStatus(RecordingStatus.INTERRUPTED))
    }

    // ─── round-trip: toStatus(fromStatus(x)) == x ────────────────────────────

    @Test
    fun roundTrip_allStatuses_preserveExactValue() {
        RecordingStatus.entries.forEach { status ->
            val serialized = converter.fromStatus(status)
            val deserialized = converter.toStatus(serialized)
            assertEquals(
                "Round-trip gagal untuk status: $status",
                status,
                deserialized
            )
        }
    }
}
