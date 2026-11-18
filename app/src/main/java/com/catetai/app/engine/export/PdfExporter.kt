package com.catetai.app.engine.export

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import com.catetai.app.domain.model.Recording
import com.catetai.app.domain.model.Summary
import com.catetai.app.domain.model.TranscriptSegment
import com.catetai.app.engine.summarizer.ActionItemParser
import com.catetai.app.presentation.home.util.RecordingFormatters
import java.io.File
import java.io.FileOutputStream
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Eksportir dokumen notulensi rapat dalam format PDF formal (FR-11).
 *
 * Menggunakan Android native [PdfDocument] API tanpa library pihak ketiga
 * untuk menjaga ukuran APK tetap di bawah 15 MB (NFR-05).
 *
 * # Tata Letak Dokumen (Standar A4: 595 x 842 pt):
 * - Margin kiri/kanan: 40 pt (lebar konten efektif: 515 pt).
 * - Margin atas/bawah: 45 pt (tinggi konten efektif: 745 pt).
 * - Header formal dengan identitas Catet AI.
 * - Kotak metadata rapat (tanggal, durasi, jumlah segmen, ukuran file).
 * - Bagian 1: Notulensi (Ringkasan Eksekutif, Keputusan, Action Items).
 * - Bagian 2: Transkrip Asli Verbatim bertanda waktu `[mm:ss - mm:ss]`.
 * - Paginasi otomatis multi-halaman dengan nomor halaman pada footer.
 */
@Singleton
class PdfExporter @Inject constructor() : DocumentExporter {

