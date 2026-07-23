package com.wandernear.travel

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.wandernear.MainActivity
import com.wandernear.R
import com.wandernear.data.PreferencesRepository
import com.wandernear.reminders.Notifier
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * The foreground service behind Travel Mode.
 *
 * It runs ONLY while the user has Travel Mode switched on, always with a visible
 * "Travel Mode is on" notification, and (from TM.2 on) uses "while-in-use"
 * location — started from the user's tap, so no background-location permission is
 * needed. It never resurrects itself in the background (START_NOT_STICKY).
 *
 * TM.1 scope: prove the service + visible banner + Stop work. The live location
 * and nearby-place alerts arrive in TM.2.
 */
class TravelModeService : Service() {

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // The notification's "Stop" button routes back here to turn the mode off.
        if (intent?.action == ACTION_STOP) {
            // Sync the saved switch from this user-initiated stop. Doing it HERE
            // (not in onDestroy) keeps it ordered before any later re-enable, so a
            // stale "false" can never clobber a fresh turn-on.
            CoroutineScope(Dispatchers.IO).launch {
                PreferencesRepository(applicationContext).setTravelMode(false)
            }
            stopSelf()
            return START_NOT_STICKY
        }
        // Must show the ongoing banner promptly (within ~10s of being started) or
        // the system kills us. Type = location so the OS knows why we run.
        startForeground(NOTIF_ID, ongoingNotification(), ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION)
        isRunning = true
        // START_NOT_STICKY: if the process is killed we do NOT silently restart in
        // the background — the user re-enables Travel Mode themselves. Privacy first.
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        isRunning = false
    }

    /** The persistent "Travel Mode is on" banner, with Stop + tap-to-open actions. */
    private fun ongoingNotification(): Notification {
        Notifier.createTravelChannel(this)
        val open = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE,
        )
        val stop = PendingIntent.getService(
            this, 1,
            Intent(this, TravelModeService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Builder(this, Notifier.TRAVEL_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("Travel Mode is on")
            .setContentText("Watching for great spots near you.")
            .setOngoing(true)
            .setContentIntent(open)
            .addAction(0, "Stop", stop)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    companion object {
        const val ACTION_STOP = "com.wandernear.travel.STOP"
        private const val NOTIF_ID = 4201

        /** True only while a service instance is actually running. It's static, so a
         *  process kill/reboot resets it to false for free — the app reads it at
         *  launch to correct a stale "on" switch when we never got to clean up. */
        @Volatile
        var isRunning = false
            private set

        /** Start Travel Mode. Call ONLY from the foreground (a toggle tap), or the
         *  while-in-use location service would be blocked by the OS. */
        fun start(context: Context) {
            ContextCompat.startForegroundService(context, Intent(context, TravelModeService::class.java))
        }

        /** Stop Travel Mode. */
        fun stop(context: Context) {
            context.stopService(Intent(context, TravelModeService::class.java))
        }
    }
}
