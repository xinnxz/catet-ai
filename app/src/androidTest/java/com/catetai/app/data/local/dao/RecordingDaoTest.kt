package com.catetai.app.data.local.dao

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.catetai.app.data.local.CatetAiDatabase
import com.catetai.app.data.local.entity.RecordingEntity
import com.catetai.app.data.local.entity.RecordingStatus
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Instrumented test untuk [RecordingDao] menggunakan in-memory Room database.
 *
 * # Mengapa Instrumented Test (bukan unit test biasa)?
 * Room bergantung pada Android SQLite driver. Meskipun Room memiliki in-memory mode
 * untuk testing, driver SQL tetap butuh Android runtime (bukan JVM biasa).
 * Karena itu test ini harus dijalankan di emulator atau perangkat fisik.
 *
 * # In-Memory Database: Keunggulan untuk Testing
 * - Dibuat baru setiap test → tidak ada state sisa dari test sebelumnya.
 * - Tidak menulis ke disk → tidak ada file database tersisa setelah test selesai.
 * - Sangat cepat → I/O langsung di RAM.
 *
 * # allowMainThreadQueries()
 * Diizinkan di test saja untuk menyederhanakan test flow. Di kode produksi, Room
 * selalu memanggil database dari thread non-main (coroutine Dispatchers.IO).
 *
 * # runTest { }
 * Menjalankan coroutine suspend di lingkungan test tanpa memerlukan delay nyata.
 * [kotlinx.coroutines.test.runTest] menggantikan [runBlocking] untuk test coroutine modern.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
class RecordingDaoTest {

