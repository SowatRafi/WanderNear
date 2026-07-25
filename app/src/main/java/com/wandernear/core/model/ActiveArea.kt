package com.wandernear.core.model

/**
 * The area the user is exploring, set by NAME (never their GPS). This is the unit of
 * the LIVE/online mode: the app fetches places live from OpenStreetMap within this
 * bounding box and ranks them "near you" on-device. The user may ALSO download it for
 * offline use, but that's optional — live is the default.
 *
 * Pure Kotlin (no Android) so it stays portable and unit-testable. The only thing that
 * ever leaves the phone to obtain this is the city/area NAME the user typed — the bbox
 * comes back from Nominatim's search of that name, not from the user's location.
 */
data class ActiveArea(
    // Nominatim's own full name, e.g. "Werribee, City of Wyndham, Victoria, Australia".
    val displayName: String,
    // Bounding box from Nominatim (south/north latitude, west/east longitude).
    val south: Double,
    val west: Double,
    val north: Double,
    val east: Double,
    val country: String? = null,
    val population: Long? = null,
) {
    /** Just the leading name, for headings — e.g. "Werribee". */
    val shortName: String get() = displayName.substringBefore(',').trim()

    /** The bbox centre — the "near me" fallback when there's no on-device GPS fix. */
    val center: LatLng get() = LatLng((south + north) / 2.0, (west + east) / 2.0)
}
