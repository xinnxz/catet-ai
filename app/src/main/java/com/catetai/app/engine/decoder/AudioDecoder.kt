package com.catetai.app.engine.decoder

import java.io.File

/**
 * Interface dekoder audio untuk Catet AI.
 *
 * Bertanggung jawab mengubah berkas audio dari berbagai format (M4A, MP3, WAV, OGG, FLAC, OPUS)
 * ke dalam format PCM 16kHz 16-bit Mono (Little-Endian) mentah tanpa header.
 *
 * # Mengapa PCM 16kHz 16-bit Mono?
 * 1. **16kHz**: Model Whisper dilatih secara eksklusif pada sample rate 16.000 Hz.
 *    Memberikan input dengan sample rate berbeda akan menurunkan akurasi transkripsi secara drastis.
 * 2. **Mono**: Whisper tidak menggunakan informasi spasial stereo; kanal ganda hanya akan
 *    menggandakan beban memori dan komputasi tanpa memberikan peningkatan akurasi.
 * 3. **16-bit**: Standar kedalaman bit PCM linier terkompresi yang dibutuhkan oleh whisper.cpp JNI.
 */
interface AudioDecoder {

    /**
     * Mendekode berkas audio masukan menjadi berkas PCM 16kHz 16-bit Mono mentah.
     *
     * @param inputFile Berkas audio sumber (misal: berkas rekaman M4A, MP3, WAV dari luar).
     * @param outputFile Berkas target untuk menampung data PCM hasil dekoding.
     * @param onProgress Callback pemantau progres dari 0.0f (mulai) hingga 1.0f (selesai).
     * @return [Result.success] dengan [outputFile] jika proses sukses, atau [Result.failure] jika gagal.
     */
    suspend fun decode(
        inputFile: File,
        outputFile: File,
        onProgress: (Float) -> Unit = {}
    ): Result<File>

    /**
     * Memeriksa apakah format audio (berdasarkan MIME type) didukung oleh dekoder ini.
     *
     * @param mimeType MIME type berkas, misalnya "audio/mp4", "audio/mpeg", "audio/ogg".
     * @return `true` jika dekoder sanggup memproses format tersebut, `false` jika tidak.
     */
    fun supportsFormat(mimeType: String): Boolean
}
