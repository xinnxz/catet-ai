package com.catetai.app.core.util

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import com.catetai.app.domain.model.ExportFormat
import java.io.File

/**
 * Utilitas untuk membagikan berkas dokumen notulensi rapat ke aplikasi luar
 * (WhatsApp, Gmail, Telegram, Google Drive) menggunakan Android ShareSheet sistem (FR-11).
 */
object ShareHelper {

    /**
     * Membuka ShareSheet Android untuk membagikan berkas [file].
     *
     * @param context Android [Context].
     * @param file Berkas dokumen yang berada di dalam path FileProvider.
     * @param format Format berkas yang menentukan MIME type intent.
     * @param title Judul subjek untuk email atau aplikasi penerima.
     */
    fun shareDocument(
        context: Context,
        file: File,
        format: ExportFormat,
        title: String
    ) {
        val contentUri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )

        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = format.mimeType
            putExtra(Intent.EXTRA_STREAM, contentUri)
            putExtra(Intent.EXTRA_SUBJECT, title)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }

        val chooser = Intent.createChooser(shareIntent, "Bagikan Dokumen Notulensi")
        chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(chooser)
    }
}
