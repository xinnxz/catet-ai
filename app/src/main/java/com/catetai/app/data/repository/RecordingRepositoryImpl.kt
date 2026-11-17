package com.catetai.app.data.repository

import android.content.Context
import android.util.Log
import com.catetai.app.core.audio.AudioConfig
import com.catetai.app.core.audio.WavHeaderUtil
import com.catetai.app.core.audio.service.RecordingForegroundService
import com.catetai.app.core.audio.service.RecordingSessionStore
import com.catetai.app.core.audio.service.ServiceRecordingState
import com.catetai.app.data.local.dao.RecordingDao
import com.catetai.app.data.local.entity.RecordingEntity
import com.catetai.app.data.mapper.toDomain
import com.catetai.app.domain.model.Recording
import com.catetai.app.domain.model.RecordingStatus
import com.catetai.app.domain.repository.RecordingRepository
import com.catetai.app.data.di.IoDispatcher
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Implementasi konkret dari [RecordingRepository].
 *
 * Mengoordinasikan:
 * - Operasi database Room ([RecordingDao])
 * - Pengelolaan Foreground Service audio ([RecordingForegroundService])
 * - Manajemen berkas fisik audio WAV di media penyimpanan internal aplikasi
 */
@Singleton
class RecordingRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val recordingDao: RecordingDao,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) : RecordingRepository {

    private val sessionStore = RecordingSessionStore(context)

    @Volatile
    private var activeRecordingId: String? = null

    override fun getAllRecordings(): Flow<List<Recording>> {
        return recordingDao.getAllRecordingsOrderByDateDesc().map { list ->
            list.map { it.toDomain() }
        }
    }

    override suspend fun getRecordingById(id: String): Recording? = withContext(ioDispatcher) {
        recordingDao.getRecordingById(id)?.toDomain()
    }

    override suspend fun startRecording(
        title: String?,
        customVocabulary: String?
    ): Result<Recording> = withContext(ioDispatcher) {
        try {
            val id = UUID.randomUUID().toString()
            val now = System.currentTimeMillis()

            // 1. Tentukan judul rapat default jika tidak diberikan
            val formattedTitle = if (!title.isNullOrBlank()) {
                title.trim()
            } else {
                val dateStr = SimpleDateFormat("d MMM yyyy, HH:mm", Locale("id", "ID")).format(Date(now))
                "Rapat $dateStr"
            }

            // 2. Siapkan direktori dan berkas WAV fisik
            val recordingsDir = File(context.filesDir, "recordings").apply { mkdirs() }
            val targetFile = File(recordingsDir, "rec_${id}.wav")

            // 3. Simpan entitas awal ke Room Database
            val entity = RecordingEntity(
                id = id,
                title = formattedTitle,
                audioFilePath = targetFile.absolutePath,
                durationMs = 0L,
                fileSizeBytes = 0L,
                sampleRate = AudioConfig.SAMPLE_RATE,
                status = RecordingStatus.RECORDING,
                wasSilencedBySystem = false,
                customVocabulary = customVocabulary?.takeIf { it.isNotBlank() }?.trim(),
                processingEngine = null,
                createdAt = now,
                updatedAt = now
            )
            recordingDao.insertRecording(entity)
            activeRecordingId = id

            // 4. Nyalakan Foreground Service audio
            RecordingForegroundService.start(context, targetFile)
            Log.i(TAG, "Berhasil memulai rekaman baru [id=$id] pada: ${targetFile.absolutePath}")

            Result.success(entity.toDomain())
        } catch (e: Exception) {
            Log.e(TAG, "Gagal memulai sesi rekaman baru", e)
            Result.failure(e)
        }
    }

