package com.wandernear.data

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import com.wandernear.core.model.ActiveArea
import com.wandernear.core.model.LatLng
import com.wandernear.core.model.Place
import com.wandernear.core.model.haversineKm
import com.wandernear.core.model.hereArea
import com.wandernear.core.pack.OsmClassifier
import com.wandernear.core.retrieval.SearchSpec
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * The LIVE data source — the online-first heart of the app.
 *
 * Instead of reading a downloaded pack, it fetches places **live** from the free
 * OpenStreetMap Overpass API for the user's [ActiveArea], for exactly what the query
 * asks (a small, fast, category-scoped request), then ranks them "near you" on-device.
 * Everything is grounded: each result is a real OSM feature — we classify and format
 * it, we never invent one. The AI/templates then reword these real rows, same as the
 * pack path.
 *
 * PRIVACY: the only thing that leaves the phone is the area's bounding box (derived from
 * the NAME the user typed), plus the OSM tags being asked for. The user's own GPS is
 * used only on-device, to sort results by distance — it is never sent anywhere.
 */
object LiveSource {

    // Same descriptive User-Agent the pack builder uses (Overpass requires one).
    private const val USER_AGENT = "WanderNear/0.1 (sowat.rafi.98@gmail.com)"
    private val OVERPASS_ENDPOINTS = listOf(
        "https://overpass-api.de/api/interpreter",
        "https://overpass.kumi.systems/api/interpreter",
    )

    // How many full passes over the mirrors before giving up, and the pause between them.
    // Two passes is the sweet spot: it rides out the common "server is momentarily busy"
    // 504 without leaving someone staring at a spinner when the service is really down.
    private const val OVERPASS_ATTEMPTS = 2
    private const val OVERPASS_RETRY_DELAY_MS = 1_500L

    /**
     * The box sizes tried around the user, smallest first — the app's answer to "works in
     * any place".
     *
     * A single fixed radius cannot work everywhere: 3 km of central Tokyo is thousands of
     * places and a slow, heavy query, while 3 km of outback Australia is nothing at all.
     * Starting SMALL and widening only when there genuinely isn't enough solves both ends
     * with one rule — in a city the first box already has plenty so we never widen (fast,
     * light), and in the country we keep going until we reach the nearest town.
     *
     * 50 km is the last resort: beyond that "around you" stops being a fair description.
     */
    private val AROUND_YOU_RADII_KM = listOf(1.5, 5.0, 15.0, 50.0)

