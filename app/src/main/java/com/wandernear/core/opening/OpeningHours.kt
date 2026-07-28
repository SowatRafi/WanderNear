package com.wandernear.core.opening

/**
 * "Is it open right now?" — read from OpenStreetMap's `opening_hours` tag.
 *
 * This is the question travellers actually ask, and the answer is already in the map data
 * we fetch. It is computed ON-DEVICE from the tag, so it works offline and costs nothing.
 *
 * **Deliberately a partial parser.** The real `opening_hours` specification is large
 * (holidays, month ranges, week numbers, sunset offsets, exceptions). This handles the
 * common shapes that cover the overwhelming majority of real tags, and returns
 * [State.UNKNOWN] for anything it cannot read with confidence. That is the honest
 * behaviour: telling someone a place is open when we have misread the rule sends them
 * across town for nothing, so "hours not listed" is always preferable to a guess.
 *
 * Understood:
 *  - `24/7`
 *  - `Mo-Fr 09:00-17:00`
 *  - `Mo-Fr 09:00-17:00; Sa 10:00-14:00`
 *  - `Mo,We,Fr 09:00-12:00`
 *  - `09:00-17:00` (no day given ⇒ every day)
 *  - several ranges in a day: `Mo-Fr 09:00-12:00,13:00-17:00`
 *  - closing after midnight: `Fr-Sa 18:00-02:00`
 *  - `Su off` / `Su closed`
 *
 * Not understood (⇒ UNKNOWN): sunrise/sunset offsets, month or week ranges, public-holiday
 * rules that would change today's answer, and anything with the specification's more
 * exotic syntax.
 *
 * Pure Kotlin, no Android imports — the caller supplies the day and time, which also makes
 * it fully unit-testable without a clock.
 */
object OpeningHours {

    enum class State {
        /** Open at the moment asked about. */
        OPEN,

        /** Genuinely closed at the moment asked about. */
        CLOSED,

        /** No tag, or a rule we can't read — we say so rather than guess. */
        UNKNOWN,
    }

    /**
     * [until] is set when OPEN (when it closes), [opensAt] when CLOSED and it opens again
     * later the same day. Both are "HH:MM"; either may be null when we don't know.
     */
    data class Status(val state: State, val until: String? = null, val opensAt: String? = null)

    /** Monday..Sunday, matching the ISO day numbers 1..7 used by [status]. */
    private val DAY_NAMES = listOf("mo", "tu", "we", "th", "fr", "sa", "su")

    /** Any of these means the rule depends on things we can't evaluate — bail out honestly. */
    private val UNREADABLE = listOf(
        "sunrise", "sunset", "dawn", "dusk", "easter", "week", "[",
        "jan", "feb", "mar", "apr", "may", "jun",
        "jul", "aug", "sep", "oct", "nov", "dec",
    )

    /** One parsed rule: which days it applies to, and the minute ranges it is open. */
    private class Rule(val days: Set<Int>, val ranges: List<IntRange>, val closed: Boolean)

    /**
     * Whether a place with this [spec] is open, for [day] (1 = Monday … 7 = Sunday) at
     * [minutes] past midnight. A null/blank spec is UNKNOWN — most OSM places have no
     * hours tagged, and that is simply unknown, not closed.
     */
    fun status(spec: String?, day: Int, minutes: Int): Status {
        val text = spec?.trim()?.lowercase()?.takeIf { it.isNotEmpty() } ?: return Status(State.UNKNOWN)
        if (text == "24/7") return Status(State.OPEN)
        val rules = parse(text) ?: return Status(State.UNKNOWN)

        // Open right now? Check today's rules first.
        for (rule in rules.filter { day in it.days && !it.closed }) {
            rule.ranges.firstOrNull { minutes in it }?.let {
                return Status(State.OPEN, until = clock(it.last + 1))
            }
        }
        // Still open from YESTERDAY — a bar tagged 18:00-02:00 is open at 01:00 today, and
        // that belongs to yesterday's rule, not today's.
        val yesterday = if (day == 1) 7 else day - 1
        for (rule in rules.filter { yesterday in it.days && !it.closed }) {
            // Only ranges that were split across midnight can spill into today.
            rule.ranges.filter { it.last >= MINUTES_IN_DAY }.forEach {
                if (minutes + MINUTES_IN_DAY in it) return Status(State.OPEN, until = clock(it.last + 1))
            }
        }
        // Closed. If it opens again later today, say when.
        val nextToday = rules.filter { day in it.days && !it.closed }
            .flatMap { it.ranges }
            .map { it.first }
            .filter { it > minutes }
            .minOrNull()
        return Status(State.CLOSED, opensAt = nextToday?.let { clock(it) })
    }

