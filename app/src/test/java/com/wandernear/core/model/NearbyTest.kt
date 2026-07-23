package com.wandernear.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Tests for [fixInCity] — the guard that decides whether your location is close enough
 * to the active city to be treated as "you are here".
 *
 * This matters because packs can now be ANY city: download Kyoto while standing in
 * Melbourne and, without this, the home screen offers you "daily needs near you"
 * 8,000 km away and Travel Mode announces cafés on another continent. Both the home
 * screen and the Travel Mode service call this one function, so these tests cover both.
 */
class NearbyTest {

    private val melbourneCbd = LatLng(-37.8136, 144.9631)
    private val werribee = LatLng(-37.9000, 144.6600)      // ~32 km from the CBD
    private val kyoto = LatLng(35.0116, 135.7681)          // ~8,000 km away

    @Test
    fun `a fix in the same city is kept`() {
        assertEquals(werribee, fixInCity(werribee, melbourneCbd))
    }

    @Test
    fun `a fix on the other side of the world is dropped`() {
        assertNull(fixInCity(melbourneCbd, kyoto))
    }

    @Test
    fun `no fix stays no fix`() {
        assertNull(fixInCity(null, melbourneCbd))
    }

    @Test
    fun `a fix is kept when the city centre is unknown`() {
        // With nothing to compare against, the real fix is still the best we have —
        // better than pretending we don't know where the user is.
        assertEquals(kyoto, fixInCity(kyoto, null))
    }

    @Test
    fun `the boundary is inclusive`() {
        // A point just inside the limit is kept; just outside is dropped. Walking the
        // exact edge shouldn't flicker on a rounding error.
        val nearLimit = LatLng(melbourneCbd.lat + (AWAY_FROM_CITY_KM - 1) / 111.0, melbourneCbd.lng)
        val pastLimit = LatLng(melbourneCbd.lat + (AWAY_FROM_CITY_KM + 1) / 111.0, melbourneCbd.lng)
        assertEquals(nearLimit, fixInCity(nearLimit, melbourneCbd))
        assertNull(fixInCity(pastLimit, melbourneCbd))
    }

    @Test
    fun `category labels are friendly and never blank`() {
        assertEquals("Police", categoryLabel("safety"))
        assertEquals("Hospital", categoryLabel("health"))
        assertEquals("Outdoors", categoryLabel("outdoor"))
        // An unknown category still reads as a word rather than showing raw data.
        assertEquals("Something_new", categoryLabel("something_new"))
    }
}
