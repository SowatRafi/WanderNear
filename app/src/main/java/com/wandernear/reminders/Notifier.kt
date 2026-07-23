package com.wandernear.reminders

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.wandernear.MainActivity
import com.wandernear.R

/**
 * Posts the journal's reminder notifications. Everything is a no-op if the user
 * hasn't granted the POST_NOTIFICATIONS permission (Android 13+), so nothing
 * ever crashes when notifications are off.
 */
object Notifier {

    private const val CHANNEL_ID = "journal_reminders"

    /** Quiet channel for Travel Mode's ongoing banner. */
    const val TRAVEL_CHANNEL_ID = "travel_mode"

    /** Audible channel for the "worth a visit nearby" alerts. */
    const val TRAVEL_ALERT_CHANNEL_ID = "travel_alerts"

    fun notify(context: Context, id: Int, title: String, text: String) {
        if (!canPost(context)) return
        createChannel(context)
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()
        NotificationManagerCompat.from(context).notify(id, notification)
    }

    private fun createChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Journal reminders",
                NotificationManager.IMPORTANCE_DEFAULT,
            )
            context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    /** Creates the quiet Travel Mode channel (low importance so the ongoing banner
     *  doesn't buzz). Safe to call repeatedly. */
    fun createTravelChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                TRAVEL_CHANNEL_ID, "Travel Mode", NotificationManager.IMPORTANCE_LOW,
            )
            context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    /** A "worth a visit nearby" alert (Travel Mode). No-op without POST_NOTIFICATIONS;
     *  tapping it opens the app. */
    fun notifyTravel(context: Context, id: Int, title: String, text: String) {
        if (!canPost(context)) return
        createTravelAlertChannel(context)
        val open = PendingIntent.getActivity(
            context, 0, Intent(context, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(context, TRAVEL_ALERT_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(open)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()
        NotificationManagerCompat.from(context).notify(id, notification)
    }

    private fun createTravelAlertChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                TRAVEL_ALERT_CHANNEL_ID, "Nearby places", NotificationManager.IMPORTANCE_DEFAULT,
            )
            context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    /** True unless we're on Android 13+ without the notification permission. */
    private fun canPost(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
}
