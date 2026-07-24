package com.wandernear.core.model

/**
 * The faiths the app can surface a nearest place of worship for. [key] is the OSM
 * `religion` tag value stored on a worship place, so each entry maps 1:1 to grounded
 * data in the pack — we never invent a place. [placeType] is what that faith's place is
 * called, so labels read naturally ("Nearest church", "Nearest synagogue").
 *
 * Only Islam has a universal, astronomically CALCULATED daily timetable (see
 * [com.wandernear.core.prayer.PrayerTimes]); every other faith's service times are set
 * per place, so the app points the user to the place's own website/phone rather than
 * invent a time.
 */
enum class Faith(val key: String, val label: String, val placeType: String) {
    MUSLIM("muslim", "Muslim", "mosque"),
    CHRISTIAN("christian", "Christian", "church"),
    HINDU("hindu", "Hindu", "temple"),
    BUDDHIST("buddhist", "Buddhist", "temple"),
    JEWISH("jewish", "Jewish", "synagogue"),
    SIKH("sikh", "Sikh", "gurdwara");

    /** How the app tells the user where a service time actually comes from. */
    val serviceNote: String
        get() = if (this == MUSLIM) "Friday time is set by the mosque."
        else "Service times are set by the $placeType."

    companion object {
        /** The [Faith] for an OSM religion key, or null (none / unsupported). */
        fun fromKey(key: String?): Faith? = entries.firstOrNull { it.key == key }
    }
}