    private const val MINUTES_IN_DAY = 24 * 60

    /** Minutes past midnight → "HH:MM", wrapping past midnight (1500 → "01:00"). */
    private fun clock(minutes: Int): String {
        val m = minutes % MINUTES_IN_DAY
        return "%02d:%02d".format(m / 60, m % 60)
    }

    /** All rules in the spec, or null if any part of it is beyond this parser. */
    private fun parse(text: String): List<Rule>? {
        val rules = ArrayList<Rule>()
        for (part in text.split(";")) {
            val rule = part.trim().takeIf { it.isNotEmpty() } ?: continue
            // A public/school-holiday rule could change today's answer and we have no
            // holiday calendar, so we can't answer confidently at all.
            if (rule.startsWith("ph") || rule.startsWith("sh")) return null
            if (UNREADABLE.any { it in rule }) return null
            rules += parseRule(rule) ?: return null
        }
        return rules.takeIf { it.isNotEmpty() }
    }

    private fun parseRule(rule: String): Rule? {
        // Split "Mo-Fr 09:00-17:00" into the day part and the time part, by testing whether
        // the first word actually IS a set of days. Splitting on the first digit instead
        // would break "Su off", which has no digits at all. A rule with no day part (just
        // "09:00-17:00") applies every day.
        val space = rule.indexOf(' ')
        val parsedDays = if (space > 0) parseDays(rule.substring(0, space).trim()) else null
        val days = parsedDays ?: (1..7).toSet()
        val timeText = if (parsedDays != null) rule.substring(space + 1).trim() else rule.trim()

        // "off"/"closed" marks days the place is shut — e.g. "Su off".
        if (timeText.startsWith("off") || timeText.startsWith("closed")) {
            return Rule(days, emptyList(), closed = true)
        }
        val ranges = timeText.split(",").map { it.trim() }.map { parseRange(it) ?: return null }
        return Rule(days, ranges, closed = false)
    }

    /** "mo-fr" / "mo,we,fr" / "mo" → the ISO day numbers it covers. */
    private fun parseDays(text: String): Set<Int>? {
        val days = HashSet<Int>()
        for (token in text.split(",").map { it.trim() }.filter { it.isNotEmpty() }) {
            if ("-" in token) {
                val (from, to) = token.split("-", limit = 2).map { dayNumber(it.trim()) ?: return null }
                // Wraps the week: "Sa-Su" is 6,7 but "Fr-Mo" is 5,6,7,1.
                var d = from
                while (true) {
                    days += d
                    if (d == to) break
                    d = if (d == 7) 1 else d + 1
                }
            } else {
                days += dayNumber(token) ?: return null
            }
        }
        return days.takeIf { it.isNotEmpty() }
    }

    /** "mo" / "mon" → 1. Length-capped so a longer word that merely starts with a day
     *  abbreviation (e.g. "summer") isn't silently read as Sunday. */
    private fun dayNumber(token: String): Int? {
        if (token.length !in 2..3) return null
        return DAY_NAMES.indexOf(token.take(2)).takeIf { it >= 0 }?.plus(1)
    }

    /**
     * "09:00-17:00" → the minutes it covers. A range that ends at or before it starts runs
     * past midnight, so it's extended beyond the end of the day and [status] looks at the
     * previous day for it.
     */
    private fun parseRange(text: String): IntRange? {
        val (fromText, toText) = text.split("-", limit = 2).takeIf { it.size == 2 } ?: return null
        val from = parseTime(fromText.trim()) ?: return null
        var to = parseTime(toText.trim()) ?: return null
        if (to <= from) to += MINUTES_IN_DAY
        return from until to
    }

    private fun parseTime(text: String): Int? {
        val (h, m) = text.split(":", limit = 2).takeIf { it.size == 2 } ?: return null
        val hours = h.toIntOrNull() ?: return null
        val mins = m.toIntOrNull() ?: return null
        if (hours !in 0..24 || mins !in 0..59) return null
        return hours * 60 + mins
    }
}
