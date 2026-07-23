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
            amenity == "marketplace" -> Kind("shopping", "marketplace")
            shop != null && shop in SHOPPING -> Kind("shopping", shop)
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

    /**
     * The Overpass query body that fetches exactly the categories [classify] keeps,
     * scoped to [areaId]. Mirrors `fetch_osm.build_query`. `nwr` = nodes/ways/
     * relations; `out center tags;` gives each result one lat/lon point plus its
     * tags — what we need for map pins.
     */
    fun overpassBody(areaId: Long): String {
        fun re(values: Set<String>) = values.joinToString("|")
        return """
            [out:json][timeout:180];
            area($areaId)->.a;
            (
              nwr["amenity"~"^(${re(FOOD)})${'$'}"](area.a);
              nwr["amenity"="place_of_worship"](area.a);
              nwr["tourism"~"^(${re(TOURISM)})${'$'}"](area.a);
              nwr["historic"~"^(${re(HISTORIC)})${'$'}"](area.a);
              nwr["natural"~"^(${re(NATURAL)})${'$'}"](area.a);
              nwr["leisure"~"^(${re(LEISURE)})${'$'}"](area.a);
              nwr["amenity"="police"](area.a);
              nwr["amenity"="marketplace"](area.a);
              nwr["shop"~"^(${re(SHOPPING)})${'$'}"](area.a);
              relation["route"="hiking"](area.a);
            );
            out center tags;
        """.trimIndent()
    }
}
