package com.wandernear.data

import com.wandernear.core.model.LatLng
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The catalogue decides which downloadable pack a traveller is offered. Getting it wrong
 * means someone downloads several MB of the wrong country — so the coverage test and the
 * ordering are worth pinning down.
 *
 * These cover the pure logic only; the HTTP fetch and download need a real host.
 */
class PackCatalogueTest {

    private fun pack(
        id: String, name: String,
        south: Double, west: Double, north: Double, east: Double,
        places: Int = 1000, bytes: Long = 1_048_576,
    ) = PackCatalogue.Pack(
        id = id, name = name, country = "Testland",
        south = south, west = west, north = north, east = east,
        places = places, bytes = bytes, built = "2026-07-28",
        url = "https://example.test/$id.db",
    )

    // Melbourne's real extent, as measured by build_pack.py.
    private val melbourne = pack("melbourne", "Melbourne", -38.0999, 144.4577, -37.5015, 145.4496)
    private val victoria = pack("victoria", "Victoria", -39.2, 140.9, -33.9, 150.0)
    private val andorra = pack("andorra", "Andorra la Vella", 42.4246, 1.3969, 42.7196, 1.7806)

    private val werribee = LatLng(-37.9010, 144.6614)

    @Test
    fun coversWhereTheUserActuallyIs() {
        assertTrue(melbourne.covers(werribee))
        assertFalse(andorra.covers(werribee))
    }

    @Test
    fun doesNotCoverJustOutsideTheEdge() {
        // A pack must not claim ground it has no places for.
        assertFalse(melbourne.covers(LatLng(-37.9010, 150.0)))
        assertFalse(melbourne.covers(LatLng(-30.0, 144.6614)))
    }

    @Test
    fun aWesternSeedBugWouldHaveBeenCaughtHere() {
        // The bounds tracker in build_pack.py originally seeded min_lng at 90, which made
        // the Melbourne pack claim a west edge of 90 — covering India. This is what that
        // looked like, and it must NOT be offered to someone in Mumbai.
        val broken = pack("melbourne-broken", "Melbourne", -38.0999, 90.0, -37.5015, 145.4496)
        val mumbai = LatLng(19.0760, 72.8777)
        assertFalse("a pack must never cover a city 6,000 km away", broken.covers(mumbai))
    }

    @Test
    fun theTighterCoveringPackComesFirst() {
        // Both cover Werribee; the city pack is the more useful (and smaller) download.
        val ordered = PackCatalogue.forUser(listOf(victoria, melbourne), werribee)
        assertEquals("melbourne", ordered.first().id)
    }

    @Test
    fun packsThatCoverYouBeatOnesThatDont() {
        val ordered = PackCatalogue.forUser(listOf(andorra, melbourne), werribee)
        assertEquals("melbourne", ordered.first().id)
        assertEquals("andorra", ordered.last().id)
    }

    @Test
    fun withNoFixWeDoNotPretendToKnowWhatIsNearby() {
        val ordered = PackCatalogue.forUser(listOf(melbourne, andorra), null)
        assertEquals(listOf("Andorra la Vella", "Melbourne"), ordered.map { it.name })
    }

    @Test
    fun fileNameIsSafeToUseAsAPath() {
        // The id arrives over the network, so it must never be able to escape packs/.
        val nasty = pack("../../etc/passwd", "Nasty", 0.0, 0.0, 1.0, 1.0)
        val name = nasty.fileName
        assertFalse(name.contains("/"))
        assertFalse(name.contains(".."))
        assertTrue(name.endsWith(".db"))
    }

    @Test
    fun summaryStatesTheTwoFactsThatDecideADownload() {
        val p = pack("x", "X", 0.0, 0.0, 1.0, 1.0, places = 21149, bytes = 4_411_392)
        assertEquals("21,149 places · 4.2 MB", p.summary)
    }
}
