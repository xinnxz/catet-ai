package com.catetai.app.domain.usecase

import android.content.Context
import com.catetai.app.data.di.IoDispatcher
import com.catetai.app.domain.model.ExportFormat
import com.catetai.app.domain.repository.RecordingRepository
import com.catetai.app.domain.repository.SummaryRepository
import com.catetai.app.domain.repository.TranscriptRepository
import com.catetai.app.engine.export.MarkdownExporter
import com.catetai.app.engine.export.PdfExporter
import com.catetai.app.engine.export.TextExporter
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Use case untuk mengekspor rekaman rapat ke dokumen PDF, Markdown, atau TXT (FR-11).
 *
 * Mengoordinasikan:
 * 1. Pengambilan metadata rekaman, segmen transkrip verbatim, dan notulensi rapat.
 * 2. Penentuan format berkas target ([ExportFormat.PDF], [ExportFormat.MARKDOWN], [ExportFormat.TXT]).
 * 3. Pembuatan berkas dokumen di direktori `cache/exports/` agar dapat langsung
 *    dibagikan via Android ShareSheet melalui FileProvider.
 */
@Singleton
class ExportDocumentUseCase @Inject constructor(
    @ApplicationContext private val context: Context,
    private val recordingRepository: RecordingRepository,
    private val transcriptRepository: TranscriptRepository,
    private val summaryRepository: SummaryRepository,
    private val pdfExporter: PdfExporter,
    private val markdownExporter: MarkdownExporter,
    private val textExporter: TextExporter,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher
) {

    /** Konstruktor sekunder untuk pengujian unit test tanpa Hilt */
    constructor(
        context: Context,
        recordingRepository: RecordingRepository,
        transcriptRepository: TranscriptRepository,
        summaryRepository: SummaryRepository,
        pdfExporter: PdfExporter,
        markdownExporter: MarkdownExporter,
        textExporter: TextExporter
    ) : this(
        context,
        recordingRepository,
        transcriptRepository,
        summaryRepository,
        pdfExporter,
        markdownExporter,
        textExporter,
        Dispatchers.IO
    )

    /**
     * Mengekspor dokumen sesi rapat dengan [recordingId] ke format [format].
     *
     * @param recordingId ID unik sesi rekaman.
     * @param format Format dokumen target (PDF, Markdown, atau TXT).
     * @return [Result.success] dengan [File] dokumen yang siap dibagikan, atau [Result.failure] jika gagal.
     */
    suspend operator fun invoke(
        recordingId: String,
        format: ExportFormat
    ): Result<File> = withContext(ioDispatcher) {
        try {
            val recording = recordingRepository.getRecordingById(recordingId)
                ?: return@withContext Result.failure(
                    IllegalArgumentException("Rekaman dengan ID '$recordingId' tidak ditemukan.")
                )

            val segments = transcriptRepository.getSegmentsByRecordingIdOnce(recordingId)
            val summary = summaryRepository.getSummaryByRecordingIdOnce(recordingId)

            val exportDir = File(context.cacheDir, "exports").apply { mkdirs() }
            val cleanTitle = recording.title
                .trim()
                .replace("[^a-zA-Z0-9_.-]+".toRegex(), "_")
                .take(30)
                .ifBlank { "Notulensi_Rapat" }
            val fileName = "CatetAI_${cleanTitle}_${System.currentTimeMillis()}.${format.extension}"
            val outputFile = File(exportDir, fileName)

            when (format) {
                ExportFormat.PDF -> pdfExporter.export(recording, segments, summary, outputFile)
                ExportFormat.MARKDOWN -> markdownExporter.export(recording, segments, summary, outputFile)
                ExportFormat.TXT -> textExporter.export(recording, segments, summary, outputFile)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
