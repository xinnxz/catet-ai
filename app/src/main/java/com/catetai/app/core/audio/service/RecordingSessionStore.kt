package com.catetai.app.core.audio.service

import android.content.Context
import android.content.SharedPreferences

/**
 * Penyimpan status sesi perekaman aktif berbasis [SharedPreferences].
 *
 * Menyimpan path file audio yang sedang ditulis secara streaming ke disk.
 * Jika sistem operasi mematikan proses aplikasi secara tiba-tiba (Low Memory Killer atau
 * kehabisan daya baterai), catatan sesi ini akan tetap ada. Ketika service atau aplikasi
 * menyala kembali, file audio yang belum terfinalisasi dapat segera dipulihkan melalui
 * [com.catetai.app.core.audio.WavHeaderUtil.recoverUnfinalizedHeader].
 */
class RecordingSessionStore(context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences(
        PREFS_NAME,
        Context.MODE_PRIVATE
    )

    /**
     * Menyimpan path file yang sedang aktif direkam.
     */
    fun saveActiveSession(filePath: String) {
        prefs.edit().putString(KEY_ACTIVE_RECORDING_PATH, filePath).apply()
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
