package com.wandernear.data

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.util.JsonReader
import com.wandernear.core.pack.OsmClassifier
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * Builds a city data pack ON THE PHONE from the free OpenStreetMap APIs — the
 * on-device twin of the Python pipeline (`pipeline/fetch_osm.py` + `build_db.py`),
 * so the app can add ANY city with no server of ours.
 *
 * Flow: geocode the name (Nominatim) → fetch every place we care about (Overpass)
 * → classify each (shared [OsmClassifier], identical to the pipeline) → write a
 * SQLite pack + full-text index into `filesDir/packs/`. The big Overpass response
 * is STREAMED with [JsonReader] straight into SQLite, so memory stays flat even for
 * a large city (Melbourne's raw feed is ~8 MB / 40k features).
 *
 * Grounding is preserved: every row is a real OSM feature — we never invent data.
 * v1 deliberately SKIPS Wikipedia enrichment (the slow part); summaries stay NULL
 * and the pack is fully usable (search, categories, directions, safety, shopping).
 * Enrichment is a later background step.
 */
object CityPackBuilder {

    // Nominatim + Overpass REQUIRE a descriptive User-Agent (same as the pipeline).
    private const val USER_AGENT = "WanderNear/0.1 (sowat.rafi.98@gmail.com)"
    private const val NOMINATIM_URL = "https://nominatim.openstreetmap.org/search"
    private val OVERPASS_ENDPOINTS = listOf(
        "https://overpass-api.de/api/interpreter",
        "https://overpass.kumi.systems/api/interpreter",
    )
    // Same legally-required credit the pipeline writes into every pack.
    private const val ATTRIBUTION =
        "© OpenStreetMap contributors (ODbL). Cultural descriptions from Wikipedia (CC BY-SA 4.0)."

    /** Downloaded packs live here, separate from the bundled `melbourne.db`. */
    fun packsDir(context: Context): File = File(context.filesDir, "packs").apply { mkdirs() }

    /** Outcome of a build: the finished pack + counts, or a friendly error message. */
    sealed interface Result {
        data class Success(val file: File, val cityName: String, val placeCount: Int) : Result
        data class Failure(val message: String) : Result
    }

    /**
     * Build a pack for [match] — one of the areas [find] returned, so the user has
     * already confirmed WHICH city, and we never geocode twice. [onProgress] reports
     * a rough 0f..1f fraction. Does all IO on [Dispatchers.IO] and NEVER throws —
     * every problem comes back as [Result.Failure].
     */
    suspend fun build(
        context: Context,
        match: Match,
        onProgress: (Float) -> Unit = {},
    ): Result = withContext(Dispatchers.IO) {
        var tmp: File? = null
        try {
            onProgress(0.03f)
            val areaId = OsmClassifier.overpassAreaId(match.osmType, match.osmId)
                ?: return@withContext Result.Failure("\"${match.shortLabel}\" isn't a mappable area — try a city or town name.")

            onProgress(0.1f)
            val connection = fetchOverpass(OsmClassifier.overpassBody(areaId))
                ?: return@withContext Result.Failure("Map servers are busy right now — please try again later.")

            // The filename includes the OSM area id, so two same-named cities
            // (Paris, France vs Paris, Texas) never collide — while rebuilding the
            // SAME city reuses the same file.
            val outFile = File(packsDir(context), slug(match.label) + "_" + match.osmId + ".db")
            val part = File(outFile.path + ".part").also { tmp = it }
            deletePack(part)

            val count = try {
                writePack(context, part, match, connection, onProgress)
            } finally {
                connection.disconnect()
            }

            if (count == 0) {
                deletePack(part)
                return@withContext Result.Failure("No places found for \"${match.shortLabel}\".")
            }
            // Publish atomically: only a fully-built pack ever gets the real name.
            deletePack(outFile)
            if (!part.renameTo(outFile)) {
                deletePack(part)
                return@withContext Result.Failure("Couldn't save the pack to storage.")
            }
            deletePack(part)     // the renamed .db is live; drop the .part's leftover -journal sidecar
            onProgress(1f)
            Result.Success(outFile, match.label, count)
        } catch (e: CancellationException) {
            tmp?.let { deletePack(it) }   // cancelled build → drop the partial pack, then propagate
            throw e
        } catch (e: Exception) {
            tmp?.let { deletePack(it) }   // a failed build leaves no orphaned files behind
            Result.Failure("Something went wrong: ${e.message ?: e.javaClass.simpleName}")
        }
    }

    // ---- Searching for a city (Nominatim) --------------------------------------

    // Only these can scope an Overpass area query — see [find].
    private val AREA_TYPES = setOf("relation", "way")

    /**
     * One real area OpenStreetMap returned for a search: what the user picks from,
     * and what [build] then uses. [label] is Nominatim's own `display_name`, shown
     * verbatim so "Paris, Texas" can never be mistaken for "Paris, France" — we
     * never re-word it. The rest is what the pack's `city` row needs.
     */
    class Match internal constructor(
        val label: String,
        internal val osmType: String, internal val osmId: Long,
        internal val country: String?, internal val population: Long?,
        internal val south: Double, internal val north: Double,
        internal val west: Double, internal val east: Double,
    ) {
        /** Just the leading name, for headings — e.g. "Geelong". */
        val shortLabel: String get() = label.substringBefore(',').trim()
    }

    /**
     * Search OpenStreetMap for [query] and return the real AREAS it matched, at most
     * [limit]. NEVER throws: an empty list means "nothing matched, or we couldn't
     * reach the server" — the caller says that honestly instead of guessing.
     *
     * Only relations and ways are kept: a plain node is a single point and can't
     * scope an Overpass area query, and Nominatim often ranks a city's node above its
     * boundary relation (which is why a bare "Geelong" used to fail). Returning
     * several matches — rather than silently taking the first — is what lets the user
     * confirm they're getting Paris, France and not Paris, Texas.
     *
     * PRIVACY: the only thing sent is the city name the user typed. Never their location.
     */
    suspend fun find(query: String, limit: Int = 5): List<Match> = withContext(Dispatchers.IO) {
        val url = "$NOMINATIM_URL?q=${URLEncoder.encode(query, "UTF-8")}" +
            "&format=json&limit=10&addressdetails=1&extratags=1"
        val body = httpGet(url) ?: return@withContext emptyList()
        // A malformed/unexpected response is treated as "no matches", never a crash.
        runCatching {
            val results = JSONArray(body)
            (0 until results.length())
                .map { results.getJSONObject(it) }
                .filter { it.optString("osm_type") in AREA_TYPES }
                .take(limit)
                .map { parseMatch(it) }
        }.getOrDefault(emptyList())
    }

    private fun parseMatch(o: JSONObject): Match {
        val bbox = o.getJSONArray("boundingbox")   // [south, north, west, east] as strings
        val address = o.optJSONObject("address")
        val extra = o.optJSONObject("extratags")
        return Match(
            label = o.getString("display_name"),
            osmType = o.getString("osm_type"),
            osmId = o.getLong("osm_id"),
            country = address?.optString("country")?.ifBlank { null },
            population = parsePopulation(extra?.optString("population")),
            south = bbox.getString(0).toDouble(),
            north = bbox.getString(1).toDouble(),
            west = bbox.getString(2).toDouble(),
            east = bbox.getString(3).toDouble(),
        )
    }

    /** "5,031,195" or "5031195.0" → 5031195; null/garbage → null (never guessed). */
    private fun parsePopulation(raw: String?): Long? =
        raw?.replace(",", "")?.substringBefore(".")?.toLongOrNull()

    // ---- Overpass fetch --------------------------------------------------------

    /**
     * POST the query to Overpass, trying each mirror and backing off on a busy
     * server (429/504). Returns a connected 200 [HttpURLConnection] to STREAM from
     * (the caller streams then disconnects), or null if every mirror failed.
     */
    private suspend fun fetchOverpass(body: String): HttpURLConnection? {
        for (endpoint in OVERPASS_ENDPOINTS) {
            for (attempt in 1..4) {
                try {
                    val conn = (URL(endpoint).openConnection() as HttpURLConnection).apply {
                        requestMethod = "POST"
                        connectTimeout = 30_000
                        readTimeout = 300_000
                        doOutput = true
                        setRequestProperty("User-Agent", USER_AGENT)
                        setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
                    }
                    conn.outputStream.use {
                        it.write(("data=" + URLEncoder.encode(body, "UTF-8")).toByteArray())
                    }
                    when (conn.responseCode) {
                        200 -> return conn
                        // Back off before RE-trying this mirror — but not after the last
                        // attempt (nothing follows it, so the wait would be dead time).
                        429, 504 -> { conn.disconnect(); if (attempt < 4) delay(5000L * attempt) }
                        else -> { conn.disconnect(); break }   // hard error → next mirror
                    }
                } catch (e: CancellationException) {
                    throw e                                    // honour coroutine cancellation promptly
                } catch (e: Exception) {
                    if (attempt < 4) delay(5000L * attempt)
                }
            }
        }
        return null
    }

    /** A small GET returning the response body as text, or null on any failure. */
    private fun httpGet(url: String): String? {
        var conn: HttpURLConnection? = null
        return try {
            conn = (URL(url).openConnection() as HttpURLConnection).apply {
                connectTimeout = 30_000
                readTimeout = 60_000
                setRequestProperty("User-Agent", USER_AGENT)
                // Ask for names in the PHONE's language. Without this, Nominatim answers
                // in each place's own language — searching "Kyoto" comes back as "京都市",
                // which we'd then store as the city's name and show on every heading.
                setRequestProperty("Accept-Language", Locale.getDefault().toLanguageTag())
            }
            if (conn.responseCode == 200) conn.inputStream.bufferedReader().use { it.readText() }
            else { conn.errorStream?.close(); null }   // drain the error body so the socket is freed
        } catch (e: Exception) {
            null
        } finally {
            conn?.disconnect()                          // release on every path, unlike the happy-only .use
        }
    }

    // ---- SQLite pack writing ---------------------------------------------------

    /**
     * Create the pack, insert the city row, then stream every OSM element into it.
     * Everything runs inside ONE transaction so ~20k inserts are fast. Returns the
     * number of places kept.
     */
    private fun writePack(
        context: Context, dbFile: File, match: Match,
        connection: HttpURLConnection, onProgress: (Float) -> Unit,
    ): Int {
        val db = SQLiteDatabase.openOrCreateDatabase(dbFile, null)
        try {
            applySchema(context, db)
            db.beginTransaction()
            try {
                insertCity(db, match)
                val count = connection.inputStream.use { input ->
                    JsonReader(InputStreamReader(input, Charsets.UTF_8)).use { reader ->
                        streamElements(reader, db, onProgress)
                    }
                }
                db.setTransactionSuccessful()
                return count
            } finally {
                // Always end the transaction: commits if setTransactionSuccessful ran
                // above, otherwise rolls back — a mid-stream error can't leave it open.
                db.endTransaction()
            }
        } finally {
            db.close()
        }
    }

    /** Run schema.sql (shipped as an asset) statement-by-statement. */
    private fun applySchema(context: Context, db: SQLiteDatabase) {
        val sql = context.assets.open("schema.sql").bufferedReader().use { it.readText() }
        // Strip `--` comments — WHOLE-LINE and INLINE — before splitting on ';'.
        // Some inline comments contain a ';' (e.g. "NULL when unknown; never guessed"),
        // which would otherwise cut a CREATE TABLE in half → SQLite "incomplete input".
        val stripped = sql.lines().joinToString("\n") { it.substringBefore("--") }
        stripped.split(";").map { it.trim() }.filter { it.isNotEmpty() }
            .forEach { db.execSQL(it) }
    }

    private fun insertCity(db: SQLiteDatabase, match: Match) {
        val cv = ContentValues().apply {
            put("id", 1)
            // OSM's own display_name, not the user's typing — so the city row says
            // exactly what the map data says (CityInfo.shortName trims it for headings).
            put("name", match.label)
            match.country?.let { put("country", it) }
            match.population?.let { put("population", it) }
            put("osm_type", match.osmType)
            put("osm_id", match.osmId)
            put("min_lat", match.south); put("min_lng", match.west)
            put("max_lat", match.north); put("max_lng", match.east)
            put("data_version", isoDate("yyyy-MM-dd"))
            put("fetched_at", isoDate("yyyy-MM-dd'T'HH:mm:ss'Z'"))
            put("attribution", ATTRIBUTION)
        }
        db.insert("city", null, cv)
    }

    /** Stream the `elements` array, inserting each place we keep. Returns the count. */
    private fun streamElements(reader: JsonReader, db: SQLiteDatabase, onProgress: (Float) -> Unit): Int {
        var kept = 0
        var seen = 0
        reader.beginObject()
        while (reader.hasNext()) {
            if (reader.nextName() == "elements") {
                reader.beginArray()
                while (reader.hasNext()) {
                    if (insertElement(db, readElement(reader))) kept++
                    // We don't know the total up front, so nudge progress asymptotically.
                    if (++seen % 1000 == 0) onProgress((0.1f + seen / 60_000f).coerceAtMost(0.95f))
                }
                reader.endArray()
            } else {
                reader.skipValue()
            }
        }
        reader.endObject()
        return kept
    }

    /** One parsed OSM element: id/type, a lat/lng, and its tags. */
    private class El {
        var type: String? = null
        var id: Long = 0
        var lat: Double? = null
        var lng: Double? = null
        val tags = HashMap<String, String>()
    }

    private fun readElement(reader: JsonReader): El {
        val el = El()
        var centerLat: Double? = null
        var centerLng: Double? = null
        reader.beginObject()
        while (reader.hasNext()) {
            when (reader.nextName()) {
                "type" -> el.type = reader.nextString()
                "id" -> el.id = reader.nextLong()
                "lat" -> el.lat = reader.nextDouble()
                "lon" -> el.lng = reader.nextDouble()
                "center" -> {
                    reader.beginObject()
                    while (reader.hasNext()) {
                        when (reader.nextName()) {
                            "lat" -> centerLat = reader.nextDouble()
                            "lon" -> centerLng = reader.nextDouble()
                            else -> reader.skipValue()
                        }
                    }
                    reader.endObject()
                }
                "tags" -> {
                    reader.beginObject()
                    while (reader.hasNext()) el.tags[reader.nextName()] = reader.nextString()
                    reader.endObject()
                }
                else -> reader.skipValue()
            }
        }
        reader.endObject()
        // Ways/relations carry their point under "center"; nodes have their own lat/lon.
        if (el.lat == null) el.lat = centerLat
        if (el.lng == null) el.lng = centerLng
        return el
    }

    /**
     * Insert one place if it's named, classifiable, and locatable — mirroring
     * `build_db.main`'s keep/skip rules. Also writes its diet tags and FTS row.
     * Returns true if a place was kept (false = skipped or duplicate OSM feature).
     */
    private fun insertElement(db: SQLiteDatabase, el: El): Boolean {
        // Skip missing OR empty/blank names — matches build_db.main's `if not name`,
        // so an on-device pack never shows a nameless card (the bundled pack doesn't).
        val name = el.tags["name"]?.ifBlank { null } ?: return false
        val kind = OsmClassifier.classify(el.tags) ?: return false
        val type = el.type ?: return false
        val lat = el.lat ?: return false
        val lng = el.lng ?: return false

        val place = ContentValues().apply {
            put("city_id", 1)
            put("osm_type", type)
            put("osm_id", el.id)
            put("name", name)
            put("category", kind.category)
            put("subcategory", kind.subcategory)
            put("lat", lat); put("lng", lng)
            address(el.tags)?.let { put("address", it) }
            el.tags["addr:suburb"]?.ifBlank { null }?.let { put("suburb", it) }
            el.tags["cuisine"]?.let { put("cuisine", it) }
            el.tags["religion"]?.let { put("religion", it) }
            el.tags["denomination"]?.let { put("denomination", it) }
            el.tags["opening_hours"]?.let { put("opening_hours", it) }
            (el.tags["phone"] ?: el.tags["contact:phone"])?.let { put("phone", it) }
            (el.tags["website"] ?: el.tags["contact:website"])?.let { put("website", it) }
            el.tags["wikidata"]?.let { put("wikidata_qid", it) }
            // summary / summary_url / summary_license / thumbnail_url: NULL in v1 (no enrichment).
        }
        // UNIQUE(osm_type, osm_id): a repeat OSM feature is ignored (rowid -1).
        val placeId = db.insertWithOnConflict("place", null, place, SQLiteDatabase.CONFLICT_IGNORE)
        if (placeId == -1L) return false

        for ((key, value) in el.tags) {
            if (key.startsWith("diet:")) {
                val diet = ContentValues().apply {
                    put("place_id", placeId)
                    put("diet", key.removePrefix("diet:"))
                    put("value", value)
                }
                db.insertWithOnConflict("place_diet", null, diet, SQLiteDatabase.CONFLICT_IGNORE)
            }
        }

        val fts = ContentValues().apply {
            put("docid", placeId)
            put("name", name)
            // summary NULL (no enrichment in v1)
            el.tags["cuisine"]?.let { put("cuisine", it) }
            put("subcategory", kind.subcategory)
        }
        db.insert("places_fts", null, fts)
        return true
    }

    /** Build a readable address from OSM addr:* tags — mirrors `build_db.address`. */
    private fun address(tags: Map<String, String>): String? {
        // Treat an empty tag as absent (like Python's `if p`), so we never emit a
        // stray segment like "Main St, , Geelong".
        fun tag(key: String) = tags[key]?.ifBlank { null }
        val street = listOfNotNull(tag("addr:housenumber"), tag("addr:street")).joinToString(" ")
        val parts = listOfNotNull(
            street.ifBlank { null }, tag("addr:suburb"), tag("addr:city"), tag("addr:postcode"),
        )
        return if (parts.isEmpty()) null else parts.joinToString(", ")
    }

    // ---- small helpers ---------------------------------------------------------

    /** Delete a pack file plus any SQLite sidecars (journal/wal/shm) it left behind. */
    private fun deletePack(file: File) {
        for (suffix in listOf("", "-journal", "-wal", "-shm")) File(file.path + suffix).delete()
    }

    /** A filesystem-safe short name, e.g. "Geelong, Victoria, Australia" → "geelong". */
    private fun slug(query: String): String =
        query.substringBefore(',').lowercase().replace(Regex("[^a-z0-9]+"), "").ifBlank { "city" }

    private fun isoDate(pattern: String): String =
        SimpleDateFormat(pattern, Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") }.format(Date())
}
