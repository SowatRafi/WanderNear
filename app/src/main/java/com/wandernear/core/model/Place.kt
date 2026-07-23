package com.wandernear.core.model

import kotlin.math.PI
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * A place from the city data pack, as shown to the user.
 * Pure Kotlin (no Android) so it stays portable. Nullable fields are simply the
 * facts OpenStreetMap/Wikipedia didn't have — we never fill them with guesses.
 */
data class Place(
    val id: Int,
    val name: String,
    val category: String,        // food | worship | attraction | outdoor
    val subcategory: String?,    // e.g. restaurant, viewpoint, mosque
    val lat: Double,
    val lng: Double,
    val address: String?,
    val cuisine: String?,
    val religion: String?,
    val summary: String?,        // short "why it matters" text from Wikipedia
    val diets: Set<String> = emptySet(),  // dietary tags present (value != "no")
    val distanceKm: Double? = null,       // filled in when we rank by distance
)

/** A latitude/longitude point. */
data class LatLng(val lat: Double, val lng: Double)

/**
 * The one-row summary of the active city pack, shown on the City Info card.
 * country/population may be null — we show only what the data actually has.
 */
data class CityInfo(
    val name: String,          // as stored, e.g. "Melbourne, Victoria, Australia"
    val country: String?,
    val population: Long?,
) {
    /** Just the leading city name for a clean heading, e.g. "Melbourne". */
    val shortName: String get() = name.substringBefore(',').trim()
}

/** Straight-line distance between two points in kilometres (haversine formula). */
fun haversineKm(a: LatLng, b: LatLng): Double {
    val earthRadiusKm = 6371.0
    fun rad(deg: Double) = deg * PI / 180.0
    val dLat = rad(b.lat - a.lat)
    val dLng = rad(b.lng - a.lng)
    val h = sin(dLat / 2).pow(2) +
        cos(rad(a.lat)) * cos(rad(b.lat)) * sin(dLng / 2).pow(2)
    return earthRadiusKm * 2 * asin(min(1.0, sqrt(h)))
}
