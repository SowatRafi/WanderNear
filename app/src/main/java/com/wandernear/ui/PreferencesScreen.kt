package com.wandernear.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.wandernear.core.model.UserPreferences
import com.wandernear.data.PreferencesRepository
import kotlinx.coroutines.launch

// The options shown as chips. The first item of each pair is the value we store
// (must match the OSM tags used in retrieval); the second is the label shown.
private val DIET_OPTIONS = listOf(
    "halal" to "Halal", "vegetarian" to "Vegetarian", "vegan" to "Vegan",
    "kosher" to "Kosher", "gluten_free" to "Gluten-free",
)
private val INTEREST_OPTIONS = listOf(
    "food" to "Food", "worship" to "Temples & worship",
    "attraction" to "Attractions", "outdoor" to "Outdoors",
    "shopping" to "Shopping", "culture" to "Culture & venues",
)
private val STYLE_OPTIONS = listOf(
    "foodie" to "Foodie", "culture" to "Culture buff",
    "outdoors" to "Outdoor adventurer", "hidden" to "Hidden gems",
)

/** The second tab: pick diet, interests, and travel style, plus city/AI/faith settings.
 *  Everything is saved on-device. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PreferencesScreen(repo: PreferencesRepository) {
    val prefs by repo.preferences.collectAsState(initial = UserPreferences())
    val scope = rememberCoroutineScope()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
    ) {
        Text("Preferences", style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(6.dp))
        Text(
            "These quietly shape every recommendation. Nothing here ever leaves your phone.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        PrefSection(Icons.Filled.Restaurant, "Dietary needs")
        ChipRow(DIET_OPTIONS, isSelected = { it in prefs.diets }) { key ->
            scope.launch { repo.setDiets(prefs.diets.toggle(key)) }
        }

        PrefSection(Icons.Filled.Favorite, "What you love")
        ChipRow(INTEREST_OPTIONS, isSelected = { it in prefs.interests }) { key ->
            scope.launch { repo.setInterests(prefs.interests.toggle(key)) }
        }

        PrefSection(Icons.Filled.Explore, "Travel style")
        ChipRow(STYLE_OPTIONS, isSelected = { it == prefs.travelStyle }) { key ->
            // Single choice: tapping the selected one again clears it.
            scope.launch { repo.setTravelStyle(if (prefs.travelStyle == key) null else key) }
        }

        // Switching/adding a city changes everything else on screen, so it comes first.
        Spacer(Modifier.height(28.dp))
        CitiesSection(repo)

        Spacer(Modifier.height(20.dp))
        AiSettingsSection(repo)

        Spacer(Modifier.height(20.dp))
        TravelModeSection(repo)

        Spacer(Modifier.height(20.dp))
        FaithSettingsSection(repo)

        Spacer(Modifier.height(8.dp))
    }
}

/** A screen-level section heading: a small tinted icon + the title. */
@Composable
private fun PrefSection(icon: ImageVector, title: String) {
    Spacer(Modifier.height(28.dp))
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.tertiary, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(8.dp))
        Text(title, style = MaterialTheme.typography.titleLarge)
    }
    Spacer(Modifier.height(12.dp))
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ChipRow(
    options: List<Pair<String, String>>,
    isSelected: (String) -> Boolean,
    onToggle: (String) -> Unit,
) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        options.forEach { (key, label) ->
            FilterChip(
                selected = isSelected(key),
                onClick = { onToggle(key) },
                label = { Text(label) },
            )
        }
    }
}

private fun <T> Set<T>.toggle(item: T): Set<T> =
    if (contains(item)) this - item else this + item
