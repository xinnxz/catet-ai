package com.catetai.app.legacy.recorder.service

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Unit test untuk [RecordingNotificationHelper].
 *
 * Menguji logika pemformatan durasi audio ke format teks waktu (MM:SS dan HH:MM:SS)
 * secara murni di JVM tanpa memerlukan mock Android OS.
 */
class RecordingNotificationHelperTest {

    @Test
    fun formatDuration_zero_returnsZeroMinutesZeroSeconds() {
        val result = RecordingNotificationHelper.formatDuration(0L)
        assertEquals("00:00", result)
    }

    @Test
    fun formatDuration_subSecond_roundsDownToZero() {
        val result = RecordingNotificationHelper.formatDuration(750L)
        assertEquals("00:00", result)
    }

    @Test
    fun formatDuration_oneSecond_returnsZeroOne() {
        val result = RecordingNotificationHelper.formatDuration(1_000L)
        assertEquals("00:01", result)
    }

    @Test
    fun formatDuration_fiftyNineSeconds_returnsZeroFiftyNine() {
        val result = RecordingNotificationHelper.formatDuration(59_000L)
        assertEquals("00:59", result)
    }

    @Test
    fun formatDuration_exactlyOneMinute_returnsOneZeroZero() {
        val result = RecordingNotificationHelper.formatDuration(60_000L)
        assertEquals("01:00", result)
    }

    @Test
    fun formatDuration_oneMinuteAndFiveSeconds_returnsCorrectFormat() {
        val result = RecordingNotificationHelper.formatDuration(65_000L)
        assertEquals("01:05", result)
    }

    @Test
    fun formatDuration_fiftyNineMinutesFiftyNineSeconds_returnsNoHourPrefix() {
        val ms = (59 * 60 + 59) * 1000L
        val result = RecordingNotificationHelper.formatDuration(ms)
        assertEquals("59:59", result)
    }

    @Test
    fun formatDuration_exactlyOneHour_includesHourPrefix() {
        val ms = 60 * 60 * 1000L
        val result = RecordingNotificationHelper.formatDuration(ms)
        assertEquals("01:00:00", result)
    }

    @Test
    fun formatDuration_complexHoursMinutesSeconds_formatsProperly() {
        // 2 jam + 14 menit + 35 detik
        val ms = (2 * 3600 + 14 * 60 + 35) * 1000L
        val result = RecordingNotificationHelper.formatDuration(ms)
        assertEquals("02:14:35", result)
    }

    @Test
    fun formatDuration_negativeDuration_safeCoerceToZero() {
        val result = RecordingNotificationHelper.formatDuration(-10_000L)
        assertEquals("00:00", result)
    }
}
