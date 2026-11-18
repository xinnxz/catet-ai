package com.catetai.app.legacy.recorder

import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import java.io.File

/**
 * Hasil metadata setelah proses perekaman dihentikan secara normal.
 *
 * @param file File WAV yang tersimpan di disk.
 * @param durationMs Durasi audio dalam milidetik.
 * @param fileSizeBytes Ukuran fisik file dalam byte.
 * @param wasSilencedBySystem Bernilai true jika selama merekam mikrofon sempat dibisukan oleh OS
 *                            (misalnya karena ada panggilan masuk atau asisten suara).
 */
data class RecordingResult(
    val file: File,
    val durationMs: Long,
    val fileSizeBytes: Long,
    val wasSilencedBySystem: Boolean = false
)

/**
 * Kontrak antarmuka perekam audio Catet AI.
 */
interface AudioRecorder {

    /**
     * Menandakan apakah perekaman sedang berjalan aktif.
     */
    val isRecording: StateFlow<Boolean>

    /**
     * Menandakan apakah mikrofon saat ini sedang dibisukan oleh sistem operasi
     * (AudioRecordingCallback - isClientSilenced).
     */
    val isSilenced: StateFlow<Boolean>

    /**
     * Aliran nilai amplitudo suara real-time (0.0 - 1.0) untuk visualizer waveform di UI.
     */
    val amplitudeFlow: SharedFlow<Float>

    /**
     * Aliran durasi perekaman yang sedang berjalan (dalam milidetik).
     */
    val durationMsFlow: StateFlow<Long>

    /**
     * Memulai proses perekaman audio dan menyimpannya ke [outputFile].
     *
     * @return [Result.success] jika perekaman berhasil dimulai, atau [Result.failure] jika gagal.
     */
    fun startRecording(outputFile: File): Result<Unit>

    /**
     * Menghentikan perekaman audio, memfinalisasi file WAV, dan mengembalikan [RecordingResult].
     */
    fun stopRecording(): Result<RecordingResult>
}
