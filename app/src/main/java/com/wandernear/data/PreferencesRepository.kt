package com.wandernear.data

import android.content.Context
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
    }

    /** Emits the current preferences now, and again after every change. */
    val preferences: Flow<UserPreferences> = context.dataStore.data.map { prefs ->
        UserPreferences(
            diets = prefs[Keys.DIETS] ?: emptySet(),
            interests = prefs[Keys.INTERESTS] ?: emptySet(),
            travelStyle = prefs[Keys.TRAVEL_STYLE],
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
}