    /**
     * A quick HINT of whether we're online — used only to decide whether to TRY live first.
     * Requires NET_CAPABILITY_VALIDATED (Android confirmed real connectivity), not merely
     * INTERNET, since a VPN or a dropped WiFi advertises INTERNET while unreachable. It can
     * still be fooled (a VPN that keeps reporting VALIDATED after its tunnel dies), so callers
     * do NOT trust it blindly: a live fetch that then fails ([places]/[search] return null)
     * falls back to the offline pack. This is just the fast path that avoids a pointless
     * network attempt when we're plainly offline (no active network at all).
     */
    fun isOnline(context: Context): Boolean {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return false
        val active = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(active) ?: return false
        if (!caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) ||
            !caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)) return false
        // A normal validated network → we're online.
        if (!caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN)) return true
        // The active network is a VPN, which can keep reporting VALIDATED after its underlying
        // transport dies (WiFi off in airplane mode, tunnel still "up"). Trust it only if some
        // NON-VPN network is ALSO validated — i.e. the VPN actually has a live path out.
        @Suppress("DEPRECATION")
        return cm.allNetworks.any { n ->
            cm.getNetworkCapabilities(n)?.let { c ->
                !c.hasTransport(NetworkCapabilities.TRANSPORT_VPN) &&
                    c.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
                    c.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
            } ?: false
        }
    }

    /**
     * Fetch the best matches for [spec] live within [area], ranked by distance from
     * [origin]. Returns at most [limit], or an empty list if nothing matched (the caller
     * then shows the honest "I don't have that" reply — the AI is never asked to fill a gap).
     *
     * Mirrors the pack's retrieval rules so the two paths behave the same: filter by
     * category/religion/diet, then narrow by the free-text words — but if those words
     * match nothing yet a real filter exists (e.g. "vegetarian sushi" with no name hit),
     * fall back to the filtered set rather than coming up empty.
     */
    suspend fun search(area: ActiveArea, spec: SearchSpec, origin: LatLng, limit: Int = 5): List<Place>? =
        withContext(Dispatchers.IO) {
            // Fetch only the asked-for category when we know it (fast); else everything.
            val categories = spec.category?.let { listOf(it) }
            // A named area (a city you chose to download) is searched exactly as asked —
            // widening it would quietly answer about somewhere you didn't ask about.
            if (!area.isAroundYou) {
                val all = fetchPlaces(area, categories, origin) ?: return@withContext null
                return@withContext matching(all, spec).take(limit)
            }
            // Around YOU: sweep outwards until something matches, so "a café" answers in a
            // city AND in a country town, without a big query in either.
            var best: List<Place>? = null
            for (radiusKm in AROUND_YOU_RADII_KM) {
                val box = hereArea(area.center, area.country, radiusKm)
                // null ⇒ the network actually failed (e.g. a "connected" VPN with a dead
                // tunnel). Keep whatever a smaller box already found; if nothing had, the
                // null tells the caller to fall back to the offline pack.
                val all = fetchPlaces(box, categories, origin) ?: return@withContext best
                best = matching(all, spec)
                if (best.isNotEmpty()) break
            }
            best?.take(limit)
        }

    /**
     * Apply [spec] to fetched places: structured filters (category/religion/diet) first,
     * then the free-text words. If those words match nothing but a real filter exists
     * (e.g. "vegetarian sushi" with no name hit), fall back to the filtered set rather
     * than coming up empty — but a pure free-text query with no match stays empty, so the
     * app refuses honestly instead of showing something irrelevant.
     */
    private fun matching(all: List<Place>, spec: SearchSpec): List<Place> {
        val base = all.filter { passesFilters(it, spec) }
        val result = when {
            spec.ftsTerms.isEmpty() -> base
            else -> {
                val hit = base.filter { matchesText(it, spec.ftsTerms) }
                val hasFilter = spec.category != null || spec.religion != null || spec.diets.isNotEmpty()
                if (hit.isNotEmpty()) hit else if (hasFilter) base else emptyList()
            }
        }
        return preferSoftDiets(result, spec)
    }

    /**
     * Float places matching a SOFT dietary preference (one implied by faith) to the top,
     * without removing anything. `sortedByDescending` is stable, so within each group the
     * nearest-first order is preserved.
     */
    private fun preferSoftDiets(places: List<Place>, spec: SearchSpec): List<Place> {
        if (spec.softDiets.isEmpty()) return places
        return places.sortedByDescending { p -> spec.softDiets.any { it in p.diets } }
    }

    /**
     * Fetch every place in [categories] for the area, ranked nearest-first — the source
     * for the live HOME cards (daily needs, worth-visiting, for-you, worship all derive
     * from ONE such fetch, so the home costs a single Overpass call, not one per card).
     */
    suspend fun places(
        area: ActiveArea,
        categories: Collection<String>,
        origin: LatLng,
        minResults: Int = 0,
    ): List<Place>? = withContext(Dispatchers.IO) {
        // A named area is fetched exactly as given — its bbox IS the city you picked.
        if (!area.isAroundYou) return@withContext fetchPlaces(area, categories, origin)
        // Around YOU: widen until the home has enough to be worth showing. In a city the
        // first (small) box already clears the bar, so this costs one quick query.
        var best: List<Place>? = null
        for (radiusKm in AROUND_YOU_RADII_KM) {
            val box = hereArea(area.center, area.country, radiusKm)
            // Network failure: keep whatever a smaller box found rather than losing it.
            val got = fetchPlaces(box, categories, origin) ?: return@withContext best
            best = got
            if (got.size >= minResults) break
        }
        best
    }

    /**
     * Fetch grounded Wikipedia "stories" for the wiki-linked places in [places] — in
     * PARALLEL, so a handful cost about one round-trip — returning copies with `summary`
     * set. A place with no OSM Wikipedia link (or one that already has a summary) passes
     * through unchanged. Call this on the FEW places you're about to show, never a whole
     * fetch. Grounded: only OSM-linked places get a story; nothing is invented.
     */
    suspend fun enrichStories(places: List<Place>): List<Place> = withContext(Dispatchers.IO) {
        coroutineScope {
            places.map { p ->
                async {
                    if (p.summary != null || (p.wikipedia == null && p.wikidata == null)) p
                    else Wikipedia.summaryFor(p.wikipedia, p.wikidata)?.let { p.copy(summary = it.text) } ?: p
                }
            }.awaitAll()
        }
    }

    /**
     * Shared fetch + parse + distance-rank. [categories] null/empty ⇒ everything. Returns null
     * when the request FAILED (no connectivity / server error) — distinct from an empty list,
     * which means the request succeeded but the area genuinely has none of those places. The
     * caller uses null as the signal to fall back to the offline pack.
     */
    private suspend fun fetchPlaces(area: ActiveArea, categories: Collection<String>?, origin: LatLng): List<Place>? {
        val body = OsmClassifier.overpassBodyBbox(area.south, area.west, area.north, area.east, categories)
        val json = overpassPost(body) ?: return null
        // A valid Overpass response always carries an "elements" array (possibly empty). If it's
        // absent or the body doesn't parse, the request FAILED (e.g. a 200 with a server-timeout
        // remark) — treat that as failure (null) so the caller falls back to the offline pack,
        // NOT as a genuine "no matches" (which an empty array correctly is).
        val elements = runCatching { JSONObject(json).optJSONArray("elements") }.getOrNull() ?: return null
        // Parse every element to a grounded Place, keeping only the named/classifiable ones.
        val all = ArrayList<Place>(elements.length())
        for (i in 0 until elements.length()) {
            val el = elements.optJSONObject(i) ?: continue
            all += toPlace(el, origin) ?: continue
        }
        return all.sortedBy { it.distanceKm ?: Double.MAX_VALUE }
    }

    /** One OSM element → a grounded [Place], or null if it isn't named/classifiable/locatable. */
    private fun toPlace(el: JSONObject, origin: LatLng): Place? {
        val tags = tagsOf(el)
        val name = tags["name"]?.ifBlank { null } ?: return null
        val kind = OsmClassifier.classify(tags) ?: return null
        val lat = coord(el, "lat") ?: return null
        val lng = coord(el, "lon") ?: return null
        // Dietary tags present and not explicitly "no" — same rule as the pack.
        val diets = tags.filter { it.key.startsWith("diet:") && it.value != "no" }
            .keys.map { it.removePrefix("diet:") }.toSet()
        return Place(
            id = "${el.optString("type")}${el.optLong("id")}".hashCode(),
            name = name,
            category = kind.category,
            subcategory = kind.subcategory,
            lat = lat,
            lng = lng,
            address = OsmClassifier.address(tags),
            cuisine = tags["cuisine"],
            religion = tags["religion"],
            summary = null,   // filled in later, only for wiki-linked places — see enrichStories
            phone = tags["phone"] ?: tags["contact:phone"],
            website = tags["website"] ?: tags["contact:website"],
            wikipedia = tags["wikipedia"],   // carried so we can fetch a grounded "story" later
            wikidata = tags["wikidata"],
            // How the app names where you are, on-device: no reverse-geocode, just the
            // locality OSM already records against this place.
            suburb = tags["addr:suburb"]?.ifBlank { null } ?: tags["addr:city"]?.ifBlank { null },
            // "Is it open?" — the question travellers ask most, answered on-device.
            openingHours = tags["opening_hours"]?.ifBlank { null },
            diets = diets,
            distanceKm = haversineKm(origin, LatLng(lat, lng)),
        )
    }

    private fun passesFilters(place: Place, spec: SearchSpec): Boolean {
        if (spec.category != null && place.category != spec.category) return false
        if (spec.religion != null && place.religion != spec.religion) return false
        // Diet applies to food only (a halal user's parks aren't diet-filtered).
        if (spec.diets.isNotEmpty() && place.category == "food" && spec.diets.none { it in place.diets }) return false
        return true
    }

    private fun matchesText(place: Place, terms: List<String>): Boolean {
        val hay = (place.name + " " + (place.cuisine ?: "") + " " + (place.subcategory ?: "")).lowercase()
        return terms.any { hay.contains(it.lowercase()) }
    }

    /** A node carries its own lat/lon; a way/relation carries a single "center" point. */
    private fun coord(el: JSONObject, key: String): Double? {
        if (el.has(key)) return el.optDouble(key).takeIf { !it.isNaN() }
        val center = el.optJSONObject("center") ?: return null
        return if (center.has(key)) center.optDouble(key).takeIf { !it.isNaN() } else null
    }

    private fun tagsOf(el: JSONObject): Map<String, String> {
        val t = el.optJSONObject("tags") ?: return emptyMap()
        val map = HashMap<String, String>(t.length())
        for (k in t.keys()) map[k] = t.optString(k)
        return map
    }

    /**
     * POST the query to Overpass. Tries every mirror, then — because these are free,
     * heavily shared servers that routinely answer "too busy" — waits briefly and tries
     * them all once more. Two quick rounds turn most transient 504s into a normal result
     * without making a genuine outage feel like a hang. A total failure returns null, and
     * the caller says honestly that the map service didn't answer.
     */
    private suspend fun overpassPost(body: String): String? {
        for (attempt in 0 until OVERPASS_ATTEMPTS) {
            if (attempt > 0) delay(OVERPASS_RETRY_DELAY_MS)   // cancellation-aware pause
            overpassRound(body)?.let { return it }
        }
        return null
    }

    /** One pass over every mirror; the first that answers 200 wins. */
    private fun overpassRound(body: String): String? {
        for (endpoint in OVERPASS_ENDPOINTS) {
            var conn: HttpURLConnection? = null
            try {
                conn = (URL(endpoint).openConnection() as HttpURLConnection).apply {
                    requestMethod = "POST"
                    // A tighter connect timeout so a dead network (e.g. a stale VPN tunnel) falls
                    // back to the offline pack quickly instead of hanging the home for 20 s.
                    connectTimeout = 10_000
                    // A few square km of map is a small query — if a mirror hasn't answered in
                    // 30 s it's struggling, and moving on beats waiting. This also bounds the
                    // retry above: worst case is 2 mirrors x 2 rounds, not six minutes.
                    readTimeout = 30_000
                    doOutput = true
                    setRequestProperty("User-Agent", USER_AGENT)
                    setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
                }
                conn.outputStream.use { it.write(("data=" + URLEncoder.encode(body, "UTF-8")).toByteArray()) }
                if (conn.responseCode == 200) {
                    return conn.inputStream.bufferedReader().use { it.readText() }
                }
                conn.errorStream?.close()
            } catch (e: CancellationException) {
                throw e                       // honour coroutine cancellation promptly
            } catch (e: Exception) {
                // try the next mirror
            } finally {
                conn?.disconnect()
            }
        }
        return null
    }
}
