package com.wandernear.core.pack

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Locks the on-device classifier to the Python pipeline's behaviour: the same
 * (category, subcategory) for the same tags, the same first-match order, and an
 * Overpass query that fetches exactly those categories. If this drifts, downloaded
 * city packs would categorise places differently from the bundled one.
 */
class OsmClassifierTest {

    private fun kind(vararg tags: Pair<String, String>) = OsmClassifier.classify(mapOf(*tags))

    @Test
    fun classifiesEachCategory() {
        assertEquals(OsmClassifier.Kind("food", "cafe"), kind("amenity" to "cafe"))
        assertEquals(OsmClassifier.Kind("safety", "police"), kind("amenity" to "police"))
        assertEquals(OsmClassifier.Kind("health", "hospital"), kind("amenity" to "hospital"))
        assertEquals(OsmClassifier.Kind("fuel", "fuel"), kind("amenity" to "fuel"))
        assertEquals(OsmClassifier.Kind("parking", "parking"), kind("amenity" to "parking"))
        assertEquals(OsmClassifier.Kind("shopping", "marketplace"), kind("amenity" to "marketplace"))
        assertEquals(OsmClassifier.Kind("shopping", "mall"), kind("shop" to "mall"))
        assertEquals(OsmClassifier.Kind("attraction", "museum"), kind("tourism" to "museum"))
        assertEquals(OsmClassifier.Kind("outdoor", "beach"), kind("natural" to "beach"))
        assertEquals(OsmClassifier.Kind("outdoor", "park"), kind("leisure" to "park"))
        assertEquals(OsmClassifier.Kind("outdoor", "hiking"), kind("route" to "hiking"))
    }

    @Test
    fun worshipUsesReligionThenFallback() {
        assertEquals(OsmClassifier.Kind("worship", "hindu"), kind("amenity" to "place_of_worship", "religion" to "hindu"))
        assertEquals(OsmClassifier.Kind("worship", "place_of_worship"), kind("amenity" to "place_of_worship"))
    }

    @Test
    fun historicMatchesAnyValue() {
        // Faithful to Python: `if tags.get("historic")` accepts any historic value,
        // even one the fetch query doesn't request.
        assertEquals(OsmClassifier.Kind("attraction", "battlefield"), kind("historic" to "battlefield"))
    }

    @Test
    fun firstMatchWins() {
        // amenity is checked before shop, so a café tagged shop=mall is food.
        assertEquals(OsmClassifier.Kind("food", "cafe"), kind("amenity" to "cafe", "shop" to "mall"))
    }

    @Test
    fun unknownTagsAreDropped() {
        assertNull(kind("building" to "yes"))
        assertNull(kind("shop" to "convenience"))   // not in the tight shopping set
        assertNull(OsmClassifier.classify(emptyMap()))
    }

    @Test
    fun overpassAreaId_offsetsByType() {
        assertEquals(3_604_246_124L, OsmClassifier.overpassAreaId("relation", 4_246_124))
        assertEquals(2_400_000_000L + 42, OsmClassifier.overpassAreaId("way", 42))
        assertNull(OsmClassifier.overpassAreaId("node", 42))   // a point isn't an area
    }

    @Test
    fun overpassBody_scopesToAreaAndFetchesEveryCategory() {
        val q = OsmClassifier.overpassBody(3_604_246_124L)
        assertTrue(q.contains("area(3604246124)"))
        // Pin every category's exact value list to the Python reference, so a typo
        // in any set fails here (the on-device pack would otherwise diverge silently).
        assertTrue(q.contains("""nwr["amenity"~"^(restaurant|cafe|fast_food|food_court|bar|pub|ice_cream)${'$'}"]"""))
        assertTrue(q.contains("""nwr["amenity"="place_of_worship"]"""))
        assertTrue(q.contains("""nwr["tourism"~"^(attraction|viewpoint|museum|artwork|gallery|zoo|theme_park|aquarium)${'$'}"]"""))
        assertTrue(q.contains("""nwr["historic"~"^(monument|memorial|castle|ruins|archaeological_site|monastery)${'$'}"]"""))
        assertTrue(q.contains("""nwr["natural"~"^(beach|peak|waterfall|water|cliff|cave_entrance|spring|bay|hot_spring|volcano)${'$'}"]"""))
        assertTrue(q.contains("""nwr["leisure"~"^(park|nature_reserve|garden|beach_resort)${'$'}"]"""))
        assertTrue(q.contains("""nwr["amenity"="police"]"""))
        assertTrue(q.contains("""nwr["amenity"="hospital"]"""))
        assertTrue(q.contains("""nwr["amenity"="fuel"]"""))
        assertTrue(q.contains("""nwr["amenity"="parking"]"""))
        assertTrue(q.contains("""nwr["amenity"="marketplace"]"""))
        assertTrue(q.contains("""nwr["shop"~"^(mall|department_store)${'$'}"]"""))
        assertTrue(q.contains("""nwr["amenity"~"^(theatre|arts_centre|cinema|community_centre|events_venue)${'$'}"]"""))
        assertTrue(q.contains("""nwr["leisure"~"^(stadium)${'$'}"]"""))
        assertTrue(q.contains("""relation["route"="hiking"]"""))
        assertTrue(q.contains("out center tags;"))
    }

    @Test
    fun `culture venues are classified, and beat a generic attraction tag`() {
        assertEquals(
            OsmClassifier.Kind("culture", "theatre"),
            OsmClassifier.classify(mapOf("amenity" to "theatre")),
        )
        assertEquals(
            OsmClassifier.Kind("culture", "arts_centre"),
            OsmClassifier.classify(mapOf("amenity" to "arts_centre")),
        )
        assertEquals(
            OsmClassifier.Kind("culture", "stadium"),
            OsmClassifier.classify(mapOf("leisure" to "stadium")),
        )
        // A gym or tennis club is NOT culture — it swamped the category and made
        // "theatres" answer with a tennis club, so it's deliberately not kept at all.
        assertNull(OsmClassifier.classify(mapOf("leisure" to "sports_centre")))
        // A theatre that ALSO carries tourism=attraction is culture: the more
        // specific fact wins, exactly as in the Python reference.
        assertEquals(
            OsmClassifier.Kind("culture", "theatre"),
            OsmClassifier.classify(mapOf("amenity" to "theatre", "tourism" to "attraction")),
        )
        // A park is still outdoor — culture must not swallow leisure values it
        // doesn't own.
        assertEquals(
            OsmClassifier.Kind("outdoor", "park"),
            OsmClassifier.classify(mapOf("leisure" to "park")),
        )
    }
}
