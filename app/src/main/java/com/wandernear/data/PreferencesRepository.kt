package com.wandernear.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.wandernear.core.model.UserPreferences
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

// One DataStore for the whole app. `preferencesDataStore` creates the file
// lazily and reuses the same instance — it must be a top-level property.
private val Context.dataStore by preferencesDataStore(name = "user_prefs")

/**
 * Reads and writes the traveller's preferences to on-device storage.
 * Exposes them as a Flow so the UI updates automatically when they change.
 */
class PreferencesRepository(private val context: Context) {

    private object Keys {
        val DIETS = stringSetPreferencesKey("diets")
        val INTERESTS = stringSetPreferencesKey("interests")
        val TRAVEL_STYLE = stringPreferencesKey("travel_style")
        val USE_AI = booleanPreferencesKey("use_ai")
        val TRAVEL_MODE_ON = booleanPreferencesKey("travel_mode_on")
        val ACTIVE_PACK = stringPreferencesKey("active_pack")
        val PRAYER_ENABLED = booleanPreferencesKey("prayer_enabled")
        val PRAYER_METHOD = stringPreferencesKey("prayer_method")
        val PRAYER_ASR = stringPreferencesKey("prayer_asr")
    }

    /**
     * Which city pack is active (a path relative to filesDir). It's app state rather
     * than a taste preference, so it lives outside [UserPreferences]; the UI observes
     * it and re-opens the data when it changes. Defaults to the bundled city.
     */
    val activePack: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[Keys.ACTIVE_PACK] ?: CityDatabase.BUNDLED_PACK
    }

    /** Switch the active city to [name] (e.g. after a download). */
    suspend fun setActivePack(name: String) {
        context.dataStore.edit { it[Keys.ACTIVE_PACK] = name }
    }

    /** Emits the current preferences now, and again after every change. */
    val preferences: Flow<UserPreferences> = context.dataStore.data.map { prefs ->
        UserPreferences(
            diets = prefs[Keys.DIETS] ?: emptySet(),
            interests = prefs[Keys.INTERESTS] ?: emptySet(),
            travelStyle = prefs[Keys.TRAVEL_STYLE],
            useAi = prefs[Keys.USE_AI] ?: false,
            travelModeOn = prefs[Keys.TRAVEL_MODE_ON] ?: false,
            prayerEnabled = prefs[Keys.PRAYER_ENABLED] ?: false,
            prayerMethod = prefs[Keys.PRAYER_METHOD] ?: "MWL",
            prayerAsr = prefs[Keys.PRAYER_ASR] ?: "STANDARD",
        )
    }

    suspend fun setDiets(value: Set<String>) {
        context.dataStore.edit { it[Keys.DIETS] = value }
    }

    suspend fun setInterests(value: Set<String>) {
        context.dataStore.edit { it[Keys.INTERESTS] = value }
    }

    suspend fun setTravelStyle(value: String?) {
        context.dataStore.edit { prefs ->
            if (value == null) prefs.remove(Keys.TRAVEL_STYLE) else prefs[Keys.TRAVEL_STYLE] = value
        }
    }

    suspend fun setUseAi(value: Boolean) {
        context.dataStore.edit { it[Keys.USE_AI] = value }
    }

    suspend fun setTravelMode(value: Boolean) {
        context.dataStore.edit { it[Keys.TRAVEL_MODE_ON] = value }
    }

    suspend fun setPrayerEnabled(value: Boolean) {
        context.dataStore.edit { it[Keys.PRAYER_ENABLED] = value }
    }

    suspend fun setPrayerMethod(value: String) {
        context.dataStore.edit { it[Keys.PRAYER_METHOD] = value }
    }

    suspend fun setPrayerAsr(value: String) {
        context.dataStore.edit { it[Keys.PRAYER_ASR] = value }
    }
}
