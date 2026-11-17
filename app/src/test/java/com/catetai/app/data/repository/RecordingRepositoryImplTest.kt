package com.catetai.app.data.repository

import android.content.Context
import com.catetai.app.data.local.dao.RecordingDao
import com.catetai.app.data.local.entity.RecordingEntity
import com.catetai.app.domain.model.RecordingStatus
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
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
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`
import java.io.File

/**
 * Implementasi palsu (fake) in-memory [RecordingDao] untuk pengujian JVM murni.
 *
 * Menggunakan [MutableStateFlow] agar Flow bersifat reaktif seperti Room yang sesungguhnya.
 * Tanpa Robolectric / Android — 100% JVM test.
 *
 * # Penyesuaian dari v1 ke v2:
 * - DIHAPUS : updateAudioMetadata (kolom sampleRate/wasSilencedBySystem tidak ada di v2)
 * - DIHAPUS : deleteRecording(entity) (hanya deleteRecordingById yang dipakai)
 * - DIHAPUS : getRecordingsByStatus(RecordingStatus) → diganti getRecordingsByStatus(String)
 * - DITAMBAH: updateCustomVocabulary
 * - Urut berdasarkan importedAt, bukan createdAt
 */
class FakeRecordingDao : RecordingDao {

    private val storage = mutableMapOf<String, RecordingEntity>()
    private val flow = MutableStateFlow<List<RecordingEntity>>(emptyList())

    /** Memancarkan ulang Flow setelah setiap perubahan storage, diurutkan terbaru dulu. */
    private fun emit() {
        flow.value = storage.values.sortedByDescending { it.importedAt }
    }

    override suspend fun insertRecording(recording: RecordingEntity) {
        storage[recording.id] = recording
        emit()
    }

    override fun getAllRecordingsOrderByDateDesc(): Flow<List<RecordingEntity>> = flow

    override suspend fun getRecordingById(id: String): RecordingEntity? = storage[id]

    override fun getRecordingsByStatus(status: String): Flow<List<RecordingEntity>> {
        return flow.map { list -> list.filter { it.status == status } }
    }

    override suspend fun updateStatus(id: String, newStatus: String, updatedAt: Long) {
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

    override suspend fun updateCustomVocabulary(id: String, vocabulary: String?, updatedAt: Long) {
        storage[id]?.let {
            storage[id] = it.copy(customVocabulary = vocabulary, updatedAt = updatedAt)
            emit()
        }
    }

    override suspend fun updateRecording(recording: RecordingEntity) {
        storage[recording.id] = recording
        emit()
    }

    override suspend fun deleteRecordingById(id: String) {
        storage.remove(id)
        emit()
    }
}

/**
 * Unit test untuk [RecordingRepositoryImpl] menggunakan JVM murni (tanpa Robolectric).
 *
 * # Strategi Pengujian
 * - [FakeRecordingDao] menggantikan Room DAO yang sesungguhnya.
 * - [TemporaryFolder] menyediakan filesystem sementara untuk pengujian penghapusan berkas fisik.
 * - Mockito mock [Context] agar [getFilesDir] mengembalikan direktori temp.
 * - Semua test menggunakan [runBlocking] agar kode suspend dapat dipanggil secara sinkron.
 */
class RecordingRepositoryImplTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var fakeDao: FakeRecordingDao
    private lateinit var repository: RecordingRepositoryImpl
    private lateinit var mockContext: Context

    @Before
    fun setUp() {
        fakeDao = FakeRecordingDao()
        mockContext = mock(Context::class.java)
        `when`(mockContext.filesDir).thenReturn(tempFolder.root)
        repository = RecordingRepositoryImpl(
            context = mockContext,
            recordingDao = fakeDao
        )
    }

    // ─── Fungsi pembantu ─────────────────────────────────────────────────────

    private suspend fun insertFakeRecording(
        id: String = "rec-${System.nanoTime()}",
        title: String = "Rapat Test",
        audioFileName: String = "audio_test.enc",
        status: String = "IMPORTED",
        importedAt: Long = System.currentTimeMillis()
    ): RecordingEntity {
        val entity = RecordingEntity(
            id = id,
            title = title,
            originalFileName = "original.m4a",
            audioFileName = audioFileName,
            durationMs = 60_000L,
            fileSizeBytes = 1_024_000L,
            mimeType = "audio/mp4",
            status = status,
            importedAt = importedAt
        )
        fakeDao.insertRecording(entity)
        return entity
    }

    // ─── getAllRecordings ─────────────────────────────────────────────────────

    @Test
    fun getAllRecordings_mapsCorrectlyAndSortsNewestFirst() = runBlocking {
        insertFakeRecording(id = "rec-lama", title = "Rapat Lama", importedAt = 1_000L)
        insertFakeRecording(id = "rec-baru", title = "Rapat Baru", importedAt = 2_000L)

        val list = repository.getAllRecordings().first()

        assertEquals(2, list.size)
        assertEquals("Rapat Baru", list[0].title)
        assertEquals("Rapat Lama", list[1].title)
    }

    @Test
    fun getAllRecordings_domainStatusMappedCorrectly() = runBlocking {
        insertFakeRecording(id = "rec-ts", status = "TRANSCRIBING")

        val list = repository.getAllRecordings().first()

        assertEquals(1, list.size)
        assertEquals(RecordingStatus.TRANSCRIBING, list[0].status)
    }

    // ─── getRecordingById ─────────────────────────────────────────────────────

    @Test
    fun getRecordingById_existingId_returnsMappedDomainObject() = runBlocking {
        insertFakeRecording(id = "rec-byid", title = "Rekaman Spesifik")

        val result = repository.getRecordingById("rec-byid")

        assertNotNull(result)
        assertEquals("rec-byid", result?.id)
        assertEquals("Rekaman Spesifik", result?.title)
    }

    @Test
    fun getRecordingById_nonExistingId_returnsNull() = runBlocking {
        val result = repository.getRecordingById("id-yang-tidak-ada")
        assertNull(result)
    }

    // ─── deleteRecording ─────────────────────────────────────────────────────

    @Test
    fun deleteRecording_removesPhysicalFileAndDatabaseRow() = runBlocking {
        val audioDir = File(tempFolder.root, "audio").apply { mkdirs() }
        val audioFile = File(audioDir, "audio_to_delete.enc")
        audioFile.writeText("konten audio palsu")
        assertTrue("Berkas fisik harus ada sebelum dihapus", audioFile.exists())

        insertFakeRecording(id = "rec-hapus", audioFileName = "audio_to_delete.enc")

        val deleteResult = repository.deleteRecording("rec-hapus")

        assertTrue("deleteRecording harus sukses", deleteResult.isSuccess)
        assertFalse("Berkas fisik harus terhapus dari disk", audioFile.exists())
        assertNull("Entitas harus terhapus dari DB", fakeDao.getRecordingById("rec-hapus"))
    }

    @Test
    fun deleteRecording_fileDoesNotExist_stillDeletesDatabaseRow() = runBlocking {
        insertFakeRecording(id = "rec-tanpa-file", audioFileName = "berkas_tidak_ada.enc")

        val result = repository.deleteRecording("rec-tanpa-file")

        assertTrue("Harus sukses meski berkas tidak ada", result.isSuccess)
        assertNull("Baris DB harus terhapus", fakeDao.getRecordingById("rec-tanpa-file"))
    }

    @Test
    fun deleteRecording_idNotFound_returnsFailure() = runBlocking {
        val result = repository.deleteRecording("id-tidak-ada")

        assertTrue("Harus gagal jika ID tidak ada", result.isFailure)
        assertTrue(result.exceptionOrNull() is IllegalArgumentException)
    }

    // ─── updateTitle ─────────────────────────────────────────────────────────

    @Test
    fun updateTitle_validTitle_updatesSuccessfully() = runBlocking {
        insertFakeRecording(id = "rec-rename", title = "Judul Lama")

        val result = repository.updateTitle("rec-rename", "Judul Baru Yang Jelas")

        assertTrue("updateTitle harus sukses", result.isSuccess)
        val updated = repository.getRecordingById("rec-rename")
        assertEquals("Judul Baru Yang Jelas", updated?.title)
    }

    @Test
    fun updateTitle_blankTitle_returnsFailureWithIllegalArgumentException() = runBlocking {
        insertFakeRecording(id = "rec-blank", title = "Judul Awal")

        val result = repository.updateTitle("rec-blank", "   ")

        assertTrue("Harus gagal jika judul hanya spasi", result.isFailure)
        assertTrue(result.exceptionOrNull() is IllegalArgumentException)
        val unchanged = repository.getRecordingById("rec-blank")
        assertEquals("Judul Awal", unchanged?.title)
    }

    @Test
    fun updateTitle_emptyString_returnsFailure() = runBlocking {
        insertFakeRecording(id = "rec-empty", title = "Judul Asli")

        val result = repository.updateTitle("rec-empty", "")

        assertTrue("Harus gagal jika judul kosong", result.isFailure)
        assertTrue(result.exceptionOrNull() is IllegalArgumentException)
    }

    @Test
    fun updateTitle_idNotFound_returnsFailure() = runBlocking {
        val result = repository.updateTitle("id-tidak-ada", "Judul Apapun")

        assertTrue("Harus gagal jika ID tidak ada", result.isFailure)
        assertTrue(result.exceptionOrNull() is IllegalArgumentException)
    }

    @Test
    fun updateTitle_trimsWhitespace_savesCleanTitle() = runBlocking {
        insertFakeRecording(id = "rec-trim", title = "Judul Awal")

        val result = repository.updateTitle("rec-trim", "  Judul Bersih  ")

        assertTrue(result.isSuccess)
        val updated = repository.getRecordingById("rec-trim")
        assertEquals("Judul Bersih", updated?.title)
    }
}
