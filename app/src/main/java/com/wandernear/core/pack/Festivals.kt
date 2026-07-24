package com.wandernear.core.pack

/**
 * The rules for turning Wikipedia's festival category into a city's `event` rows —
 * shared by the Python pipeline (`pipeline/fetch_festivals.py`) and the on-device
 * builder, so the bundled pack and a downloaded one can't drift apart.
 *
 * WHY WIKIPEDIA AND NOT WIKIDATA. Wikidata looks like the obvious source but cannot
 * answer this question: its festival items are only reachable by coordinates or by
 * "located in", and the festivals travellers care about have neither — Melbourne
 * International Comedy Festival (Q17012417) has no coordinates and no location, so no
 * structured query returns it. A coordinate search around Melbourne yields 6 items,
 * one already defunct, missing the Comedy Festival, Moomba and the Fringe. Wikipedia's
 * own category lists all 25.
 *
 * NO DATES, DELIBERATELY. No free source publishes a trustworthy date for a recurring
 * festival (Wikidata's "day in year" property is empty across the board, and its only
 * dates sit on one-off past editions). So `when_text` stays NULL and the app tells the
 * user dates change each year rather than inventing one.
 */
object Festivals {

    /** How many to keep: enough for a festival-heavy city, short enough to read. */
    const val MAX = 30

    /**
     * The category a city's festivals live under. A FORMULA from the city's own name,
     * never a hand-kept list per city — so any city gets whatever Wikipedia has, and a
     * city with no such category simply gets no events.
     */
    fun categoryTitle(cityShortName: String): String = "Category:Festivals in $cityShortName"

    /**
     * True when Wikipedia describes the festival in the past tense — "MEL&NYC festival
     * WAS a cultural festival ... in 2018". Someone looking for what's on shouldn't be
     * shown one that stopped running.
     *
     * ponytail: a tense check on the opening sentence, not real language parsing. It
     * only ever reads what Wikipedia actually wrote — we never invent a status — and the
     * worst case is that one oddly-phrased entry is kept or dropped. Revisit if it
     * misfires in practice.
     */
    fun isHistorical(summary: String): Boolean {
        val text = " ${summary.lowercase()} "
        val past = " was " in text || " were " in text
        val present = " is " in text || " are " in text
        return past && !present
    }
}
