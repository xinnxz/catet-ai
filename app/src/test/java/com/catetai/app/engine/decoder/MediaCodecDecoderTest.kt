package com.catetai.app.engine.decoder

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/**
 * Unit test pada JVM untuk [MediaCodecDecoder].
 * Memverifikasi validasi berkas masukan dan dukungan MIME type.
 */
class MediaCodecDecoderTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private val decoder = MediaCodecDecoder()

    // ─── supportsFormat ──────────────────────────────────────────────────────

    @Test
    fun supportsFormat_supportedMimeTypes_returnTrue() {
        assertTrue(decoder.supportsFormat("audio/mp4"))
        assertTrue(decoder.supportsFormat("audio/mp4a-latm"))
        assertTrue(decoder.supportsFormat("audio/mpeg"))
        assertTrue(decoder.supportsFormat("audio/mp3"))
        assertTrue(decoder.supportsFormat("audio/ogg"))
        assertTrue(decoder.supportsFormat("audio/opus"))
        assertTrue(decoder.supportsFormat("audio/flac"))
        assertTrue(decoder.supportsFormat("audio/wav"))
        assertTrue(decoder.supportsFormat("audio/x-wav"))
    }

    @Test
    fun supportsFormat_unsupportedMimeTypes_returnFalse() {
        assertFalse(decoder.supportsFormat("video/mp4"))
        assertFalse(decoder.supportsFormat("image/jpeg"))
        assertFalse(decoder.supportsFormat("application/pdf"))
        assertFalse(decoder.supportsFormat("text/plain"))
    }

    @Test
    fun supportsFormat_caseInsensitiveAndWhitespace_handledGracefully() {
        assertTrue(decoder.supportsFormat("  AUDIO/MPEG  "))
        assertTrue(decoder.supportsFormat("Audio/MP4"))
    }

    // ─── Input Validation ────────────────────────────────────────────────────

    @Test
    fun decode_nonExistentInputFile_returnsFailure() = runBlocking {
        val nonExistent = File(tempFolder.root, "non_existent.m4a")
        val output = File(tempFolder.root, "output.pcm")

        val result = decoder.decode(nonExistent, output)

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is IllegalArgumentException)
    }

    @Test
    fun decode_emptyInputFile_returnsFailure() = runBlocking {
        val emptyFile = tempFolder.newFile("empty.mp3")
        val output = File(tempFolder.root, "output.pcm")

        val result = decoder.decode(emptyFile, output)

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is IllegalArgumentException)
    }
}
