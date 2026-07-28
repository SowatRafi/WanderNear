package com.wandernear.data

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.CancellationSignal
import android.os.Looper
import android.telephony.TelephonyManager
import androidx.core.content.ContextCompat
import com.wandernear.core.model.LatLng
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import java.util.Locale
import kotlin.coroutines.resume

/**
 * A tiny "where am I" helper built on Android's own LocationManager — no Google
 * Play Services dependency. It returns the most recent last-known fix to rank
 * "near me" results, or null if we have no permission or no fix yet, in which
 * case the caller falls back to the city centre. Location is only read on
 * demand (per search); it is never tracked in the background.
 */
object LocationProvider {

    fun hasPermission(context: Context): Boolean =
        granted(context, Manifest.permission.ACCESS_FINE_LOCATION) ||
            granted(context, Manifest.permission.ACCESS_COARSE_LOCATION)

    /** The most recent last-known position, or null if unavailable. Fine for
     *  city-scale "near me" ranking, which tolerates a stale fix. */
    fun lastKnown(context: Context): LatLng? =
        newestFix(context)?.let { LatLng(it.latitude, it.longitude) }

    /**
     * A position to work with NOW — the cached fix if there is one, otherwise ask the
     * platform for a single fresh one and wait (up to [timeoutMs]) for it. Null if we
     * have no permission, no enabled provider, or nothing arrives in time.
     *
     * This is what the home uses to decide WHERE YOU ARE, so unlike [lastKnown] it can't
     * just give up when the phone has no cached fix — that's exactly the state a fresh
     * install is in the moment you grant permission. Uses the platform's own one-shot
     * `getCurrentLocation` (API 30+; our minSdk is 31) rather than a hand-rolled
     * listener-with-timeout.
     */
    suspend fun currentFix(context: Context, timeoutMs: Long = 15_000): LatLng? {
        lastKnown(context)?.let { return it }        // cached is instant and good enough
        if (!hasPermission(context)) return null
        val manager = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager ?: return null
        // Network first: it fixes in seconds indoors, where GPS may never fix at all.
        val provider = listOf(LocationManager.NETWORK_PROVIDER, LocationManager.GPS_PROVIDER)
            .firstOrNull { runCatching { manager.isProviderEnabled(it) }.getOrDefault(false) } ?: return null
        val signal = CancellationSignal()
        return try {
            withTimeoutOrNull(timeoutMs) {
                suspendCancellableCoroutine { cont ->
                    cont.invokeOnCancellation { signal.cancel() }
                    manager.getCurrentLocation(provider, signal, ContextCompat.getMainExecutor(context)) { loc ->
                        // Documented to fire exactly once, but guard anyway — resuming a
                        // continuation twice would crash the app.
                        if (cont.isActive) cont.resume(loc?.let { LatLng(it.latitude, it.longitude) })
                    }
                }
            }
        } catch (e: SecurityException) {
            null   // permission revoked mid-call — give up quietly
        } finally {
            signal.cancel()   // never leave a request running after we've stopped waiting
        }
    }

    /**
     * The ISO country you're currently in, as an English country NAME (e.g. "Australia"),
     * or null if the phone can't say. Read from the SIM's network country — the mobile
     * network you're attached to already knows which country it is, so this costs no
     * request and reveals nothing: we ASK the phone, we don't TELL anyone.
     *
     * Forced to English names because that's how `CountryFacts` is keyed; a localised
     * name would simply miss the table and the card would omit the facts rather than
     * show a wrong one.
     */
    fun countryName(context: Context): String? {
        val tm = context.getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager ?: return null
        val iso = tm.networkCountryIso?.takeIf { it.isNotBlank() } ?: return null
        return Locale("", iso).getDisplayCountry(Locale.ENGLISH).takeIf { it.isNotBlank() && it != iso }
    }

    /** Like [lastKnown] but only if the fix is fresh (younger than [maxAgeMs]),
     *  else null. Used where a stale position would mislead — e.g. a Travel Mode
     *  "you're near here" alert that must not fire from an old location. */
    fun recentLastKnown(context: Context, maxAgeMs: Long): LatLng? {
        val fix = newestFix(context) ?: return null
        if (System.currentTimeMillis() - fix.time > maxAgeMs) return null
        return LatLng(fix.latitude, fix.longitude)
    }

    /** Newest last-known [Location] across GPS/NETWORK/PASSIVE, or null. */
    private fun newestFix(context: Context): Location? {
        if (!hasPermission(context)) return null
        val manager = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager ?: return null
        return listOf(
            LocationManager.GPS_PROVIDER,
            LocationManager.NETWORK_PROVIDER,
            LocationManager.PASSIVE_PROVIDER,
        ).mapNotNull { provider ->
            try {
                manager.getLastKnownLocation(provider)
            } catch (e: Exception) {
                null   // provider missing or a permission race — just skip it
            }
        }.maxByOrNull { it.time }
    }

    /**
     * Starts live location updates for Travel Mode (needs location permission).
     * Returns the listener so the caller can stop it later, or null if we can't
     * start (no permission / no enabled provider). Prefers the low-power network
     * provider; [minTimeMs] and [minDistanceM] keep it battery-friendly.
     */
    fun requestUpdates(
        context: Context,
        minTimeMs: Long,
        minDistanceM: Float,
        onLocation: (LatLng) -> Unit,
    ): LocationListener? {
        if (!hasPermission(context)) return null
        val manager = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager ?: return null
        val provider = when {
            manager.isProviderEnabled(LocationManager.NETWORK_PROVIDER) -> LocationManager.NETWORK_PROVIDER
            manager.isProviderEnabled(LocationManager.GPS_PROVIDER) -> LocationManager.GPS_PROVIDER
            else -> return null
        }
        val listener = LocationListener { loc -> onLocation(LatLng(loc.latitude, loc.longitude)) }
        return try {
            manager.requestLocationUpdates(provider, minTimeMs, minDistanceM, listener, Looper.getMainLooper())
            listener
        } catch (e: SecurityException) {
            null   // permission race — give up quietly
        }
    }

    /** Stops updates started by [requestUpdates]. */
    fun stopUpdates(context: Context, listener: LocationListener) {
        val manager = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager ?: return
        manager.removeUpdates(listener)
    }

    private fun granted(context: Context, permission: String): Boolean =
        ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
}
