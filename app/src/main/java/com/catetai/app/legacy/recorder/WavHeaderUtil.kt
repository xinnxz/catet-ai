package com.catetai.app.legacy.recorder

import java.io.File
import java.io.IOException
import java.io.OutputStream
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Metadata representasi header file WAV setelah diparsing.
 */
data class WavHeaderInfo(
    val sampleRate: Int,
    val channels: Int,
    val bitsPerSample: Int,
    val byteRate: Int,
    val blockAlign: Int,
    val dataSizeBytes: Long,
    val totalFileSizeBytes: Long
)

/**
 * WavHeaderUtil — Generator, parser, dan updater header file WAV (RIFF standard).
 *
 * Mengapa file WAV butuh Header 44-byte?
 * =====================================
 * Buffer audio mentah yang kita rekam dari mikrofon via AudioRecord adalah sinyal PCM
 * (Pulse-Code Modulation) murni: urutan byte tanpa metadata. Jika disimpan apa adanya,
 * pemutar audio (seperti ExoPlayer atau media player HP) tidak akan tahu:
 *   - Berapa sample rate-nya? (16.000 Hz, 44.100 Hz, atau 48.000 Hz?)
 *   - Berapa bit per sampel? (16-bit atau 24-bit?)
 *   - Mono atau stereo?
 *
 * 44-byte WAV header bertindak sebagai "KTP" file audio, mengemas semua informasi tersebut
 * di awal file. Formatnya mengikuti spesifikasi standar RIFF (Resource Interchange File Format):
 *
 *   Offset | Ukuran | Field          | Tipe Data      | Nilai di Catet AI
 *   -------+--------+----------------+----------------+-----------------------------
 *   0      | 4 byte | ChunkID        | ASCII (Big-E)  | "RIFF" (0x52494646)
 *   4      | 4 byte | ChunkSize      | uint32 (Lit-E) | Total File Size - 8 byte (36 + dataSize)
 *   8      | 4 byte | Format         | ASCII (Big-E)  | "WAVE" (0x57415645)
 *   12     | 4 byte | Subchunk1ID    | ASCII (Big-E)  | "fmt " (0x666D7420)
 *   16     | 4 byte | Subchunk1Size  | uint32 (Lit-E) | 16 (ukuran PCM subchunk)
 *   20     | 2 byte | AudioFormat    | uint16 (Lit-E) | 1 (Linear PCM uncompressed)
 *   22     | 2 byte | NumChannels    | uint16 (Lit-E) | 1 (Mono)
 *   24     | 4 byte | SampleRate     | uint32 (Lit-E) | 16000 (16 kHz)
 *   28     | 4 byte | ByteRate       | uint32 (Lit-E) | 32000 (SampleRate × Channels × Bits/8)
 *   32     | 2 byte | BlockAlign     | uint16 (Lit-E) | 2 (Channels × Bits/8)
 *   34     | 2 byte | BitsPerSample  | uint16 (Lit-E) | 16 (16-bit)
 *   36     | 4 byte | Subchunk2ID    | ASCII (Big-E)  | "data" (0x64617461)
 *   40     | 4 byte | Subchunk2Size  | uint32 (Lit-E) | Ukuran data audio PCM (bytes)
 *   44     | n byte | Audio Data     | Byte[]         | Konten suara rekaman mentah
 *
 * Catatan Arsitektur (End-of-Recording Update):
 * Saat perekaman dimulai, durasi rapat belum diketahui, sehingga `ChunkSize` dan `Subchunk2Size`
 * ditulis sebagai placeholder (0). Saat perekaman berhenti, fungsi [updateHeaderDataSize]
 * menggunakan [RandomAccessFile] untuk meng-seek ke byte 4 dan byte 40, memperbarui ukuran
 * data yang sebenarnya tanpa perlu menulis ulang seluruh file audio berukuran ratusan MB.
 */
object WavHeaderUtil {

    const val HEADER_SIZE: Int = AudioConfig.WAV_HEADER_SIZE_BYTES // 44 bytes

