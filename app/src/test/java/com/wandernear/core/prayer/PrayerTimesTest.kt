package com.wandernear.core.prayer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pins the on-device prayer-time calc to an INDEPENDENT implementation — the free
 * Aladhan API (aladhan.com) — for Melbourne on 2026-07-24. If our astronomy drifts,
 * these fail. Tolerance is ±1 minute (rounding differs between implementations).
 *
 * Reference (method=Muslim World League, tz Australia/Melbourne = UTC+10 in July):
 *   Fajr 05:56 · Sunrise 07:27 · Dhuhr 12:27 · Asr 15:05 · Maghrib 17:26 · Isha 18:53
 *   Asr (Hanafi) 15:46
 */
class PrayerTimesTest {

    private val lat = -37.8136
    private val lng = 144.9631
    private val tz = 10.0

    private fun minutes(hhmm: String): Int {
        val (h, m) = hhmm.split(":").map { it.toInt() }
        return h * 60 + m
    }

    /** Asserts our computed time is within one minute of the reference "HH:mm". */
    private fun assertClose(expected: String, actualHour: Double) {
        val diff = Math.abs(minutes(PrayerTimes.format(actualHour)) - minutes(expected))
        assertTrue(
            "expected ~$expected but got ${PrayerTimes.format(actualHour)}",
            diff <= 1,
        )
    }

    @Test
    fun `matches the reference for Melbourne, MWL, Standard Asr`() {
        val t = PrayerTimes.compute(
            2026, 7, 24, lat, lng, tz,
            PrayerTimes.Method.MWL, PrayerTimes.Asr.STANDARD,
        )
        assertClose("05:56", t.fajr)
        assertClose("07:27", t.sunrise)
        assertClose("12:27", t.dhuhr)
        assertClose("15:05", t.asr)
        assertClose("17:26", t.maghrib)
        assertClose("18:53", t.isha)
    }

    @Test
    fun `Hanafi shifts only Asr later`() {
        val standard = PrayerTimes.compute(2026, 7, 24, lat, lng, tz, PrayerTimes.Method.MWL, PrayerTimes.Asr.STANDARD)
        val hanafi = PrayerTimes.compute(2026, 7, 24, lat, lng, tz, PrayerTimes.Method.MWL, PrayerTimes.Asr.HANAFI)
        assertClose("15:46", hanafi.asr)
        // Every other prayer is identical — the madhab changes Asr only.
        assertEquals(standard.fajr, hanafi.fajr, 1e-9)
        assertEquals(standard.maghrib, hanafi.maghrib, 1e-9)
    }

    @Test
    fun `format rounds to the minute and wraps the clock`() {
        assertEquals("12:27", PrayerTimes.format(12.45))     // 12h 27m
        assertEquals("00:00", PrayerTimes.format(24.0))      // wraps, never "24:00"
        assertEquals("—", PrayerTimes.format(Double.NaN))    // a prayer that doesn't occur
    }
}
