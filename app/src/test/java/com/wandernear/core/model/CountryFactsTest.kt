package com.wandernear.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The emergency number is the one fact in this app that must never be missing or wrong,
 * so these lock in that it is ALWAYS answered — and that we're honest about whether the
 * answer is the country's own number or the international fallback.
 */
class CountryFactsTest {

    @Test
    fun knownCountryGivesItsOwnNumber() {
        val au = CountryFacts.emergencyFor("Australia")
        assertEquals("000", au.number)
        assertTrue("Australia's own number, not a fallback", au.local)
    }

    @Test
    fun unknownCountryStillGivesANumber() {
        // The whole point: a traveller somewhere we have no row for must not see a blank.
        val somewhere = CountryFacts.emergencyFor("Kiribati")
        assertEquals(CountryFacts.INTERNATIONAL_EMERGENCY, somewhere.number)
        assertTrue("must be flagged as the international fallback", !somewhere.local)
    }

    @Test
    fun unknownCountryIsNeverBlank() {
        for (country in listOf(null, "", "Not A Country")) {
            val e = CountryFacts.emergencyFor(country)
            assertTrue("blank number for country=$country", e.number.isNotBlank())
        }
    }

    @Test
    fun everyKnownCountryHasBothFacts() {
        // A half-filled row would show a currency with no way to call for help.
        for (country in listOf("Australia", "United States", "United Kingdom", "Japan", "India")) {
            val facts = CountryFacts.forCountry(country)
            assertNotNull("missing facts for $country", facts)
            assertTrue(facts!!.currency.isNotBlank())
            assertTrue(facts.emergency.isNotBlank())
        }
    }
}
