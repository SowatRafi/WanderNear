package com.wandernear.data

import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import com.wandernear.core.model.CityEvent
import com.wandernear.core.model.CityInfo
import com.wandernear.core.model.LatLng
import com.wandernear.core.model.Place
import com.wandernear.core.model.haversineKm
import com.wandernear.core.retrieval.SearchSpec
import kotlin.math.cos
import java.io.File

/**
 * Reads the bundled, read-only Melbourne data pack.
 *
 * The pack ships in the app's assets; on first use it's copied once into private
 * storage and opened read-only. We open SQLite directly (not Room) because the
 * pack is read-only and already has the full-text search index we built in the
 * data pipeline. This is the single source of truth — the app never invents data.
 */
class CityDatabase(
    private val context: Context,
    // Which pack to read, as a path relative to filesDir. Defaults to the bundled
    // city; a downloaded pack is e.g. "packs/geelong_2456176.db".
    private val packName: String = BUNDLED_PACK,
) {

    private fun open(): SQLiteDatabase {
        // If a downloaded pack was set active but its file is gone (deleted/cleared),
        // fall back to the bundled city instead of crashing on a missing file.
        val name =
            if (packName != BUNDLED_PACK && !File(context.filesDir, packName).exists()) BUNDLED_PACK
            else packName
        val dbFile = File(context.filesDir, name)
        // Only the BUNDLED pack is seeded from assets; downloaded packs already live
        // in filesDir/packs/. This is what lets us open ANY active city, not just Melbourne.
        if (name == BUNDLED_PACK) seedBundled(dbFile)
        return SQLiteDatabase.openDatabase(dbFile.path, null, SQLiteDatabase.OPEN_READONLY)
    }

    /**
     * Install the bundled pack on first run — AND re-install it after an app update that
     * ships a newer one, tracked by a small version marker beside it.
     *
     * Without that version check the pack was copied exactly once, so a rebuilt pack (new
     * data, or a new column like `suburb`) never reached an existing install — it silently
     * kept the stale one, and a query for a new column would crash. Publishes atomically
     * (temp file, then rename) and is serialized across instances (chat + Travel Mode) so a
     * concurrent open never sees a half-written pack.
     */
    private fun seedBundled(dbFile: File) {
        val marker = File(context.filesDir, "$BUNDLED_PACK.version")
        fun installed(): Int? =
            if (marker.exists()) marker.runCatching { readText().trim().toInt() }.getOrNull() else null
        if (dbFile.exists() && installed() == BUNDLED_PACK_VERSION) return
        synchronized(COPY_LOCK) {
            if (dbFile.exists() && installed() == BUNDLED_PACK_VERSION) return   // re-check under the lock
            val tmp = File(context.filesDir, "$BUNDLED_PACK.tmp")
            context.assets.open(BUNDLED_PACK).use { input ->
                tmp.outputStream().use { output -> input.copyTo(output) }
            }
            if (dbFile.exists()) dbFile.delete()
            if (!tmp.renameTo(dbFile)) {
                tmp.delete()
                throw IllegalStateException("Failed to install $BUNDLED_PACK")
            }
            marker.writeText(BUNDLED_PACK_VERSION.toString())
        }
    }

    /**
     * Finds the best matching places for a search, ranked by distance from
     * [origin]. Returns at most [limit] places, or an empty list if nothing
     * matches (the caller then shows the honest "I don't have that" reply).
     */
    fun search(spec: SearchSpec, origin: LatLng, limit: Int = 5): List<Place> = open().use { db ->
        var candidates = query(db, spec, useFts = spec.ftsTerms.isNotEmpty())

        // If the free-text words matched nothing but we still have real filters
        // (e.g. "vegetarian food"), retry using just the filters so we don't
        // wrongly come up empty.
        val hasFilters = spec.category != null || spec.diets.isNotEmpty() || spec.religion != null
        if (candidates.isEmpty() && spec.ftsTerms.isNotEmpty() && hasFilters) {
            candidates = query(db, spec, useFts = false)
        }

        // SQLite has no distance function here, so rank the nearest few in Kotlin.
        val ranked = candidates
            .map { it.copy(distanceKm = haversineKm(origin, LatLng(it.lat, it.lng))) }
            .sortedBy { it.distanceKm }
            .take(limit)

        attachDiets(db, ranked)
    }

    /** Reads the pack's single city row — the facts shown on the City Info card. */
    fun cityInfo(): CityInfo? = open().use { db ->
        db.rawQuery("SELECT name, country, population FROM city LIMIT 1", null).use { c ->
            if (!c.moveToFirst()) return@use null
            CityInfo(
                name = c.getString(0),
                country = c.getStringOrNull(1),
                population = if (c.isNull(2)) null else c.getLong(2),
            )
        }
    }

    /**
     * The city's annual festivals, alphabetically. Empty for a pack built before
     * festivals existed, or for a city Wikipedia has no festival category for — the
     * caller then shows nothing at all rather than an empty section or a guess.
     */
    fun festivals(limit: Int = 30): List<CityEvent> = open().use { db ->
        try {
            db.rawQuery(
                "SELECT name, summary, summary_url FROM event ORDER BY name LIMIT $limit", null,
            ).use { c ->
                val out = ArrayList<CityEvent>(c.count)
                while (c.moveToNext()) {
                    out += CityEvent(c.getString(0), c.getStringOrNull(1), c.getStringOrNull(2))
                }
                out
            }
        } catch (e: Exception) {
            emptyList()   // an older pack without the table shouldn't break the home screen
        }
    }

    /**
     * The active city's centre — the sensible "near me" fallback when there's no GPS
     * fix, for ANY city. We use the CENTROID of the places, not the bounding-box
     * midpoint: a big/irregular admin boundary (e.g. Greater Melbourne) has a midpoint
     * far out in the suburbs, whereas the average place location sits where the data
     * actually is — the city core.
     */
    fun cityCenter(): LatLng? = open().use { db ->
        db.rawQuery("SELECT AVG(lat), AVG(lng) FROM place", null).use { c ->
            if (!c.moveToFirst() || c.isNull(0) || c.isNull(1)) return@use null
            LatLng(c.getDouble(0), c.getDouble(1))
        }
    }

    /**
     * The suburb of the nearest place that has one, if it's within [maxKm] of [origin]
     * — the ON-DEVICE "which suburb am I in". No GPS ever leaves the phone (unlike a
     * web reverse-geocode). Returns null (→ caller shows the pack city) when we're
     * outside the pack's area or the pack has no nearby addr:suburb data. Grounded:
     * a real OSM addr:suburb. The [maxKm] guard means a fix in a DIFFERENT city (e.g.
     * Sydney with the Melbourne pack) yields null, never a wrong "suburb in city" claim.
     */
    fun nearestSuburb(origin: LatLng, maxKm: Double): String? = open().use { db ->
        // Rough bounding box first so we don't scan all rows (lat/lng aren't indexed).
        val dLat = maxKm / 111.0
        val dLng = maxKm / (111.0 * cos(Math.toRadians(origin.lat)).coerceAtLeast(0.01))
        val args = arrayOf(
            (origin.lat - dLat).toString(), (origin.lat + dLat).toString(),
            (origin.lng - dLng).toString(), (origin.lng + dLng).toString(),
        )
        try {
            db.rawQuery(
                "SELECT suburb, lat, lng FROM place WHERE suburb IS NOT NULL " +
                    "AND lat BETWEEN ? AND ? AND lng BETWEEN ? AND ?",
                args,
            ).use { c ->
                var best: String? = null
                var bestKm = Double.MAX_VALUE
                while (c.moveToNext()) {
                    val km = haversineKm(origin, LatLng(c.getDouble(1), c.getDouble(2)))
                    if (km < bestKm) { bestKm = km; best = c.getString(0) }
                }
                if (bestKm <= maxKm) best else null
            }
        } catch (e: Exception) {
            // A pack built before the `suburb` column exists (an older download) has no
            // such column — degrade to showing the city name rather than crashing.
            null
        }
    }

    /**
     * The single nearest place in each of [categories] — the "daily needs near you"
     * and "around you now" cards. Every result is a real retrieved row; a category the
     * pack has nothing for is simply omitted (we never pad the list).
     *
     * [maxKm] null (the default) means "however far it takes": the nearest police
     * station or hospital is worth pointing at even from 40 km away. Pass a radius for
     * the things that only make sense close by — a café 30 km off isn't "around you" —
     * and the query then pre-filters by bounding box instead of measuring every row in
     * the category, which matters when Travel Mode runs this every couple of minutes.
     */
    fun nearestEssentials(
        origin: LatLng,
        categories: List<String>,
        maxKm: Double? = null,
    ): List<Place> = open().use { db ->
        // Same rough box trick as nearbyNotable — lat/lng aren't indexed.
        val boxSql: String
        val boxArgs: Array<String>
        if (maxKm == null) {
            boxSql = ""
            boxArgs = emptyArray()
        } else {
            val dLat = maxKm / 111.0
            val dLng = maxKm / (111.0 * cos(Math.toRadians(origin.lat)).coerceAtLeast(0.01))
            boxSql = " AND p.lat BETWEEN ? AND ? AND p.lng BETWEEN ? AND ?"
            boxArgs = arrayOf(
                (origin.lat - dLat).toString(), (origin.lat + dLat).toString(),
                (origin.lng - dLng).toString(), (origin.lng + dLng).toString(),
            )
        }
        categories.mapNotNull { category ->
            db.rawQuery("SELECT $PLACE_COLS FROM place p WHERE p.category = ?$boxSql", arrayOf(category) + boxArgs)
                .use { readPlaces(it) }
                .map { it.copy(distanceKm = haversineKm(origin, LatLng(it.lat, it.lng))) }
                .minByOrNull { it.distanceKm ?: Double.MAX_VALUE }
                // The box is a square; drop the corners so "within 2 km" really is.
                ?.takeIf { maxKm == null || (it.distanceKm ?: Double.MAX_VALUE) <= maxKm }
        }
    }

    /**
     * Grounded "worth a visit near you" query for Travel Mode: notable places (a
     * Wikipedia write-up, or an attraction) within [radiusKm] of [origin], nearest
     * first. Every result is a real retrieved row — never invented.
     */
    fun nearbyNotable(origin: LatLng, radiusKm: Double, limit: Int = 5): List<Place> = open().use { db ->
        // Rough bounding box so we don't scan all ~20k rows (lat/lng aren't indexed).
        val dLat = radiusKm / 111.0
        val dLng = radiusKm / (111.0 * cos(Math.toRadians(origin.lat)).coerceAtLeast(0.01))
        val sql = "SELECT $PLACE_COLS FROM place p " +
            "WHERE (p.summary IS NOT NULL OR p.category = 'attraction') " +
            "AND p.lat BETWEEN ? AND ? AND p.lng BETWEEN ? AND ?"
        val args = arrayOf(
            (origin.lat - dLat).toString(), (origin.lat + dLat).toString(),
            (origin.lng - dLng).toString(), (origin.lng + dLng).toString(),
        )
        db.rawQuery(sql, args).use { readPlaces(it) }
            .map { it.copy(distanceKm = haversineKm(origin, LatLng(it.lat, it.lng))) }
            .filter { (it.distanceKm ?: Double.MAX_VALUE) <= radiusKm }
            .sortedBy { it.distanceKm }
            .take(limit)
    }

    /**
     * Nearest police stations to [origin] (the Safety section). Grounded: every
     * row is a real `amenity=police` place from the pack — we never invent one.
     * Unlike Travel Mode's [nearbyNotable] there's no radius cap: in a quiet area
     * the closest station can be far, and we still want to point the user to it.
     */
    fun nearbyPolice(origin: LatLng, limit: Int = 3): List<Place> = open().use { db ->
        val sql = "SELECT $PLACE_COLS FROM place p WHERE p.category = 'safety'"
        db.rawQuery(sql, null).use { readPlaces(it) }
            .map { it.copy(distanceKm = haversineKm(origin, LatLng(it.lat, it.lng))) }
            .sortedBy { it.distanceKm }
            .take(limit)
    }

    /**
     * The nearest places of worship of [religion] (an OSM `religion` tag value, e.g.
     * "muslim", "christian", "hindu") to [origin] — grounded: every row is a real
     * `amenity=place_of_worship` place from the pack. Each carries its OSM website/phone
     * (often null), so the caller can point the user to the place itself for its service
     * or prayer times, which no free source lists. Like [nearbyPolice] there's no radius
     * cap — the nearest one is worth showing even a few km out.
     */
    fun nearestWorship(origin: LatLng, religion: String, limit: Int = 1): List<Place> = open().use { db ->
        val sql = "SELECT $PLACE_COLS FROM place p WHERE p.category = 'worship' AND p.religion = ?"
        db.rawQuery(sql, arrayOf(religion)).use { readPlaces(it) }
            .map { it.copy(distanceKm = haversineKm(origin, LatLng(it.lat, it.lng))) }
            .sortedBy { it.distanceKm }
            .take(limit)
    }

    /**
     * "For you": nearby places in the user's chosen interest [categories], nearest first.
     * [diets] filter only FOOD rows (a halal user's food is halal, but their parks and
     * museums aren't food-filtered). Grounded — every row is real. Empty when the user
     * has picked no interests, so the caller simply shows the generic suggestions instead.
     */
    fun forYou(origin: LatLng, categories: List<String>, diets: Set<String>, limit: Int = 5): List<Place> =
        open().use { db ->
            if (categories.isEmpty()) return@use emptyList()
            val args = ArrayList<String>()
            val catPlaceholders = categories.joinToString(",") { "?" }
            args += categories
            var sql = "SELECT $PLACE_COLS FROM place p WHERE p.category IN ($catPlaceholders)"
            if (diets.isNotEmpty()) {
                val dietPlaceholders = diets.joinToString(",") { "?" }
                // Apply the diet filter to food rows only; other categories pass through.
                sql += " AND (p.category != 'food' OR EXISTS (SELECT 1 FROM place_diet d " +
                    "WHERE d.place_id = p.id AND d.diet IN ($dietPlaceholders) " +
                    "AND (d.value IS NULL OR d.value != 'no')))"
                args += diets
            }
            db.rawQuery(sql, args.toTypedArray()).use { readPlaces(it) }
                .map { it.copy(distanceKm = haversineKm(origin, LatLng(it.lat, it.lng))) }
                .sortedBy { it.distanceKm }
                .take(limit)
        }

    /** Runs one search query and reads the rows into [Place] objects. */
    private fun query(db: SQLiteDatabase, spec: SearchSpec, useFts: Boolean): List<Place> {
        val where = mutableListOf<String>()
        val args = mutableListOf<String>()

        val from = if (useFts) {
            where += "places_fts MATCH ?"
            args += spec.ftsTerms.joinToString(" OR ")   // any of the words
            "places_fts f JOIN place p ON p.id = f.docid"
        } else {
            "place p"
        }

        spec.category?.let { where += "p.category = ?"; args += it }
        spec.religion?.let { where += "p.religion = ?"; args += it }
        if (spec.diets.isNotEmpty()) {
            val placeholders = spec.diets.joinToString(",") { "?" }
            where += "EXISTS (SELECT 1 FROM place_diet d WHERE d.place_id = p.id " +
                "AND d.diet IN ($placeholders) AND (d.value IS NULL OR d.value != 'no'))"
            args += spec.diets
        }

        // No search words and no filters → nothing to go on; refuse honestly
        // rather than dumping the whole database.
        if (where.isEmpty()) return emptyList()

        val whereSql = "WHERE " + where.joinToString(" AND ")
        // Cap candidates; we only need the nearest handful after ranking.
        val sql = "SELECT $PLACE_COLS FROM $from $whereSql LIMIT 300"
        return db.rawQuery(sql, args.toTypedArray()).use { readPlaces(it) }
    }

    private fun readPlaces(c: Cursor): List<Place> {
        val out = ArrayList<Place>(c.count)
        while (c.moveToNext()) {
            out += Place(
                id = c.getInt(0),
                name = c.getString(1),
                category = c.getString(2),
                subcategory = c.getStringOrNull(3),
                lat = c.getDouble(4),
                lng = c.getDouble(5),
                address = c.getStringOrNull(6),
                cuisine = c.getStringOrNull(7),
                religion = c.getStringOrNull(8),
                summary = c.getStringOrNull(9),
                phone = c.getStringOrNull(10),
                website = c.getStringOrNull(11),
            )
        }
        return out
    }

    /** Loads dietary tags for just the handful of places we're about to show. */
    private fun attachDiets(db: SQLiteDatabase, places: List<Place>): List<Place> {
        if (places.isEmpty()) return places
        val ids = places.joinToString(",") { it.id.toString() }   // our own ints, safe
        val byId = HashMap<Int, MutableSet<String>>()
        db.rawQuery(
            "SELECT place_id, diet FROM place_diet WHERE place_id IN ($ids) " +
                "AND (value IS NULL OR value != 'no')",
            null,
        ).use { c ->
            while (c.moveToNext()) {
                byId.getOrPut(c.getInt(0)) { mutableSetOf() }.add(c.getString(1))
            }
        }
        return places.map { it.copy(diets = byId[it.id] ?: emptySet()) }
    }

    private fun Cursor.getStringOrNull(i: Int): String? = if (isNull(i)) null else getString(i)

    companion object {
        /** The bundled pack shipped in assets — the default active city. */
        const val BUNDLED_PACK = "melbourne.db"

        /**
         * Bump whenever the bundled pack in assets is rebuilt (new data OR a new column),
         * so an app update re-installs it over the copy a previous install left behind.
         * v2 = M6.5's rebuild (adds the `suburb` column + health/fuel/parking).
         * v3 = M6.6's rebuild (adds the `culture` category + the city's annual festivals).
         */
        const val BUNDLED_PACK_VERSION = 3
        private val COPY_LOCK = Any()   // guards the one-time assets → filesDir copy

        // The place columns every query selects, in the exact order [readPlaces]
        // reads them (index 0..11). Kept in one place so the order can never drift.
        private const val PLACE_COLS = "p.id, p.name, p.category, p.subcategory, p.lat, p.lng, " +
            "p.address, p.cuisine, p.religion, p.summary, p.phone, p.website"
    }
}
