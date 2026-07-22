package com.wandernear.core.response

import com.wandernear.core.model.Place
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Checks the AI grounding guardrail: a reply is accepted only if it names just
 * the places we retrieved, and rejected the moment it invents one. (This is also
 * where M4.4's adversarial cases will live.)
 */
class GroundingCheckTest {

    private fun place(name: String) = Place(
        id = 0, name = name, category = "food", subcategory = "cafe",
        lat = 0.0, lng = 0.0, address = null, cuisine = null,
        religion = null, summary = null,
    )

    private val places = listOf(place("Mr Ed Coffee & More"), place("Akshaya"))

    @Test
    fun realReply_namingOnlyRetrievedPlaces_isGrounded() {
        val text = "Hello there! For vegetarian options, check out Mr Ed Coffee & More, " +
            "a great coffee shop. Akshaya is another spot for Indian cuisine. Enjoy your visit!"
        assertTrue(GroundingCheck.isGrounded(text, places))
    }

    @Test
    fun partialAndReorderedNames_areGrounded() {
        assertTrue(GroundingCheck.isGrounded("Akshaya is lovely, and Mr Ed is nearby too.", places))
    }

    @Test
    fun invented_venue_phrase_isRejected() {
        val text = "Mr Ed Coffee & More is nice, or try Bella Vista Cafe just around the corner."
        assertFalse(GroundingCheck.isGrounded(text, places))
    }

    @Test
    fun invented_multiword_name_isRejected() {
        assertFalse(GroundingCheck.isGrounded("You could also visit Golden Dragon nearby.", places))
    }

    @Test
    fun emptyOrRefusalText_isGrounded() {
        assertTrue(GroundingCheck.isGrounded("", places))
    }
}
