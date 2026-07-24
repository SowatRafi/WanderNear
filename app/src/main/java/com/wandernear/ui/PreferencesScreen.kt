package com.wandernear.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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

/** The second tab: pick diet, interests, and travel style. Saved on-device. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PreferencesScreen(repo: PreferencesRepository) {
    val prefs by repo.preferences.collectAsState(initial = UserPreferences())
    val scope = rememberCoroutineScope()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
    ) {
        Text("Your preferences", fontSize = 26.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        Text(
            "These quietly shape every recommendation. Nothing here ever leaves your phone.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Section("Dietary needs")
        ChipRow(DIET_OPTIONS, isSelected = { it in prefs.diets }) { key ->
            scope.launch { repo.setDiets(prefs.diets.toggle(key)) }
        }

        Section("What you love")
        ChipRow(INTEREST_OPTIONS, isSelected = { it in prefs.interests }) { key ->
            scope.launch { repo.setInterests(prefs.interests.toggle(key)) }
        }

        Section("Travel style")
        ChipRow(STYLE_OPTIONS, isSelected = { it == prefs.travelStyle }) { key ->
            // Single choice: tapping the selected one again clears it.
            scope.launch { repo.setTravelStyle(if (prefs.travelStyle == key) null else key) }
        }

        // Switching/adding a city changes everything else on screen, so it comes first.
        Spacer(Modifier.height(24.dp))
        CitiesSection(repo)

        Spacer(Modifier.height(24.dp))
        AiSettingsSection(repo)

        Spacer(Modifier.height(24.dp))
        TravelModeSection(repo)

        Spacer(Modifier.height(24.dp))
        FaithSettingsSection(repo)
    }
}

@Composable
private fun Section(title: String) {
    Spacer(Modifier.height(24.dp))
    Text(title, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
    Spacer(Modifier.height(8.dp))
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ChipRow(
    options: List<Pair<String, String>>,
    isSelected: (String) -> Boolean,
    onToggle: (String) -> Unit,
) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
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
