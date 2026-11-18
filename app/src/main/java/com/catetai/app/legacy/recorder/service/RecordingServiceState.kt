package com.catetai.app.legacy.recorder.service

import com.catetai.app.legacy.recorder.RecordingResult
import java.io.File

/**
 * Alasan penghentian proses perekaman audio di [RecordingForegroundService].
 */
enum class StopReason {
    /** Penghentian normal yang dipicu oleh interaksi pengguna (tombol Stop di UI atau notifikasi). */
    USER_REQUESTED,

    /** Penghentian otomatis karena level baterai mencapai batas kritis (<= 2%). */
    CRITICAL_BATTERY,

    /** Penghentian otomatis karena sisa ruang penyimpanan disk mencapai batas kritis (<= 50 MB). */
    CRITICAL_DISK_SPACE,

    /** Penghentian karena sistem operasi menghentikan proses (misalnya background kill / LMK). */
    SYSTEM_INTERRUPTED,

    /** Penghentian akibat exception atau kegagalan hardware audio. */
    ERROR
}

/**
 * Representasi status perekaman audio yang dipancarkan secara reaktif ke UI (Compose screen / ViewModel).
 */
sealed interface ServiceRecordingState {

    /** Kondisi menganggur (tidak ada sesi perekaman yang aktif). */
    object Idle : ServiceRecordingState

    /**
     * Kondisi saat mikrofon aktif merekam suara di latar belakang.
     *
     * @param file File WAV tempat streaming audio disimpan.
     * @param durationMs Durasi perekaman berjalan dalam milidetik.
     * @param isSilenced Menandakan apakah mikrofon sedang dibisukan oleh OS (panggilan telepon/asisten).
     * @param isLowBattery Menandakan apakah baterai telah mencapai batas peringatan (<= 5%).
     * @param isLowDisk Menandakan apakah ruang disk telah mencapai batas peringatan (<= 100 MB).
     */
    data class Recording(
        val file: File,
        val durationMs: Long = 0L,
        val isSilenced: Boolean = false,
        val isLowBattery: Boolean = false,
        val isLowDisk: Boolean = false
    ) : ServiceRecordingState

    /**
     * Kondisi setelah sesi perekaman dihentikan dan file WAV telah difinalisasi.
     *
     * @param result Metadata hasil rekaman jika berhasil disimpan secara utuh.
     * @param reason Penyebab rekaman dihentikan.
     */
    data class Stopped(
        val result: RecordingResult?,
        val reason: StopReason
    ) : ServiceRecordingState

    /**
     * Kondisi saat terjadi kegagalan fatal pada pipeline audio.
     */
    data class Error(
        val message: String,
        val cause: Throwable? = null
    ) : ServiceRecordingState
}
