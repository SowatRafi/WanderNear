package com.wandernear.core.retrieval

import com.wandernear.core.model.UserPreferences
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The parser turns words into the exact SQL filters used for retrieval. If it
 * mis-parses, the wrong (or too many) places get retrieved — so these lock in
 * the mapping, including that a nonsense query gets NO filters (so it correctly
 * finds nothing and the app refuses instead of guessing).
 */
class QueryParserTest {

    @Test
    fun detectsHalalFood() {
        val spec = QueryParser.parse("halal food", UserPreferences())
        assertEquals("food", spec.category)
        assertTrue("halal" in spec.diets)
    }

    @Test
    fun dietWordImpliesFood() {
        val spec = QueryParser.parse("vegetarian", UserPreferences())
        assertEquals("food", spec.category)
        assertTrue("vegetarian" in spec.diets)
    }

    @Test
    fun detectsWorshipAndReligion() {
        val spec = QueryParser.parse("a mosque", UserPreferences())
        assertEquals("worship", spec.category)
        assertEquals("muslim", spec.religion)
    }

    @Test
    fun savedDietPreferenceAppliesToFood() {
        val spec = QueryParser.parse("restaurant", UserPreferences(diets = setOf("vegetarian")))
        assertEquals("food", spec.category)
        assertTrue("vegetarian" in spec.diets)
    }

    @Test
    fun detectsShopping() {
        assertEquals("shopping", QueryParser.parse("shopping near me", UserPreferences()).category)
        assertEquals("shopping", QueryParser.parse("markets", UserPreferences()).category)
    }

    @Test
    fun detectsCultureVenues() {
        assertEquals("culture", QueryParser.parse("theatre", UserPreferences()).category)
        assertEquals("culture", QueryParser.parse("live music", UserPreferences()).category)
        // "events"/"festival" land on venues too: we can't know what's on tonight, but
        // we can honestly show the real places it would be on at.
        assertEquals("culture", QueryParser.parse("events near me", UserPreferences()).category)
        assertEquals("culture", QueryParser.parse("festivals", UserPreferences()).category)
    }

    @Test
    fun detectsFaithWorshipChips() {
        // The faith chips ("Mosques"/"Churches"/…) must resolve to the right religion +
        // the worship category, so a faith-driven chip searches the right places.
        val church = QueryParser.parse("Churches", UserPreferences())
        assertEquals("worship", church.category)
        assertEquals("christian", church.religion)
        assertEquals("jewish", QueryParser.parse("Synagogues", UserPreferences()).religion)
        val gurdwara = QueryParser.parse("Gurdwaras", UserPreferences())
        assertEquals("worship", gurdwara.category)
        assertEquals("sikh", gurdwara.religion)
    }

    @Test
    fun nonsenseQuery_getsNoFilters() {
        val spec = QueryParser.parse("zxcvbnm", UserPreferences())
        assertNull(spec.category)          // won't wrongly narrow to a category
        assertTrue(spec.diets.isEmpty())
    }

    @Test
    fun religiousWordsMapToWorship() {
        // The reported bug: "religious places" wasn't understood as worship at all.
        assertEquals("worship", QueryParser.parse("I want to see the religious places", UserPreferences()).category)
        assertEquals("worship", QueryParser.parse("somewhere spiritual", UserPreferences()).category)
        assertEquals("worship", QueryParser.parse("faith", UserPreferences()).category)
    }

    @Test
    fun savedFaithNarrowsGenericWorship() {
        // The precision the owner asked for: a Buddhist asking generically for
        // religious places gets BUDDHIST ones — the parallel of saved-diet → food.
        val prefs = UserPreferences(faith = "buddhist")
        val spec = QueryParser.parse("religious places", prefs)
        assertEquals("worship", spec.category)
        assertEquals("buddhist", spec.religion)
        // A plain building-type word (no faith named) also picks up the saved faith.
        assertEquals("buddhist", QueryParser.parse("temples", prefs).religion)
    }

    @Test
    fun religionInQueryBeatsSavedFaith() {
        // Ask for "churches" while your saved faith is Buddhist → you still get churches.
        val spec = QueryParser.parse("churches", UserPreferences(faith = "buddhist"))
        assertEquals("christian", spec.religion)
    }

    @Test
    fun savedFaithDoesNotLeakIntoNonWorshipQueries() {
        // A Buddhist asking for food gets food, not temples — the faith only ever
        // narrows a worship search, never forces one.
        val spec = QueryParser.parse("food", UserPreferences(faith = "buddhist"))
        assertEquals("food", spec.category)
        assertNull(spec.religion)
    }
}
