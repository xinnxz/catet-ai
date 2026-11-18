package com.catetai.app.engine.export

import com.catetai.app.domain.model.Recording
import com.catetai.app.domain.model.Summary
import com.catetai.app.domain.model.TranscriptSegment
import com.catetai.app.engine.summarizer.ActionItemParser
import com.catetai.app.presentation.home.util.RecordingFormatters
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Eksportir dokumen notulensi rapat dalam format Markdown (.md).
 *
 * Menghasilkan struktur dokumen bersih yang kompatibel dengan
 * Notion, GitHub, Obsidian, dan Markdown viewer lainnya.
 */
@Singleton
class MarkdownExporter @Inject constructor() : DocumentExporter {

    override fun export(
        recording: Recording,
        segments: List<TranscriptSegment>,
        summary: Summary?,
        outputFile: File
    ): Result<File> = runCatching {
        val markdownContent = buildString {
            // Judul Dokumen
            appendLine("# ${recording.title}")
            appendLine()

            // Metadata Rapat
            appendLine("> **Catet AI** — Notulensi Rapat Privat On-Device")
            appendLine("> ")
            appendLine("> - **Tanggal Rapat:** ${RecordingFormatters.formatDateTime(recording.createdAt)}")
            appendLine("> - **Durasi Audio:** ${RecordingFormatters.formatDuration(recording.durationMs)}")
            appendLine("> - **Total Segmen Bicara:** ${segments.size} segmen")
            appendLine("> - **Berkas Asli:** `${recording.originalFileName}` (${RecordingFormatters.formatFileSize(recording.fileSizeBytes)})")
            appendLine()
            appendLine("---")
            appendLine()

            // Bagian Notulensi jika tersedia
            if (summary != null) {
                appendLine("## 1. Ringkasan Eksekutif")
                appendLine()
                appendLine(summary.summaryText)
                appendLine()

                if (summary.decisions.isNotBlank()) {
                    appendLine("## 2. Keputusan Rapat")
                    appendLine()
                    appendLine(summary.decisions)
                    appendLine()
                }

                val actionItems = ActionItemParser.parse(summary.actionItemsJson)
                if (actionItems.isNotEmpty()) {
                    appendLine("## 3. Daftar Tugas & Tindak Lanjut (Action Items)")
                    appendLine()
                    actionItems.forEach { item ->
                        val deadlineStr = if (item.deadline != null) " *(Tenggat: ${item.deadline})*" else ""
                        appendLine("- [ ] **${item.task}** — `PIC: ${item.pic}`$deadlineStr")
                    }
                    appendLine()
                }

                appendLine("---")
                appendLine()
            }

            // Bagian Transkrip Asli Verbatim
            appendLine("## Transkrip Asli (Verbatim Speech-to-Text)")
            appendLine()
            if (segments.isEmpty()) {
                appendLine("*Belum ada transkrip teks untuk rekaman ini.*")
            } else {
                segments.forEach { segment ->
                    val timeStart = RecordingFormatters.formatDuration(segment.startMs)
                    val timeEnd = RecordingFormatters.formatDuration(segment.endMs)
                    appendLine("- **`[$timeStart - $timeEnd]`** ${segment.text}")
                }
            }
            appendLine()
            appendLine("---")
            appendLine("*Dokumen ini diproses 100% secara lokal di perangkat menggunakan Catet AI tanpa telemetri.*")
        }

        outputFile.parentFile?.mkdirs()
        outputFile.writeText(markdownContent, Charsets.UTF_8)
        outputFile
    }
}
