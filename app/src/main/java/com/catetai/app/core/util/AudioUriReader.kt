package com.catetai.app.core.util

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.OpenableColumns
import android.webkit.MimeTypeMap
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.InputStream
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Abstraksi antarmuka untuk membaca metadata dan aliran byte berkas audio dari [Uri] (Storage Access Framework).
 *
 * Pemisahan ini memungkinkan logika [com.catetai.app.domain.usecase.ImportAudioUseCase]
 * diuji 100% pada lingkungan JVM murni menggunakan stub/fake tanpa ketergantungan framework Android.
 */
interface AudioUriReader {

    /** Mengambil nama tampilan berkas dari ContentResolver (misal: "Voice 001.m4a"). */
    fun getDisplayName(uri: Uri): String?

    /** Mengambil ukuran berkas asli dalam byte. */
    fun getFileSize(uri: Uri): Long

    /** Mendeteksi MIME type berkas (misal: "audio/mp4", "audio/mpeg"). */
    fun getMimeType(uri: Uri): String?

    /** Membuka aliran masukan byte ([InputStream]) dari Uri berkas. */
    fun openInputStream(uri: Uri): InputStream?

    /** Mengekstrak durasi pemutaran berkas audio dalam milidetik via MediaMetadataRetriever. */
    fun getAudioDurationMs(file: File): Long

    /** Mengembalikan sisa ruang penyimpanan internal perangkat yang dapat digunakan (dalam byte). */
    fun getUsableStorageSpace(): Long
}

/**
 * Implementasi konkret [AudioUriReader] menggunakan Android [ContentResolver] dan [MediaMetadataRetriever].
 */
@Singleton
class AndroidAudioUriReader @Inject constructor(
    @ApplicationContext private val context: Context
) : AudioUriReader {

    override fun getDisplayName(uri: Uri): String? {
        // Coba baca OpenableColumns.DISPLAY_NAME via ContentResolver query
        var name: String? = null
        try {
            context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (nameIndex != -1) {
                        name = cursor.getString(nameIndex)
                    }
                }
            }
        } catch (ignored: Exception) {}

        // Fallback ke segmen terakhir URI jika query gagal
        return name ?: uri.lastPathSegment
    }

    override fun getFileSize(uri: Uri): Long {
        var size: Long = 0L
        try {
            context.contentResolver.query(uri, arrayOf(OpenableColumns.SIZE), null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                    if (sizeIndex != -1) {
                        size = cursor.getLong(sizeIndex)
                    }
                }
            }
        } catch (ignored: Exception) {}
        return size
    }

    override fun getMimeType(uri: Uri): String? {
        val detected = context.contentResolver.getType(uri)
        if (!detected.isNullOrBlank()) return detected

        // Fallback: deteksi berdasarkan ekstensi nama berkas
        val displayName = getDisplayName(uri) ?: ""
        val extension = displayName.substringAfterLast('.', "")
        if (extension.isNotBlank()) {
            val mapType = MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension.lowercase())
            if (!mapType.isNullOrBlank()) return mapType
        }

        return "audio/mp4" // Default asumsi format rekaman Android
    }

    override fun openInputStream(uri: Uri): InputStream? {
        return context.contentResolver.openInputStream(uri)
    }

    override fun getAudioDurationMs(file: File): Long {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(file.absolutePath)
            val durationStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
            durationStr?.toLongOrNull() ?: 0L
        } catch (ignored: Exception) {
            0L
        } finally {
            try {
                retriever.release()
            } catch (ignored: Exception) {}
        }
    }

    override fun getUsableStorageSpace(): Long {
        return context.filesDir.usableSpace
    }
}
