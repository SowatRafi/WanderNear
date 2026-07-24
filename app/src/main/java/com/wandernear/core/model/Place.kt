package com.wandernear.core.model

import kotlin.math.PI
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
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
    val category: String,        // food | worship | attraction | outdoor | shopping | safety
    val subcategory: String?,    // e.g. restaurant, viewpoint, mosque
    val lat: Double,
    val lng: Double,
    val address: String?,
    val cuisine: String?,
    val religion: String?,
    val summary: String?,        // short "why it matters" text from Wikipedia
    val phone: String? = null,   // raw OSM phone, e.g. for a police station's Call button
    val website: String? = null, // raw OSM website — e.g. a mosque's page for its Friday times
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

/**
 * An annual festival from the pack's `event` table — a real Wikipedia article, never
 * invented. There is deliberately no date: no free source publishes a trustworthy one
 * for a recurring festival, so the app says dates change each year instead of guessing.
 */
data class CityEvent(
    val name: String,
    val summary: String?,
    val summaryUrl: String?,
)

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

/**
 * How far from the active city a location fix can be before we stop treating it as
 * "you are here". Beyond this you aren't in this city at all — you've downloaded
 * somewhere you plan to go. Generous enough to cover a whole metro area and the
 * towns around it.
 */
const val AWAY_FROM_CITY_KM = 100.0

/**
 * [fix], but ONLY when it's actually inside the active city — otherwise null.
 *
 * Download Kyoto while standing in Melbourne and every result is truthfully 8,000 km
 * away: true, useless, and calling it "near you" would be a lie. Returning null lets
 * every caller fall back to the city centre and drop the "near you" wording, so the
 * guard lives in one place instead of being re-derived by each screen.
 */
fun fixInCity(fix: LatLng?, center: LatLng?): LatLng? =
    fix?.takeIf { center == null || haversineKm(it, center) <= AWAY_FROM_CITY_KM }

/**
 * How far away, written the way someone on foot reads it: metres up close, kilometres
 * once it's a drive. Null distance in, null out (we never guess a distance).
 *
 * Rounded to the nearest 10 m because a phone's fix isn't accurate to the metre — "430 m"
 * is honest where "431 m" would claim precision we don't have.
 */
fun distanceLabel(km: Double?): String? = when {
    km == null -> null
    km < 1.0 -> "${(km * 100).roundToInt() * 10} m"
    else -> "%.1f km".format(km)
}

/**
 * A friendly label for a place's category, shown on the "around you now" / "daily needs"
 * rows and in the Travel Mode banner. One mapping in one place, so the app can never
 * call the same category two different things on two different screens.
 */
fun categoryLabel(category: String): String = when (category) {
    "safety" -> "Police"
    "health" -> "Hospital"
    "fuel" -> "Fuel"
    "parking" -> "Parking"
    "food" -> "Food"
    "shopping" -> "Shopping"
    "outdoor" -> "Outdoors"
    "worship" -> "Place of worship"
    "attraction" -> "Attraction"
    else -> category.replaceFirstChar { it.uppercase() }
}
