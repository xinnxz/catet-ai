package com.catetai.app.data.repository

import android.content.Context
import android.util.Log
import com.catetai.app.data.di.IoDispatcher
import com.catetai.app.data.local.dao.RecordingDao
import com.catetai.app.data.mapper.toDomain
import com.catetai.app.domain.model.Recording
import com.catetai.app.domain.repository.RecordingRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Implementasi konkret dari [RecordingRepository].
 *
 * Versi v2 — alur berbasis impor audio dari luar aplikasi.
 * Kelas ini hanya bertugas:
 * - Membaca daftar rekaman dari Room Database ([RecordingDao])
 * - Menghapus rekaman (berkas fisik + baris database)
 * - Memperbarui judul rekaman
 *
 * Proses impor audio, transkripsi, dan pembuatan ringkasan ditangani
 * oleh use case dan service terpisah di lapisan Domain/Data.
 *
 * @param context Konteks aplikasi untuk mengakses path penyimpanan internal.
 * @param recordingDao DAO untuk operasi database Room terenkripsi.
 * @param ioDispatcher Dispatcher koroutine untuk operasi I/O (dapat diganti saat pengujian).
 */
@Singleton
class RecordingRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val recordingDao: RecordingDao,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) : RecordingRepository {

    /**
     * Mengalirkan seluruh daftar rekaman secara reaktif dari Room Database,
     * diurutkan berdasarkan waktu impor terbaru ([com.catetai.app.data.local.entity.RecordingEntity.importedAt]).
     *
     * Flow ini otomatis memancarkan data terbaru setiap kali ada perubahan di tabel `recordings`.
     */
    override fun getAllRecordings(): Flow<List<Recording>> {
        return recordingDao.getAllRecordingsOrderByDateDesc().map { list ->
            list.map { it.toDomain() }
        }
    }

    /**
     * Mengambil satu data rekaman dari database berdasarkan ID unik-nya,
     * kemudian memetakannya ke domain model.
     */
    override suspend fun getRecordingById(id: String): Recording? = withContext(ioDispatcher) {
        recordingDao.getRecordingById(id)?.toDomain()
    }

    /**
     * Menghapus rekaman secara permanen dari disk dan database.
     *
     * Urutan operasi:
     * 1. Ambil entitas dari database untuk mendapatkan nama berkas audio relatif.
     * 2. Bangun path absolut: `context.filesDir/audio/<audioFileName>`.
     * 3. Hapus berkas fisik jika ada (abaikan jika sudah tidak ada).
     * 4. Hapus baris di tabel `recordings` — Room CASCADE otomatis menghapus
     *    baris terkait di `transcript_segments` dan `summaries`.
     */
    override suspend fun deleteRecording(id: String): Result<Unit> = withContext(ioDispatcher) {
        try {
            val entity = recordingDao.getRecordingById(id)
                ?: return@withContext Result.failure(
                    IllegalArgumentException("Rekaman dengan ID [$id] tidak ditemukan.")
                )

            // Hapus berkas audio terenkripsi dari penyimpanan internal
            val audioFile = File(context.filesDir, "audio/${entity.audioFileName}")
            if (audioFile.exists()) {
                val deleted = audioFile.delete()
                Log.d(TAG, "Hapus berkas fisik [${audioFile.name}]: $deleted")
            } else {
                Log.w(TAG, "Berkas fisik [${audioFile.name}] tidak ditemukan, lewati penghapusan fisik.")
            }

            // Hapus baris dari database (CASCADE menghapus segmen & ringkasan secara otomatis)
            recordingDao.deleteRecordingById(id)
            Log.i(TAG, "Rekaman [id=$id] berhasil dihapus permanen.")

            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Gagal menghapus rekaman [$id]", e)
            Result.failure(e)
        }
    }

    /**
     * Memperbarui judul rekaman di database.
     *
     * Validasi: judul tidak boleh kosong atau hanya berisi spasi.
     * Jika kosong, mengembalikan [Result.failure] dengan [IllegalArgumentException].
     */
    override suspend fun updateTitle(id: String, newTitle: String): Result<Unit> = withContext(ioDispatcher) {
        try {
            val cleanTitle = newTitle.trim()
            if (cleanTitle.isEmpty()) {
                return@withContext Result.failure(
                    IllegalArgumentException("Judul rekaman tidak boleh kosong.")
                )
            }

            // Pastikan rekaman dengan ID tersebut ada sebelum update
            recordingDao.getRecordingById(id)
                ?: return@withContext Result.failure(
                    IllegalArgumentException("Rekaman [$id] tidak ditemukan.")
                )

            val now = System.currentTimeMillis()
            recordingDao.updateTitle(id, cleanTitle, now)
            Log.i(TAG, "Judul rekaman [$id] berhasil diubah menjadi: '$cleanTitle'")

            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Gagal mengubah judul rekaman [$id]", e)
            Result.failure(e)
        }
    }

    companion object {
        private const val TAG = "RecordingRepo"
    }
}
