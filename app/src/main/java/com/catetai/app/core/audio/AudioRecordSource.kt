package com.catetai.app.core.audio

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder

/**
 * Abstraksi sumber data rekaman mikrofon untuk memisahkan implementasi hardware Android
 * dari logika orkestrasi Producer-Consumer (sehingga memungkinkan unit testing di JVM).
 */
interface AudioRecordSource : AutoCloseable {

    /**
     * ID sesi audio dari Android framework (dipakai untuk mencocokkan AudioRecordingCallback).
     */
    val audioSessionId: Int

    /**
     * Status inisialisasi AudioRecord (AudioRecord.STATE_INITIALIZED = 1).
     */
    val state: Int

    /**
     * Memulai penangkapan sinyal audio dari hardware mikrofon.
     */
    fun startRecording()

    /**
     * Menghentikan penangkapan sinyal audio dari hardware.
     */
    fun stop()

    /**
     * Membaca chunk byte audio PCM ke dalam [audioData].
     *
     * @return Jumlah byte yang berhasil dibaca, atau kode error negatif dari AudioRecord.
     */
    fun read(audioData: ByteArray, offsetInBytes: Int, sizeInBytes: Int): Int

    override fun close()
}

/**
 * Factory untuk membuat instance [AudioRecordSource].
 */
interface AudioSourceFactory {
    fun createSource(
        sampleRate: Int = AudioConfig.SAMPLE_RATE,
        channelConfig: Int = AudioFormat.CHANNEL_IN_MONO,
        audioEncoding: Int = AudioFormat.ENCODING_PCM_16BIT,
        audioSource: Int = MediaRecorder.AudioSource.MIC
    ): AudioRecordSource
}

/**
 * Implementasi produksi yang menggunakan hardware [AudioRecord] Android.
 */
class AndroidAudioRecordSource(
    private val audioRecord: AudioRecord
) : AudioRecordSource {

    override val audioSessionId: Int
        get() = audioRecord.audioSessionId

    override val state: Int
        get() = audioRecord.state

    override fun startRecording() {
        audioRecord.startRecording()
    }

    override fun stop() {
        try {
            audioRecord.stop()
        } catch (ignored: Exception) {
        }
    }

    override fun read(audioData: ByteArray, offsetInBytes: Int, sizeInBytes: Int): Int {
        return audioRecord.read(audioData, offsetInBytes, sizeInBytes)
    }

    override fun close() {
        try {
            stop()
        } finally {
            audioRecord.release()
        }
    }
}

/**
 * Factory standar untuk aplikasi Android pada runtime normal.
 */
class DefaultAudioSourceFactory : AudioSourceFactory {

    @SuppressLint("MissingPermission")
    override fun createSource(
        sampleRate: Int,
        channelConfig: Int,
        audioEncoding: Int,
        audioSource: Int
    ): AudioRecordSource {
        val minBufferSize = AudioRecord.getMinBufferSize(sampleRate, channelConfig, audioEncoding)
        val bufferSize = if (minBufferSize > 0) minBufferSize * 2 else AudioConfig.DEFAULT_BUFFER_SIZE_BYTES

        val record = AudioRecord(
            audioSource,
            sampleRate,
            channelConfig,
            audioEncoding,
            bufferSize
        )

        return AndroidAudioRecordSource(record)
    }
}
