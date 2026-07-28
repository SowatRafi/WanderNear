package com.wandernear.data

import android.content.Context
import com.wandernear.core.model.LatLng
import com.wandernear.core.model.haversineKm
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/**
 * The list of ready-made data packs we publish, and how to download one.
 *
 * This replaces building a pack on the phone by calling Overpass. Packs are built ahead
 * of time from free OpenStreetMap extracts (see `pipeline/build_pack.py` and DEPLOY.md)
 * and served as ordinary static files, which means:
 *
 *  - **No per-user API calls**, so nothing to rate-limit and nobody's usage policy to
 *    breach — Overpass explicitly excludes commercial apps from its public instances.
 *  - **Nothing to go down.** A free community server having a bad hour used to leave the
 *    app with nothing to show.
 *  - **Much less work on the phone**: a plain file download instead of geocoding, a
 *    streaming parse and assembling a database.
 *
 * The catalogue carries each pack's URL, so hosting can move without an app update.
 */
object PackCatalogue {

    private const val USER_AGENT = "WanderNear/0.1 (sowat.rafi.98@gmail.com)"

    /** Where the catalogue lives. See [catalogueUrl] for the override used in testing. */
    private const val DEFAULT_CATALOGUE_URL = "https://packs.wandernear.app/packs.json"

    /**
     * The catalogue URL, overridable by dropping a URL into `filesDir/catalogue_url.txt`.
     *
     * That hook exists so a build can be pointed at a local or staging host without
     * shipping a different APK — and it is the seed of the "switch service without a
     * software update" ability that OpenStreetMap's policies ask for.
     */
    fun catalogueUrl(context: Context): String {
        val override = File(context.filesDir, "catalogue_url.txt")
        return runCatching { override.readText().trim() }.getOrNull()?.takeIf { it.isNotEmpty() }
            ?: DEFAULT_CATALOGUE_URL
    }

    /** One downloadable pack, exactly as described by the published catalogue. */
    data class Pack(
        val id: String,
        val name: String,
        val country: String?,
        val south: Double,
        val west: Double,
        val north: Double,
        val east: Double,
        val places: Int,
        val bytes: Long,
        val built: String?,
        val url: String,
    ) {
        /** True when [fix] falls inside this pack's area — i.e. it covers where you are. */
        fun covers(fix: LatLng): Boolean =
            fix.lat in south..north && fix.lng in west..east

        val centre: LatLng get() = LatLng((south + north) / 2.0, (west + east) / 2.0)

        /** Roughly how big the area is, for preferring the tighter of two that both fit. */
        val spread: Double get() = (north - south) * (east - west)

        /** "21,149 places · 4.2 MB" — the two facts that decide whether to download it. */
        val summary: String
            get() = "%,d places · %.1f MB".format(places, bytes / 1048576.0)

        /** The filename we store it under. The id comes from our own catalogue, but it
         *  reaches us over the network, so it is stripped to safe characters before it is
         *  ever used as a path. */
        val fileName: String
            get() = id.map { if (it.isLetterOrDigit() || it == '-' || it == '_') it else '-' }
                .joinToString("").take(64) + ".db"
    }

    /** Everything we publish, or null if the catalogue couldn't be fetched or parsed. */
    suspend fun load(context: Context): List<Pack>? = withContext(Dispatchers.IO) {
        val body = httpGet(catalogueUrl(context)) ?: return@withContext null
        runCatching {
            val array = JSONObject(body).getJSONArray("packs")
            (0 until array.length()).mapNotNull { i ->
                val o = array.optJSONObject(i) ?: return@mapNotNull null
                Pack(
                    id = o.getString("id"),
                    name = o.getString("name"),
                    country = o.optString("country").ifBlank { null },
                    south = o.getDouble("south"),
                    west = o.getDouble("west"),
                    north = o.getDouble("north"),
                    east = o.getDouble("east"),
                    places = o.optInt("places"),
                    bytes = o.optLong("bytes"),
                    built = o.optString("built").ifBlank { null },
                    url = o.getString("url"),
                )
            }
        }.getOrNull()
    }

    /**
     * [packs] ordered for someone standing at [fix]: the ones that actually cover them
     * first (tightest first, since a city pack beats a whole-state one), then the rest by
     * distance. With no fix, alphabetical — we don't pretend to know what's nearby.
     */
    fun forUser(packs: List<Pack>, fix: LatLng?): List<Pack> {
        if (fix == null) return packs.sortedBy { it.name }
        val (covering, others) = packs.partition { it.covers(fix) }
        return covering.sortedBy { it.spread } +
            others.sortedBy { haversineKm(fix, it.centre) }
    }

    /** A small GET returning the body as text, or null on any failure. The catalogue is
     *  tiny, so short timeouts are right: a slow answer here would stall the screen. */
    private fun httpGet(url: String): String? {
        var conn: HttpURLConnection? = null
        return try {
            conn = (URL(url).openConnection() as HttpURLConnection).apply {
                connectTimeout = 10_000
                readTimeout = 15_000
                setRequestProperty("User-Agent", USER_AGENT)
            }
            if (conn.responseCode == 200) conn.inputStream.bufferedReader().use { it.readText() }
            else { conn.errorStream?.close(); null }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            null
        } finally {
            conn?.disconnect()
        }
    }

    sealed interface Result {
        data class Success(val file: File) : Result
        data class Failure(val message: String) : Result
    }

    /**
     * Download [pack] into `filesDir/packs/`, reporting progress 0..1.
     *
     * Written to a `.part` file and renamed only once complete, so an interrupted download
     * can never leave a half-written database that the app would later try to open.
     */
    suspend fun download(
        context: Context,
        pack: Pack,
        onProgress: (Float) -> Unit,
    ): Result = withContext(Dispatchers.IO) {
        val dir = CityPackBuilder.packsDir(context)
        val target = File(dir, pack.fileName)
        val part = File(dir, pack.fileName + ".part")
        part.delete()
        var conn: HttpURLConnection? = null
        try {
            conn = (URL(pack.url).openConnection() as HttpURLConnection).apply {
                connectTimeout = 15_000
                readTimeout = 60_000
                setRequestProperty("User-Agent", USER_AGENT)
            }
            if (conn.responseCode != 200) {
                return@withContext Result.Failure("Couldn't download ${pack.name} (${conn.responseCode}).")
            }
            // The catalogue's size is only a hint for the progress bar; the real total is
            // whatever the server sends.
            val total = conn.contentLengthLong.takeIf { it > 0 } ?: pack.bytes
            conn.inputStream.use { input ->
                part.outputStream().use { output ->
                    val buffer = ByteArray(64 * 1024)
                    var written = 0L
                    while (true) {
                        val read = input.read(buffer)
                        if (read < 0) break
                        output.write(buffer, 0, read)
                        written += read
                        if (total > 0) onProgress((written.toFloat() / total).coerceIn(0f, 1f))
                    }
                }
            }
            target.delete()
            if (!part.renameTo(target)) return@withContext Result.Failure("Couldn't save ${pack.name}.")
            Result.Success(target)
        } catch (e: CancellationException) {
            part.delete()          // cancelled by the user — leave nothing behind
            throw e
        } catch (e: Exception) {
            part.delete()
            Result.Failure("Couldn't download ${pack.name} — check your connection.")
        } finally {
            conn?.disconnect()
        }
    }
}
