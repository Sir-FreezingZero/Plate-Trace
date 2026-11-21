package com.example.platescanner

import android.Manifest
import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import java.text.DateFormat
import java.util.Date

/** Vibration + heads-up notification when a plate on the watch list is seen. */
object WatchAlerts {

    private const val CHANNEL_ID = "watch_alerts"
    private val PATTERN = longArrayOf(0, 300, 150, 300, 150, 300)

    /** Notifications need a runtime permission on Android 13+. */
    fun canNotify(context: Context): Boolean =
        Build.VERSION.SDK_INT < 33 ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED

    @SuppressLint("MissingPermission") // checked by canNotify()
    fun alert(context: Context, plate: String, vehicle: Bitmap?) {
        vibrate(context)
        if (!canNotify(context)) return
        ensureChannel(context)

        val open = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val time = DateFormat.getTimeInstance(DateFormat.SHORT).format(Date())
        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("Watched plate: $plate")
            .setContentText("Spotted at $time")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setAutoCancel(true)
            .setContentIntent(open)
        if (vehicle != null) {
            builder.setLargeIcon(vehicle).setStyle(
                NotificationCompat.BigPictureStyle()
                    .bigPicture(vehicle)
                    .bigLargeIcon(null as Bitmap?)
            )
        }
        try {
            NotificationManagerCompat.from(context).notify(plate.hashCode(), builder.build())
        } catch (e: SecurityException) {
            Log.w("WatchAlerts", "Notification not allowed", e)
        }
    }

    private fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT < 26) return
        val manager = context.getSystemService(NotificationManager::class.java)
        if (manager.getNotificationChannel(CHANNEL_ID) != null) return
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "Watched plates", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "Alerts when a plate on your watch list is seen"
                enableVibration(true)
                vibrationPattern = PATTERN
            }
        )
    }

    private fun vibrate(context: Context) {
        try {
            val vibrator: Vibrator = if (Build.VERSION.SDK_INT >= 31) {
                context.getSystemService(VibratorManager::class.java).defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
            }
            if (Build.VERSION.SDK_INT >= 26) {
                vibrator.vibrate(VibrationEffect.createWaveform(PATTERN, -1))
            } else {
                @Suppress("DEPRECATION")
                vibrator.vibrate(PATTERN, -1)
            }
        } catch (e: Exception) {
            Log.w("WatchAlerts", "Vibrate failed", e)
        }
    }
}
