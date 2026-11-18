package com.catetai.app.engine.player

import android.content.Context
import android.media.MediaPlayer
import com.catetai.app.core.crypto.AudioEncryptor
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream
import javax.inject.Inject
import javax.inject.Singleton

/**
 * State pemutar audio yang diamati oleh Compose UI.
 *
 * @param isPlaying           Apakah audio sedang diputar sekarang.
 * @param currentPositionMs   Posisi pemutaran saat ini dalam milidetik.
 * @param durationMs          Total durasi audio dalam milidetik.
 * @param playbackSpeed       Kecepatan pemutaran saat ini (0.5x, 1.0x, 1.5x, 2.0x).
 * @param isReady             Apakah MediaPlayer sudah siap memutar (setelah prepared).
 * @param error               Pesan error jika ada masalah.
 */
data class PlayerState(
    val isPlaying: Boolean = false,
    val currentPositionMs: Int = 0,
    val durationMs: Int = 0,
    val playbackSpeed: Float = 1.0f,
    val isReady: Boolean = false,
    val error: String? = null
)

/**
 * AudioPlayerManager — Pengelola pemutaran audio terenkripsi.
 *
 * # Arsitektur
 * Class ini membungkus Android [MediaPlayer] dengan lifecycle yang aman:
 * - `prepare()`: Mendekripsi berkas `.enc` ke file cache sementara, lalu inisialisasi MediaPlayer.
 * - `play()`/`pause()`: Kontrol pemutaran standar.
 * - `seekTo()`: Lompat ke posisi tertentu (digunakan saat pengguna mengetuk baris transkrip).
 * - `setSpeed()`: Mengubah kecepatan pemutaran menggunakan [MediaPlayer.setPlaybackParams].
 * - `release()`: Membebaskan semua resource dan menghapus file cache sementara.
 *
 * # Alur Dekripsi On-Demand
 * File audio yang tersimpan di penyimpanan internal selalu dalam bentuk terenkripsi (`.enc`).
 * Sebelum bisa diputar, file harus didekripsi ke direktori cache (`cacheDir/playback/`) terlebih dulu.
 * File cache ini bersifat **sementara** — dihapus otomatis saat `release()` dipanggil.
 *
 * # Pembaruan Posisi Real-Time
 * Coroutine `positionUpdateJob` berjalan selama audio diputar, memperbarui [playerState.currentPositionMs]
 * setiap 200ms agar seek bar di UI bisa bergerak mulus.
 */
