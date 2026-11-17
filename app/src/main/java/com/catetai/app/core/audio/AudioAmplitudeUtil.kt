package com.catetai.app.core.audio

import kotlin.math.sqrt

/**
 * AudioAmplitudeUtil — Utilitas kalkulasi amplitudo sinyal audio PCM 16-bit.
 *
 * Mengapa menggunakan RMS (Root Mean Square)?
 * ===========================================
 * Sinyal suara adalah gelombang bolak-balik (AC - Alternating Current) dengan nilai positif
 * dan negatif. Mengambil nilai rata-rata biasa akan selalu menghasilkan angka mendekati 0.
 *
 * Dua metode umum untuk visualisasi waveform:
 *   1. Peak Amplitude: Mengambil nilai sampel absolut tertinggi dalam satu jendela.
 *      Kelemahan: Sangat rentan terhadap letupan sesaat (*mic pop* / gesekan meja),
 *      sehingga waveform melompat ekstrem meskipun volume suara sebenarnya kecil.
 *   2. RMS (Root Mean Square): Menghitung rata-rata kuadrat dari seluruh sampel, lalu diakarkan.
 *      Keunggulan: Merepresentasikan energi suara rata-rata (*perceived loudness*) yang
 *      jauh lebih stabil, halus, dan cocok untuk visualizer gelombang suara di UI.
 *
 * Kalkulasi dilakukan murni di memori RAM tanpa alokasi objek baru (*zero garbage collection*),
 * sehingga sangat cepat (< 0.05 ms per 1024 byte) dan aman dipanggil 30+ kali per detik.
 */
object AudioAmplitudeUtil {

    /**
     * Menghitung nilai amplitudo RMS dari buffer PCM 16-bit bertanda (*signed Little-Endian*).
     *
     * @param buffer Array byte mentah dari AudioRecord.
     * @param offset Indeks byte awal (default: 0).
     * @param length Jumlah byte yang akan dievaluasi (harus kelipatan 2).
     * @return Amplitudo yang dinormalisasi ke rentang `0.0f` (hening) hingga `1.0f` (puncak maksimum).
     */
    fun calculateRms(buffer: ByteArray, offset: Int = 0, length: Int = buffer.size): Float {
        if (length < 2) return 0f
        require(offset >= 0 && offset + length <= buffer.size) {
            "Offset ($offset) + length ($length) di luar batas ukuran buffer (${buffer.size})"
        }

        val sampleCount = length / 2
        var sumSquares = 0.0

        var i = offset
        val end = offset + length
        while (i < end) {
            // Konversi 2 byte Little-Endian ke 16-bit short bertanda (-32768 .. 32767)
            val byteLow = buffer[i].toInt() and 0xFF
            val byteHigh = buffer[i + 1].toInt()
            val sample = ((byteHigh shl 8) or byteLow).toShort()

            val sampleDouble = sample.toDouble()
            sumSquares += sampleDouble * sampleDouble
            i += 2
        }

        val rms = sqrt(sumSquares / sampleCount)

        // Normalisasi terhadap nilai maksimum 16-bit (32.767)
        val normalized = (rms / 32767.0).toFloat()
        return normalized.coerceIn(0f, 1f)
    }
}
