package com.wandernear.data

import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
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
        // in filesDir/packs/. This is what lets us open ANY active city, not just
        // Melbourne. Seeds atomically (temp file, then rename) across all instances
        // (chat + Travel Mode) so a concurrent open never sees a half-written pack.
        if (name == BUNDLED_PACK && !dbFile.exists()) {
            synchronized(COPY_LOCK) {
                if (!dbFile.exists()) {                       // re-check under the lock
                    val tmp = File(context.filesDir, "$BUNDLED_PACK.tmp")
                    context.assets.open(BUNDLED_PACK).use { input ->
                        tmp.outputStream().use { output -> input.copyTo(output) }
                    }
                    if (!tmp.renameTo(dbFile)) {
                        tmp.delete()
                        throw IllegalStateException("Failed to install $BUNDLED_PACK")
                    }
                }
            }
        }
        return SQLiteDatabase.openDatabase(dbFile.path, null, SQLiteDatabase.OPEN_READONLY)
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
        private val COPY_LOCK = Any()   // guards the one-time assets → filesDir copy

        // The place columns every query selects, in the exact order [readPlaces]
        // reads them (index 0..10). Kept in one place so the order can never drift.
        private const val PLACE_COLS = "p.id, p.name, p.category, p.subcategory, p.lat, p.lng, " +
            "p.address, p.cuisine, p.religion, p.summary, p.phone"
    }
}
