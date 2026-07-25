package com.wandernear.core.pack

/**
 * Pure-Kotlin port of the data pipeline's place classification and the Overpass
 * query that feeds it. See the reference implementation in `pipeline/build_db.py`
 * (`classify`) and `pipeline/fetch_osm.py` (`build_query`) — the same rules that
 * built the bundled Melbourne pack.
 *
 * It lives in `core/` (no Android imports) so the SAME rules drive the on-device
 * "download a city" builder AND stay unit-testable without a device.
 *
 * The OSM value lists below are the SINGLE source of truth: both [classify] and
 * [overpassBody] read them, so the fetch query and the classifier can never drift
 * apart (the bug class we hit each time a new category was added). When you add a
 * category, edit it here once — and keep it in step with the `pipeline/` Python
 * scripts, which remain the reference used to build the shipped pack.
 */
object OsmClassifier {

    // OSM tag values → our app categories. Each list is used by BOTH the classifier
    // (to label a fetched place) and the Overpass query (to decide what to fetch).
    private val FOOD = setOf("restaurant", "cafe", "fast_food", "food_court", "bar", "pub", "ice_cream")
    private val TOURISM = setOf("attraction", "viewpoint", "museum", "artwork", "gallery", "zoo", "theme_park", "aquarium")
    private val HISTORIC = setOf("monument", "memorial", "castle", "ruins", "archaeological_site", "monastery")
    private val NATURAL = setOf("beach", "peak", "waterfall", "water", "cliff", "cave_entrance", "spring", "bay", "hot_spring", "volcano")
    private val LEISURE = setOf("park", "nature_reserve", "garden", "beach_resort")
    private val SHOPPING = setOf("mall", "department_store")

    // Where a city's events actually happen. Wikidata can name a city's annual
    // festivals but never says WHEN they run, so these venues are the grounded,
    // always-true half of "what's on here": the theatre, the arts centre, the
    // concert hall are real places you can walk to today.
    private val CULTURE = setOf("theatre", "arts_centre", "cinema", "community_centre", "events_venue")
    // Stadiums host events; `sports_centre` was tried and removed — it's gyms and tennis
    // clubs, and it swamped the category (578 of 1410 rows), so asking for theatres
    // answered with a tennis club.
    private val CULTURE_LEISURE = setOf("stadium")

    /** A place's category + subcategory. Null [classify] result = nothing we recommend. */
    data class Kind(val category: String, val subcategory: String)

    /**
     * Decide a place's (category, subcategory) from its OSM tags — the exact ladder
     * from `build_db.classify`. Order matters: the first branch that matches wins
     * (e.g. a café that also tags `shop=mall` is food, because amenity is checked
     * first). Returns null for anything we don't keep.
     *
     * Note `historic` matches ANY historic value (not just the fetched [HISTORIC]
     * set), exactly like the Python reference — on device it only ever sees the
     * fetched values anyway, but this keeps classification identical to the pack.
     */
    fun classify(tags: Map<String, String>): Kind? {
        val amenity = tags["amenity"]
        val shop = tags["shop"]
        val tourism = tags["tourism"]
        val historic = tags["historic"]
        val natural = tags["natural"]
        val leisure = tags["leisure"]
        return when {
            amenity != null && amenity in FOOD -> Kind("food", amenity)
            amenity == "place_of_worship" -> Kind("worship", tags["religion"] ?: "place_of_worship")
            amenity == "police" -> Kind("safety", "police")
            amenity == "hospital" -> Kind("health", "hospital")
            amenity == "fuel" -> Kind("fuel", "fuel")
            amenity == "parking" -> Kind("parking", "parking")
            amenity == "marketplace" -> Kind("shopping", "marketplace")
            shop != null && shop in SHOPPING -> Kind("shopping", shop)
            // Before `tourism`, so a theatre that's also tagged tourism=attraction is
            // filed as culture — the more specific fact about it.
            amenity != null && amenity in CULTURE -> Kind("culture", amenity)
            leisure != null && leisure in CULTURE_LEISURE -> Kind("culture", leisure)
            tourism != null && tourism in TOURISM -> Kind("attraction", tourism)
            historic != null -> Kind("attraction", historic)
            natural != null && natural in NATURAL -> Kind("outdoor", natural)
            leisure != null && leisure in LEISURE -> Kind("outdoor", leisure)
            tags["route"] == "hiking" -> Kind("outdoor", "hiking")
            else -> null
        }
    }

