package com.wandernear.travel

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.location.LocationListener
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.wandernear.MainActivity
import com.wandernear.R
import com.wandernear.core.model.LatLng
import com.wandernear.data.CityDatabase
import com.wandernear.data.LocationProvider
import com.wandernear.data.PreferencesRepository
import com.wandernear.reminders.Notifier
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * The foreground service behind Travel Mode.
 *
 * It runs ONLY while the user has Travel Mode switched on, always with a visible
 * "Travel Mode is on" notification, and uses "while-in-use" location — started
 * from the user's tap, so no background-location permission is needed. It never
 * resurrects itself in the background (START_NOT_STICKY).
 *
 * TM.1 gave the service + visible banner + Stop. TM.2 (here) adds the live
 * location watch and the grounded "worth a visit nearby" alerts — each alert is a
 * real retrieved DB row, never invented.
 */
class TravelModeService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    // Places we've already alerted this session, so we don't nag about the same one.
    private val alerted = ConcurrentHashMap.newKeySet<Int>()
    // Which pack those ids belong to. Place ids are pack-local, so switching cities
    // must reset the set — otherwise a new city's place could be wrongly suppressed
    // by a same-numbered id from the old one.
    @Volatile private var alertedPack: String? = null
    private var locationListener: LocationListener? = null

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
        startLocationWatch()
        // START_NOT_STICKY: if the process is killed we do NOT silently restart in
        // the background — the user re-enables Travel Mode themselves. Privacy first.
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        locationListener?.let { LocationProvider.stopUpdates(this, it) }
        locationListener = null
        scope.cancel()
        isRunning = false
    }

    /** Begins watching location: an immediate check off the last-known fix, then
     *  live updates as the user moves (a battery-friendly cadence). */
    private fun startLocationWatch() {
        // A quick off→on toggle (or Stop then re-enable) can deliver a second
        // onStartCommand to this SAME instance before onDestroy runs — stop any prior
        // listener first so we never orphan one that keeps reading location.
        locationListener?.let { LocationProvider.stopUpdates(this, it) }
        // Seed an immediate check ONLY from a fresh fix — a stale last-known could
        // falsely claim you're near a place you left. Live updates follow.
        LocationProvider.recentLastKnown(this, MIN_TIME_MS)?.let { checkNearby(it) }
        locationListener = LocationProvider.requestUpdates(this, MIN_TIME_MS, MIN_DISTANCE_M) { here ->
            checkNearby(here)
        }
    }

    /** For a fix, find the nearest notable place we haven't mentioned yet and, if
     *  it's within range, alert the user. Every result is a real retrieved row. */
    private fun checkNearby(here: LatLng) {
        scope.launch {
            // Read whichever city pack is active (bundled or a downloaded one) each time.
            val pack = PreferencesRepository(applicationContext).activePack.first()
            if (pack != alertedPack) {            // switched city → de-dup from scratch
                alerted.clear()
                alertedPack = pack
            }
            val db = CityDatabase(this@TravelModeService, pack)
            val hit = db.nearbyNotable(here, RADIUS_KM).firstOrNull { it.id !in alerted } ?: return@launch
            if (!alerted.add(hit.id)) return@launch          // another fix just alerted it
            val sub = hit.subcategory?.replace('_', ' ')?.replaceFirstChar { it.uppercase() } ?: "Worth a visit"
            val dist = hit.distanceKm?.let { " · ${String.format(Locale.US, "%.1f", it)} km away" } ?: ""
            Notifier.notifyTravel(
                this@TravelModeService,
                "travel:${hit.id}".hashCode(),
                "Worth a visit nearby",
                "${hit.name} — $sub$dist",
            )
        }
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
        private const val MIN_TIME_MS = 120_000L    // ~2 min between location checks…
        private const val MIN_DISTANCE_M = 120f     // …or when you've moved ~120 m
        private const val RADIUS_KM = 0.3           // "near you" = within 300 m

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
