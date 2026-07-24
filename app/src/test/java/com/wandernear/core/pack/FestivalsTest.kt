package com.wandernear.core.pack

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests for the festival rules shared by the Python pipeline and the on-device builder.
 *
 * The tense filter matters because Wikipedia's festival category mixes live festivals
 * with dead ones — "MEL&NYC festival was a cultural festival in Melbourne in 2018" sits
 * right next to the Comedy Festival. Showing a traveller a festival that stopped running
 * is a quiet lie, so it gets filtered on what Wikipedia actually wrote.
 */
class FestivalsTest {

    @Test
    fun `the category title is a formula from the city name, not a curated list`() {
        assertEquals("Category:Festivals in Melbourne", Festivals.categoryTitle("Melbourne"))
        assertEquals("Category:Festivals in Kyoto", Festivals.categoryTitle("Kyoto"))
    }

    @Test
    fun `a festival still running is kept`() {
        assertFalse(
            Festivals.isHistorical(
                "The Melbourne International Comedy Festival is an annual comedy festival.",
            ),
        )
        assertFalse(
            Festivals.isHistorical("Moomba is a festival that is held every March in Melbourne."),
        )
    }

    @Test
    fun `a festival described in the past tense is dropped`() {
        assertTrue(
            Festivals.isHistorical("MEL&NYC festival was a cultural festival in Melbourne in 2018."),
        )
        assertTrue(
            Festivals.isHistorical("Festival Melbourne2006 was a twelve-day cultural event."),
        )
    }

    @Test
    fun `a sentence mixing both tenses is kept, because it still describes something live`() {
        // "was founded ... and is held annually" — past history, present festival.
        assertFalse(
            Festivals.isHistorical(
                "The festival was founded in 1955 and is held annually in the city.",
            ),
        )
    }

    @Test
    fun `an empty summary is not mistaken for history`() {
        assertFalse(Festivals.isHistorical(""))
    }
}