    private const val OFFSET_CHUNK_SIZE: Long = 4L
    private const val OFFSET_SUBCHUNK2_SIZE: Long = 40L

    /**
     * Membuat ByteArray 44-byte yang berisi header RIFF WAV lengkap.
     *
     * @param dataSizeBytes Ukuran data PCM dalam byte (default: 0 saat awal rekaman).
     * @param sampleRate Frekuensi sampling (default: 16.000 Hz).
     * @param channels Jumlah channel (default: 1 = Mono).
     * @param bitsPerSample Bit per sampel (default: 16-bit).
     */
    fun createHeaderByteArray(
        dataSizeBytes: Long = 0L,
        sampleRate: Int = AudioConfig.SAMPLE_RATE,
        channels: Int = AudioConfig.CHANNEL_COUNT,
        bitsPerSample: Int = AudioConfig.BYTES_PER_SAMPLE * 8
    ): ByteArray {
        require(dataSizeBytes >= 0L) { "dataSizeBytes tidak boleh negatif: $dataSizeBytes" }
        require(sampleRate > 0) { "sampleRate harus positif: $sampleRate" }
        require(channels > 0) { "channels harus positif: $channels" }
        require(bitsPerSample > 0 && bitsPerSample % 8 == 0) {
            "bitsPerSample harus kelipatan 8 dan positif: $bitsPerSample"
        }

        val byteRate = sampleRate * channels * (bitsPerSample / 8)
        val blockAlign = (channels * (bitsPerSample / 8)).toShort()
        val totalChunkSize = 36L + dataSizeBytes

        val buffer = ByteBuffer.allocate(HEADER_SIZE).order(ByteOrder.LITTLE_ENDIAN)

        // 0..3: ChunkID "RIFF" (Big-Endian ASCII)
        buffer.put('R'.code.toByte())
        buffer.put('I'.code.toByte())
        buffer.put('F'.code.toByte())
        buffer.put('F'.code.toByte())

        // 4..7: ChunkSize = 36 + dataSizeBytes (Little-Endian 32-bit uint)
        buffer.putInt(totalChunkSize.toInt())

        // 8..11: Format "WAVE" (Big-Endian ASCII)
        buffer.put('W'.code.toByte())
        buffer.put('A'.code.toByte())
        buffer.put('V'.code.toByte())
        buffer.put('E'.code.toByte())

        // 12..15: Subchunk1ID "fmt " (Big-Endian ASCII)
        buffer.put('f'.code.toByte())
        buffer.put('m'.code.toByte())
        buffer.put('t'.code.toByte())
        buffer.put(' '.code.toByte())

        // 16..19: Subchunk1Size = 16 untuk Linear PCM (Little-Endian)
        buffer.putInt(16)

        // 20..21: AudioFormat = 1 (Linear PCM, Little-Endian short)
        buffer.putShort(1.toShort())

        // 22..23: NumChannels (Little-Endian short)
        buffer.putShort(channels.toShort())

        // 24..27: SampleRate (Little-Endian int)
        buffer.putInt(sampleRate)

        // 28..31: ByteRate (Little-Endian int)
        buffer.putInt(byteRate)

        // 32..33: BlockAlign (Little-Endian short)
        buffer.putShort(blockAlign)

        // 34..35: BitsPerSample (Little-Endian short)
        buffer.putShort(bitsPerSample.toShort())

        // 36..39: Subchunk2ID "data" (Big-Endian ASCII)
        buffer.put('d'.code.toByte())
        buffer.put('a'.code.toByte())
        buffer.put('t'.code.toByte())
        buffer.put('a'.code.toByte())

        // 40..43: Subchunk2Size = dataSizeBytes (Little-Endian 32-bit uint)
        buffer.putInt(dataSizeBytes.toInt())

        return buffer.array()
    }

