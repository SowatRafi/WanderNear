package com.wandernear.data

import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import com.wandernear.core.model.LatLng
import com.wandernear.core.model.Place
import com.wandernear.core.model.haversineKm
import com.wandernear.core.retrieval.SearchSpec
import java.io.File

/**
 * Reads the bundled, read-only Melbourne data pack.
 *
 * The pack ships in the app's assets; on first use it's copied once into private
 * storage and opened read-only. We open SQLite directly (not Room) because the
 * pack is read-only and already has the full-text search index we built in the
 * data pipeline. This is the single source of truth — the app never invents data.
 */
class CityDatabase(private val context: Context) {

    private fun open(): SQLiteDatabase {
        val dbFile = File(context.filesDir, DB_NAME)
        if (!dbFile.exists()) {
            context.assets.open(DB_NAME).use { input ->
                dbFile.outputStream().use { output -> input.copyTo(output) }
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
        val sql = "SELECT p.id, p.name, p.category, p.subcategory, p.lat, p.lng, " +
            "p.address, p.cuisine, p.religion, p.summary FROM $from $whereSql LIMIT 300"
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

    private companion object {
        const val DB_NAME = "melbourne.db"
    }
}
