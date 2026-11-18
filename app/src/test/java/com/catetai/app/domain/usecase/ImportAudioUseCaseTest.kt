package com.catetai.app.domain.usecase

import android.content.Context
import android.net.Uri
import com.catetai.app.core.crypto.TinkAudioEncryptor
import com.catetai.app.core.util.AudioUriReader
import com.catetai.app.domain.model.Recording
import com.catetai.app.domain.model.RecordingStatus
import com.catetai.app.domain.repository.RecordingRepository
import com.google.crypto.tink.KeyTemplates
import com.google.crypto.tink.KeysetHandle
import com.google.crypto.tink.StreamingAead
import com.google.crypto.tink.streamingaead.StreamingAeadConfig
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.BeforeClass
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.io.InputStream

/**
 * Fake implementation of [RecordingRepository] for Use Case unit tests.
 */
class FakeRecordingRepository : RecordingRepository {
    private val storage = mutableMapOf<String, Recording>()
    private val flow = MutableStateFlow<List<Recording>>(emptyList())

    override fun getAllRecordings(): Flow<List<Recording>> = flow

    override suspend fun getRecordingById(id: String): Recording? = storage[id]

    override suspend fun findDuplicate(originalFileName: String, fileSizeBytes: Long): Recording? =
        storage.values.find { it.originalFileName == originalFileName && it.fileSizeBytes == fileSizeBytes }

    override suspend fun insertRecording(recording: Recording): Result<Unit> {
        storage[recording.id] = recording
        flow.value = storage.values.toList()
        return Result.success(Unit)
    }

    override suspend fun updateStatus(id: String, status: RecordingStatus): Result<Unit> {
        storage[id]?.let {
            storage[id] = it.copy(status = status)
            flow.value = storage.values.toList()
        }
        return Result.success(Unit)
    }

    override suspend fun deleteRecording(id: String): Result<Unit> {
        storage.remove(id)
        flow.value = storage.values.toList()
        return Result.success(Unit)
    }

    override suspend fun updateTitle(id: String, newTitle: String): Result<Unit> {
        storage[id]?.let {
            storage[id] = it.copy(title = newTitle)
            flow.value = storage.values.toList()
        }
        return Result.success(Unit)
    }
}

/**
 * Fake [AudioUriReader] untuk menyimulasikan data berkas SAF di pengujian JVM murni.
 */
class FakeAudioUriReader(
    var displayName: String? = "Voice 001.m4a",
    var fileSize: Long = 1024L,
    var mimeType: String? = "audio/mp4",
    var durationMs: Long = 10_000L,
    var storageSpaceAvailable: Long = 500 * 1024 * 1024L, // 500 MB
    var streamContent: ByteArray = ByteArray(1024) { (it % 256).toByte() }
) : AudioUriReader {

    override fun getDisplayName(uri: Uri): String? = displayName
    override fun getFileSize(uri: Uri): Long = fileSize
    override fun getMimeType(uri: Uri): String? = mimeType
    override fun openInputStream(uri: Uri): InputStream = ByteArrayInputStream(streamContent)
    override fun getAudioDurationMs(file: File): Long = durationMs
    override fun getUsableStorageSpace(): Long = storageSpaceAvailable
}

/**
 * Unit test komprehensif untuk [ImportAudioUseCase] dengan enkripsi Google Tink Streaming AEAD.
 */
class ImportAudioUseCaseTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var fakeRepo: FakeRecordingRepository
    private lateinit var fakeReader: FakeAudioUriReader
    private lateinit var mockContext: Context
    private lateinit var encryptor: TinkAudioEncryptor
    private lateinit var useCase: ImportAudioUseCase
    private lateinit var filesDir: File
    private lateinit var cacheDir: File
    private val dummyUri: Uri = mock(Uri::class.java)

    companion object {
        @BeforeClass
        @JvmStatic
        fun initTink() {
            StreamingAeadConfig.register()
        }
    }

    @Before
    fun setUp() {
        fakeRepo = FakeRecordingRepository()
        fakeReader = FakeAudioUriReader()
        mockContext = mock(Context::class.java)

        filesDir = tempFolder.newFolder("files")
        cacheDir = tempFolder.newFolder("cache")
        `when`(mockContext.filesDir).thenReturn(filesDir)
        `when`(mockContext.cacheDir).thenReturn(cacheDir)

        val keysetHandle = KeysetHandle.generateNew(KeyTemplates.get("AES256_GCM_HKDF_4KB"))
        val streamingAead = keysetHandle.getPrimitive(StreamingAead::class.java)
        encryptor = TinkAudioEncryptor(streamingAead)

        useCase = ImportAudioUseCase(
            context = mockContext,
            audioUriReader = fakeReader,
            recordingRepository = fakeRepo,
            audioEncryptor = encryptor
        )
    }

    // ─── Happy Path ──────────────────────────────────────────────────────────

    @Test
    fun invoke_validAudio_importsSuccessfullyAndEncryptsToEncFile() = runBlocking {
        fakeReader.displayName = "Notulensi Rapat Q3.m4a"
        fakeReader.durationMs = 60_000L

        val result = useCase(dummyUri)

        assertTrue("Import harus berhasil", result.isSuccess)
        val recording = result.getOrThrow()

        // Verifikasi metadata
        assertEquals("Notulensi Rapat Q3", recording.title)
        assertEquals("Notulensi Rapat Q3.m4a", recording.originalFileName)
        assertEquals(RecordingStatus.IMPORTED, recording.status)
        assertEquals(60_000L, recording.durationMs)
        assertEquals("audio/mp4", recording.mimeType)
        assertTrue("Nama berkas audio harus berakhiran .enc", recording.audioFileName.endsWith(".enc"))

        // Verifikasi berkas fisik terenkripsi ada di filesDir/audio/
        val audioFile = File(filesDir, "audio/${recording.audioFileName}")
        assertTrue("Berkas terenkripsi harus ada di disk", audioFile.exists())
        assertTrue("Ukuran berkas terenkripsi harus > 0", audioFile.length() > 0)

        // Verifikasi isi berkas terenkripsi berbeda dari isi plaintext asli
        val encryptedBytes = audioFile.readBytes()
        assertFalse("Konten .enc tidak boleh sama persis dengan plaintext mentah", fakeReader.streamContent.contentEquals(encryptedBytes))

        // Verifikasi dekripsi menghasilkan byte yang 100% identik dengan sumber asli
        val decryptedStream = ByteArrayOutputStream()
        encryptor.decrypt(audioFile, decryptedStream).getOrThrow()
        assertArrayEquals("Konten hasil dekripsi harus identik dengan data asli", fakeReader.streamContent, decryptedStream.toByteArray())

        // Verifikasi buffer sementara di cacheDir/temp_import telah dihapus bersih
        val tempDir = File(cacheDir, "temp_import")
        val remainingTempFiles = tempDir.listFiles() ?: emptyArray()
        assertEquals("Buffer sementara harus terhapus bersih", 0, remainingTempFiles.size)

        // Verifikasi tersimpan di repositori
        val fromRepo = fakeRepo.getRecordingById(recording.id)
        assertNotNull("Harus tersimpan di repository", fromRepo)
        assertEquals(recording.id, fromRepo?.id)
    }

    @Test
    fun invoke_reportsProgressAccuratelyDuringCopyAndEncryption() = runBlocking {
        fakeReader.fileSize = 4096L
        fakeReader.streamContent = ByteArray(4096) { 1 }

        val progressList = mutableListOf<Float>()
        val result = useCase(
            uri = dummyUri,
            onProgress = { progressList.add(it) }
        )

        assertTrue(result.isSuccess)
        assertTrue("Callback progres harus dipanggil", progressList.isNotEmpty())
        assertEquals(1.0f, progressList.last(), 0.001f)
    }

    @Test
    fun invoke_customTitleProvided_usesCustomTitle() = runBlocking {
        fakeReader.displayName = "rec_009.m4a"

        val result = useCase(dummyUri, customTitle = "  Judul Kustom Rapat  ")

        assertTrue(result.isSuccess)
        val recording = result.getOrThrow()
        assertEquals("Judul Kustom Rapat", recording.title)
    }

    // ─── Format Validation ───────────────────────────────────────────────────

    @Test
    fun invoke_nonAudioMimeAndExtension_returnsFailure() = runBlocking {
        fakeReader.displayName = "dokumen.pdf"
        fakeReader.mimeType = "application/pdf"

        val result = useCase(dummyUri)

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is IllegalArgumentException)
        assertTrue(
            result.exceptionOrNull()?.message?.contains("bukan audio yang didukung") == true
        )
    }

    @Test
    fun invoke_audioExtensionWithGenericMime_isAccepted() = runBlocking {
        fakeReader.displayName = "rekaman_kantor.opus"
        fakeReader.mimeType = "application/octet-stream"

        val result = useCase(dummyUri)

        assertTrue("Ekstensi .opus harus diterima meski MIME generic", result.isSuccess)
    }

    // ─── Duration Validation (>= 3s) ─────────────────────────────────────────

    @Test
    fun invoke_audioDurationTooShort_returnsFailureAndDeletesCopiedFile() = runBlocking {
        fakeReader.durationMs = 2_000L // 2 detik (< 3 detik)

        val result = useCase(dummyUri)

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is IllegalArgumentException)
        assertTrue(
            result.exceptionOrNull()?.message?.contains("Minimal durasi rekaman") == true
        )

        // Verifikasi bahwa berkas sementara dan file terenkripsi tidak tersisa
        val audioDir = File(filesDir, "audio")
        val remainingFiles = audioDir.listFiles() ?: emptyArray()
        assertEquals("Berkas fisik yang gagal harus dihapus", 0, remainingFiles.size)

        val tempDir = File(cacheDir, "temp_import")
        val remainingTemp = tempDir.listFiles() ?: emptyArray()
        assertEquals("Berkas sementara harus dihapus", 0, remainingTemp.size)
    }

    // ─── Storage Capacity Validation (PRD EC-04) ─────────────────────────────

    @Test
    fun invoke_insufficientDiskSpace_returnsFailureWithoutCopying() = runBlocking {
        fakeReader.fileSize = 10_000_000L // 10 MB
        fakeReader.storageSpaceAvailable = 50_000_000L // 50 MB (kurang dari 2x + 100 MB buffer)

        val result = useCase(dummyUri)

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is IOException)
        assertTrue(
            result.exceptionOrNull()?.message?.contains("Ruang penyimpanan tidak cukup") == true
        )
    }

    // ─── Duplicate Detection (PRD EC-10 / F1-05) ─────────────────────────────

    @Test
    fun invoke_duplicateAudioFile_returnsFailureWithClearMessage() = runBlocking {
        fakeReader.displayName = "Rapat Sinkronisasi.m4a"
        fakeReader.fileSize = 1024L
        fakeReader.storageSpaceAvailable = 500_000_000L // 500 MB

        // 1. Impor pertama berhasil
        val firstResult = useCase(dummyUri)
        assertTrue("Impor pertama harus sukses", firstResult.isSuccess)

        // 2. Impor kedua dengan nama dan ukuran yang persis sama
        val secondResult = useCase(dummyUri)
        assertTrue("Impor kedua harus ditolak sebagai duplikat", secondResult.isFailure)
        assertTrue(secondResult.exceptionOrNull() is IllegalArgumentException)
        assertTrue(
            secondResult.exceptionOrNull()?.message?.contains("sudah pernah diimpor") == true
        )
    }

    // ─── Empty File Validation ───────────────────────────────────────────────

    @Test
    fun invoke_emptyFile_returnsFailure() = runBlocking {
        fakeReader.fileSize = 0L
        fakeReader.streamContent = ByteArray(0)

        val result = useCase(dummyUri)

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is IOException)
        assertTrue(
            result.exceptionOrNull()?.message?.contains("kosong") == true
        )
    }
}