    /**
     * Menulis header WAV 44-byte langsung ke [outputStream].
     * Biasanya dipanggil sekali saat file audio baru dibuka.
     */
    fun writeHeader(
        outputStream: OutputStream,
        dataSizeBytes: Long = 0L,
        sampleRate: Int = AudioConfig.SAMPLE_RATE,
        channels: Int = AudioConfig.CHANNEL_COUNT,
        bitsPerSample: Int = AudioConfig.BYTES_PER_SAMPLE * 8
    ) {
        val headerBytes = createHeaderByteArray(
            dataSizeBytes = dataSizeBytes,
            sampleRate = sampleRate,
            channels = channels,
            bitsPerSample = bitsPerSample
        )
        outputStream.write(headerBytes)
        outputStream.flush()
    }

    /**
     * Meng-update ukuran data pada header WAV yang sudah tertulis di disk tanpa rewrite seluruh file.
     * Meng-seek langsung ke byte 4 (ChunkSize) dan byte 40 (Subchunk2Size).
     *
     * @param file File WAV di filesystem lokal.
     * @param dataSizeBytes Ukuran byte audio PCM aktual yang berhasil ditulis.
     */
    fun updateHeaderDataSize(file: File, dataSizeBytes: Long) {
        require(file.exists()) { "File WAV tidak ditemukan: ${file.absolutePath}" }
        require(dataSizeBytes >= 0L) { "dataSizeBytes tidak boleh negatif: $dataSizeBytes" }

        val totalChunkSize = 36L + dataSizeBytes

        RandomAccessFile(file, "rw").use { raf ->
            // Update ChunkSize (offset 4)
            raf.seek(OFFSET_CHUNK_SIZE)
            val chunkBuffer = ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN)
            chunkBuffer.putInt(totalChunkSize.toInt())
            raf.write(chunkBuffer.array())

            // Update Subchunk2Size (offset 40)
            raf.seek(OFFSET_SUBCHUNK2_SIZE)
            val dataBuffer = ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN)
            dataBuffer.putInt(dataSizeBytes.toInt())
            raf.write(dataBuffer.array())
        }
    }

    /**
     * Membaca dan memvalidasi header WAV 44-byte dari [file].
     *
     * @throws IOException Jika file berukuran < 44 byte atau magic signature ("RIFF", "WAVE") tidak cocok.
     * @return [WavHeaderInfo] yang berisi semua parameter teknis file audio tersebut.
     */
    fun readHeader(file: File): WavHeaderInfo {
        if (!file.exists()) {
            throw IOException("File tidak ditemukan: ${file.absolutePath}")
        }
        if (file.length() < HEADER_SIZE) {
            throw IOException("File terlalu kecil untuk format WAV: ${file.length()} byte (minimal 44 byte)")
        }

        val headerBytes = ByteArray(HEADER_SIZE)
        RandomAccessFile(file, "r").use { raf ->
            raf.readFully(headerBytes)
        }

        val buffer = ByteBuffer.wrap(headerBytes).order(ByteOrder.LITTLE_ENDIAN)

        // Validasi "RIFF"
        val riff = String(headerBytes, 0, 4, Charsets.US_ASCII)
        if (riff != "RIFF") {
            throw IOException("Header korup: Signature awal bukan 'RIFF', melainkan '$riff'")
        }

        // ChunkSize (offset 4)
        buffer.position(4)
        val chunkSize = buffer.getInt().toLong() and 0xFFFFFFFFL

        // Validasi "WAVE" (offset 8)
        val wave = String(headerBytes, 8, 4, Charsets.US_ASCII)
        if (wave != "WAVE") {
            throw IOException("Header korup: Format bukan 'WAVE', melainkan '$wave'")
        }

        // Validasi "fmt " (offset 12)
        val fmt = String(headerBytes, 12, 4, Charsets.US_ASCII)
        if (fmt != "fmt ") {
            throw IOException("Header korup: Subchunk1ID bukan 'fmt ', melainkan '$fmt'")
        }

        // Subchunk1Size (offset 16)
        buffer.position(16)
        val subchunk1Size = buffer.getInt()
        if (subchunk1Size < 16) {
            throw IOException("Header tidak valid: Subchunk1Size = $subchunk1Size (minimal 16 untuk PCM)")
        }

        // AudioFormat (offset 20)
        val audioFormat = buffer.getShort().toInt()
        if (audioFormat != 1) {
            throw IOException("Format audio tidak didukung: AudioFormat = $audioFormat (hanya mendukung 1 = Linear PCM)")
        }

        // Channels (offset 22)
        val channels = buffer.getShort().toInt()

        // SampleRate (offset 24)
        val sampleRate = buffer.getInt()

        // ByteRate (offset 28)
        val byteRate = buffer.getInt()

        // BlockAlign (offset 32)
        val blockAlign = buffer.getShort().toInt()

        // BitsPerSample (offset 34)
        val bitsPerSample = buffer.getShort().toInt()

        // Validasi "data" (offset 36)
        val dataTag = String(headerBytes, 36, 4, Charsets.US_ASCII)
        if (dataTag != "data") {
            throw IOException("Header korup: Subchunk2ID bukan 'data', melainkan '$dataTag'")
        }

        // Subchunk2Size (offset 40)
        buffer.position(40)
        val dataSizeBytes = buffer.getInt().toLong() and 0xFFFFFFFFL

        return WavHeaderInfo(
            sampleRate = sampleRate,
            channels = channels,
            bitsPerSample = bitsPerSample,
            byteRate = byteRate,
            blockAlign = blockAlign,
            dataSizeBytes = dataSizeBytes,
            totalFileSizeBytes = file.length()
        )
    }

    /**
     * Memperbaiki header file WAV jika proses perekaman sebelumnya terputus mendadak
     * (misalnya baterai habis, crash, atau killed by OS sebelum updateHeaderDataSize dipanggil).
     *
     * Jika file memiliki ukuran > 44 byte namun field `dataSize` di header bernilai 0
     * (atau tidak sinkron dengan ukuran file di disk), fungsi ini merekonstruksi ukuran
     * sebenarnya dengan rumus: `actualDataSize = file.length() - 44`.
     *
     * @return true jika perbaikan berhasil dilakukan, false jika header sudah benar atau file < 44 byte.
     */
    fun recoverUnfinalizedHeader(file: File): Boolean {
        if (!file.exists() || file.length() <= HEADER_SIZE) {
            return false
        }

        val rawDataSize = file.length() - HEADER_SIZE
        val frameSizeBytes = AudioConfig.BYTES_PER_SAMPLE * AudioConfig.CHANNEL_COUNT // 2 byte per frame PCM 16-bit mono
        // Potong ke kelipatan frame size (2 byte) untuk mencegah sampel audio terputus di tengah byte
        val actualDataSize = rawDataSize - (rawDataSize % frameSizeBytes)
        if (actualDataSize <= 0L) {
            return false
        }

        val targetFileLength = HEADER_SIZE + actualDataSize

        try {
            val currentHeader = readHeader(file)
            if (currentHeader.dataSizeBytes != actualDataSize || file.length() != targetFileLength) {
                // Pangkas trailing orphan byte jika proses terputus tepat di tengah 16-bit sample
                if (file.length() > targetFileLength) {
                    RandomAccessFile(file, "rw").use { raf ->
                        raf.setLength(targetFileLength)
                    }
                }
                updateHeaderDataSize(file, actualDataSize)
                return true
            }
        } catch (e: Exception) {
            // Jika header rusak parah atau belum lengkap, tulis ulang 44 byte pertama
            try {
                if (file.length() > targetFileLength) {
                    RandomAccessFile(file, "rw").use { raf ->
                        raf.setLength(targetFileLength)
                    }
                }
                val reconstructedHeader = createHeaderByteArray(
                    dataSizeBytes = actualDataSize,
                    sampleRate = AudioConfig.SAMPLE_RATE,
                    channels = AudioConfig.CHANNEL_COUNT,
                    bitsPerSample = AudioConfig.BYTES_PER_SAMPLE * 8
                )
                RandomAccessFile(file, "rw").use { raf ->
                    raf.seek(0)
                    raf.write(reconstructedHeader)
                }
                return true
            } catch (ignored: Exception) {
                return false
            }
        }

        return false
    }
}