    private lateinit var db: CatetAiDatabase
    private lateinit var dao: RecordingDao

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, CatetAiDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        dao = db.recordingDao()
    }

    @After
    fun tearDown() {
        db.close()
    }

    // ---- Helper: membuat entitas rekaman contoh ----

    private fun buildRecording(
        id: String = "test-id-001",
        title: String = "Rapat Mingguan",
        audioFilePath: String = "/data/user/0/com.catetai.app/files/recordings/rec_test.wav",
        status: RecordingStatus = RecordingStatus.STOPPED,
        durationMs: Long = 1_800_000L,   // 30 menit
        fileSizeBytes: Long = 57_600_000L, // ~54.9 MB (30 menit × 32000 B/s)
        wasSilencedBySystem: Boolean = false,
        createdAt: Long = 1_700_000_000_000L
    ) = RecordingEntity(
        id = id,
        title = title,
        audioFilePath = audioFilePath,
        durationMs = durationMs,
        fileSizeBytes = fileSizeBytes,
        status = status,
        wasSilencedBySystem = wasSilencedBySystem,
        createdAt = createdAt,
        updatedAt = createdAt
    )

    // ================================================================
    // INSERT
    // ================================================================

    @Test
    fun insertRecording_thenQueryById_returnsCorrectEntity() = runTest {
        val recording = buildRecording()
        dao.insertRecording(recording)

        val result = dao.getRecordingById(recording.id)

        assertNotNull("Rekaman yang baru diinsert seharusnya ditemukan", result)
        assertEquals(recording.id, result!!.id)
        assertEquals(recording.title, result.title)
        assertEquals(recording.audioFilePath, result.audioFilePath)
        assertEquals(recording.durationMs, result.durationMs)
        assertEquals(recording.fileSizeBytes, result.fileSizeBytes)
        assertEquals(recording.status, result.status)
        assertEquals(recording.wasSilencedBySystem, result.wasSilencedBySystem)
        assertEquals(recording.sampleRate, result.sampleRate)
    }

    @Test
    fun insertRecording_duplicate_replacesExistingEntry() = runTest {
        val original = buildRecording(title = "Judul Awal")
        dao.insertRecording(original)

        val updated = original.copy(title = "Judul Baru (Replaced)")
        dao.insertRecording(updated) // insert dengan id sama → REPLACE

        val result = dao.getRecordingById(original.id)
        assertEquals("Judul Baru (Replaced)", result?.title)
    }

    // ================================================================
    // SELECT (getAllRecordingsOrderByDateDesc)
    // ================================================================

    @Test
    fun getAllRecordingsOrderByDateDesc_returnsLatestFirst() = runTest {
        val older = buildRecording(
            id = "id-older",
            title = "Rekaman Lama",
            createdAt = 1_700_000_000_000L
        )
        val newer = buildRecording(
            id = "id-newer",
            title = "Rekaman Baru",
            createdAt = 1_700_100_000_000L  // 100 detik lebih baru
        )
        dao.insertRecording(older)
        dao.insertRecording(newer)

        val results = dao.getAllRecordingsOrderByDateDesc().first()

        assertEquals(2, results.size)
        assertEquals("id-newer", results[0].id) // Terbaru di indeks 0
        assertEquals("id-older", results[1].id)
    }

    @Test
    fun getAllRecordingsOrderByDateDesc_emptyTable_returnsEmptyList() = runTest {
        val results = dao.getAllRecordingsOrderByDateDesc().first()
        assertTrue("DB kosong seharusnya mengembalikan list kosong", results.isEmpty())
    }

    @Test
    fun getRecordingById_nonExistentId_returnsNull() = runTest {
        val result = dao.getRecordingById("id-yang-tidak-ada")
        assertNull("ID tidak dikenal harus mengembalikan null", result)
    }

    // ================================================================
    // SELECT (getRecordingsByStatus)
    // ================================================================

    @Test
    fun getRecordingsByStatus_filtersCorrectly() = runTest {
        val processing = buildRecording(id = "id-processing", status = RecordingStatus.PROCESSING)
        val stopped1 = buildRecording(id = "id-stopped-1", status = RecordingStatus.STOPPED)
        val stopped2 = buildRecording(id = "id-stopped-2", status = RecordingStatus.STOPPED,
            createdAt = 1_700_000_001_000L)

        dao.insertRecording(processing)
        dao.insertRecording(stopped1)
        dao.insertRecording(stopped2)

        val stoppedResults = dao.getRecordingsByStatus(RecordingStatus.STOPPED).first()
        assertEquals(2, stoppedResults.size)
        assertTrue(stoppedResults.all { it.status == RecordingStatus.STOPPED })

        val processingResults = dao.getRecordingsByStatus(RecordingStatus.PROCESSING).first()
        assertEquals(1, processingResults.size)
        assertEquals(RecordingStatus.PROCESSING, processingResults[0].status)
    }

    // ================================================================
    // UPDATE
    // ================================================================

    @Test
    fun updateStatus_changesStatusAndUpdatedAt() = runTest {
        val recording = buildRecording(status = RecordingStatus.STOPPED)
        dao.insertRecording(recording)

        val newUpdatedAt = recording.createdAt + 5_000L
        dao.updateStatus(
            id = recording.id,
            newStatus = RecordingStatus.PROCESSING,
            updatedAt = newUpdatedAt
        )

        val result = dao.getRecordingById(recording.id)
        assertEquals(RecordingStatus.PROCESSING, result?.status)
        assertEquals(newUpdatedAt, result?.updatedAt)
        // Kolom lain tidak berubah
        assertEquals(recording.title, result?.title)
        assertEquals(recording.durationMs, result?.durationMs)
    }

    @Test
    fun updateTitle_changesOnlyTitleAndUpdatedAt() = runTest {
        val recording = buildRecording(title = "Judul Awal")
        dao.insertRecording(recording)

        val newUpdatedAt = recording.createdAt + 2_000L
        dao.updateTitle(
            id = recording.id,
            newTitle = "Judul Baru yang Diedit Pengguna",
            updatedAt = newUpdatedAt
        )

        val result = dao.getRecordingById(recording.id)
        assertEquals("Judul Baru yang Diedit Pengguna", result?.title)
        assertEquals(newUpdatedAt, result?.updatedAt)
        // Status tidak berubah
        assertEquals(recording.status, result?.status)
    }

    @Test
    fun updateAudioMetadata_updatesMetricsAfterStop() = runTest {
        val recording = buildRecording(
            status = RecordingStatus.RECORDING,
            durationMs = 0L,
            fileSizeBytes = 0L,
            wasSilencedBySystem = false
        )
        dao.insertRecording(recording)

        val finalDuration = 1_234_567L   // ~20,5 menit
        val finalSize = 39_506_144L      // ~37,7 MB
        val newUpdatedAt = recording.createdAt + 1_234_567L

        dao.updateAudioMetadata(
            id = recording.id,
            durationMs = finalDuration,
            fileSizeBytes = finalSize,
            wasSilencedBySystem = true,
            updatedAt = newUpdatedAt
        )

        val result = dao.getRecordingById(recording.id)
        assertEquals(finalDuration, result?.durationMs)
        assertEquals(finalSize, result?.fileSizeBytes)
        assertEquals(true, result?.wasSilencedBySystem)
        assertEquals(newUpdatedAt, result?.updatedAt)
        // Status tidak berubah oleh updateAudioMetadata
        assertEquals(RecordingStatus.RECORDING, result?.status)
    }

    @Test
    fun updateRecording_fullEntityUpdate_persistsAllChanges() = runTest {
        val original = buildRecording()
        dao.insertRecording(original)

        val modified = original.copy(
            title = "Judul Baru",
            status = RecordingStatus.TRANSCRIBED,
            durationMs = 999_000L,
            processingEngine = "whisper-tiny",
            customVocabulary = "Luthfi,Catet AI,Jakarta"
        )
        dao.updateRecording(modified)

        val result = dao.getRecordingById(original.id)
        assertEquals("Judul Baru", result?.title)
        assertEquals(RecordingStatus.TRANSCRIBED, result?.status)
        assertEquals(999_000L, result?.durationMs)
        assertEquals("whisper-tiny", result?.processingEngine)
        assertEquals("Luthfi,Catet AI,Jakarta", result?.customVocabulary)
    }

    // ================================================================
    // DELETE
    // ================================================================

    @Test
    fun deleteRecording_entityDeleted_notFoundAfterwards() = runTest {
        val recording = buildRecording()
        dao.insertRecording(recording)

        dao.deleteRecording(recording)

        val result = dao.getRecordingById(recording.id)
        assertNull("Rekaman yang dihapus seharusnya null", result)
    }

    @Test
    fun deleteRecordingById_entityDeleted_notFoundAfterwards() = runTest {
        val recording = buildRecording()
        dao.insertRecording(recording)

        dao.deleteRecordingById(recording.id)

        val result = dao.getRecordingById(recording.id)
        assertNull("Rekaman yang dihapus via ID seharusnya null", result)
    }

    @Test
    fun deleteRecordingById_unknownId_doesNotCrash() = runTest {
        // Menghapus id yang tidak ada seharusnya tidak crash (0 baris terhapus)
        dao.deleteRecordingById("id-tidak-ada-di-db")
        // Jika sampai sini tanpa exception, test lulus
    }

    // ================================================================
    // ALUR LENGKAP: Siklus hidup rekaman
    // ================================================================

    @Test
    fun fullRecordingLifecycle_insertToSummarized() = runTest {
        // 1. Buat rekaman baru saat mikrofon mulai aktif
        val newRecording = buildRecording(
            id = "lifecycle-test",
            title = "Rapat Sprint Review",
            status = RecordingStatus.RECORDING,
            durationMs = 0L,
            fileSizeBytes = 0L
        )
        dao.insertRecording(newRecording)

        // 2. Rekaman selesai → update metadata durasi dan status
        dao.updateAudioMetadata(
            id = newRecording.id,
            durationMs = 2_700_000L,     // 45 menit
            fileSizeBytes = 86_400_000L,
            wasSilencedBySystem = false
        )
        dao.updateStatus(newRecording.id, RecordingStatus.STOPPED)

        // 3. Whisper mulai diproses
        dao.updateStatus(newRecording.id, RecordingStatus.PROCESSING)

        // 4. Transkripsi selesai
        dao.updateStatus(newRecording.id, RecordingStatus.TRANSCRIBED)
        dao.updateRecording(
            dao.getRecordingById(newRecording.id)!!.copy(
                processingEngine = "whisper-base"
            )
        )

        // 5. Notulensi selesai
        dao.updateStatus(newRecording.id, RecordingStatus.SUMMARIZED)

        // 6. Verifikasi keadaan akhir
        val final = dao.getRecordingById(newRecording.id)
        assertNotNull(final)
        assertEquals(RecordingStatus.SUMMARIZED, final?.status)
        assertEquals(2_700_000L, final?.durationMs)
        assertEquals("whisper-base", final?.processingEngine)
        assertEquals(false, final?.wasSilencedBySystem)

        // 7. Pengguna menghapus rekaman
        dao.deleteRecordingById(newRecording.id)
        assertNull(dao.getRecordingById(newRecording.id))
    }
}
