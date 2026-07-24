package com.wandernear.core.prayer

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.tan

/**
 * On-device prayer-time calculation — the five daily prayers plus sunrise, from a
 * position + date. This is the PrayTimes.org algorithm (pure astronomy), ported into
 * `core/` so it has NO Android imports, needs NO network, and is unit-testable.
 *
 * These are a CALCULATION, not a stored fact, so they don't break the never-invent
 * rule — but the app must stay honest about that: it shows WHICH [Method]/[Asr]
 * produced the numbers (conventions legitimately differ), and treats them as when the
 * prayer window BEGINS. A mosque may hold congregation a little later.
 *
 * Friday Jumu'ah is deliberately NOT computed: each mosque sets its own khutbah time
 * and no free source lists it, so the app points the user to the mosque's own website.
 */
object PrayerTimes {

    /** A calculation method = the twilight angles that define Fajr and Isha. */
    enum class Method(
        val label: String,
        val fajrAngle: Double,
        val ishaAngle: Double,
        val ishaIntervalMin: Int,   // >0 ⇒ Isha is a fixed interval after Maghrib, not an angle
    ) {
        MWL("Muslim World League", 18.0, 17.0, 0),
        ISNA("Islamic Society of North America", 15.0, 15.0, 0),
        EGYPT("Egyptian General Authority", 19.5, 17.5, 0),
        KARACHI("Univ. of Islamic Sciences, Karachi", 18.0, 18.0, 0),
        MAKKAH("Umm al-Qura, Makkah", 18.5, 0.0, 90),   // Isha = 90 min after Maghrib
    }

    /** The juristic method for Asr — it changes only the Asr time. */
    enum class Asr(val label: String, val factor: Int) {
        STANDARD("Standard (Shafi'i / Maliki / Hanbali)", 1),
        HANAFI("Hanafi", 2),
    }

    /** A day's prayer times as local-clock hours in 0..24 (see [format]). NaN for a
     *  prayer that doesn't occur at extreme latitudes — the caller shows it as "—". */
    data class Times(
        val fajr: Double, val sunrise: Double, val dhuhr: Double,
        val asr: Double, val maghrib: Double, val isha: Double,
    )

    /** "HH:mm" for a 0..24 hour value, rounded to the nearest minute; "—" for NaN. */
    fun format(hour: Double): String {
        if (hour.isNaN()) return "—"
        val mins = (((hour * 60).roundToInt() % 1440) + 1440) % 1440
        return "%02d:%02d".format(mins / 60, mins % 60)
    }

    private const val RISE_SET = 0.833   // sun's apparent radius + refraction at the horizon

    /**
     * The prayer times for [year]-[month]-[day] at ([lat], [lng]), where the clock is
     * [tzHours] ahead of UTC (e.g. +10.0 for Melbourne in winter — pass the ACTUAL
     * offset in effect that day, DST included).
     *
     * ponytail: no high-latitude twilight rule — beyond ~48° in summer Fajr/Isha may
     * not occur and come back NaN (shown as "—"). Add an angle-based rule if the app
     * ever targets the far north; every city we ship is well within range.
     */
    fun compute(
        year: Int, month: Int, day: Int,
        lat: Double, lng: Double, tzHours: Double,
        method: Method, asr: Asr,
    ): Times {
        // Longitude is folded into the Julian date here and taken back out in `adj`
        // below — exactly as the reference does, so the two corrections don't drift.
        val jd = julianDay(year, month, day) - lng / (15.0 * 24.0)

        fun sun(t: Double) = sunPosition(jd + t)
        fun midDay(t: Double) = fixHour(12.0 - sun(t).eqt)
        // Time (hours) at which the sun sits [angle]° below the horizon; ccw = the
        // morning side of noon (Fajr, sunrise), otherwise the afternoon side.
        fun angleTime(angle: Double, t: Double, ccw: Boolean): Double {
            val decl = sun(t).decl
            val x = (-sinD(angle) - sinD(decl) * sinD(lat)) / (cosD(decl) * cosD(lat))
            val h = arccosD(x) / 15.0            // NaN when |x|>1 (prayer doesn't occur)
            return midDay(t) + if (ccw) -h else h
        }
        fun asrTime(t: Double): Double {
            val decl = sun(t).decl
            val angle = -arccotD(asr.factor + tanD(abs(lat - decl)))
            return angleTime(angle, t, ccw = false)
        }

        // Fixed initial guesses (in day-fractions), refined by one evaluation pass —
        // enough for minute accuracy, matching the reference's single iteration.
        val fajr = angleTime(method.fajrAngle, 5.0 / 24, ccw = true)
        val sunrise = angleTime(RISE_SET, 6.0 / 24, ccw = true)
        val dhuhr = midDay(12.0 / 24)
        val asrT = asrTime(13.0 / 24)
        val maghrib = angleTime(RISE_SET, 18.0 / 24, ccw = false)   // sunset (Sunni maghrib)
        val isha = if (method.ishaIntervalMin > 0) maghrib + method.ishaIntervalMin / 60.0
        else angleTime(method.ishaAngle, 18.0 / 24, ccw = false)

        val adj = tzHours - lng / 15.0   // day-fraction times → local clock
        return Times(
            fajr = fixHour(fajr + adj),
            sunrise = fixHour(sunrise + adj),
            dhuhr = fixHour(dhuhr + adj),
            asr = fixHour(asrT + adj),
            maghrib = fixHour(maghrib + adj),
            isha = fixHour(isha + adj),
        )
    }

    // ---- astronomy ------------------------------------------------------------

    private class Sun(val decl: Double, val eqt: Double)

    /** Sun declination + equation of time (hours) at a Julian date. */
    private fun sunPosition(jd: Double): Sun {
        val d = jd - 2451545.0
        val g = fixAngle(357.529 + 0.98560028 * d)
        val q = fixAngle(280.459 + 0.98564736 * d)
        val l = fixAngle(q + 1.915 * sinD(g) + 0.020 * sinD(2 * g))
        val e = 23.439 - 0.00000036 * d
        val ra = fixHour(arctan2D(cosD(e) * sinD(l), cosD(l)) / 15.0)
        return Sun(decl = arcsinD(sinD(e) * sinD(l)), eqt = q / 15.0 - ra)
    }

    private fun julianDay(year: Int, month: Int, day: Int): Double {
        var y = year
        var m = month
        if (m <= 2) { y -= 1; m += 12 }
        val a = floor(y / 100.0)
        val b = 2 - a + floor(a / 4.0)
        return floor(365.25 * (y + 4716)) + floor(30.6001 * (m + 1)) + day + b - 1524.5
    }

    // Degree-based trig, so the formulae above read like the published algorithm.
    private const val DEG = PI / 180.0
    private fun sinD(d: Double) = sin(d * DEG)
    private fun cosD(d: Double) = cos(d * DEG)
    private fun tanD(d: Double) = tan(d * DEG)
    private fun arcsinD(x: Double) = asin(x) / DEG
    private fun arccosD(x: Double) = acos(x) / DEG
    private fun arctan2D(y: Double, x: Double) = atan2(y, x) / DEG
    private fun arccotD(x: Double) = atan2(1.0, x) / DEG
    private fun fixAngle(a: Double) = ((a % 360.0) + 360.0) % 360.0
    private fun fixHour(h: Double) = ((h % 24.0) + 24.0) % 24.0
}