    override fun export(
        recording: Recording,
        segments: List<TranscriptSegment>,
        summary: Summary?,
        outputFile: File
    ): Result<File> = runCatching {
        val document = PdfDocument()

        val pageWidth = 595
        val pageHeight = 842
        val marginLeft = 40f
        val marginRight = 555f
        val contentWidth = marginRight - marginLeft // 515f
        val marginTop = 45f
        val marginBottom = 790f

        var pageNumber = 1
        var currentPageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create()
        var currentPage = document.startPage(currentPageInfo)
        var canvas = currentPage.canvas

        var currentY = marginTop

        // Paint Konfigurasi
        val titlePaint = Paint().apply {
            color = Color.rgb(30, 58, 138) // Deep Blue (#1E3A8A)
            textSize = 15f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            isAntiAlias = true
        }

        val sectionHeaderPaint = Paint().apply {
            color = Color.rgb(30, 58, 138)
            textSize = 12f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            isAntiAlias = true
        }

        val bodyPaint = Paint().apply {
            color = Color.rgb(31, 41, 55) // Dark Charcoal (#1F2937)
            textSize = 9.5f
            typeface = Typeface.DEFAULT
            isAntiAlias = true
        }

        val boldBodyPaint = Paint().apply {
            color = Color.rgb(31, 41, 55)
            textSize = 9.5f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            isAntiAlias = true
        }

        val metadataPaint = Paint().apply {
            color = Color.rgb(75, 85, 99) // Gray (#4B5563)
            textSize = 8.5f
            typeface = Typeface.DEFAULT
            isAntiAlias = true
        }

        val timestampPaint = Paint().apply {
            color = Color.rgb(37, 99, 235) // Blue (#2563EB)
            textSize = 8.5f
            typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
            isAntiAlias = true
        }

        val lineDividerPaint = Paint().apply {
            color = Color.rgb(229, 231, 235) // Light gray divider
            strokeWidth = 1f
            isAntiAlias = true
        }

        val metadataBoxPaint = Paint().apply {
            color = Color.rgb(243, 244, 246) // Light gray fill
            style = Paint.Style.FILL
            isAntiAlias = true
        }

        val footerPaint = Paint().apply {
            color = Color.rgb(156, 163, 175)
            textSize = 8f
            typeface = Typeface.DEFAULT
            isAntiAlias = true
        }

        fun drawFooter() {
            val footerText = "Catet AI — On-Device Private Meeting Notes  •  Halaman $pageNumber"
            canvas.drawText(footerText, marginLeft, 815f, footerPaint)
            canvas.drawLine(marginLeft, 805f, marginRight, 805f, lineDividerPaint)
        }

        fun checkPageBreak(requiredHeight: Float) {
            if (currentY + requiredHeight > marginBottom) {
                drawFooter()
                document.finishPage(currentPage)
                pageNumber++
                currentPageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create()
                currentPage = document.startPage(currentPageInfo)
                canvas = currentPage.canvas
                currentY = marginTop
            }
        }

        fun wrapText(text: String, paint: Paint, maxWidth: Float): List<String> {
            val words = text.split("\\s+".toRegex())
            val lines = mutableListOf<String>()
            var currentLine = ""

            for (word in words) {
                val candidate = if (currentLine.isEmpty()) word else "$currentLine $word"
                if (paint.measureText(candidate) <= maxWidth) {
                    currentLine = candidate
                } else {
                    if (currentLine.isNotEmpty()) lines.add(currentLine)
                    currentLine = word
                }
            }
            if (currentLine.isNotEmpty()) lines.add(currentLine)
            return lines
        }

        // --- 1. HEADER DOKUMEN ---
        canvas.drawText(recording.title.uppercase(), marginLeft, currentY, titlePaint)
        currentY += 16f
        canvas.drawText("Dihasilkan secara luring dan privat oleh Catet AI", marginLeft, currentY, metadataPaint)
        currentY += 12f
        canvas.drawLine(marginLeft, currentY, marginRight, currentY, lineDividerPaint)
        currentY += 12f

        // --- 2. KOTAK METADATA ---
        val boxHeight = 54f
        val boxRect = RectF(marginLeft, currentY, marginRight, currentY + boxHeight)
        canvas.drawRoundRect(boxRect, 6f, 6f, metadataBoxPaint)

        val metaCol1X = marginLeft + 12f
        val metaCol2X = marginLeft + 260f
        var metaY = currentY + 16f

        canvas.drawText("Tanggal Rapat: ${RecordingFormatters.formatDateTime(recording.createdAt)}", metaCol1X, metaY, metadataPaint)
        canvas.drawText("Durasi Audio: ${RecordingFormatters.formatDuration(recording.durationMs)}", metaCol2X, metaY, metadataPaint)
        metaY += 14f
        canvas.drawText("Total Segmen: ${segments.size} segmen bicara", metaCol1X, metaY, metadataPaint)
        canvas.drawText("Ukuran Berkas: ${RecordingFormatters.formatFileSize(recording.fileSizeBytes)} (${recording.originalFileName})", metaCol2X, metaY, metadataPaint)
        metaY += 14f
        canvas.drawText("Status: Selesai (100% On-Device)", metaCol1X, metaY, metadataPaint)

        currentY += boxHeight + 20f

        // --- 3. BAGIAN NOTULENSI (JIKA ADA) ---
        if (summary != null) {
            checkPageBreak(30f)
            canvas.drawText("1. RINGKASAN EKSEKUTIF", marginLeft, currentY, sectionHeaderPaint)
            currentY += 16f

            val summaryLines = summary.summaryText.split("\n").filter { it.isNotBlank() }
            for (line in summaryLines) {
                val wrapped = wrapText(line, bodyPaint, contentWidth - 10f)
                checkPageBreak(wrapped.size * 14f + 4f)
                for (subLine in wrapped) {
                    canvas.drawText(subLine, marginLeft + 8f, currentY, bodyPaint)
                    currentY += 13f
                }
                currentY += 4f
            }
            currentY += 12f

            // Keputusan Rapat
            if (summary.decisions.isNotBlank()) {
                checkPageBreak(30f)
                canvas.drawText("2. KEPUTUSAN RAPAT", marginLeft, currentY, sectionHeaderPaint)
                currentY += 16f

                val decisionLines = summary.decisions.split("\n").filter { it.isNotBlank() }
                for (line in decisionLines) {
                    val wrapped = wrapText(line, bodyPaint, contentWidth - 10f)
                    checkPageBreak(wrapped.size * 14f + 4f)
                    for (subLine in wrapped) {
                        canvas.drawText(subLine, marginLeft + 8f, currentY, bodyPaint)
                        currentY += 13f
                    }
                    currentY += 4f
                }
                currentY += 12f
            }

            // Action Items
            val actionItems = ActionItemParser.parse(summary.actionItemsJson)
            if (actionItems.isNotEmpty()) {
                checkPageBreak(30f)
                canvas.drawText("3. BUTIR TINDAKAN & ACTION ITEMS", marginLeft, currentY, sectionHeaderPaint)
                currentY += 16f

                actionItems.forEachIndexed { index, item ->
                    val deadlineStr = if (item.deadline != null) " (Tenggat: ${item.deadline})" else ""
                    val itemFullText = "${index + 1}. [ ] ${item.task} — PIC: ${item.pic}$deadlineStr"
                    val wrapped = wrapText(itemFullText, bodyPaint, contentWidth - 10f)
                    checkPageBreak(wrapped.size * 14f + 4f)
                    for (subLine in wrapped) {
                        canvas.drawText(subLine, marginLeft + 8f, currentY, bodyPaint)
                        currentY += 13f
                    }
                    currentY += 4f
                }
                currentY += 12f
            }

            checkPageBreak(15f)
            canvas.drawLine(marginLeft, currentY, marginRight, currentY, lineDividerPaint)
            currentY += 16f
        }

        // --- 4. BAGIAN TRANSKRIP ASLI (VERBATIM) ---
        checkPageBreak(35f)
        canvas.drawText("TRANSKRIP ASLI (VERBATIM SPEECH-TO-TEXT)", marginLeft, currentY, sectionHeaderPaint)
        currentY += 16f

        if (segments.isEmpty()) {
            canvas.drawText("(Tidak ada segmen transkrip tersedia)", marginLeft, currentY, metadataPaint)
            currentY += 15f
        } else {
            for (segment in segments) {
                val timeStart = RecordingFormatters.formatDuration(segment.startMs)
                val timeEnd = RecordingFormatters.formatDuration(segment.endMs)
                val timePill = "[$timeStart - $timeEnd]"
                val pillWidth = timestampPaint.measureText(timePill) + 8f

                val wrappedText = wrapText(segment.text, bodyPaint, contentWidth - pillWidth - 10f)
                val itemHeight = (wrappedText.size * 13f).coerceAtLeast(14f) + 6f
                checkPageBreak(itemHeight)

                // Gambar Timestamp Pill
                canvas.drawText(timePill, marginLeft, currentY, timestampPaint)

                // Gambar Teks Segmen
                var segTextY = currentY
                for (subLine in wrappedText) {
                    canvas.drawText(subLine, marginLeft + pillWidth, segTextY, bodyPaint)
                    segTextY += 13f
                }
                currentY = segTextY + 6f
            }
        }

        // Gambar footer untuk halaman terakhir
        drawFooter()
        document.finishPage(currentPage)

        // Tulis berkas PDF ke disk
        outputFile.parentFile?.mkdirs()
        FileOutputStream(outputFile).use { fos ->
            document.writeTo(fos)
        }
        document.close()

        outputFile
    }
}
