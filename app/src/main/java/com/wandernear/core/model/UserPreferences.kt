package com.wandernear.core.model

/**
 * The traveller's saved preferences.
 *
 * This is pure Kotlin with no Android imports, so it lives in `core` and could
 * be reused if the app is ever ported. It is stored on-device (via DataStore)
 * and never leaves the phone.
 */
data class UserPreferences(
    val diets: Set<String> = emptySet(),      // e.g. "halal", "vegetarian", "vegan"
    val interests: Set<String> = emptySet(),  // e.g. "food", "worship", "attraction", "outdoor"
    val travelStyle: String? = null,          // e.g. "foodie", "culture", "outdoors", "hidden"
    val useAi: Boolean = false,               // reword replies with the on-device AI model
)
