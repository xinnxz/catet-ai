package com.catetai.app.engine.export

import com.catetai.app.domain.model.Recording
import com.catetai.app.domain.model.RecordingStatus
import com.catetai.app.domain.model.Summary
import com.catetai.app.domain.model.TranscriptSegment
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class TextExporterTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var exporter: TextExporter
    private lateinit var outputFile: File

    private val testRecording = Recording(
        id = "rec-1",
        title = "Rapat Tim Mobile",
        originalFileName = "recording.m4a",
        audioFileName = "audio.enc",
        durationMs = 60_000L,
        fileSizeBytes = 1_000_000L,
        status = RecordingStatus.COMPLETED
    )

    private val testSegments = listOf(
        TranscriptSegment(1, "rec-1", 0, 4000, "Halo selamat pagi."),
        TranscriptSegment(2, "rec-1", 5000, 10000, "Rapat dimulai.")
    )

    private val testSummary = Summary(
        id = 1L,
        recordingId = "rec-1",
        summaryText = "• Poin penting.",
        decisions = "1. Keputusan 1.",
        actionItemsJson = """[{"task":"Deploy APK","pic":"Budi","deadline":"Senin"}]""",
        mode = "extractive"
    )

    @Before
    fun setUp() {
        exporter = TextExporter()
        outputFile = File(tempFolder.newFolder(), "export.txt")
    }

    @Test
    fun export_writesCompletePlainTextStructure() {
        val result = exporter.export(testRecording, testSegments, testSummary, outputFile)

        assertTrue(result.isSuccess)
        val content = outputFile.readText()

        assertTrue(content.contains("NOTULENSI RAPAT: RAPAT TIM MOBILE"))
        assertTrue(content.contains("[ 1. RINGKASAN EKSEKUTIF ]"))
        assertTrue(content.contains("[ 2. KEPUTUSAN RAPAT ]"))
        assertTrue(content.contains("[ 3. ACTION ITEMS & TINDAK LANJUT ]"))
        assertTrue(content.contains("Deploy APK"))
        assertTrue(content.contains("[ TRANSKRIP ASLI (VERBATIM SPEECH-TO-TEXT) ]"))
        assertTrue(content.contains("Halo selamat pagi."))
    }
}
