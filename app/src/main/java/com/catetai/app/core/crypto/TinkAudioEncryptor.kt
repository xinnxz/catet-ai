package com.catetai.app.core.crypto

import com.google.crypto.tink.StreamingAead
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.io.OutputStream
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Implementasi [AudioEncryptor] berbasis Google Tink [StreamingAead].
 *
 * Menggunakan chunking AES256-GCM dengan HKDF dan blok autentikasi 4 KB,
 * sehingga berkas audio rapat panjang berukuran ratusan megabyte dapat dienkripsi
 * dan didekripsi dengan konsumsi memori RAM yang sangat kecil (beberapa kilobyte).
 *
 * @param streamingAead Primitif StreamingAead yang di-inject dari Keystore/KeysetHandle.
 */
@Singleton
class TinkAudioEncryptor @Inject constructor(
    private val streamingAead: StreamingAead
) : AudioEncryptor {

    override fun encrypt(
        inputFile: File,
        outputFile: File,
        associatedData: ByteArray
    ): Result<File> = runCatching {
        require(inputFile.exists()) { "Berkas sumber tidak ditemukan: ${inputFile.absolutePath}" }
        outputFile.parentFile?.mkdirs()

        FileInputStream(inputFile).use { input ->
            FileOutputStream(outputFile).use { fileOut ->
                streamingAead.newEncryptingStream(fileOut, associatedData).use { encryptingOut ->
                    input.copyTo(encryptingOut)
                }
            }
        }
        outputFile
    }

    override fun decrypt(
        encryptedFile: File,
        outputStream: OutputStream,
        associatedData: ByteArray
    ): Result<Unit> = runCatching {
        require(encryptedFile.exists()) { "Berkas terenkripsi tidak ditemukan: ${encryptedFile.absolutePath}" }
        FileInputStream(encryptedFile).use { fileIn ->
            streamingAead.newDecryptingStream(fileIn, associatedData).use { decryptingIn ->
                decryptingIn.copyTo(outputStream)
            }
        }
    }

    override fun openDecryptedStream(
        encryptedFile: File,
        associatedData: ByteArray
    ): InputStream {
        require(encryptedFile.exists()) { "Berkas terenkripsi tidak ditemukan: ${encryptedFile.absolutePath}" }
        val fileIn = FileInputStream(encryptedFile)
        return streamingAead.newDecryptingStream(fileIn, associatedData)
    }
}
