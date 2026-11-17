package com.catetai.app.data.repository

import android.content.ComponentName
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.SharedPreferences
import com.catetai.app.data.local.dao.RecordingDao
import com.catetai.app.data.local.entity.RecordingEntity
import com.catetai.app.domain.model.RecordingStatus
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/**
 * Fake in-memory DAO untuk pengujian [RecordingRepositoryImpl] di JVM murni.
 */
class FakeRecordingDao : RecordingDao {

    private val storage = mutableMapOf<String, RecordingEntity>()
    private val flow = MutableStateFlow<List<RecordingEntity>>(emptyList())

    private fun emit() {
        flow.value = storage.values.sortedByDescending { it.createdAt }
    }

    override suspend fun insertRecording(recording: RecordingEntity) {
        storage[recording.id] = recording
        emit()
    }

    override fun getAllRecordingsOrderByDateDesc(): Flow<List<RecordingEntity>> = flow

    override suspend fun getRecordingById(id: String): RecordingEntity? = storage[id]

    override fun getRecordingsByStatus(status: RecordingStatus): Flow<List<RecordingEntity>> {
        return flow.map { list -> list.filter { it.status == status } }
    }

    override suspend fun updateStatus(id: String, newStatus: RecordingStatus, updatedAt: Long) {
        storage[id]?.let {
            storage[id] = it.copy(status = newStatus, updatedAt = updatedAt)
            emit()
        }
    }

    override suspend fun updateTitle(id: String, newTitle: String, updatedAt: Long) {
        storage[id]?.let {
            storage[id] = it.copy(title = newTitle, updatedAt = updatedAt)
            emit()
        }
    }

    override suspend fun updateAudioMetadata(
        id: String,
        durationMs: Long,
        fileSizeBytes: Long,
        wasSilencedBySystem: Boolean,
        updatedAt: Long
    ) {
        storage[id]?.let {
            storage[id] = it.copy(
                durationMs = durationMs,
                fileSizeBytes = fileSizeBytes,
                wasSilencedBySystem = wasSilencedBySystem,
                updatedAt = updatedAt
            )
            emit()
        }
    }

    override suspend fun updateRecording(recording: RecordingEntity) {
        storage[recording.id] = recording
        emit()
    }

    override suspend fun deleteRecording(recording: RecordingEntity) {
        storage.remove(recording.id)
        emit()
    }

    override suspend fun deleteRecordingById(id: String) {
        storage.remove(id)
        emit()
    }
}

/**
 * Fake SharedPreferences sederhana untuk [RecordingSessionStore].
 */
class FakeSharedPreferences : SharedPreferences {
    private val map = mutableMapOf<String, Any?>()

    override fun getAll(): Map<String, *> = map
    override fun getString(key: String?, defValue: String?): String? = map[key] as? String ?: defValue
    override fun getStringSet(key: String?, defValues: Set<String>?): Set<String>? = null
    override fun getInt(key: String?, defValue: Int): Int = map[key] as? Int ?: defValue
    override fun getLong(key: String?, defValue: Long): Long = map[key] as? Long ?: defValue
    override fun getFloat(key: String?, defValue: Float): Float = map[key] as? Float ?: defValue
    override fun getBoolean(key: String?, defValue: Boolean): Boolean = map[key] as? Boolean ?: defValue
    override fun contains(key: String?): Boolean = map.containsKey(key)
    override fun registerOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) {}
    override fun unregisterOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) {}

    override fun edit(): SharedPreferences.Editor = FakeEditor(map)

    class FakeEditor(private val target: MutableMap<String, Any?>) : SharedPreferences.Editor {
        private val staging = mutableMapOf<String, Any?>()
        private val removed = mutableSetOf<String>()

        override fun putString(key: String?, value: String?): SharedPreferences.Editor {
            key?.let { staging[it] = value }
            return this
        }
        override fun putStringSet(key: String?, values: Set<String>?): SharedPreferences.Editor = this
        override fun putInt(key: String?, value: Int): SharedPreferences.Editor = this
        override fun putLong(key: String?, value: Long): SharedPreferences.Editor = this
        override fun putFloat(key: String?, value: Float): SharedPreferences.Editor = this
        override fun putBoolean(key: String?, value: Boolean): SharedPreferences.Editor = this
        override fun remove(key: String?): SharedPreferences.Editor {
            key?.let { removed.add(it) }
            return this
        }
        override fun clear(): SharedPreferences.Editor {
            staging.clear()
            return this
        }
        override fun commit(): Boolean {
            apply()
            return true
        }
        override fun apply() {
            removed.forEach { target.remove(it) }
            target.putAll(staging)
        }
    }
}

class FakeTestContext(private val testFilesDir: File) : ContextWrapper(null) {
    private val prefs = FakeSharedPreferences()

    override fun getFilesDir(): File = testFilesDir
    override fun getApplicationContext(): Context = this
    override fun getSharedPreferences(name: String?, mode: Int): SharedPreferences = prefs
    override fun startForegroundService(service: Intent?): ComponentName? = null
    override fun startService(service: Intent?): ComponentName? = null
}

/**
 * Unit test untuk [RecordingRepositoryImpl].
 */
class RecordingRepositoryImplTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var fakeDao: FakeRecordingDao
    private lateinit var fakeContext: FakeTestContext
    private lateinit var repository: RecordingRepositoryImpl

    @Before
    fun setUp() {
        fakeDao = FakeRecordingDao()
        fakeContext = FakeTestContext(tempFolder.root)
        repository = RecordingRepositoryImpl(
            context = fakeContext,
            recordingDao = fakeDao
        )
    }

    @Test
    fun startRecording_createsValidRecordingEntityAndTargetFile() = runBlocking {
        val result = repository.startRecording(
            title = "Rapat Tim Mobile",
            customVocabulary = "Catet AI, Hilt, Room"
        )

        assertTrue(result.isSuccess)
        val recording = result.getOrThrow()

        assertEquals("Rapat Tim Mobile", recording.title)
        assertEquals(RecordingStatus.RECORDING, recording.status)
        assertEquals("Catet AI, Hilt, Room", recording.customVocabulary)
        assertEquals(16_000, recording.sampleRate)
        assertNotNull(recording.id)
        assertTrue(recording.audioFilePath.endsWith(".wav"))

        // Verifikasi entitas tersimpan di database DAO
        val fromDao = fakeDao.getRecordingById(recording.id)
        assertNotNull(fromDao)
        assertEquals("Rapat Tim Mobile", fromDao?.title)
        assertEquals(RecordingStatus.RECORDING, fromDao?.status)
    }

    @Test
    fun startRecording_blankTitle_generatesDefaultDateTitle() = runBlocking {
        val result = repository.startRecording(title = "   ")
        assertTrue(result.isSuccess)

        val recording = result.getOrThrow()
        assertTrue("Judul default harus memuat kata 'Rapat'", recording.title.startsWith("Rapat"))
    }

    @Test
    fun stopRecording_updatesMetadataAndMarksStatusStopped() = runBlocking {
        // 1. Mulai rekaman
        val startResult = repository.startRecording(title = "Sesi Review")
        val recording = startResult.getOrThrow()

        // Buat file WAV dummy dengan data 32.000 byte (~1 detik audio)
        val audioFile = File(recording.audioFilePath)
        audioFile.parentFile?.mkdirs()
        audioFile.writeBytes(ByteArray(32_044)) // 44 byte header + 32.000 byte PCM

        // 2. Hentikan rekaman
        val stopResult = repository.stopRecording()
        assertTrue(stopResult.isSuccess)

        val stopped = stopResult.getOrThrow()
        assertEquals(RecordingStatus.STOPPED, stopped.status)
        assertEquals(audioFile.length(), stopped.fileSizeBytes)
        assertEquals(1_000L, stopped.durationMs) // 32.000 byte @ 32.000 B/s = 1.000 ms

        // Verifikasi di DAO
        val fromDao = fakeDao.getRecordingById(recording.id)
        assertEquals(RecordingStatus.STOPPED, fromDao?.status)
        assertEquals(1_000L, fromDao?.durationMs)
    }

    @Test
    fun stopRecording_whenNoActiveSession_returnsFailure() = runBlocking {
        val result = repository.stopRecording()
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is IllegalStateException)
    }

    @Test
    fun deleteRecording_removesPhysicalFileAndDatabaseRow() = runBlocking {
        // Mulai dan stop rekaman
        val startResult = repository.startRecording(title = "Akan Dihapus")
        val recording = startResult.getOrThrow()

        val audioFile = File(recording.audioFilePath)
        audioFile.writeText("dummy content")
        assertTrue("File fisik harus ada sebelum dihapus", audioFile.exists())

        // Eksekusi penghapusan
        val deleteResult = repository.deleteRecording(recording.id)
        assertTrue(deleteResult.isSuccess)

        // Verifikasi file fisik terhapus
        assertFalse("File fisik harus terhapus dari disk", audioFile.exists())

        // Verifikasi baris di database terhapus
        val fromDao = fakeDao.getRecordingById(recording.id)
        assertNull("Entitas harus terhapus dari DB", fromDao)
    }

    @Test
    fun updateTitle_validTitle_updatesSuccessfully() = runBlocking {
        val startResult = repository.startRecording(title = "Judul Lama")
        val recording = startResult.getOrThrow()

        val updateResult = repository.updateTitle(recording.id, "Judul Baru Yang Jelas")
        assertTrue(updateResult.isSuccess)

        val updated = repository.getRecordingById(recording.id)
        assertEquals("Judul Baru Yang Jelas", updated?.title)
    }

    @Test
    fun updateTitle_blankTitle_returnsFailure() = runBlocking {
        val startResult = repository.startRecording(title = "Judul Awal")
        val recording = startResult.getOrThrow()

        val updateResult = repository.updateTitle(recording.id, "   ")
        assertTrue(updateResult.isFailure)
        assertTrue(updateResult.exceptionOrNull() is IllegalArgumentException)
    }

    @Test
    fun getAllRecordings_observesDaoAndMapsToDomain() = runBlocking {
        repository.startRecording(title = "Rapat 1")
        Thread.sleep(15) // Memastikan epoch millisecond Rapat 2 lebih baru dari Rapat 1
        repository.startRecording(title = "Rapat 2")

        val list = repository.getAllRecordings().first()
        assertEquals(2, list.size)
        assertEquals("Rapat 2", list[0].title) // Terbaru di atas
        assertEquals("Rapat 1", list[1].title)
    }
}
