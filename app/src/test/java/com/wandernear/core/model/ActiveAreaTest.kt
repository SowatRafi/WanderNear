package com.wandernear.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests for the "around you" area — the box the app explores by default.
 *
 * This is the one piece of the location-first flow that is pure maths, so it's the piece
 * worth pinning down: get the box wrong and every card on the home is about the wrong
 * patch of ground.
 */
class ActiveAreaTest {

    // Werribee, where the app is normally tested. Any mid-latitude point would do.
    private val werribee = LatLng(-37.9010, 144.6614)

    @Test
    fun `box is centred on the fix`() {
        val area = hereArea(werribee)
        assertEquals(werribee.lat, area.center.lat, 1e-9)
        assertEquals(werribee.lng, area.center.lng, 1e-9)
    }

    @Test
    fun `box edges are the requested distance away`() {
        val area = hereArea(werribee, radiusKm = 3.0)
        // North edge, due north of the fix.
        assertEquals(3.0, haversineKm(werribee, LatLng(area.north, werribee.lng)), 0.05)
        // East edge, due east — this is the one the cos(latitude) correction exists for.
        assertEquals(3.0, haversineKm(werribee, LatLng(werribee.lat, area.east)), 0.05)
    }

    @Test
    fun `longitude span widens towards the poles`() {
        // The same 3 km must cover MORE degrees of longitude at 60°N than at the equator,
        // because the meridians converge. Without the cos correction these would be equal.
        val equator = hereArea(LatLng(0.0, 0.0))
        val north = hereArea(LatLng(60.0, 0.0))
        assertTrue(
            "expected a wider longitude span at 60°N",
            (north.east - north.west) > (equator.east - equator.west) * 1.9,
        )
        // Latitude span is the same everywhere.
        assertEquals(equator.north - equator.south, north.north - north.south, 1e-9)
    }

    @Test
    fun `a polar fix does not blow the box up to the whole planet`() {
        // cos(latitude) approaches zero at the pole, which would divide the span to infinity.
        val area = hereArea(LatLng(89.999, 0.0))
        assertTrue("longitude span should stay bounded", (area.east - area.west) < 360.0)
    }

    @Test
    fun `an around-you area is marked as such and carries no invented population`() {
        val area = hereArea(werribee, country = "Australia")
        assertTrue(area.isAroundYou)
        assertEquals("Australia", area.country)
        assertEquals(null, area.population)
    }

    @Test
    fun `an area picked by name is not an around-you area`() {
        // A Nominatim match always carries a real OSM id, which is what tells them apart.
        val named = ActiveArea("Kyoto, Japan", 34.9, 135.7, 35.1, 135.8, osmId = 123456)
        assertTrue(!named.isAroundYou)
        assertEquals("Kyoto", named.shortName)
    }
}
