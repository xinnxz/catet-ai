package com.catetai.app.core.audio

import android.content.Context
import android.media.AudioManager
import android.media.AudioRecord
import android.media.AudioRecordingConfiguration
import android.os.Build
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * AudioRecorderManager — Orkestrator perekaman suara latar belakang Catet AI.
 *
 * Menerapkan Pola Arsitektur Producer-Consumer (Anti-Overrun):
 * ============================================================
 * Masalah umum pada perekam audio mobile adalah fenomena *buffer overrun*:
 * Jika thread yang membaca AudioRecord juga bertugas menulis data ke disk dan memanggil
 * `fsync`, jeda I/O selama 50-200 ms (sangat umum pada storage eMMC HP kelas menengah)
 * akan membuat ring buffer hardware AudioRecord meluap, mengakibatkan audio terpotong
 * secara diam-diam (*silent gaps*).
 *
 * Solusi Catet AI:
 *   1. PRODUCER LOOP (High-Priority):
 *      - Hanya membaca sinyal dari mikrofon ([AudioRecordSource.read]).
 *      - Menghitung amplitudo RMS secara in-memory ([AudioAmplitudeUtil.calculateRms]).
 *      - Mengirimkan chunk 1024-byte ke [Channel] berpenyangga (kapasitas ~3 detik audio).
 *   2. CONSUMER LOOP (Dispatchers.IO):
 *      - Mengambil chunk dari Channel secara independen.
 *      - Menulis ke [PcmBufferWriter] yang menangani periodic flush dan `fsync` ke disk.
 *      - Jeda I/O disk tidak akan pernah menghambat loop pembacaan mikrofon.
 *
 * Deteksi Pembisuan Mikrofon (Android 10+ API 29+):
 * =================================================
 * Menggunakan [AudioManager.AudioRecordingCallback] untuk mendeteksi `isClientSilenced`.
 * Jika ada panggilan telepon masuk atau asisten suara mengambil alih mikrofon, aplikasi
 * segera mengetahui status tersebut tanpa mematikan proses perekaman.
 */
