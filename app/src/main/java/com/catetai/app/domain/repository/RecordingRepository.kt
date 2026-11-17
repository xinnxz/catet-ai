package com.catetai.app.domain.repository

import com.catetai.app.domain.model.Recording
import kotlinx.coroutines.flow.Flow

/**
 * Kontrak repositori domain untuk manajemen siklus hidup dan persistensi rekaman audio Catet AI.
 *
 * Mengikuti prinsip Clean Architecture: Domain Layer mendefinisikan antarmuka (interface),
 * sedangkan implementasinya ([com.catetai.app.data.repository.RecordingRepositoryImpl])
 * mengoordinasikan Room Database, Foreground Service, dan berkas sistem audio di Data Layer.
 */
interface RecordingRepository {

    /**
     * Mengalirkan seluruh daftar rekaman rapat secara reaktif, diurutkan dari yang terbaru.
     */
    fun getAllRecordings(): Flow<List<Recording>>

    /**
     * Mengambil satu rekaman berdasarkan [id] uniknya.
     */
    suspend fun getRecordingById(id: String): Recording?

    /**
     * Memulai sesi perekaman audio baru:
     * 1. Menghasilkan ID unik dan menyiapkan berkas fisik WAV di internal storage.
     * 2. Menyimpan entitas awal berstatus [com.catetai.app.domain.model.RecordingStatus.RECORDING] ke Room DB.
     * 3. Menjalankan [com.catetai.app.core.audio.service.RecordingForegroundService] dengan notifikasi persisten.
     *
     * @param title Judul rapat opsional (jika kosong, menggunakan penanggalan default otomatis).
     * @param customVocabulary Daftar kata kustom/nama peserta yang akan di-inject ke Whisper prompt.
     * @return [Result.success] dengan data entitas rekaman baru jika berhasil.
     */
    suspend fun startRecording(
        title: String? = null,
        customVocabulary: String? = null
    ): Result<Recording>

    /**
     * Menghentikan sesi perekaman audio yang sedang berjalan aktif:
     * 1. Memerintahkan Foreground Service untuk berhenti.
     * 2. Mengambil metadata durasi, ukuran berkas WAV, dan deteksi pembisuan mikrofon.
     * 3. Memperbarui status rekaman di Room DB menjadi [com.catetai.app.domain.model.RecordingStatus.STOPPED].
     *
     * @return [Result.success] dengan entitas rekaman yang telah diperbarui.
     */
    suspend fun stopRecording(): Result<Recording>

    /**
     * Menghapus rekaman secara permanen (menghapus berkas audio fisik di disk dan baris di database Room).
     */
    suspend fun deleteRecording(id: String): Result<Unit>

    /**
     * Memperbarui judul rekaman (fitur penamaan ulang oleh pengguna).
     */
    suspend fun updateTitle(id: String, newTitle: String): Result<Unit>
}
