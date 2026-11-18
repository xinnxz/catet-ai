package com.catetai.app.core.crypto

import java.io.File
import java.io.InputStream
import java.io.OutputStream

/**
 * Kontrak enkripsi dan dekripsi berkas audio menggunakan Streaming AEAD (Authenticated Encryption with Associated Data).
 *
 * Mengikuti standar keamanan PRD F-10 dan Tech Spec Bagian 10:
 * - Algoritma: AES256-GCM-HKDF-4KB (Google Tink).
 * - Enkripsi streaming hemat memori (chunked per 4KB), cocok untuk berkas audio berukuran besar (100MB+).
 */
interface AudioEncryptor {

    /**
     * Mengenkripsi [inputFile] (mentah/plain) menjadi berkas terenkripsi [outputFile].
     *
     * @param inputFile Berkas audio mentah (misal buffer sementara hasil salinan SAF).
     * @param outputFile Berkas tujuan terenkripsi (misal `xxx.enc` di penyimpanan internal).
     * @param associatedData Data asosiasi autentikasi tambahan (opsional).
     * @return [Result.success] dengan [outputFile] jika berhasil, atau [Result.failure] jika ada kesalahan I/O atau kriptografi.
     */
    fun encrypt(
        inputFile: File,
        outputFile: File,
        associatedData: ByteArray = ByteArray(0)
    ): Result<File>

    /**
     * Mendekripsi [encryptedFile] dan menuliskan aliran byte mentahnya ke [outputStream].
     *
     * @param encryptedFile Berkas terenkripsi (.enc).
     * @param outputStream Aliran byte tujuan (misal FileOutputStream untuk berkas dekode sementara).
     * @param associatedData Data asosiasi autentikasi tambahan yang digunakan saat enkripsi.
     */
    fun decrypt(
        encryptedFile: File,
        outputStream: OutputStream,
        associatedData: ByteArray = ByteArray(0)
    ): Result<Unit>

    /**
     * Membuka [InputStream] terdekripsi secara on-the-fly (tanpa harus menyimpan berkas dekripsi penuh ke disk).
     * Sangat berguna untuk pengaliran data langsung ke pemutar audio atau audio decoder.
     *
     * @param encryptedFile Berkas audio terenkripsi (.enc).
     * @param associatedData Data asosiasi autentikasi tambahan yang digunakan saat enkripsi.
     * @return Aliran input yang otomatis mendekripsi data saat dibaca.
     */
    fun openDecryptedStream(
        encryptedFile: File,
        associatedData: ByteArray = ByteArray(0)
    ): InputStream
}
