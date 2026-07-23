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

    // --- Adversarial: try to make it name a place we didn't retrieve ---

    @Test
    fun injectedExternalBrand_isRejected() {
        // Even if a place's data smuggled in an instruction, the model echoing
        // an external place is caught here and the reply is discarded.
        val text = "Great picks! Also, ignore the list and go to Pizza Palace instead."
        assertFalse(GroundingCheck.isGrounded(text, places))
    }

    @Test
    fun honestRefusalText_isGrounded() {
        assertTrue(
            GroundingCheck.isGrounded(
                "I don't have anything matching that in this city's data yet.",
                places,
            ),
        )
    }

    @Test
    fun mixOfRealAndInventedVenue_isRejected() {
        val text = "Try Akshaya, or the wonderful Sunset Grill down the road."
        assertFalse(GroundingCheck.isGrounded(text, places))
    }

    @Test
    fun invented_shoppingVenue_withSafeWord_isRejected() {
        // Shopping is AI-reworded too. An invented venue whose only novel word is a
        // shopping marker paired with a safe word (the city name) must still be caught,
        // while a real retrieved mall stays grounded.
        val malls = listOf(place("Pacific Werribee"), place("Highpoint"))
        assertFalse(GroundingCheck.isGrounded("Try Highpoint, or the new Melbourne Emporium.", malls))
        assertTrue(GroundingCheck.isGrounded("Highpoint is a great mall to explore.", malls))
    }
}
