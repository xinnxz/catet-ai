package com.catetai.app.domain.repository

import com.catetai.app.domain.model.Recording
import kotlinx.coroutines.flow.Flow

/**
 * Kontrak repositori domain untuk manajemen siklus hidup dan persistensi rekaman audio Catet AI.
 *
 * Mengikuti prinsip Clean Architecture: Domain Layer mendefinisikan antarmuka (interface),
 * sedangkan implementasinya ([com.catetai.app.data.repository.RecordingRepositoryImpl])
 * mengoordinasikan Room Database dan berkas sistem audio di Data Layer.
 *
 * Versi v2 — alur berbasis impor (tidak ada perekaman bawaan).
 * Pengguna merekam suara di aplikasi rekam eksternal, kemudian mengimpor file audio ke sini.
 */
interface RecordingRepository {

    /**
     * Mengalirkan seluruh daftar rekaman secara reaktif, diurutkan dari yang terbaru diimpor.
     *
     * Flow ini otomatis memancarkan data baru setiap kali ada perubahan di Room Database,
     * sehingga UI selalu sinkron tanpa perlu polling manual.
     */
    fun getAllRecordings(): Flow<List<Recording>>

    /**
     * Mengambil satu rekaman berdasarkan [id] uniknya.
     *
     * @return Objek [Recording] jika ditemukan, atau `null` jika ID tidak ada di database.
     */
    suspend fun getRecordingById(id: String): Recording?

    /**
     * Memeriksa keberadaan rekaman dengan nama asli dan ukuran yang sama (deteksi duplikasi).
     */
    suspend fun findDuplicate(originalFileName: String, fileSizeBytes: Long): Recording?

    /**
     * Menyimpan rekaman baru ke dalam database.
     *
     * @param recording Entitas rekaman yang akan disimpan.
     * @return [Result.success] jika berhasil disimpan.
     */
    suspend fun insertRecording(recording: Recording): Result<Unit>

    /**
     * Memperbarui status pemrosesan rekaman.
     *
     * @param id ID unik rekaman.
     * @param status Status pemrosesan baru (misal: DECODING, TRANSCRIBING, dsb.).
     * @return [Result.success] jika status berhasil diperbarui.
     */
    suspend fun updateStatus(id: String, status: com.catetai.app.domain.model.RecordingStatus): Result<Unit>

    /**
     * Menghapus rekaman secara permanen:
     * 1. Menghapus berkas audio terenkripsi dari penyimpanan internal (`filesDir/audio/`).
     * 2. Menghapus baris rekaman dari Room Database (CASCADE otomatis menghapus segmen & ringkasan).
     *
     * @param id ID unik rekaman yang akan dihapus.
     * @return [Result.success] jika berhasil, [Result.failure] jika terjadi error.
     */
    suspend fun deleteRecording(id: String): Result<Unit>

    /**
     * Memperbarui judul rekaman (fitur penamaan ulang oleh pengguna).
     *
     * @param id ID unik rekaman yang judulnya akan diubah.
     * @param newTitle Judul baru. Tidak boleh kosong atau hanya spasi.
     * @return [Result.failure] dengan [IllegalArgumentException] jika [newTitle] kosong.
     */
    suspend fun updateTitle(id: String, newTitle: String): Result<Unit>

    /**
     * Memperbarui kosakata kustom (istilah teknis / prompt biasing) untuk rekaman tertentu.
     *
     * @param id ID unik rekaman.
     * @param vocabulary Kosakata yang dipisahkan koma, atau null untuk menghapus.
     */
    suspend fun updateCustomVocabulary(id: String, vocabulary: String?): Result<Unit>
}
