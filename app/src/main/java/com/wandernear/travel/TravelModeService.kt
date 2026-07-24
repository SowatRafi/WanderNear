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
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.wandernear.MainActivity
import com.wandernear.R
import com.wandernear.core.model.LatLng
import com.wandernear.core.model.Place
import com.wandernear.core.model.categoryLabel
import com.wandernear.core.model.distanceLabel
import com.wandernear.core.model.fixInCity
import com.wandernear.data.CityDatabase
import com.wandernear.data.LocationProvider
import com.wandernear.data.PreferencesRepository
import com.wandernear.reminders.Notifier
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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
    // The active pack's centre, cached per pack: computing it averages every row, so we
    // do it on a city switch rather than on every fix.
    @Volatile private var packCenter: LatLng? = null
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
        // Travel Mode is off, so the app must stop showing "around you now" — the data
        // is only as fresh as the last fix, and there are no more fixes coming.
        _around.value = null
    }

    /** Begins watching location: an immediate check off the last-known fix, then
     *  live updates as the user moves (a battery-friendly cadence). */
    private fun startLocationWatch() {
        // A quick off→on toggle (or Stop then re-enable) can deliver a second
        // onStartCommand to this SAME instance before onDestroy runs — stop any prior
        // listener first so we never orphan one that keeps reading location.
        locationListener?.let { LocationProvider.stopUpdates(this, it) }
        // Fill the digest from whatever position we already have, even an old one: it's
        // neighbourhood-level information, exactly like the home screen's own "near you"
        // lists, and updates below only arrive once you've MOVED — so without this a
        // traveller sitting still would switch Travel Mode on and see nothing at all.
        LocationProvider.lastKnown(this)?.let { update(it, withAlert = false) }
        // The ALERT is different: only ever from a FRESH fix, so a stale last-known can
        // never claim you're standing beside a place you left hours ago.
        LocationProvider.recentLastKnown(this, MIN_TIME_MS)?.let { update(it, withAlert = true) }
        locationListener = LocationProvider.requestUpdates(this, MIN_TIME_MS, MIN_DISTANCE_M) { here ->
            update(here, withAlert = true)
        }
    }

    /**
     * For a fix: refresh the "around you now" digest and, when [withAlert], also consider
     * a heads-up for something genuinely notable we haven't mentioned yet. Every result
     * is a real retrieved row.
     *
     * The digest goes into the ongoing banner we're ALREADY showing, so Travel Mode can
     * never become a stream of buzzes: one notification, updated in place, however far
     * you walk. Only the notable hit below is allowed to make a sound.
     */
    private fun update(here: LatLng, withAlert: Boolean) {
        scope.launch {
            // Read whichever city pack is active (bundled or a downloaded one) each time.
            val pack = PreferencesRepository(applicationContext).activePack.first()
            val db = CityDatabase(this@TravelModeService, pack)
            if (pack != alertedPack) {            // switched city → de-dup from scratch
                alerted.clear()
                alertedPack = pack
                // Only recompute on a switch: it averages every row in the pack, which
                // we don't want to do on every fix.
                packCenter = db.cityCenter()
            }
            // If this fix isn't in the active city at all (you downloaded Kyoto but
            // you're still home), say nothing rather than point at places 8,000 km away.
            val origin = fixInCity(here, packCenter)
            if (origin == null) {
                publishAround(pack, emptyList())
                return@launch
            }

            publishAround(pack, db.nearestEssentials(origin, AROUND_CATEGORIES, AROUND_RADIUS_KM))
            if (!withAlert) return@launch

            val hit = db.nearbyNotable(origin, RADIUS_KM).firstOrNull { it.id !in alerted } ?: return@launch
            if (!alerted.add(hit.id)) return@launch          // another fix just alerted it
            val sub = hit.subcategory?.replace('_', ' ')?.replaceFirstChar { it.uppercase() } ?: "Worth a visit"
            val dist = distanceLabel(hit.distanceKm)?.let { " · $it away" } ?: ""
            Notifier.notifyTravel(
                this@TravelModeService,
                "travel:${hit.id}".hashCode(),
                "Worth a visit nearby",
                "${hit.name} — $sub$dist",
            )
        }
    }

    /**
     * Share the digest with the app AND refresh the banner in place. Updating the same
     * notification id is what keeps this to exactly one notification.
     */
    private fun publishAround(pack: String, places: List<Place>) {
        _around.value = Around(pack, places)
        try {
            NotificationManagerCompat.from(this).notify(NOTIF_ID, ongoingNotification(places))
        } catch (e: SecurityException) {
            // Notifications revoked mid-session — the service stops on its own; don't crash.
        }
    }

    /** The persistent "Travel Mode is on" banner, with Stop + tap-to-open actions. */
    private fun ongoingNotification(around: List<Place> = emptyList()): Notification {
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
        // Collapsed: a single scannable line. Expanded: one line per kind of place.
        val collapsed =
            if (around.isEmpty()) "Watching for great spots near you."
            else around.joinToString(" · ") { "${it.name} ${distanceLabel(it.distanceKm)}" }
        val expanded =
            if (around.isEmpty()) collapsed
            else around.joinToString("\n") {
                "${categoryLabel(it.category)} · ${it.name} · ${distanceLabel(it.distanceKm)}"
            }
        return NotificationCompat.Builder(this, Notifier.TRAVEL_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            // The title never stops saying this — it's the privacy guarantee, not a slot
            // for content. Only the body below changes as you move.
            .setContentTitle("Travel Mode is on")
            .setContentText(collapsed)
            .setStyle(NotificationCompat.BigTextStyle().bigText(expanded))
            // On a locked screen show THAT Travel Mode is on (the banner is the privacy
            // guarantee, so it must never disappear) but not WHICH café you're standing
            // next to — otherwise a glance at the phone gives away where you are.
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setPublicVersion(lockScreenNotification(open, stop))
            .setOngoing(true)
            .setContentIntent(open)
            .addAction(0, "Stop", stop)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    /** The redacted banner shown on a locked screen: still visibly on, but no place names. */
    private fun lockScreenNotification(open: PendingIntent, stop: PendingIntent): Notification =
        NotificationCompat.Builder(this, Notifier.TRAVEL_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("Travel Mode is on")
            .setContentText("Watching for great spots near you.")
            .setOngoing(true)
            .setContentIntent(open)
            .addAction(0, "Stop", stop)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()


    companion object {
        const val ACTION_STOP = "com.wandernear.travel.STOP"
        private const val NOTIF_ID = 4201
        private const val MIN_TIME_MS = 120_000L    // ~2 min between location checks…
        private const val MIN_DISTANCE_M = 120f     // …or when you've moved ~120 m
        private const val RADIUS_KM = 0.3           // "near you" = within 300 m

        // What "around you now" covers. Fuel, parking, police and hospital are left out
        // ON PURPOSE — they're already on the home screen's "Daily needs near you" card,
        // and repeating them would just make the screen longer.
        private val AROUND_CATEGORIES = listOf("food", "shopping", "outdoor", "culture")
        private const val AROUND_RADIUS_KM = 2.0    // "around you" = roughly walkable

        /**
         * A digest tagged with the pack it was built from. The tag matters: a digest is
         * only refreshed when you MOVE, so after switching city the last one would linger
         * and show another city's places under the new city's name. The screen compares
         * [packName] against the active pack and ignores anything that doesn't match.
         */
        data class Around(val packName: String, val places: List<Place>)

        private val _around = MutableStateFlow<Around?>(null)

        /**
         * What's around you right now, refreshed on each location fix — real rows from
         * the active pack, never invented. The home screen reads THIS rather than asking
         * for its own location, so Travel Mode stays the single place that watches you.
         * Null/empty whenever the service isn't running, you're not in the active city,
         * or there's nothing grounded to show.
         */
        val around: StateFlow<Around?> = _around.asStateFlow()

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