@Singleton
class AudioPlayerManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val audioEncryptor: AudioEncryptor
) {

    private val _playerState = MutableStateFlow(PlayerState())
    val playerState: StateFlow<PlayerState> = _playerState.asStateFlow()

    private var mediaPlayer: MediaPlayer? = null
    private var playbackCacheFile: File? = null
    private val managerScope = CoroutineScope(Dispatchers.Main)
    private var positionUpdateJob: Job? = null

    /**
     * Mempersiapkan pemutar untuk rekaman tertentu.
     *
     * Alur:
     * 1. Jika sudah ada pemutar aktif, bebaskan dulu (release).
     * 2. Dekripsi file `.enc` ke direktori cache sementara.
     * 3. Inisialisasi MediaPlayer dengan file cache yang sudah didekripsi.
     * 4. Setelah prepared, perbarui state [PlayerState.isReady] = true.
     *
     * @param encryptedAudioFile   Berkas audio terenkripsi (`.enc`) di penyimpanan internal.
     * @param associatedData       Data asosiasi Tink untuk verifikasi dekripsi (biasanya kosong).
     */
    suspend fun prepare(encryptedAudioFile: File, associatedData: ByteArray = ByteArray(0)) {
        // 1. Bersihkan state dan resource sebelumnya
        release()
        _playerState.update { PlayerState() }

        if (!encryptedAudioFile.exists()) {
            _playerState.update { it.copy(error = "Berkas audio tidak ditemukan: ${encryptedAudioFile.name}") }
            return
        }

        // 2. Dekripsi ke file cache sementara di background thread
        val cacheDir = File(context.cacheDir, "playback").also { it.mkdirs() }
        val cacheFile = File(cacheDir, "playback_${System.currentTimeMillis()}.tmp")

        val decryptResult = kotlinx.coroutines.withContext(Dispatchers.IO) {
            runCatching {
                FileOutputStream(cacheFile).use { fos ->
                    audioEncryptor.decrypt(encryptedAudioFile, fos, associatedData)
                        .getOrThrow()
                }
                cacheFile
            }
        }

        if (decryptResult.isFailure) {
            cacheFile.delete()
            _playerState.update {
                it.copy(error = "Gagal mendekripsi audio: ${decryptResult.exceptionOrNull()?.localizedMessage}")
            }
            return
        }

        playbackCacheFile = cacheFile

        // 3. Inisialisasi MediaPlayer dengan file cache
        try {
            val player = MediaPlayer().apply {
                setDataSource(cacheFile.absolutePath)
                setOnPreparedListener { mp ->
                    _playerState.update {
                        it.copy(
                            isReady = true,
                            durationMs = mp.duration.coerceAtLeast(0)
                        )
                    }
                }
                setOnCompletionListener {
                    positionUpdateJob?.cancel()
                    _playerState.update { state ->
                        state.copy(isPlaying = false, currentPositionMs = state.durationMs)
                    }
                }
                setOnErrorListener { _, what, extra ->
                    _playerState.update { it.copy(error = "MediaPlayer error: what=$what extra=$extra") }
                    true
                }
                prepare() // Synchronous prepare — aman karena file lokal
            }
            mediaPlayer = player
        } catch (e: Exception) {
            _playerState.update { it.copy(error = "Gagal memulai pemutar: ${e.localizedMessage}") }
        }
    }

    /** Mulai atau lanjutkan pemutaran audio. */
    fun play() {
        val player = mediaPlayer ?: return
        if (!_playerState.value.isReady) return
        player.start()
        _playerState.update { it.copy(isPlaying = true) }
        startPositionUpdates()
    }

    /** Jeda pemutaran audio. */
    fun pause() {
        val player = mediaPlayer ?: return
        if (player.isPlaying) player.pause()
        positionUpdateJob?.cancel()
        _playerState.update { it.copy(isPlaying = false) }
    }

    /** Toggle antara play dan pause. */
    fun togglePlayPause() {
        if (_playerState.value.isPlaying) pause() else play()
    }

    /**
     * Lompat ke posisi waktu tertentu dalam audio.
     *
     * Digunakan saat pengguna mengetuk baris segmen di Tab Transkrip.
     * Seek dilakukan secara async (non-blocking) di Android.
     *
     * @param positionMs Posisi target dalam milidetik.
     */
    fun seekTo(positionMs: Int) {
        val player = mediaPlayer ?: return
        player.seekTo(positionMs)
        _playerState.update { it.copy(currentPositionMs = positionMs) }
    }

    /**
     * Mengubah kecepatan pemutaran audio.
     *
     * Menggunakan [MediaPlayer.setPlaybackParams] yang tersedia sejak API 23 (Marshmallow).
     * Kecepatan yang didukung: 0.5x, 1.0x, 1.5x, 2.0x.
     *
     * @param speed Kecepatan baru (0.5f, 1.0f, 1.5f, 2.0f).
     */
    fun setSpeed(speed: Float) {
        val player = mediaPlayer ?: return
        try {
            val wasPlaying = player.isPlaying
            val params = player.playbackParams.setSpeed(speed)
            player.playbackParams = params
            _playerState.update { it.copy(playbackSpeed = speed) }
            // Mempertahankan status play/pause setelah ganti kecepatan
            if (!wasPlaying) player.pause()
        } catch (e: Exception) {
            // Beberapa perangkat lawas mungkin tidak mendukung kecepatan non-1x
        }
    }

    /**
     * Membebaskan semua resource MediaPlayer dan menghapus file cache sementara.
     *
     * WAJIB dipanggil di `ViewModel.onCleared()` atau saat navigasi meninggalkan DetailScreen
     * untuk mencegah kebocoran memori (memory leak) dan file cache menumpuk.
     */
    fun release() {
        positionUpdateJob?.cancel()
        positionUpdateJob = null

        mediaPlayer?.apply {
            if (isPlaying) stop()
            reset()
            release()
        }
        mediaPlayer = null

        playbackCacheFile?.delete()
        playbackCacheFile = null

        _playerState.update { PlayerState() }
    }

    // ─── Internal ─────────────────────────────────────────────────────────────

    /**
     * Meluncurkan coroutine yang memperbarui posisi pemutaran setiap 200ms.
     *
     * Interval 200ms memberikan keseimbangan antara responsivitas UI dan
     * konsumsi baterai. Seek bar akan tampak bergerak cukup mulus di mata pengguna.
     */
    private fun startPositionUpdates() {
        positionUpdateJob?.cancel()
        positionUpdateJob = managerScope.launch {
            while (isActive) {
                val player = mediaPlayer
                if (player != null && player.isPlaying) {
                    _playerState.update { it.copy(currentPositionMs = player.currentPosition) }
                }
                delay(200L)
            }
        }
    }
}
