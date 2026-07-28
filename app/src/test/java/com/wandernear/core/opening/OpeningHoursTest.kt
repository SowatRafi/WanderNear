package com.wandernear.core.opening

import com.wandernear.core.opening.OpeningHours.State
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * "Is it open?" is the question a traveller asks most, and a WRONG answer sends them
 * across a strange city for nothing. So these tests care as much about the cases we must
 * refuse to answer as the ones we answer.
 *
 * Days are ISO: 1 = Monday … 7 = Sunday. Times are minutes past midnight.
 */
class OpeningHoursTest {

    private fun at(h: Int, m: Int = 0) = h * 60 + m

    private val MON = 1
    private val FRI = 5
    private val SAT = 6
    private val SUN = 7

    // --- the everyday shapes ---

    @Test
    fun openDuringWeekdayHours() {
        val s = OpeningHours.status("Mo-Fr 09:00-17:00", MON, at(10))
        assertEquals(State.OPEN, s.state)
        assertEquals("17:00", s.until)
    }

    @Test
    fun closedBeforeOpeningAndToldWhenItOpens() {
        val s = OpeningHours.status("Mo-Fr 09:00-17:00", MON, at(7))
        assertEquals(State.CLOSED, s.state)
        assertEquals("09:00", s.opensAt)
    }

    @Test
    fun closedAfterClosingTime() {
        val s = OpeningHours.status("Mo-Fr 09:00-17:00", MON, at(18))
        assertEquals(State.CLOSED, s.state)
        assertEquals("no later opening today", null, s.opensAt)
    }

    @Test
    fun closedOnADayTheRuleDoesNotCover() {
        assertEquals(State.CLOSED, OpeningHours.status("Mo-Fr 09:00-17:00", SAT, at(10)).state)
    }

    @Test
    fun alwaysOpen() {
        assertEquals(State.OPEN, OpeningHours.status("24/7", SUN, at(3)).state)
    }

    @Test
    fun noDayPartMeansEveryDay() {
        assertEquals(State.OPEN, OpeningHours.status("09:00-17:00", SUN, at(10)).state)
    }

    // --- the shapes that trip naive parsers ---

    @Test
    fun lunchBreakBetweenTwoRanges() {
        val spec = "Mo-Fr 09:00-12:00,13:00-17:00"
        assertEquals(State.OPEN, OpeningHours.status(spec, MON, at(11)).state)
        val closed = OpeningHours.status(spec, MON, at(12, 30))
        assertEquals(State.CLOSED, closed.state)
        assertEquals("13:00", closed.opensAt)
        assertEquals(State.OPEN, OpeningHours.status(spec, MON, at(14)).state)
    }

    @Test
    fun severalRulesSeparatedBySemicolons() {
        val spec = "Mo-Fr 09:00-17:00; Sa 10:00-14:00"
        assertEquals(State.OPEN, OpeningHours.status(spec, SAT, at(11)).state)
        assertEquals(State.CLOSED, OpeningHours.status(spec, SAT, at(15)).state)
        assertEquals(State.CLOSED, OpeningHours.status(spec, SUN, at(11)).state)
    }

    @Test
    fun listedDays() {
        val spec = "Mo,We,Fr 09:00-12:00"
        assertEquals(State.OPEN, OpeningHours.status(spec, MON, at(10)).state)
        assertEquals(State.CLOSED, OpeningHours.status(spec, 2, at(10)).state)
    }

    @Test
    fun explicitlyClosedDay() {
        val spec = "Mo-Sa 09:00-17:00; Su off"
        assertEquals(State.CLOSED, OpeningHours.status(spec, SUN, at(10)).state)
        assertEquals(State.OPEN, OpeningHours.status(spec, MON, at(10)).state)
    }

    @Test
    fun barOpenPastMidnightCountsAsYesterdaysRule() {
        // The case that matters at night: a bar tagged Fri-Sat 18:00-02:00 IS open at
        // 01:00 on Saturday, under Friday's rule.
        val spec = "Fr-Sa 18:00-02:00"
        assertEquals(State.OPEN, OpeningHours.status(spec, FRI, at(20)).state)
        assertEquals(State.OPEN, OpeningHours.status(spec, SAT, at(1)).state)
        // ...but not at 03:00, after it shut.
        assertEquals(State.CLOSED, OpeningHours.status(spec, SAT, at(3)).state)
    }

    @Test
    fun dayRangeWrappingTheWeek() {
        // "Fr-Mo" means Fri, Sat, Sun, Mon - not an empty range.
        val spec = "Fr-Mo 10:00-16:00"
        assertEquals(State.OPEN, OpeningHours.status(spec, SUN, at(12)).state)
        assertEquals(State.OPEN, OpeningHours.status(spec, MON, at(12)).state)
        assertEquals(State.CLOSED, OpeningHours.status(spec, 3, at(12)).state)
    }

    // --- what we must REFUSE to answer ---

    @Test
    fun noTagIsUnknownNotClosed() {
        // Most OSM places have no hours at all. That is unknown, and saying "closed"
        // would be inventing a fact.
        assertEquals(State.UNKNOWN, OpeningHours.status(null, MON, at(10)).state)
        assertEquals(State.UNKNOWN, OpeningHours.status("", MON, at(10)).state)
        assertEquals(State.UNKNOWN, OpeningHours.status("   ", MON, at(10)).state)
    }

    @Test
    fun rulesWeCannotEvaluateAreUnknown() {
        // Each of these needs a calendar or an almanac we don't have. Guessing would send
        // someone to a closed door.
        val beyondUs = listOf(
            "sunrise-sunset",
            "Mo-Fr 08:00-sunset",
            "Jan-Mar 10:00-16:00",
            "week 1-20 10:00-16:00",
            "PH off",
            "Mo-Fr 09:00-17:00; PH 10:00-14:00",
        )
        for (spec in beyondUs) {
            assertEquals("should not guess at \"$spec\"", State.UNKNOWN, OpeningHours.status(spec, MON, at(10)).state)
        }
    }

    @Test
    fun garbageIsUnknown() {
        for (spec in listOf("by appointment", "Mo-Fr", "09:00", "abc-def")) {
            assertEquals("should not guess at \"$spec\"", State.UNKNOWN, OpeningHours.status(spec, MON, at(10)).state)
        }
    }
}
