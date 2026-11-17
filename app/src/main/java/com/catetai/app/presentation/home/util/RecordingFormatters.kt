package com.catetai.app.presentation.home.util

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * Utilitas pemformatan data rekaman (tanggal, durasi, ukuran file) untuk UI Catet AI.
 */
object RecordingFormatters {

    private val idLocale = Locale("id", "ID")

    /**
     * Memformat epoch timestamp (ms) menjadi representasi ramah pengguna.
     * Contoh: "17 Nov 2026, 16:30"
     *
     * @param timestampMs Epoch milidetik
     * @param timeZone Zona waktu pemformatan (default: zona waktu lokal sistem pengguna)
     */
    fun formatDateTime(
        timestampMs: Long,
        timeZone: TimeZone = TimeZone.getDefault()
    ): String {
        if (timestampMs <= 0L) return "-"
        val sdf = SimpleDateFormat("dd MMM yyyy, HH:mm", idLocale).apply {
            this.timeZone = timeZone
        }
        return sdf.format(Date(timestampMs))
    }

    /**
     * Memformat durasi audio (ms) menjadi representasi standar "mm:ss" atau "hh:mm:ss".
     * Contoh: 1935000 ms -> "32:15", 120000 ms -> "02:00"
     */
    fun formatDuration(durationMs: Long): String {
        if (durationMs <= 0L) return "00:00"
        val totalSeconds = durationMs / 1000L
        val hours = totalSeconds / 3600L
        val minutes = (totalSeconds % 3600L) / 60L
        val seconds = totalSeconds % 60L

        return if (hours > 0L) {
            String.format(Locale.US, "%02d:%02d:%02d", hours, minutes, seconds)
        } else {
            String.format(Locale.US, "%02d:%02d", minutes, seconds)
        }
    }

    /**
     * Memformat ukuran file audio (bytes) menjadi unit yang mudah dibaca (KB / MB).
     * Contoh: 3840000 bytes -> "3.7 MB"
     */
    fun formatFileSize(bytes: Long): String {
        if (bytes <= 0L) return "0 B"
        val kb = bytes / 1024.0
        val mb = kb / 1024.0
        return when {
            mb >= 1.0 -> String.format(Locale.US, "%.1f MB", mb)
            kb >= 1.0 -> String.format(Locale.US, "%.0f KB", kb)
            else -> "$bytes B"
        }
    }
}
