package com.catetai.app.core.crypto

import com.google.crypto.tink.KeyTemplates
import com.google.crypto.tink.KeysetHandle
import com.google.crypto.tink.StreamingAead
import com.google.crypto.tink.streamingaead.StreamingAeadConfig
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.BeforeClass
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

/**
 * Unit test komprehensif untuk [TinkAudioEncryptor].
 *
 * Menguji enkripsi streaming AES256-GCM-HKDF-4KB pada JVM murni:
 * 1. File terenkripsi tidak terbaca / tidak identik dengan plaintext.
 * 2. Dekripsi menghasilkan byte yang 100% identik dengan berkas asli.
 * 3. [TinkAudioEncryptor.openDecryptedStream] mampu mengalirkan data yang didekripsi secara streaming.
 * 4. Data asosiasi (associatedData) yang tidak cocok akan menggagalkan dekripsi.
 */
class TinkAudioEncryptorTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var encryptor: TinkAudioEncryptor

    companion object {
        @BeforeClass
        @JvmStatic
        fun initTink() {
            StreamingAeadConfig.register()
        }
    }

    @Before
    fun setUp() {
        val keysetHandle = KeysetHandle.generateNew(KeyTemplates.get("AES256_GCM_HKDF_4KB"))
        val streamingAead = keysetHandle.getPrimitive(StreamingAead::class.java)
        encryptor = TinkAudioEncryptor(streamingAead)
    }

    @Test
    fun `encrypt and decrypt produces identical bytes`() {
        // Buat file dummy audio 64 KB (berisi data non-trivial)
        val originalBytes = ByteArray(64 * 1024) { (it % 256).toByte() }
        val inputFile = File(tempFolder.root, "sample.m4a").apply {
            writeBytes(originalBytes)
        }
        val encryptedFile = File(tempFolder.root, "sample.enc")
        val decryptedFile = File(tempFolder.root, "decrypted.m4a")

        // 1. Eksekusi Enkripsi
        val encryptResult = encryptor.encrypt(inputFile, encryptedFile)
        assertTrue("Enkripsi harus sukses", encryptResult.isSuccess)
        assertTrue("Berkas .enc harus dibuat", encryptedFile.exists())
        assertTrue("Ukuran berkas .enc harus > 0", encryptedFile.length() > 0)

        // Verifikasi bahwa isi berkas .enc bukan plaintext
        val encryptedBytes = encryptedFile.readBytes()
        assertFalse("Isi berkas .enc tidak boleh sama dengan plaintext mentah", originalBytes.contentEquals(encryptedBytes))

        // 2. Eksekusi Dekripsi
        FileOutputStream(decryptedFile).use { output ->
            val decryptResult = encryptor.decrypt(encryptedFile, output)
            assertTrue("Dekripsi harus sukses", decryptResult.isSuccess)
        }

        // 3. Verifikasi Keaslian Byte
        val decryptedBytes = decryptedFile.readBytes()
        assertEquals("Ukuran byte hasil dekripsi harus sama persis", originalBytes.size, decryptedBytes.size)
        assertArrayEquals("Konten hasil dekripsi harus 100% identik dengan data asli", originalBytes, decryptedBytes)
    }

    @Test
    fun `openDecryptedStream streams original bytes without writing to disk`() {
        val originalText = "Catet AI — Audio Recording Raw Buffer Content 1234567890"
        val originalBytes = originalText.toByteArray(Charsets.UTF_8)
        val inputFile = File(tempFolder.root, "voice.pcm").apply {
            writeBytes(originalBytes)
        }
        val encryptedFile = File(tempFolder.root, "voice.enc")

        encryptor.encrypt(inputFile, encryptedFile).getOrThrow()

        // Baca melalui stream dekripsi on-the-fly
        val streamedOutput = ByteArrayOutputStream()
        encryptor.openDecryptedStream(encryptedFile).use { decryptStream ->
            decryptStream.copyTo(streamedOutput)
        }

        val restoredText = streamedOutput.toString(Charsets.UTF_8.name())
        assertEquals(originalText, restoredText)
    }

    @Test
    fun `decrypt with mismatched associatedData fails`() {
        val originalBytes = "Data Rahasia Rapat".toByteArray(Charsets.UTF_8)
        val inputFile = File(tempFolder.root, "secret.m4a").apply {
            writeBytes(originalBytes)
        }
        val encryptedFile = File(tempFolder.root, "secret.enc")
        val associatedData = "metadata_v1".toByteArray(Charsets.UTF_8)
        val wrongAssociatedData = "metadata_v2".toByteArray(Charsets.UTF_8)

        encryptor.encrypt(inputFile, encryptedFile, associatedData).getOrThrow()

        val output = ByteArrayOutputStream()
        val decryptResult = encryptor.decrypt(encryptedFile, output, wrongAssociatedData)

        assertTrue("Dekripsi dengan metadata salah harus gagal", decryptResult.isFailure)
        assertTrue(decryptResult.exceptionOrNull() is IOException)
    }

    @Test
    fun `encrypt nonexistent file returns failure`() {
        val nonExistent = File(tempFolder.root, "ghost.m4a")
        val target = File(tempFolder.root, "ghost.enc")

        val result = encryptor.encrypt(nonExistent, target)
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is IllegalArgumentException)
    }
}