    override suspend fun stopRecording(): Result<Recording> = withContext(ioDispatcher) {
        try {
            // 1. Dapatkan ID rekaman aktif
            val id = activeRecordingId
                ?: findActiveRecordingIdFromDatabase()
                ?: return@withContext Result.failure(
                    IllegalStateException("Tidak ditemukan sesi perekaman aktif yang dapat dihentikan.")
                )

            val currentEntity = recordingDao.getRecordingById(id)
                ?: return@withContext Result.failure(
                    IllegalStateException("Data entitas rekaman dengan id [$id] tidak ditemukan di database.")
                )

            // 2. Perintahkan Foreground Service untuk berhenti
            RecordingForegroundService.stop(context)

            // 3. Periksa kondisi berkas audio fisik
            val audioFile = File(currentEntity.audioFilePath)
            val now = System.currentTimeMillis()

            var finalDurationMs = 0L
            var finalFileSizeBytes = 0L
            var wasSilenced = currentEntity.wasSilencedBySystem

            // Ambil metadata dari status service terbaru jika ada
            val serviceState = RecordingForegroundService.serviceState.value
            if (serviceState is ServiceRecordingState.Stopped && serviceState.result != null) {
                finalDurationMs = serviceState.result.durationMs
                finalFileSizeBytes = serviceState.result.fileSizeBytes
                wasSilenced = serviceState.result.wasSilencedBySystem
            } else if (audioFile.exists() && audioFile.length() > AudioConfig.WAV_HEADER_SIZE_BYTES) {
                // Fallback kalkulasi mandiri jika service stopped sebelum state flow ter-observe
                try {
                    val headerInfo = WavHeaderUtil.readHeader(audioFile)
                    finalDurationMs = AudioConfig.bytesToDurationMs(headerInfo.dataSizeBytes)
                    finalFileSizeBytes = audioFile.length()
                } catch (e: Exception) {
                    Log.w(TAG, "Gagal membaca header WAV langsung, gunakan estimasi ukuran berkas", e)
                    finalFileSizeBytes = audioFile.length()
                    val pcmBytes = (finalFileSizeBytes - AudioConfig.WAV_HEADER_SIZE_BYTES).coerceAtLeast(0L)
                    finalDurationMs = AudioConfig.bytesToDurationMs(pcmBytes)
                }
            }

            // 4. Perbarui data di Room
            recordingDao.updateAudioMetadata(
                id = id,
                durationMs = finalDurationMs,
                fileSizeBytes = finalFileSizeBytes,
                wasSilencedBySystem = wasSilenced,
                updatedAt = now
            )
            recordingDao.updateStatus(id, RecordingStatus.STOPPED, now)

            activeRecordingId = null
            sessionStore.clearActiveSession()

            val updatedEntity = recordingDao.getRecordingById(id)
                ?: currentEntity.copy(
                    durationMs = finalDurationMs,
                    fileSizeBytes = finalFileSizeBytes,
                    status = RecordingStatus.STOPPED,
                    wasSilencedBySystem = wasSilenced,
                    updatedAt = now
                )

            Log.i(TAG, "Sesi rekaman [id=$id] berhasil dihentikan. Durasi: ${finalDurationMs}ms, Ukuran: ${finalFileSizeBytes}B")
            Result.success(updatedEntity.toDomain())
        } catch (e: Exception) {
            Log.e(TAG, "Gagal menghentikan sesi perekaman", e)
            Result.failure(e)
        }
    }

    override suspend fun deleteRecording(id: String): Result<Unit> = withContext(ioDispatcher) {
        try {
            val entity = recordingDao.getRecordingById(id)
                ?: return@withContext Result.failure(
                    IllegalArgumentException("Rekaman dengan ID [$id] tidak ditemukan.")
                )

            // 1. Hapus berkas fisik audio dari storage
            val file = File(entity.audioFilePath)
            if (file.exists()) {
                val deleted = file.delete()
                Log.d(TAG, "Hapus berkas fisik [${file.name}]: $deleted")
            }

            // 2. Hapus dari basis data Room
            recordingDao.deleteRecordingById(id)
            Log.i(TAG, "Rekaman [id=$id] berhasil dihapus permanen.")

            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Gagal menghapus rekaman [$id]", e)
            Result.failure(e)
        }
    }

    override suspend fun updateTitle(id: String, newTitle: String): Result<Unit> = withContext(ioDispatcher) {
        try {
            val cleanTitle = newTitle.trim()
            if (cleanTitle.isEmpty()) {
                return@withContext Result.failure(IllegalArgumentException("Judul rekaman tidak boleh kosong."))
            }

            val entity = recordingDao.getRecordingById(id)
                ?: return@withContext Result.failure(IllegalArgumentException("Rekaman [$id] tidak ditemukan."))

            val now = System.currentTimeMillis()
            recordingDao.updateTitle(id, cleanTitle, now)
            Log.i(TAG, "Judul rekaman [$id] berhasil diubah menjadi: '$cleanTitle'")

            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Gagal mengubah judul rekaman [$id]", e)
            Result.failure(e)
        }
    }

    private suspend fun findActiveRecordingIdFromDatabase(): String? {
        val activeList = recordingDao.getRecordingsByStatus(RecordingStatus.RECORDING).firstOrNull()
        return activeList?.firstOrNull()?.id
    }

    companion object {
        private const val TAG = "RecordingRepo"
    }
}
