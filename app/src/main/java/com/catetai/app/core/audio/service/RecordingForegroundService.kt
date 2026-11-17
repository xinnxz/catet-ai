package com.catetai.app.core.audio.service

import android.Manifest
import android.app.NotificationManager
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.BatteryManager
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.util.Log
import androidx.core.content.ContextCompat
import com.catetai.app.core.audio.AudioConfig
import com.catetai.app.core.audio.AudioRecorder
import com.catetai.app.core.audio.WavHeaderUtil
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject

/**
 * RecordingForegroundService — Pengelola foreground service audio Catet AI.
 *
 * Sesuai standar Android 14+ (API 34) & Android 12+ (API 31):
 * ==========================================================
 * 1. Menjalankan foreground service dengan `foregroundServiceType="microphone"`.
 * 2. Mempertahankan CPU tetap aktif saat layar mati dengan [PowerManager.PARTIAL_WAKE_LOCK].
 * 3. Menampilkan notifikasi persisten dengan durasi real-time dan tombol Stop.
 * 4. Kepatuhan Android 12+ (API 31+) Background Mic Restriction:
 *    - Di Android 12+, background service dilarang memulai penangkapan mikrofon baru.
 *    - Menggunakan [START_NOT_STICKY] agar sistem tidak mencoba membangkitkan service
 *      secara liar di latar belakang tanpa inisiasi pengguna.
 *    - Jika service terbunuh secara mendadak (LMK/Crash), saat menyala kembali service
 *      mendeteksi sisa file yang belum difinalisasi melalui [RecordingSessionStore] dan
 *      memperbaikinya menggunakan [WavHeaderUtil.recoverUnfinalizedHeader], lalu berhenti aman.
 * 5. Pemantauan Daya & Disk:
 *    - Level baterai dipantau via [Intent.ACTION_BATTERY_CHANGED]:
 *      * Peringatan pada <= 5% ([AudioConfig.LOW_BATTERY_WARNING_PERCENT]).
 *      * Auto-stop aman pada <= 2% ([AudioConfig.CRITICAL_BATTERY_PERCENT]).
 *    - Ruang disk dipantau secara berkala:
 *      * Peringatan pada <= 100 MB ([AudioConfig.LOW_DISK_WARNING_BYTES]).
 *      * Auto-stop aman pada <= 50 MB ([AudioConfig.MIN_DISK_SPACE_BYTES]).
 * 6. Deteksi Pembisuan Mikrofon:
 *    - Memantau [AudioRecorder.isSilenced] dari AudioRecordingCallback (API 29+).
 *    - Memperbarui judul notifikasi secara dinamis jika mikrofon direbut aplikasi lain.
 */
@AndroidEntryPoint
class RecordingForegroundService : Service() {

    @Inject
    lateinit var audioRecorder: AudioRecorder

    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var tickerJob: Job? = null
    private var silenceObserverJob: Job? = null

    private var wakeLock: PowerManager.WakeLock? = null
    private var sessionStore: RecordingSessionStore? = null

    private var activeOutputFile: File? = null
    private var isBatteryReceiverRegistered = false
    private var isLowBattery = false
    private var isLowDisk = false
    private var isSilenced = false
    private var currentDurationMs = 0L