@Singleton
class AudioRecorderManager internal constructor(
    private val context: Context?,
    private val audioSourceFactory: AudioSourceFactory,
    private val ioDispatcher: CoroutineDispatcher,
    private val defaultDispatcher: CoroutineDispatcher,
    private val customAudioManager: AudioManager?
) : AudioRecorder {

    @Inject
    constructor(
        @ApplicationContext context: Context
    ) : this(
        context = context,
        audioSourceFactory = DefaultAudioSourceFactory(),
        ioDispatcher = Dispatchers.IO,
        defaultDispatcher = Dispatchers.Default,
        customAudioManager = null
    )

    /**
     * Constructor untuk unit testing di JVM murni (zero Android framework/emulator dependency).
     */
    constructor(
        audioSourceFactory: AudioSourceFactory,
        dispatcher: CoroutineDispatcher = Dispatchers.Default,
        customAudioManager: AudioManager? = null
    ) : this(
        context = null,
        audioSourceFactory = audioSourceFactory,
        ioDispatcher = dispatcher,
        defaultDispatcher = dispatcher,
        customAudioManager = customAudioManager
    )

    companion object {
        private const val TAG = "AudioRecorderManager"

        /**
         * Kapasitas antrean buffer Channel (96 chunk × 32 ms ≈ 3,07 detik audio).
         * Menyediakan ruang toleransi yang sangat lega jika disk I/O mengalami lag sesaat.
         */
        const val CHANNEL_CAPACITY: Int = 96
    }

    private val audioManager: AudioManager? by lazy {
        customAudioManager ?: context?.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
    }

    private val _isRecording = MutableStateFlow(false)
    override val isRecording: StateFlow<Boolean> = _isRecording.asStateFlow()

    private val _isSilenced = MutableStateFlow(false)
    override val isSilenced: StateFlow<Boolean> = _isSilenced.asStateFlow()

    private val _amplitudeFlow = MutableSharedFlow<Float>(replay = 1, extraBufferCapacity = 8)
    override val amplitudeFlow: SharedFlow<Float> = _amplitudeFlow.asSharedFlow()

    private val _durationMsFlow = MutableStateFlow(0L)
    override val durationMsFlow: StateFlow<Long> = _durationMsFlow.asStateFlow()

    private var activeSource: AudioRecordSource? = null
    private var activeWriter: PcmBufferWriter? = null
    private var activeOutputFile: File? = null

    private var producerJob: Job? = null
    private var consumerJob: Job? = null

    private var wasSilencedDuringSession: Boolean = false
    private var recordingCallback: Any? = null // AudioManager.AudioRecordingCallback pada API 29+

    @Synchronized
    override fun startRecording(outputFile: File): Result<Unit> {
        if (_isRecording.value) {
            return Result.failure(IllegalStateException("Perekaman sudah berjalan."))
        }

        return try {
            val source = audioSourceFactory.createSource()
            if (source.state != AudioRecord.STATE_INITIALIZED) {
                source.close()
                return Result.failure(
                    IOException("AudioRecord gagal diinisialisasi. Periksa izin RECORD_AUDIO atau konflik hardware.")
                )
            }

            val writer = PcmBufferWriter(outputFile)

            activeSource = source
            activeWriter = writer
            activeOutputFile = outputFile
            wasSilencedDuringSession = false

            _durationMsFlow.value = 0L
            _isSilenced.value = false

            // Mulai hardware capture
            source.startRecording()

            // Daftarkan listener pembisuan mikrofon untuk Android 10+ (API 29+)
            registerSilencedCallback(source.audioSessionId)

            // Buat Channel penyangga antara producer dan consumer
            val audioChannel = Channel<ByteArray>(capacity = CHANNEL_CAPACITY)

            val scope = CoroutineScope(defaultDispatcher)

            // 1. CONSUMER LOOP (Disk Writer pada Dispatchers.IO)
            consumerJob = scope.launch(ioDispatcher) {
                try {
                    for (chunk in audioChannel) {
                        writer.write(chunk)
                        _durationMsFlow.value = writer.durationMs
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Consumer error saat menulis audio ke disk", e)
                }
            }

            // 2. PRODUCER LOOP (High-Priority Reader)
            producerJob = scope.launch(defaultDispatcher) {
                val chunkBuffer = ByteArray(AudioConfig.VAD_CHUNK_BYTES)

                try {
                    while (isActive && _isRecording.value) {
                        val bytesRead = source.read(chunkBuffer, 0, chunkBuffer.size)
                        if (bytesRead > 0) {
                            // Salin byte aktual yang terbaca
                            val chunkCopy = chunkBuffer.copyOf(bytesRead)

                            // Hitung amplitudo RMS secara in-memory (murah, tanpa I/O)
                            val amplitude = AudioAmplitudeUtil.calculateRms(chunkCopy, 0, bytesRead)
                            _amplitudeFlow.tryEmit(amplitude)

                            // Kirim ke Channel
                            val sendResult = audioChannel.trySend(chunkCopy)
                            if (!sendResult.isSuccess) {
                                // Channel penuh -> Terjadi lonjakan I/O di disk.
                                // Gunakan suspending send agar tidak ada audio yang hilang.
                                Log.w(TAG, "Channel kapasitas penuh! Backpressure aktif mengantre data...")
                                audioChannel.send(chunkCopy)
                            }
                        } else if (bytesRead < 0) {
                            Log.e(TAG, "AudioRecord mengembalikan kode error: $bytesRead")
                            break
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Producer error saat membaca mikrofon", e)
                } finally {
                    audioChannel.close()
                }
            }

            _isRecording.value = true
            Result.success(Unit)
        } catch (e: Exception) {
            cleanupResources()
            Result.failure(e)
        }
    }

    @Synchronized
    override fun stopRecording(): Result<RecordingResult> {
        if (!_isRecording.value) {
            return Result.failure(IllegalStateException("Perekaman sedang tidak aktif."))
        }

        _isRecording.value = false

        return try {
            // Hentikan penangkapan hardware lebih dulu
            activeSource?.stop()
            unregisterSilencedCallback()

            // Tunggu hingga producer dan consumer selesai mengosongkan antrean channel
            runBlocking {
                producerJob?.join()
                consumerJob?.join()
            }

            val writer = activeWriter ?: throw IllegalStateException("PcmBufferWriter null saat stop.")
            val finalDurationMs = writer.durationMs
            val file = activeOutputFile ?: writer.targetFile

            // Tutup writer (otomatis memfinalisasi WAV header di offset 4 & 40)
            writer.close()
            activeSource?.close()

            val result = RecordingResult(
                file = file,
                durationMs = finalDurationMs,
                fileSizeBytes = file.length(),
                wasSilencedBySystem = wasSilencedDuringSession
            )

            cleanupResources()
            Result.success(result)
        } catch (e: Exception) {
            cleanupResources()
            Result.failure(e)
        }
    }

    private fun registerSilencedCallback(targetSessionId: Int) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            try {
                val callback = object : AudioManager.AudioRecordingCallback() {
                    override fun onRecordingConfigChanged(configs: List<AudioRecordingConfiguration>) {
                        for (config in configs) {
                            if (config.clientAudioSessionId == targetSessionId) {
                                val silenced = config.isClientSilenced
                                _isSilenced.value = silenced
                                if (silenced) {
                                    wasSilencedDuringSession = true
                                    Log.w(TAG, "Mikrofon terdeteksi dibisukan oleh OS (panggilan/asisten aktif)")
                                }
                            }
                        }
                    }
                }
                audioManager?.registerAudioRecordingCallback(callback, null)
                recordingCallback = callback
            } catch (e: Exception) {
                Log.w(TAG, "Gagal mendaftarkan AudioRecordingCallback", e)
            }
        }
    }

    private fun unregisterSilencedCallback() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val callback = recordingCallback as? AudioManager.AudioRecordingCallback
            if (callback != null) {
                try {
                    audioManager?.unregisterAudioRecordingCallback(callback)
                } catch (ignored: Exception) {
                }
            }
        }
        recordingCallback = null
    }

    private fun cleanupResources() {
        try {
            activeWriter?.close()
        } catch (ignored: Exception) {
        }
        try {
            activeSource?.close()
        } catch (ignored: Exception) {
        }

        activeWriter = null
        activeSource = null
        activeOutputFile = null
        producerJob = null
        consumerJob = null
    }
}
