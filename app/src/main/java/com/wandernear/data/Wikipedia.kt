package com.wandernear.data

import android.net.Uri
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * Fetches a place's "story" — the short Wikipedia summary — the ON-DEVICE twin of
 * `pipeline/enrich_wikipedia.py`. Used to enrich live results (and, later, downloaded
 * packs) so ANY city gets the rich write-ups only bundled Melbourne had before.
 *
 * GROUNDING (rule #5): a summary is fetched ONLY for a place OpenStreetMap ALREADY links
 * to Wikipedia (a `wikipedia` or `wikidata` tag). We never guess which article belongs to
 * a place — guessing is how you attach the wrong facts. Disambiguation pages and empty
 * extracts are dropped. A place with no link simply gets no story.
 *
 * PRIVACY: the only thing sent is the public place's article TITLE / Wikidata id (from the
 * map data) — never the user's location or query.
 */
object Wikipedia {

    private const val USER_AGENT = "WanderNear/0.1 (sowat.rafi.98@gmail.com)"

    /** A place's grounded story + a link to the full article (for the CC BY-SA credit). */
    data class Summary(val text: String, val url: String?)

    /**
     * Summaries already fetched this session, keyed by the OSM tags that produced them.
     * An article doesn't change while you're using the app, and the same places come back
     * every time you revisit the home or ask a similar question — without this, each visit
     * re-fetched write-ups we already had. Bounded in practice by how many wiki-linked
     * places one session actually shows (a handful), and gone when the process ends.
     */
    private val cache = java.util.concurrent.ConcurrentHashMap<String, Summary>()

    /**
     * The summary for a place's OSM wiki links, or null if it has none, none resolves, or
     * the article is a disambiguation / has no extract.
     */
    suspend fun summaryFor(wikipediaTag: String?, wikidataId: String?): Summary? =
        withContext(Dispatchers.IO) {
            val key = "${wikipediaTag.orEmpty()}|${wikidataId.orEmpty()}"
            cache[key]?.let { return@withContext it }
            val (lang, title) = resolveTitle(wikipediaTag, wikidataId) ?: return@withContext null
            fetchSummary(lang, title)?.also { cache[key] = it }
        }

    /** Which Wikipedia article (language + title) a place points to. Prefers the direct
     *  `wikipedia` tag; falls back to resolving the `wikidata` id to its English article. */
    private suspend fun resolveTitle(wikipediaTag: String?, wikidataId: String?): Pair<String, String>? {
        wikipediaTag?.takeIf { ":" in it }?.let {
            val (lang, title) = it.split(":", limit = 2)
            if (lang.isNotBlank() && title.isNotBlank()) return lang to title
        }
        wikidataId?.takeIf { it.isNotBlank() }?.let { return titleFromWikidata(it) }
        return null
    }

    /** Resolve a Wikidata id (e.g. "Q1234") to its English Wikipedia article title. */
    private suspend fun titleFromWikidata(qid: String): Pair<String, String>? {
        val body = httpGet("https://www.wikidata.org/wiki/Special:EntityData/$qid.json") ?: return null
        val title = runCatching {
            JSONObject(body).getJSONObject("entities").getJSONObject(qid)
                .getJSONObject("sitelinks").getJSONObject("enwiki").getString("title")
        }.getOrNull()?.ifBlank { null } ?: return null
        return "en" to title
    }

    /** The short article summary for one Wikipedia page (REST API), or null. */
    private suspend fun fetchSummary(lang: String, title: String): Summary? {
        // Encode the title as a single path segment (spaces → "_", then percent-encode the
        // rest) — matches the Python's urllib.quote(safe=""), NOT form-encoding.
        val encoded = Uri.encode(title.replace(' ', '_'))
        val body = httpGet("https://$lang.wikipedia.org/api/rest_v1/page/summary/$encoded") ?: return null
        val o = runCatching { JSONObject(body) }.getOrNull() ?: return null
        // Disambiguation pages and empty summaries carry no useful "why".
        if (o.optString("type") == "disambiguation") return null
        val extract = o.optString("extract").ifBlank { return null }
        val url = o.optJSONObject("content_urls")?.optJSONObject("desktop")?.optString("page")?.ifBlank { null }
        return Summary(extract, url)
    }

    /** A small GET returning the body as text, or null on any failure (incl. a 404). */
    private fun httpGet(url: String): String? {
        var conn: HttpURLConnection? = null
        return try {
            conn = (URL(url).openConnection() as HttpURLConnection).apply {
                connectTimeout = 10_000
                readTimeout = 20_000
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
}
