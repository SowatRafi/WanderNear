package com.wandernear.data

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.LocationManager
import androidx.core.content.ContextCompat
import com.wandernear.core.model.LatLng

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

    /** The most recent last-known position, or null if unavailable. */
    fun lastKnown(context: Context): LatLng? {
        if (!hasPermission(context)) return null
        val manager = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
            ?: return null

        // ponytail: last-known can be stale, but it's plenty for city-scale
        // ranking; a fresh live GPS fix can come with FusedLocation later.
        val providers = listOf(
            LocationManager.GPS_PROVIDER,
            LocationManager.NETWORK_PROVIDER,
            LocationManager.PASSIVE_PROVIDER,
        )
        val newest = providers.mapNotNull { provider ->
            try {
                manager.getLastKnownLocation(provider)
            } catch (e: Exception) {
                null   // provider missing or a permission race — just skip it
            }
        }.maxByOrNull { it.time }

        return newest?.let { LatLng(it.latitude, it.longitude) }
    }

    private fun granted(context: Context, permission: String): Boolean =
        ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
}
