package com.catetai.app.engine.export

import com.catetai.app.domain.model.Recording
import com.catetai.app.domain.model.Summary
import com.catetai.app.domain.model.TranscriptSegment
import java.io.File

/**
 * Antarmuka untuk pembuat/eksportir dokumen rapat Catet AI (FR-11).
 */
interface DocumentExporter {

    /**
     * Menghasilkan berkas dokumen dari data rekaman, segmen transkrip, dan notulensi.
     *
     * @param recording Entitas rekaman induk.
     * @param segments Daftar segmen transkrip bertanda waktu.
     * @param summary Entitas notulensi (opsional jika belum dirangkum).
     * @param outputFile Berkas tujuan penulisan.
     * @return [Result.success] dengan [File] yang berhasil ditulis, atau [Result.failure] jika ada kesalahan.
     */
    fun export(
        recording: Recording,
        segments: List<TranscriptSegment>,
        summary: Summary?,
        outputFile: File
    ): Result<File>
}