    /**
     * The Overpass area id for an OSM feature, mirroring `fetch_osm.area_id_for`.
     * Overpass area ids = the relation/way id plus a fixed offset. A node can't be
     * an area, so it returns null (the caller then asks for a more specific place).
     */
    fun overpassAreaId(osmType: String, osmId: Long): Long? = when (osmType) {
        "relation" -> 3_600_000_000L + osmId
        "way" -> 2_400_000_000L + osmId
        else -> null
    }

    /** Every app category we fetch, in a stable order. */
    val CATEGORIES = listOf(
        "food", "worship", "attraction", "outdoor", "shopping",
        "culture", "safety", "health", "fuel", "parking",
    )

    private fun re(values: Set<String>) = values.joinToString("|")

    /**
     * The Overpass selector(s) for one app category, WITHOUT a scope suffix — e.g.
     * `nwr["amenity"~"^(restaurant|cafe|…)$"]`. Shared by BOTH the whole-area query
     * (a download, [overpassBody]) and the bbox query (live, [overpassBodyBbox]), so
     * the two can never fetch different things than [classify] keeps.
     */
    private fun selectorsFor(category: String): List<String> = when (category) {
        "food" -> listOf("""nwr["amenity"~"^(${re(FOOD)})${'$'}"]""")
        "worship" -> listOf("""nwr["amenity"="place_of_worship"]""")
        "attraction" -> listOf(
            """nwr["tourism"~"^(${re(TOURISM)})${'$'}"]""",
            """nwr["historic"~"^(${re(HISTORIC)})${'$'}"]""",
        )
        "outdoor" -> listOf(
            """nwr["natural"~"^(${re(NATURAL)})${'$'}"]""",
            """nwr["leisure"~"^(${re(LEISURE)})${'$'}"]""",
            """relation["route"="hiking"]""",
        )
        "shopping" -> listOf(
            """nwr["amenity"="marketplace"]""",
            """nwr["shop"~"^(${re(SHOPPING)})${'$'}"]""",
        )
        "culture" -> listOf(
            """nwr["amenity"~"^(${re(CULTURE)})${'$'}"]""",
            """nwr["leisure"~"^(${re(CULTURE_LEISURE)})${'$'}"]""",
        )
        "safety" -> listOf("""nwr["amenity"="police"]""")
        "health" -> listOf("""nwr["amenity"="hospital"]""")
        "fuel" -> listOf("""nwr["amenity"="fuel"]""")
        "parking" -> listOf("""nwr["amenity"="parking"]""")
        else -> emptyList()
    }

    /**
     * The Overpass query body that fetches exactly the categories [classify] keeps,
     * scoped to [areaId] — the whole-city DOWNLOAD. Mirrors `fetch_osm.build_query`.
     * `nwr` = nodes/ways/relations; `out center tags;` gives each result one lat/lon
     * point plus its tags — what we need for map pins.
     */
    fun overpassBody(areaId: Long): String {
        val clauses = CATEGORIES.flatMap { selectorsFor(it) }.joinToString("\n") { "  $it(area.a);" }
        return "[out:json][timeout:180];\n" +
            "area($areaId)->.a;\n" +
            "(\n$clauses\n);\n" +
            "out center tags;"
    }

    /**
     * The LIVE query: the same selectors, but scoped to a bounding box instead of a
     * downloaded area, and optionally narrowed to just [categories] (e.g. only "food"
     * when the user asked for coffee) so a live fetch stays small and fast. A null or
     * empty [categories] fetches everything, like a download. The bbox order is
     * Overpass's own: (south, west, north, east).
     */
    fun overpassBodyBbox(
        south: Double, west: Double, north: Double, east: Double,
        categories: Collection<String>? = null,
    ): String {
        val cats = categories?.filter { it in CATEGORIES }?.takeIf { it.isNotEmpty() } ?: CATEGORIES
        val bbox = "($south,$west,$north,$east)"
        val clauses = cats.flatMap { selectorsFor(it) }.joinToString("\n") { "  $it$bbox;" }
        // A tighter default timeout than a full download — a live query is small.
        return "[out:json][timeout:60];\n" +
            "(\n$clauses\n);\n" +
            "out center tags;"
    }

    /**
     * A readable address from OSM addr:* tags — mirrors `build_db.address`. Shared by
     * the download builder and the live source so both format an address the same way.
     * An empty tag is treated as absent, so we never emit "Main St, , Geelong".
     */
    fun address(tags: Map<String, String>): String? {
        fun tag(key: String) = tags[key]?.ifBlank { null }
        val street = listOfNotNull(tag("addr:housenumber"), tag("addr:street")).joinToString(" ")
        val parts = listOfNotNull(
            street.ifBlank { null }, tag("addr:suburb"), tag("addr:city"), tag("addr:postcode"),
        )
        return if (parts.isEmpty()) null else parts.joinToString(", ")
    }
}
