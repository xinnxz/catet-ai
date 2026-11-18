package com.catetai.app.legacy.recorder.service

import com.catetai.app.legacy.recorder.AudioConfig
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Unit test untuk [RecordingThresholdEvaluator].
 *
 * Menguji logika evaluasi ambang batas baterai dan sisa ruang disk
 * secara deterministik di JVM.
 */
class RecordingThresholdEvaluatorTest {

    @Test
    fun evaluateBattery_aboveWarningThreshold_returnsNormal() {
        assertEquals(
            RecordingThresholdEvaluator.BatteryStatus.NORMAL,
            RecordingThresholdEvaluator.evaluateBattery(100)
        )
        assertEquals(
            RecordingThresholdEvaluator.BatteryStatus.NORMAL,
            RecordingThresholdEvaluator.evaluateBattery(50)
        )
        assertEquals(
            RecordingThresholdEvaluator.BatteryStatus.NORMAL,
            RecordingThresholdEvaluator.evaluateBattery(6)
        )
    }

    @Test
    fun evaluateBattery_atOrBelowWarningThreshold_returnsLowWarning() {
        // Tepat di batas 5%
        assertEquals(
            RecordingThresholdEvaluator.BatteryStatus.LOW_WARNING,
            RecordingThresholdEvaluator.evaluateBattery(AudioConfig.LOW_BATTERY_WARNING_PERCENT)
        )
        // Di antara 5% dan 2% (misal 3% atau 4%)
        assertEquals(
            RecordingThresholdEvaluator.BatteryStatus.LOW_WARNING,
            RecordingThresholdEvaluator.evaluateBattery(4)
        )
        assertEquals(
            RecordingThresholdEvaluator.BatteryStatus.LOW_WARNING,
            RecordingThresholdEvaluator.evaluateBattery(3)
        )
    }

    @Test
    fun evaluateBattery_atOrBelowCriticalThreshold_returnsCriticalStop() {
        // Tepat di batas 2%
        assertEquals(
            RecordingThresholdEvaluator.BatteryStatus.CRITICAL_STOP,
            RecordingThresholdEvaluator.evaluateBattery(AudioConfig.CRITICAL_BATTERY_PERCENT)
        )
        // Di bawah 2% (1% dan 0%)
        assertEquals(
            RecordingThresholdEvaluator.BatteryStatus.CRITICAL_STOP,
            RecordingThresholdEvaluator.evaluateBattery(1)
        )
        assertEquals(
            RecordingThresholdEvaluator.BatteryStatus.CRITICAL_STOP,
            RecordingThresholdEvaluator.evaluateBattery(0)
        )
    }

    @Test
    fun evaluateDiskSpace_aboveWarningThreshold_returnsNormal() {
        val largeSpace = 500L * 1024 * 1024 // 500 MB
        val justAboveWarning = AudioConfig.LOW_DISK_WARNING_BYTES + 1L

        assertEquals(
            RecordingThresholdEvaluator.DiskStatus.NORMAL,
            RecordingThresholdEvaluator.evaluateDiskSpace(largeSpace)
        )
        assertEquals(
            RecordingThresholdEvaluator.DiskStatus.NORMAL,
            RecordingThresholdEvaluator.evaluateDiskSpace(justAboveWarning)
        )
    }

    @Test
    fun evaluateDiskSpace_atOrBelowWarningThreshold_returnsLowWarning() {
        // Tepat di batas 100 MB
        assertEquals(
            RecordingThresholdEvaluator.DiskStatus.LOW_WARNING,
            RecordingThresholdEvaluator.evaluateDiskSpace(AudioConfig.LOW_DISK_WARNING_BYTES)
        )

        // Di antara 100 MB dan 50 MB (misal 75 MB)
        val intermediateSpace = 75L * 1024 * 1024
        assertEquals(
            RecordingThresholdEvaluator.DiskStatus.LOW_WARNING,
            RecordingThresholdEvaluator.evaluateDiskSpace(intermediateSpace)
        )

        val justAboveCritical = AudioConfig.MIN_DISK_SPACE_BYTES + 1L
        assertEquals(
            RecordingThresholdEvaluator.DiskStatus.LOW_WARNING,
            RecordingThresholdEvaluator.evaluateDiskSpace(justAboveCritical)
        )
    }

    @Test
    fun evaluateDiskSpace_atOrBelowCriticalThreshold_returnsCriticalStop() {
        // Tepat di batas 50 MB
        assertEquals(
            RecordingThresholdEvaluator.DiskStatus.CRITICAL_STOP,
            RecordingThresholdEvaluator.evaluateDiskSpace(AudioConfig.MIN_DISK_SPACE_BYTES)
        )

        // Di bawah 50 MB (misal 20 MB atau 0 byte)
        val belowCritical = 20L * 1024 * 1024
        assertEquals(
            RecordingThresholdEvaluator.DiskStatus.CRITICAL_STOP,
            RecordingThresholdEvaluator.evaluateDiskSpace(belowCritical)
        )

        assertEquals(
            RecordingThresholdEvaluator.DiskStatus.CRITICAL_STOP,
            RecordingThresholdEvaluator.evaluateDiskSpace(0L)
        )
    }
}