    private val batteryReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == Intent.ACTION_BATTERY_CHANGED) {
                val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
                val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
                if (level >= 0 && scale > 0) {
                    val pct = (level * 100) / scale
                    handleBatteryUpdate(pct)
                }
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        Log.i(TAG, "RecordingForegroundService created")
        RecordingNotificationHelper.createNotificationChannel(this)
        sessionStore = RecordingSessionStore(this)
        registerBatteryReceiver()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action

        if (intent == null || action == null) {
            // Service di-restart oleh sistem setelah process kill tanpa Intent eksplisit.
            // Android 12+ melarang merekam audio dari background tanpa interaksi user.
            handleSystemInterruptedRestart()
            return START_NOT_STICKY
        }

        when (action) {
            ACTION_START -> {
                val filePath = intent.getStringExtra(EXTRA_OUTPUT_FILE_PATH)
                val targetFile = if (!filePath.isNullOrBlank()) {
                    File(filePath)
                } else {
                    generateDefaultOutputFile()
                }
                startRecordingSession(targetFile)
            }
            ACTION_STOP -> {
                stopRecordingSession(StopReason.USER_REQUESTED)
            }
            else -> {
                Log.w(TAG, "Aksi tidak dikenal: $action")
            }
        }

        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        Log.i(TAG, "RecordingForegroundService destroyed")
        cleanupResources()
        serviceScope.cancel()

        if (_serviceState.value is ServiceRecordingState.Recording) {
            try {
                audioRecorder.stopRecording()
            } catch (e: Exception) {
                Log.e(TAG, "Error saat menutup audioRecorder di onDestroy", e)
            }
            _serviceState.value = ServiceRecordingState.Stopped(null, StopReason.SYSTEM_INTERRUPTED)
        }

        super.onDestroy()
    }

    /**
     * Memulai alur perekaman audio foreground secara penuh.
     */
    private fun startRecordingSession(outputFile: File) {
        if (_serviceState.value is ServiceRecordingState.Recording) {
            Log.w(TAG, "Sesi perekaman sudah berjalan aktif, abaikan permintaan START baru.")
            return
        }

        // 1. Verifikasi izin RECORD_AUDIO
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED
        ) {
            Log.e(TAG, "Gagal memulai service: Izin RECORD_AUDIO belum diberikan.")
            _serviceState.value = ServiceRecordingState.Error(
                "Izin mikrofon (RECORD_AUDIO) belum diberikan."
            )
            stopSelf()
            return
        }

        // 2. Siapkan direktori penyimpanan file
        outputFile.parentFile?.mkdirs()
        activeOutputFile = outputFile

        // 3. Bangun notifikasi persisten & panggil startForeground sesuai Android 14+
        val initialNotification = RecordingNotificationHelper.buildRecordingNotification(
            context = this,
            durationMs = 0L,
            isSilenced = false,
            isLowBattery = false,
            isLowDisk = false
        )

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(
                    RecordingNotificationHelper.NOTIFICATION_ID_RECORDING,
                    initialNotification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
                )
            } else {
                startForeground(
                    RecordingNotificationHelper.NOTIFICATION_ID_RECORDING,
                    initialNotification
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Gagal memanggil startForeground", e)
            _serviceState.value = ServiceRecordingState.Error(
                "Gagal mengaktifkan foreground service: ${e.message}",
                e
            )
            stopSelf()
            return
        }

        // 4. Kunci WakeLock agar CPU tidak tertidur saat layar HP mati
        acquireWakeLock()

        // 5. Simpan path sesi aktif untuk pemulihan jika terjadi crash mendadak
        sessionStore?.saveActiveSession(outputFile.absolutePath)

        // 6. Jalankan perekaman audio
        val startResult = audioRecorder.startRecording(outputFile)
        if (startResult.isFailure) {
            val error = startResult.exceptionOrNull()
            Log.e(TAG, "Gagal memulai perekaman di AudioRecorderManager", error)
            _serviceState.value = ServiceRecordingState.Error(
                message = "Gagal memulai AudioRecorder: ${error?.message}",
                cause = error
            )
            cleanupResources()
            stopForegroundCompat()
            stopSelf()
            return
        }

        // 7. Update status reaktif
        _serviceState.value = ServiceRecordingState.Recording(
            file = outputFile,
            durationMs = 0L,
            isSilenced = false,
            isLowBattery = isLowBattery,
            isLowDisk = isLowDisk
        )

        // 8. Luncurkan coroutine pemantauan durasi, disk, dan pembisuan
        startMonitoringLoops(outputFile)
    }

    /**
     * Memulai pemantauan background untuk timer durasi, kapasitas disk, dan interupsi mikrofon.
     */
    private fun startMonitoringLoops(outputFile: File) {
        var tickCounter = 0

        tickerJob?.cancel()
        tickerJob = serviceScope.launch {
            while (true) {
                delay(1000L)
                tickCounter++

                currentDurationMs = audioRecorder.durationMsFlow.value

                // Cek kapasitas disk setiap 5 detik
                if (tickCounter % 5 == 0) {
                    checkDiskSpace(outputFile)
                }

                // Perbarui notifikasi dengan waktu berjalan
                updateNotification()

                // Perbarui StateFlow
                val currentState = _serviceState.value
                if (currentState is ServiceRecordingState.Recording) {
                    _serviceState.value = currentState.copy(
                        durationMs = currentDurationMs,
                        isSilenced = isSilenced,
                        isLowBattery = isLowBattery,
                        isLowDisk = isLowDisk
                    )
                }
            }
        }

        silenceObserverJob?.cancel()
        silenceObserverJob = serviceScope.launch {
            audioRecorder.isSilenced.collect { silenced ->
                if (isSilenced != silenced) {
                    isSilenced = silenced
                    updateNotification()
                    val currentState = _serviceState.value
                    if (currentState is ServiceRecordingState.Recording) {
                        _serviceState.value = currentState.copy(isSilenced = silenced)
                    }
                }
            }
        }
    }

    /**
     * Menghentikan sesi perekaman audio secara aman dan memfinalisasi berkas audio.
     */
    private fun stopRecordingSession(reason: StopReason) {
        Log.i(TAG, "Menghentikan sesi perekaman audio. Alasan: $reason")

        tickerJob?.cancel()
        silenceObserverJob?.cancel()

        // Hentikan penulisan audio dan finalisasi WAV header
        val stopResult = audioRecorder.stopRecording()

        // Bersihkan sesi aktif dari persistent storage
        sessionStore?.clearActiveSession()

        // Lepas kuncian WakeLock dan batalkan receiver
        cleanupResources()

        val recordingResult = stopResult.getOrNull()
        _serviceState.value = ServiceRecordingState.Stopped(
            result = recordingResult,
            reason = reason
        )

        // Hentikan status foreground dan notifikasi
        stopForegroundCompat()
        stopSelf()
    }

    /**
     * Memeriksa kondisi baterai dan mengeksekusi peringatan atau auto-stop.
     */
    internal fun handleBatteryUpdate(batteryPct: Int) {
        when (RecordingThresholdEvaluator.evaluateBattery(batteryPct)) {
            RecordingThresholdEvaluator.BatteryStatus.CRITICAL_STOP -> {
                Log.w(TAG, "Level baterai kritis ($batteryPct%). Menghentikan perekaman otomatis...")
                stopRecordingSession(StopReason.CRITICAL_BATTERY)
                showAlertNotification(
                    title = "Catet AI — Perekaman Dihentikan",
                    message = "Perekaman dihentikan otomatis: Baterai kritis (≤$batteryPct%). File tersimpan."
                )
            }
            RecordingThresholdEvaluator.BatteryStatus.LOW_WARNING -> {
                if (!isLowBattery) {
                    Log.w(TAG, "Peringatan baterai lemah: $batteryPct%")
                    isLowBattery = true
                    updateNotification()
                }
            }
            RecordingThresholdEvaluator.BatteryStatus.NORMAL -> {
                if (isLowBattery) {
                    isLowBattery = false
                    updateNotification()
                }
            }
        }
    }

    /**
     * Memeriksa kapasitas ruang penyimpanan tersisa pada disk.
     */
    internal fun checkDiskSpace(outputFile: File) {
        val usableSpace = outputFile.parentFile?.usableSpace ?: filesDir.usableSpace

        when (RecordingThresholdEvaluator.evaluateDiskSpace(usableSpace)) {
            RecordingThresholdEvaluator.DiskStatus.CRITICAL_STOP -> {
                val spaceMb = usableSpace / (1024 * 1024)
                Log.w(TAG, "Ruang penyimpanan kritis ($spaceMb MB tersisa). Menghentikan perekaman otomatis...")
                stopRecordingSession(StopReason.CRITICAL_DISK_SPACE)
                showAlertNotification(
                    title = "Catet AI — Perekaman Dihentikan",
                    message = "Ruang penyimpanan hampir penuh (<50MB). File audio berhasil diamankan."
                )
            }
            RecordingThresholdEvaluator.DiskStatus.LOW_WARNING -> {
                if (!isLowDisk) {
                    Log.w(TAG, "Peringatan ruang penyimpanan disk menipis (<=100MB).")
                    isLowDisk = true
                    updateNotification()
                }
            }
            RecordingThresholdEvaluator.DiskStatus.NORMAL -> {
                if (isLowDisk) {
                    isLowDisk = false
                    updateNotification()
                }
            }
        }
    }

    /**
     * Menangani kondisi saat service di-restart oleh OS setelah force-stop/LMK.
     * Android 12+ melarang memulai capture mikrofon dari background, sehingga
     * tugas utama di sini adalah memulihkan file audio yang terpotong dan keluar aman.
     */
    private fun handleSystemInterruptedRestart() {
        Log.w(TAG, "Service di-restart oleh OS tanpa intent. Memeriksa sisa file untuk dipulihkan...")
        val interruptedPath = sessionStore?.getActiveSessionPath()
        if (!interruptedPath.isNullOrBlank()) {
            val file = File(interruptedPath)
            if (file.exists() && file.length() > AudioConfig.WAV_HEADER_SIZE_BYTES) {
                try {
                    val recoveredBytes = WavHeaderUtil.recoverUnfinalizedHeader(file)
                    Log.i(TAG, "Berhasil memulihkan file WAV terpotong ($recoveredBytes byte): ${file.name}")
                    showAlertNotification(
                        title = "Catet AI — Rekaman Terputus Dipulihkan",
                        message = "Perekaman terhenti oleh sistem operasi. File audio ${file.name} telah dipulihkan."
                    )
                } catch (e: Exception) {
                    Log.e(TAG, "Gagal memulihkan file WAV yang terpotong", e)
                }
            }
            sessionStore?.clearActiveSession()
        }

        _serviceState.value = ServiceRecordingState.Stopped(
            result = null,
            reason = StopReason.SYSTEM_INTERRUPTED
        )
        stopSelf()
    }

    private fun updateNotification() {
        if (_serviceState.value !is ServiceRecordingState.Recording) return

        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
        val updatedNotification = RecordingNotificationHelper.buildRecordingNotification(
            context = this,
            durationMs = currentDurationMs,
            isSilenced = isSilenced,
            isLowBattery = isLowBattery,
            isLowDisk = isLowDisk
        )
        notificationManager?.notify(
            RecordingNotificationHelper.NOTIFICATION_ID_RECORDING,
            updatedNotification
        )
    }

    private fun showAlertNotification(title: String, message: String) {
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
        val alertNotification = RecordingNotificationHelper.buildAlertNotification(
            context = this,
            title = title,
            message = message
        )
        notificationManager?.notify(
            RecordingNotificationHelper.NOTIFICATION_ID_ALERT,
            alertNotification
        )
    }

    private fun acquireWakeLock() {
        try {
            if (wakeLock == null) {
                val powerManager = getSystemService(Context.POWER_SERVICE) as? PowerManager
                wakeLock = powerManager?.newWakeLock(
                    PowerManager.PARTIAL_WAKE_LOCK,
                    "CatetAI:RecordingWakeLock"
                )?.apply {
                    setReferenceCounted(false)
                }
            }
            if (wakeLock?.isHeld == false) {
                // Batas keselamatan 12 jam (mencegah battery drain ekstrem jika app terlupakan)
                wakeLock?.acquire(12 * 60 * 60 * 1000L)
                Log.d(TAG, "WakeLock berhasil diakuisisi")
            }
        } catch (e: Exception) {
            Log.w(TAG, "Gagal mengakuisisi WakeLock", e)
        }
    }

    private fun releaseWakeLock() {
        try {
            if (wakeLock?.isHeld == true) {
                wakeLock?.release()
                Log.d(TAG, "WakeLock berhasil dilepas")
            }
        } catch (e: Exception) {
            Log.w(TAG, "Gagal melepaskan WakeLock", e)
        } finally {
            wakeLock = null
        }
    }

    private fun registerBatteryReceiver() {
        if (!isBatteryReceiverRegistered) {
            val filter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
            registerReceiver(batteryReceiver, filter)
            isBatteryReceiverRegistered = true
        }
    }

    private fun unregisterBatteryReceiver() {
        if (isBatteryReceiverRegistered) {
            try {
                unregisterReceiver(batteryReceiver)
            } catch (e: Exception) {
                Log.w(TAG, "Gagal unregister batteryReceiver", e)
            }
            isBatteryReceiverRegistered = false
        }
    }

    private fun cleanupResources() {
        releaseWakeLock()
        unregisterBatteryReceiver()
    }

    private fun stopForegroundCompat() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } else {
            @Suppress("DEPRECATION")
            stopForeground(true)
        }
    }

    private fun generateDefaultOutputFile(): File {
        val recordingsDir = File(filesDir, "recordings").apply { mkdirs() }
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        return File(recordingsDir, "rec_$timestamp.wav")
    }

    companion object {
        private const val TAG = "RecordingService"

        const val ACTION_START = "com.catetai.app.action.START_RECORDING"
        const val ACTION_STOP = "com.catetai.app.action.STOP_RECORDING"
        const val EXTRA_OUTPUT_FILE_PATH = "com.catetai.app.extra.OUTPUT_FILE_PATH"

        private val _serviceState = MutableStateFlow<ServiceRecordingState>(ServiceRecordingState.Idle)

        /**
         * Aliran state status perekaman yang dapat diobservasi oleh antarmuka Compose / ViewModel.
         */
        val serviceState: StateFlow<ServiceRecordingState> = _serviceState.asStateFlow()

        /**
         * Memulai [RecordingForegroundService].
         */
        fun start(context: Context, outputFile: File? = null) {
            val intent = Intent(context, RecordingForegroundService::class.java).apply {
                action = ACTION_START
                outputFile?.let { putExtra(EXTRA_OUTPUT_FILE_PATH, it.absolutePath) }
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        /**
         * Menghentikan [RecordingForegroundService].
         */
        fun stop(context: Context) {
            val intent = Intent(context, RecordingForegroundService::class.java).apply {
                action = ACTION_STOP
            }
            context.startService(intent)
        }
    }
}
