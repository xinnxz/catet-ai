package com.catetai.app.legacy.recorder.service

import com.catetai.app.legacy.recorder.AudioConfig

/**
 * Evaluator ambang batas daya dan penyimpanan untuk pipeline perekaman audio.
 *
 * Logika murni tanpa ketergantungan framework Android, sehingga 100% dapat diuji di JVM.
 */
object RecordingThresholdEvaluator {

    /**
     * Klasifikasi status daya baterai saat proses perekaman berlangsung.
     */
    enum class BatteryStatus {
        /** Daya normal (> 5%). */
        NORMAL,

        /** Baterai lemah (<= 5%). Perlu memunculkan peringatan di notifikasi dan UI. */
        LOW_WARNING,

        /** Baterai kritis (<= 2%). Harus segera melakukan auto-stop untuk mencegah file korup saat HP mati. */
        CRITICAL_STOP
    }

    /**
     * Klasifikasi status kapasitas ruang penyimpanan disk saat proses perekaman berlangsung.
     */
    enum class DiskStatus {
        /** Kapasitas normal (> 100 MB). */
        NORMAL,

        /** Ruang disk menipis (<= 100 MB). Perlu memunculkan peringatan di notifikasi dan UI. */
        LOW_WARNING,

        /** Ruang disk kritis (<= 50 MB). Harus segera melakukan auto-stop agar ada sisa ruang untuk header WAV. */
        CRITICAL_STOP
    }

    /**
     * Mengevaluasi persentase baterai ([batteryPercent]) terhadap konstanta [AudioConfig].
     */
    fun evaluateBattery(batteryPercent: Int): BatteryStatus {
        return when {
            batteryPercent <= AudioConfig.CRITICAL_BATTERY_PERCENT -> BatteryStatus.CRITICAL_STOP
            batteryPercent <= AudioConfig.LOW_BATTERY_WARNING_PERCENT -> BatteryStatus.LOW_WARNING
            else -> BatteryStatus.NORMAL
        }
    }

    /**
     * Mengevaluasi sisa ruang penyimpanan dalam byte ([usableSpaceBytes]) terhadap konstanta [AudioConfig].
     */
    fun evaluateDiskSpace(usableSpaceBytes: Long): DiskStatus {
        return when {
            usableSpaceBytes <= AudioConfig.MIN_DISK_SPACE_BYTES -> DiskStatus.CRITICAL_STOP
            usableSpaceBytes <= AudioConfig.LOW_DISK_WARNING_BYTES -> DiskStatus.LOW_WARNING
            else -> DiskStatus.NORMAL
        }
    }
}
