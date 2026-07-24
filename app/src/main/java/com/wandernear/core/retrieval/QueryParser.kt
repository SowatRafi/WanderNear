package com.wandernear.core.retrieval

import com.wandernear.core.model.UserPreferences

/**
 * What we will actually search for, worked out from the user's words + prefs.
 * `ftsTerms` are leftover free-text words for the full-text index; the rest are
 * structured filters applied as plain SQL WHERE clauses.
 */
data class SearchSpec(
    val ftsTerms: List<String> = emptyList(),
    val category: String? = null,   // food | worship | attraction | outdoor | shopping | culture | safety
    val religion: String? = null,
    val diets: Set<String> = emptySet(),
)

/**
 * Turns a typed request like "cheap vegetarian food" into a [SearchSpec] using
 * simple keyword rules — NOT an AI. Predictable and impossible to hallucinate:
 * it only ever chooses filters, never invents places.
 */
object QueryParser {

    // Diet words → the value stored in the database's place_diet table.
    private val DIET_WORDS = mapOf(
        "halal" to "halal",
        "vegetarian" to "vegetarian", "veggie" to "vegetarian", "veg" to "vegetarian",
        "vegan" to "vegan",
        "kosher" to "kosher",
        "gluten" to "gluten_free", "glutenfree" to "gluten_free",
    )

    // Words that signal one of our place categories.
    private val CATEGORY_WORDS = mapOf(
        "food" to "food", "eat" to "food", "restaurant" to "food", "restaurants" to "food",
        "cafe" to "food", "cafes" to "food", "coffee" to "food", "breakfast" to "food",
        "lunch" to "food", "dinner" to "food", "brunch" to "food", "hungry" to "food",
        "temple" to "worship", "temples" to "worship", "mosque" to "worship",
        "mosques" to "worship", "church" to "worship", "churches" to "worship",
        "worship" to "worship", "shrine" to "worship", "synagogue" to "worship",
        "synagogues" to "worship", "gurdwara" to "worship", "gurdwaras" to "worship",
        "pray" to "worship", "prayer" to "worship",
        // Generic ways people ask for a place of worship without naming a building
        // type — "religious places", "somewhere spiritual". These pick the category;
        // the specific faith comes from the query (below) or the saved preference.
        // ("holy"/"sacred" deliberately left out — they show up in ordinary place
        // names like "Holy Basil", and would wrongly send a food search to worship.)
        "religious" to "worship", "religion" to "worship", "faith" to "worship",
        "spiritual" to "worship",
        "museum" to "attraction", "museums" to "attraction", "gallery" to "attraction",
        "attraction" to "attraction", "attractions" to "attraction", "landmark" to "attraction",
        "monument" to "attraction", "sightseeing" to "attraction",
        "park" to "outdoor", "parks" to "outdoor", "beach" to "outdoor", "beaches" to "outdoor",
        "hike" to "outdoor", "hiking" to "outdoor", "trail" to "outdoor", "nature" to "outdoor",
        "outdoor" to "outdoor", "outdoors" to "outdoor", "viewpoint" to "outdoor", "garden" to "outdoor",
        "shopping" to "shopping", "shop" to "shopping", "shops" to "shopping",
        "market" to "shopping", "markets" to "shopping", "mall" to "shopping",
        "malls" to "shopping", "souvenir" to "shopping", "souvenirs" to "shopping",
        // Culture venues — the grounded "where things happen here". "Event"/"events"
        // and "festival" land here too: we can't know WHAT is on tonight, but we can
        // honestly show the real venues it would be on at.
        "theatre" to "culture", "theater" to "culture", "theatres" to "culture",
        "cinema" to "culture", "cinemas" to "culture", "movie" to "culture", "movies" to "culture",
        "show" to "culture", "shows" to "culture", "concert" to "culture", "concerts" to "culture",
        "music" to "culture", "live" to "culture", "gig" to "culture", "gigs" to "culture",
        "event" to "culture", "events" to "culture", "festival" to "culture", "festivals" to "culture",
        "stadium" to "culture", "arena" to "culture", "venue" to "culture", "venues" to "culture",
        "culture" to "culture", "cultural" to "culture", "entertainment" to "culture",
    )

    // Words that pin down a specific religion (so "mosque" → muslim). "temple" is
    // deliberately left out because it can be Hindu, Buddhist, Sikh, etc.
    private val RELIGION_WORDS = mapOf(
        "mosque" to "muslim", "mosques" to "muslim", "islamic" to "muslim",
        "church" to "christian", "churches" to "christian", "cathedral" to "christian",
        "synagogue" to "jewish", "synagogues" to "jewish", "jewish" to "jewish",
        "hindu" to "hindu", "buddhist" to "buddhist", "buddhism" to "buddhist",
        "sikh" to "sikh", "gurdwara" to "sikh", "gurdwaras" to "sikh",
    )

    // Common filler words we don't want to full-text search on.
    private val STOPWORDS = setOf(
        "a", "an", "the", "near", "me", "some", "good", "best", "cheap", "nice", "find",
        "show", "want", "looking", "for", "to", "in", "around", "my", "is", "are", "of",
        "and", "or", "place", "places", "spot", "spots", "where", "can", "get", "with", "please",
        "see", "view", "visit",   // filler verbs — "I want to see…" shouldn't hit the text index
    )

    fun parse(text: String, prefs: UserPreferences): SearchSpec {
        val words = text.lowercase().split(Regex("[^a-z]+")).filter { it.isNotBlank() }

        val diets = words.mapNotNull { DIET_WORDS[it] }.toMutableSet()
        var category = words.firstNotNullOfOrNull { CATEGORY_WORDS[it] }
        val religion = words.firstNotNullOfOrNull { RELIGION_WORDS[it] }

        if (religion != null) category = "worship"        // "mosque" ⇒ worship
        if (diets.isNotEmpty() && category == null) category = "food"  // "vegan" ⇒ food

        // Anything left after removing the words we understood becomes free-text.
        val consumed = DIET_WORDS.keys + CATEGORY_WORDS.keys + RELIGION_WORDS.keys + STOPWORDS
        val terms = words.filter { it.length >= 3 && it !in consumed }

        // Only fall back to a saved interest when the request is otherwise empty
        // (no category, no diet, no leftover search words), so a specific query
        // like "sushi" is never hijacked into the user's interest category.
        if (category == null && diets.isEmpty() && terms.isEmpty()) {
            category = prefs.interests.singleOrNull()
        }

        // Saved diet preferences also constrain food searches, so a vegetarian
        // user's plain "food near me" is filtered to vegetarian-friendly places.
        val effectiveDiets = if (category == "food") (diets + prefs.diets) else emptySet()

        // The parallel for faith: a saved faith narrows a worship search to that
        // faith's places, so a Buddhist asking for "religious places" gets Buddhist
        // ones — the precision the preference is meant to give. A religion named IN
        // the query still wins (ask for "churches" and you get churches, whatever
        // your saved faith); the preference only fills the gap when you didn't say.
        val effectiveReligion = religion ?: if (category == "worship") prefs.faith.ifBlank { null } else null

        return SearchSpec(terms, category, effectiveReligion, effectiveDiets)
    }
}
