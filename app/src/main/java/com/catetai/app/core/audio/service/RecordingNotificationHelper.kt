package com.catetai.app.core.audio.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.catetai.app.R
import com.catetai.app.presentation.MainActivity
import java.util.Locale

/**
 * Helper pembuat dan pengelola notifikasi untuk [RecordingForegroundService].
 *
 * Mengikuti pedoman Android 14+ (API 34):
 * - Memiliki Notification Channel dengan [NotificationManager.IMPORTANCE_LOW] agar
 *   pembaruan timer durasi setiap detik tidak memicu getar atau suara yang mengganggu pengguna.
 * - Menggunakan `onlyAlertOnce = true` untuk mencegah alert berulang pada setiap pembaruan.
 * - Menyertakan action button "Berhenti" untuk interaksi cepat tanpa membuka aplikasi.
 * - Intent utama mengarahkan pengguna kembali ke [MainActivity].
 */
object RecordingNotificationHelper {

    const val CHANNEL_ID = "catet_ai_recording_channel"
    const val NOTIFICATION_ID_RECORDING = 1001
    const val NOTIFICATION_ID_ALERT = 1002

    private const val REQUEST_CODE_CONTENT = 101
    private const val REQUEST_CODE_STOP = 102

    /**
     * Memastikan Notification Channel telah dibuat pada Android 8.0+ (API 26+).
     */
    fun createNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Catet AI — Status Perekaman",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Menampilkan durasi rekaman berjalan dan tombol kontrol penghentian audio."
                setShowBadge(false)
            }
            notificationManager?.createNotificationChannel(channel)
        }
    }

    /**
     * Mengonversi durasi dalam milidetik menjadi teks berformat "MM:SS" atau "HH:MM:SS".
     */
    fun formatDuration(durationMs: Long): String {
        val totalSeconds = (durationMs / 1000).coerceAtLeast(0)
        val hours = totalSeconds / 3600
        val minutes = (totalSeconds % 3600) / 60
        val seconds = totalSeconds % 60

        return if (hours > 0) {
            String.format(Locale.US, "%02d:%02d:%02d", hours, minutes, seconds)
        } else {
            String.format(Locale.US, "%02d:%02d", minutes, seconds)
        }
    }

    /**
     * Membangun notifikasi persisten untuk foreground service yang sedang aktif merekam.
     */
    fun buildRecordingNotification(
        context: Context,
        durationMs: Long,
        isSilenced: Boolean = false,
        isLowBattery: Boolean = false,
        isLowDisk: Boolean = false
    ): Notification {
        val contentIntent = PendingIntent.getActivity(
            context,
            REQUEST_CODE_CONTENT,
            Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val stopIntent = PendingIntent.getService(
            context,
            REQUEST_CODE_STOP,
            Intent(context, RecordingForegroundService::class.java).apply {
                action = RecordingForegroundService.ACTION_STOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val formattedDuration = formatDuration(durationMs)

        val title = if (isSilenced) {
            "⚠️ Catet AI — Mikrofon Dibisukan"
        } else {
            "Catet AI — Sedang Merekam"
        }

        val subtextBuilder = StringBuilder("Durasi: $formattedDuration")
        if (isSilenced) {
            subtextBuilder.append(" • Dipakai aplikasi lain")
        }
        if (isLowBattery) {
            subtextBuilder.append(" • ⚠️ Baterai Lemah (≤5%)")
        }
        if (isLowDisk) {
            subtextBuilder.append(" • ⚠️ Ruang Disk Hampir Penuh (≤100MB)")
        }

        return NotificationCompat.Builder(context, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(subtextBuilder.toString())
            .setSmallIcon(R.drawable.ic_mic)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(contentIntent)
            .addAction(
                R.drawable.ic_stop,
                "Berhenti",
                stopIntent
            )
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .build()
    }

    /**
     * Membangun notifikasi peringatan saat rekaman berhenti otomatis atau dipulihkan setelah crash.
     */
    fun buildAlertNotification(
        context: Context,
        title: String,
        message: String
    ): Notification {
        val contentIntent = PendingIntent.getActivity(
            context,
            REQUEST_CODE_CONTENT,
            Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(context, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(message)
            .setSmallIcon(R.drawable.ic_mic)
            .setAutoCancel(true)
            .setContentIntent(contentIntent)
            .build()
    }
}
