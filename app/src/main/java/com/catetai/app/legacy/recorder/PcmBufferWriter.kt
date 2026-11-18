package com.catetai.app.legacy.recorder

import java.io.BufferedOutputStream
import java.io.Closeable
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

/**
 * PcmBufferWriter — Komponen penyimpan aliran data audio PCM ke disk lokal.
 *
 * Mengapa komponen ini krusial dalam arsitektur perekaman?
 * =========================================================
 * Selama perekaman suara rapat berlangsung, hardware mikrofon menghasilkan ribuan byte per detik
 * (32.000 byte/detik untuk format 16kHz 16-bit mono). Menyimpan seluruh data tersebut di memori RAM
 * akan memboroskan memori (rapat 2 jam = ~230 MB RAM) dan berisiko kehilangan seluruh rekaman jika
 * aplikasi dimatikan mendadak oleh sistem operasi (*process death*).
 *
 * PcmBufferWriter menyelesaikan masalah ini dengan:
 *   1. Streaming I/O: Menulis buffer audio langsung ke disk secara bertahap menggunakan
 *      [BufferedOutputStream] berukuran 8KB untuk efisiensi transfer data.
 *   2. Otomatisasi Header: Menulis 44-byte RIFF WAV header di awal file saat stream dibuka.
 *   3. Periodic Flush & Sync: Memicu pemaksaan tulis ke flash storage fisik (`fsync`) setiap
 *      [AudioConfig.FLUSH_INTERVAL_MS] (5 detik), sehingga jika baterai HP dicabut atau proses
 *      dibunuh, data audio yang hilang maksimal hanya 5 detik terakhir.
 *   4. Safe Finalization: Mengimplementasikan [Closeable] (mendukung idiom Kotlin `use { ... }`).
 *      Saat ditutup, ia menutup stream I/O lalu memanggil [WavHeaderUtil.updateHeaderDataSize]
 *      untuk menuliskan ukuran byte aktual ke header file WAV.
 *
 * @param targetFile File tujuan penyimpanan file WAV (disimpan di sandbox internal aplikasi).
 * @param flushIntervalMs Interval waktu berkala untuk memaksa flush data ke disk (default: 5.000 ms).
 * @param timeProvider Fungsi penyedia timestamp sistem untuk mempermudah unit testing.
 */
class PcmBufferWriter(
    val targetFile: File,
    val flushIntervalMs: Long = AudioConfig.FLUSH_INTERVAL_MS,
    private val timeProvider: () -> Long = { System.currentTimeMillis() }
) : Closeable {

    private var rawFileOutputStream: FileOutputStream? = null
    private var outputStream: BufferedOutputStream? = null

    private var _bytesWritten: Long = 0L

    /**
     * Total byte PCM audio yang telah berhasil ditulis ke file (tidak termasuk 44-byte WAV header).
     */
    val bytesWritten: Long
        @Synchronized get() = _bytesWritten

    /**
     * Estimasi durasi audio yang telah tersimpan (dalam milidetik), dihitung berdasarkan [bytesWritten].
     */
    val durationMs: Long
        @Synchronized get() = AudioConfig.bytesToDurationMs(_bytesWritten)

    private var lastFlushTimeMs: Long = 0L

    private var _isClosed: Boolean = false

    /**
     * Menandakan apakah stream penulis file telah ditutup dan difinalisasi.
     */
    val isClosed: Boolean
        @Synchronized get() = _isClosed

    init {
        // Buat folder induk jika belum ada
        targetFile.parentFile?.mkdirs()

        val fos = FileOutputStream(targetFile)
        rawFileOutputStream = fos
        val bos = BufferedOutputStream(fos, AudioConfig.DEFAULT_BUFFER_SIZE_BYTES)
        outputStream = bos

        // Tulis 44-byte WAV header awal dengan placeholder dataSize = 0
        WavHeaderUtil.writeHeader(bos, dataSizeBytes = 0L)
        lastFlushTimeMs = timeProvider()
    }

    /**
     * Menulis chunk [buffer] data audio PCM ke file.
     *
     * @param buffer Array byte yang berisi sampel audio dari AudioRecord.
     * @param offset Indeks awal pembacaan dari buffer (default: 0).
     * @param length Jumlah byte yang akan ditulis (default: buffer.size).
     * @return Jumlah byte yang berhasil ditulis.
     * @throws IllegalStateException Jika [PcmBufferWriter] sudah dalam keadaan tertutup ([isClosed]).
     * @throws IllegalArgumentException Jika index offset/length berada di luar batas buffer.
     */
    @Synchronized
    fun write(buffer: ByteArray, offset: Int = 0, length: Int = buffer.size): Int {
        check(!_isClosed) { "PcmBufferWriter sudah ditutup; tidak bisa menulis data baru." }
        if (length <= 0) return 0
        require(offset >= 0 && offset + length <= buffer.size) {
            "Offset ($offset) + length ($length) berada di luar batas ukuran buffer (${buffer.size})."
        }

        val stream = outputStream ?: throw IOException("Output stream belum diinisialisasi.")
        stream.write(buffer, offset, length)
        _bytesWritten += length

        // Evaluasi apakah sudah saatnya melakukan flush periodik ke disk
        val now = timeProvider()
        if (now - lastFlushTimeMs >= flushIntervalMs) {
            flushInternal(syncToDisk = true)
            lastFlushTimeMs = now
        }

        return length
    }

    /**
     * Memaksa seluruh buffer audio yang ada di memori RAM untuk segera ditulis
     * dan disinkronisasikan (`fsync`) ke media penyimpanan fisik.
     */
    @Synchronized
    fun flush() {
        check(!_isClosed) { "PcmBufferWriter sudah ditutup." }
        flushInternal(syncToDisk = true)
    }

    private fun flushInternal(syncToDisk: Boolean) {
        outputStream?.flush()
        if (syncToDisk) {
            try {
                // fsync syscall: memaksa OS kernel mengosongkan dirty page cache ke chip flash storage
                rawFileOutputStream?.fd?.sync()
            } catch (e: Exception) {
                // Diabaikan pada environment test/emulasi yang tidak mendukung hardware fsync
            }
        }
    }

    /**
     * Menutup stream audio, mengosongkan buffer tersisa, dan secara otomatis
     * memfinalisasi header WAV (mengisi ukuran total byte aktual).
     *
     * Aman dipanggil berulang kali (idempoten).
     */
    @Synchronized
    override fun close() {
        if (_isClosed) return
        _isClosed = true

        try {
            outputStream?.flush()
            try {
                rawFileOutputStream?.fd?.sync()
            } catch (ignored: Exception) {
            }
        } catch (ignored: Exception) {
        } finally {
            try {
                outputStream?.close()
            } catch (ignored: Exception) {
            }
            outputStream = null
            rawFileOutputStream = null
        }

        // Finalisasi header WAV: seek ke byte 4 dan 40 untuk meng-update dataSizeBytes
        if (targetFile.exists() && targetFile.length() >= WavHeaderUtil.HEADER_SIZE) {
            WavHeaderUtil.updateHeaderDataSize(targetFile, _bytesWritten)
        }
    }
}
