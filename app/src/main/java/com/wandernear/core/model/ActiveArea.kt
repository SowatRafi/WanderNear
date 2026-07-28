package com.wandernear.core.model

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max

/**
 * The area the user is exploring. This is the unit of the LIVE/online mode: the app
 * fetches places live from OpenStreetMap within this bounding box and ranks them
 * "near you" on-device. Downloading the area for offline use is optional — live is
 * the default.
 *
 * There are two ways an area comes about:
 *  - **Around you** (the DEFAULT) — [hereArea] builds the box straight from your own
 *    location fix. Nothing is typed and no name is looked up; what the area is CALLED
 *    is worked out afterwards, on-device, from the OSM data that comes back.
 *  - **By name** — you picked a city in Preferences so you can download it before a
 *    trip. That one's bbox comes back from Nominatim's search of the name you typed.
 *
 * Pure Kotlin (no Android) so it stays portable and unit-testable.
 */
data class ActiveArea(
    // Nominatim's own full name, e.g. "Werribee, City of Wyndham, Victoria, Australia".
    val displayName: String,
    // Bounding box from Nominatim (south/north latitude, west/east longitude).
    val south: Double,
    val west: Double,
    val north: Double,
    val east: Double,
    // The OSM area id — the same id the downloaded pack's filename encodes, so we can tell
    // OFFLINE whether a downloaded pack backs THIS area (see CityPackBuilder.packForOsmId).
    val osmId: Long = 0,
    val country: String? = null,
    val population: Long? = null,
) {
    /** Just the leading name, for headings — e.g. "Werribee". */
    val shortName: String get() = displayName.substringBefore(',').trim()

    /** The bbox centre — the "near me" fallback when there's no on-device GPS fix. */
    val center: LatLng get() = LatLng((south + north) / 2.0, (west + east) / 2.0)

    /**
     * True when this is a box around the user's own fix rather than a named OSM area.
     * A Nominatim match always carries a real [osmId]; [hereArea] leaves it 0 because
     * it corresponds to no OSM feature at all. Callers use this to know that the area
     * has no name yet (it gets one from the fetched places) and that it can't be
     * matched to a downloaded pack by id.
     */
    val isAroundYou: Boolean get() = osmId == 0L
}

/** Half-width of the "around you" box. 3 km ⇒ a ~6 km square: enough to hold the
 *  suburb you're standing in, small enough that one Overpass fetch stays quick. */
const val HERE_RADIUS_KM = 3.0

/** The placeholder name an "around you" area carries until the fetched places name it. */
const val HERE_NAME = "Nearby"

// One degree of latitude is ~111.32 km everywhere. Longitude shrinks towards the poles.
private const val KM_PER_DEGREE_LAT = 111.32

/**
 * The area AROUND a location fix — the app's default "where I am" mode.
 *
 * The box is built purely from arithmetic on [fix]: no service is asked where you are,
 * and no name is sent or received. [country] is passed in by the caller from the phone
 * itself (the SIM's network country) so the home can show the right currency and
 * emergency number without any lookup.
 *
 * The longitude span is divided by cos(latitude) because a degree of longitude covers
 * less ground the further you are from the equator — without it the box would be far
 * too narrow in Oslo and too wide in Singapore. The cos is floored at 0.01 so a fix
 * near a pole can't blow the box up to the whole planet.
 */
fun hereArea(fix: LatLng, country: String? = null, radiusKm: Double = HERE_RADIUS_KM): ActiveArea {
    val dLat = radiusKm / KM_PER_DEGREE_LAT
    val dLng = radiusKm / (KM_PER_DEGREE_LAT * max(cos(fix.lat * PI / 180.0), 0.01))
    return ActiveArea(
        displayName = HERE_NAME,
        south = fix.lat - dLat,
        north = fix.lat + dLat,
        west = fix.lng - dLng,
        east = fix.lng + dLng,
        osmId = 0,        // not an OSM feature — see ActiveArea.isAroundYou
        country = country,
        population = null,   // unknown without a name lookup, so we never guess one
    )
}
