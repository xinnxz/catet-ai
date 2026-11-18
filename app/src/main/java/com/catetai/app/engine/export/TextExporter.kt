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
 * Eksportir dokumen notulensi rapat dalam format teks polos (.txt).
 *
 * Menghasilkan berkas teks yang bersih dan mudah dibaca di aplikasi catatan standar,
 * SMS, WhatsApp, atau terminal tanpa format markdown khusus.
 */
@Singleton
class TextExporter @Inject constructor() : DocumentExporter {

    override fun export(
        recording: Recording,
        segments: List<TranscriptSegment>,
        summary: Summary?,
        outputFile: File
    ): Result<File> = runCatching {
        val textContent = buildString {
            appendLine("================================================================================")
            appendLine("NOTULENSI RAPAT: ${recording.title.uppercase()}")
            appendLine("Catet AI — On-Device Private Meeting Notes")
            appendLine("================================================================================")
            appendLine("Tanggal Rapat       : ${RecordingFormatters.formatDateTime(recording.createdAt)}")
            appendLine("Durasi Audio        : ${RecordingFormatters.formatDuration(recording.durationMs)}")
            appendLine("Total Segmen Bicara : ${segments.size} segmen")
            appendLine("Nama Berkas Asli    : ${recording.originalFileName}")
            appendLine("Ukuran Berkas       : ${RecordingFormatters.formatFileSize(recording.fileSizeBytes)}")
            appendLine("================================================================================")
            appendLine()

            // Bagian Notulensi
            if (summary != null) {
                appendLine("[ 1. RINGKASAN EKSEKUTIF ]")
                appendLine(summary.summaryText)
                appendLine()

                if (summary.decisions.isNotBlank()) {
                    appendLine("[ 2. KEPUTUSAN RAPAT ]")
                    appendLine(summary.decisions)
                    appendLine()
                }

                val actionItems = ActionItemParser.parse(summary.actionItemsJson)
                if (actionItems.isNotEmpty()) {
                    appendLine("[ 3. ACTION ITEMS & TINDAK LANJUT ]")
                    actionItems.forEachIndexed { index, item ->
                        val deadlineStr = if (item.deadline != null) " (Tenggat: ${item.deadline})" else ""
                        appendLine("${index + 1}. [ ] ${item.task} [PIC: ${item.pic}]$deadlineStr")
                    }
                    appendLine()
                }

                appendLine("--------------------------------------------------------------------------------")
                appendLine()
            }

            // Bagian Transkrip Asli
            appendLine("[ TRANSKRIP ASLI (VERBATIM SPEECH-TO-TEXT) ]")
            appendLine()
            if (segments.isEmpty()) {
                appendLine("(Belum ada transkrip teks untuk rekaman ini)")
            } else {
                segments.forEach { segment ->
                    val timeStart = RecordingFormatters.formatDuration(segment.startMs)
                    val timeEnd = RecordingFormatters.formatDuration(segment.endMs)
                    appendLine("[$timeStart - $timeEnd] ${segment.text}")
                }
            }
            appendLine()
            appendLine("================================================================================")
            appendLine("Dibuat secara privat di perangkat oleh Catet AI tanpa telemetri pihak ketiga.")
        }

        outputFile.parentFile?.mkdirs()
        outputFile.writeText(textContent, Charsets.UTF_8)
        outputFile
    }
}
