package com.wandernear.core.response

import com.wandernear.core.model.Place

/**
 * The final guardrail for the on-device AI (never-hallucinate rule #5).
 *
 * It checks that a generated reply only names places we actually retrieved. It
 * pulls out the capitalized "proper-noun" phrases (how place names appear in
 * text) and rejects any that look like a venue but match none of the retrieved
 * names — i.e. an invented place. If it returns false, the caller shows the safe
 * template instead. When unsure it errs toward rejecting, which is the right
 * bias for an app that must never invent a place.
 *
 * Known limit: a single-word invented name (e.g. one made-up word) can slip
 * through; the strong system prompt + closed-set context + template fallback
 * still cover that case. False rejections only cost us the nicer wording, never
 * correctness.
 */
object GroundingCheck {

    // Words that mark a capitalized phrase as the name of a venue/place.
    private val VENUE_WORDS = setOf(
        "cafe", "café", "restaurant", "bar", "pub", "eatery", "bistro", "bakery",
        "grill", "kitchen", "diner", "teahouse", "temple", "church", "mosque",
        "synagogue", "shrine", "cathedral", "chapel", "park", "gardens", "garden",
        "museum", "gallery", "beach", "reserve", "market", "hotel", "inn",
        // Shopping venue markers, so an invented mall/store name in the AI intro is
        // caught the same way food venues are — parity for the new shopping category.
        "mall", "malls", "plaza", "arcade", "emporium", "outlet", "bazaar",
        "department", "store", "stores",
        // Culture venue markers — same parity for the new culture category, so an
        // invented theatre or arts centre can't slip through the AI intro either.
        "theatre", "theater", "cinema", "playhouse", "auditorium", "hall",
        "arts", "centre", "center", "stadium", "arena", "amphitheatre", "opera",
    )

    // Words that are fine to see capitalized without being a place (sentence
    // starters, cuisines, the city). Keeps false alarms down.
    private val SAFE_WORDS = setOf(
        "the", "a", "an", "and", "or", "for", "if", "but", "so", "to", "of", "in",
        "on", "at", "is", "are", "you", "your", "i", "we", "it", "this", "these",
        "cbd",   // generic; the ACTIVE city's own name words are added dynamically in isGrounded
        "indian", "italian", "thai", "chinese", "japanese", "korean", "vietnamese",
        "mexican", "greek", "turkish", "lebanese", "french", "spanish", "asian",
        "mediterranean", "middle", "eastern", "western", "modern", "local",
        "vegetarian", "vegan", "halal", "kosher", "gluten", "free",
        "hello", "hi", "there", "enjoy", "visit", "try", "check", "out", "near",
        "nearby", "also", "another", "great", "lovely", "perfect", "option",
        "options", "spot", "spots", "place", "places", "food", "cuisine", "coffee",
    )

    // A capitalized word, optionally followed by more capitalized/number words.
    private val PROPER_NOUN = Regex("""[A-Z][A-Za-z0-9&'’.-]*(?:\s+[A-Z0-9][A-Za-z0-9&'’.-]*)*""")

    fun isGrounded(aiText: String, places: List<Place>, cityName: String? = null): Boolean {
        val names = places.map { normalize(it.name) }.filter { it.isNotBlank() }
        // The ACTIVE city's own name/region words are safe to see capitalized (works
        // for any city, e.g. "Geelong", "Victoria") — never flagged as an invented place.
        val citySafe = cityName?.let { normalize(it).split(' ').filter(String::isNotBlank).toSet() } ?: emptySet()
        fun safe(word: String) = word in SAFE_WORDS || word in citySafe

        for (match in PROPER_NOUN.findAll(aiText)) {
            val phrase = normalize(match.value)
            if (phrase.isBlank()) continue
            // Mentions a real retrieved place (either direction of containment) → fine.
            if (names.any { it.contains(phrase) || phrase.contains(it) }) continue

            val words = phrase.split(' ').filter { it.isNotBlank() }
            if (words.all { safe(it) }) continue   // just common / city-name capitalized words

            val looksLikeVenue = words.any { it in VENUE_WORDS }
            val isNovelMultiWord = words.size >= 2 && words.none { safe(it) }
            // A place-looking phrase that matches no retrieved place = invented.
            if (looksLikeVenue || isNovelMultiWord) return false
        }
        return true
    }

    private fun normalize(text: String): String =
        text.lowercase()
            .replace(Regex("""[^a-z0-9 ]"""), " ")
            .replace(Regex("""\s+"""), " ")
            .trim()
}
