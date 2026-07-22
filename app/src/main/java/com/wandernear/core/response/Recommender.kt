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
        "I don't have anything matching that in Melbourne's data yet. " +
            "Try different words — or I can refresh this city's data in a later version."

    /** The one-line intro shown above the recommendation cards. */
    fun reply(spec: SearchSpec, places: List<Place>): String {
        if (places.isEmpty()) return NO_RESULTS
        return "Here are a few ${describe(spec)} near the city centre:"
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

    /** Names the kind of thing being shown, e.g. "vegetarian spots", "temples". */
    private fun describe(spec: SearchSpec): String {
        val diet = spec.diets.joinToString("/") { it.replace('_', ' ') }
        return when (spec.category) {
            "food" -> if (diet.isNotBlank()) "$diet spots" else "food spots"
            "worship" -> "places of worship"
            "attraction" -> "attractions"
            "outdoor" -> "outdoor spots"
            else -> "places"
        }
    }
}
