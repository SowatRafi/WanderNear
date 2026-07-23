package com.wandernear.core.response

import com.wandernear.core.model.Place
import com.wandernear.core.retrieval.SearchSpec

/**
 * Builds the friendly chat reply from the places we retrieved — using ONLY the
 * real fields on each place. This is the "templates first" guarantee: because
 * every sentence is assembled from database columns, it cannot invent a place,
 * address, or fact. If nothing was found, it says so honestly.
 */
object Recommender {

    const val NO_RESULTS =
        "I don't have anything matching that in this city's data yet. " +
            "Try different words — or I can refresh this city's data in a later version."

    /** System instruction for the on-device LLM: reword only, never invent. */
    const val AI_SYSTEM =
        "You are a warm, concise local guide. You may ONLY mention the places listed in " +
            "CONTEXT. Never invent a place, address, time, price, or fact that is not shown. " +
            "Reply in 2 to 4 friendly sentences."

    /** The one-line intro shown above the recommendation cards. */
    fun reply(spec: SearchSpec, places: List<Place>, nearYou: Boolean): String {
        if (places.isEmpty()) return NO_RESULTS
        val where = if (nearYou) "near you" else "near the city centre"
        return "Here are a few ${describe(spec)} $where:"
    }

    /** A short "why" line for one place, built only from real fields. */
    fun reason(place: Place, spec: SearchSpec): String {
        val parts = mutableListOf<String>()
        place.distanceKm?.let { parts += "%.1f km away".format(it) }
        // Only mention diets the user actually asked for and the place really has.
        (place.diets intersect spec.diets).forEach {
            parts += it.replace('_', ' ') + "-friendly"
        }
        place.cuisine?.let { parts += it.split(";").first().replace('_', ' ') }
        if (place.category == "worship") place.religion?.let { parts += "$it place of worship" }
        return parts.joinToString(" · ")
    }

    /**
     * Builds the prompt for the on-device LLM: an ID-tagged CONTEXT block of the
     * exact retrieved places, plus the traveller's question. The model may only
     * reword these — it never sees anything else, so it cannot invent a place.
     */
    fun aiPrompt(userQuestion: String, places: List<Place>, nearYou: Boolean): String {
        val where = if (nearYou) "near the traveller right now" else "near the city centre"
        val context = places.mapIndexed { index, place ->
            val bits = mutableListOf(place.name)
            place.subcategory?.let { bits += it.replace('_', ' ') }
            place.distanceKm?.let { bits += "%.1f km away".format(it) }
            place.cuisine?.let { bits += it.split(";").first().replace('_', ' ') }
            if (place.category == "worship") place.religion?.let { bits += it }
            "[${index + 1}] ${bits.joinToString(", ")}"
        }.joinToString("\n")
        return buildString {
            append("CONTEXT — the only places you may mention (they are ")
            append(where).append("):\n")
            append(context)
            append("\n\nThe traveller asked: \"").append(userQuestion).append("\"\n")
            append("Recommend these places warmly, naming them and why they fit. ")
            append("Mention only the places above.")
        }
    }

    /** Names the kind of thing being shown, e.g. "vegetarian spots", "temples". */
    private fun describe(spec: SearchSpec): String {
        val diet = spec.diets.joinToString("/") { it.replace('_', ' ') }
        return when (spec.category) {
            "food" -> if (diet.isNotBlank()) "$diet spots" else "food spots"
            "worship" -> "places of worship"
            "attraction" -> "attractions"
            "outdoor" -> "outdoor spots"
            "shopping" -> "shopping spots"
            else -> "places"
        }
    }
}
