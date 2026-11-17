package com.catetai.app.presentation.home.util

import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone

class RecordingFormattersTest {

    @Test
    fun `formatDuration returns 00 00 for zero or negative values`() {
        assertEquals("00:00", RecordingFormatters.formatDuration(0L))
        assertEquals("00:00", RecordingFormatters.formatDuration(-5000L))
    }

    @Test
    fun `formatDuration returns mm ss for duration under one hour`() {
        // 32 menit 15 detik = (32 * 60 + 15) * 1000 = 1935000 ms
        assertEquals("32:15", RecordingFormatters.formatDuration(1_935_000L))
        // 2 menit 0 detik = 120000 ms
        assertEquals("02:00", RecordingFormatters.formatDuration(120_000L))
        // 45 detik = 45000 ms
        assertEquals("00:45", RecordingFormatters.formatDuration(45_000L))
    }

    @Test
    fun `formatDuration returns hh mm ss for duration of one hour or more`() {
        // 1 jam 15 menit 30 detik = (3600 + 15 * 60 + 30) * 1000 = 4530000 ms
        assertEquals("01:15:30", RecordingFormatters.formatDuration(4_530_000L))
    }

    @Test
    fun `formatDateTime formats valid epoch timestamp into indonesian representation`() {
        val testTimeZone = TimeZone.getTimeZone("Asia/Jakarta")
        val calendar = Calendar.getInstance(testTimeZone, Locale("id", "ID")).apply {
            set(Calendar.YEAR, 2026)
            set(Calendar.MONTH, Calendar.NOVEMBER)
            set(Calendar.DAY_OF_MONTH, 17)
            set(Calendar.HOUR_OF_DAY, 16)
            set(Calendar.MINUTE, 30)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val formatted = RecordingFormatters.formatDateTime(calendar.timeInMillis, timeZone = testTimeZone)
        // Pastikan format persis "17 Nov 2026, 16:30" terlepas dari zona waktu host mesin CI
        assertEquals("17 Nov 2026, 16:30", formatted)
    }

    @Test
    fun `formatDateTime returns dash for non positive values`() {
        assertEquals("-", RecordingFormatters.formatDateTime(0L))
        assertEquals("-", RecordingFormatters.formatDateTime(-100L))
    }

    @Test
    fun `formatFileSize formats bytes, KB, and MB accurately`() {
        assertEquals("0 B", RecordingFormatters.formatFileSize(0L))
        assertEquals("500 B", RecordingFormatters.formatFileSize(500L))
        assertEquals("100 KB", RecordingFormatters.formatFileSize(100 * 1024L))
        assertEquals("3.7 MB", RecordingFormatters.formatFileSize((3.7 * 1024 * 1024).toLong()))
    }
}
