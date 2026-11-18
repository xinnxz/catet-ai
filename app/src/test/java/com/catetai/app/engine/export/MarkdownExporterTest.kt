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

class MarkdownExporterTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var exporter: MarkdownExporter
    private lateinit var outputFile: File

    private val testRecording = Recording(
        id = "rec-1",
        title = "Sprint Planning Q1",
        originalFileName = "recording.m4a",
        audioFileName = "audio.enc",
        durationMs = 90_000L,
        fileSizeBytes = 1_500_000L,
        status = RecordingStatus.COMPLETED
    )

    private val testSegments = listOf(
        TranscriptSegment(1, "rec-1", 0, 4000, "Halo selamat pagi."),
        TranscriptSegment(2, "rec-1", 5000, 10000, "Kita mulai sprint planning hari ini.")
    )

    private val testSummary = Summary(
        id = 1L,
        recordingId = "rec-1",
        summaryText = "• Diskusi sprint planning.",
        decisions = "1. Sepakat rilis v1.0.",
        actionItemsJson = """[{"task":"Selesaikan unit test","pic":"Tim Dev","deadline":"Jumat"}]""",
        mode = "extractive"
    )

    @Before
    fun setUp() {
        exporter = MarkdownExporter()
        outputFile = File(tempFolder.newFolder(), "export.md")
    }

    @Test
    fun export_writesCompleteMarkdownStructure() {
        val result = exporter.export(testRecording, testSegments, testSummary, outputFile)

        assertTrue(result.isSuccess)
        val content = outputFile.readText()

        assertTrue(content.contains("# Sprint Planning Q1"))
        assertTrue(content.contains("Catet AI"))
        assertTrue(content.contains("## 1. Ringkasan Eksekutif"))
        assertTrue(content.contains("• Diskusi sprint planning."))
        assertTrue(content.contains("## 2. Keputusan Rapat"))
        assertTrue(content.contains("1. Sepakat rilis v1.0."))
        assertTrue(content.contains("## 3. Daftar Tugas & Tindak Lanjut (Action Items)"))
        assertTrue(content.contains("Selesaikan unit test"))
        assertTrue(content.contains("## Transkrip Asli (Verbatim Speech-to-Text)"))
        assertTrue(content.contains("Halo selamat pagi."))
    }
}
