package com.catetai.app.legacy.recorder.service

import android.content.Context
import android.content.SharedPreferences

/**
 * Penyimpan status sesi perekaman aktif berbasis [SharedPreferences].
 *
 * Menyimpan path file audio yang sedang ditulis secara streaming ke disk.
 * Jika sistem operasi mematikan proses aplikasi secara tiba-tiba (Low Memory Killer atau
 * kehabisan daya baterai), catatan sesi ini akan tetap ada. Ketika service atau aplikasi
 * menyala kembali, file audio yang belum terfinalisasi dapat segera dipulihkan melalui
 * [com.catetai.app.legacy.recorder.WavHeaderUtil.recoverUnfinalizedHeader].
 */
class RecordingSessionStore(context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences(
        PREFS_NAME,
        Context.MODE_PRIVATE
    )

    /**
     * Menyimpan path file yang sedang aktif direkam.
     */
    /**
     * Menyimpan path file yang sedang aktif direkam.
     *
     * WAJIB menggunakan [SharedPreferences.Editor.commit] (sinkron, bukan [apply])
     * agar data sudah pasti ditulis ke disk SEBELUM AudioRecord mulai merekam.
     * Jika menggunakan [apply] (asinkron), ada jendela waktu di mana proses bisa
     * di-kill oleh sistem sebelum penulisan ke disk selesai, sehingga path sesi
     * hilang dan [WavHeaderUtil.recoverUnfinalizedHeader] tidak bisa menemukan
     * file yang perlu dipulihkan.
     *
     * @return true jika berhasil ditulis ke disk, false jika gagal.
     */
    fun saveActiveSession(filePath: String): Boolean {
        return prefs.edit().putString(KEY_ACTIVE_RECORDING_PATH, filePath).commit()
    }

    /**
     * Mengambil path file sesi yang masih belum di-clear (jika ada).
     */
    fun getActiveSessionPath(): String? {
        return prefs.getString(KEY_ACTIVE_RECORDING_PATH, null)
    }

    /**
     * Menghapus sesi aktif setelah perekaman selesai secara normal atau setelah file berhasil dipulihkan.
     */
    fun clearActiveSession() {
        prefs.edit().remove(KEY_ACTIVE_RECORDING_PATH).apply()
    }

    companion object {
        private const val PREFS_NAME = "catet_ai_recording_session"
        private const val KEY_ACTIVE_RECORDING_PATH = "active_recording_path"
    }
}
