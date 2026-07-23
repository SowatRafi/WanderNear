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
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wandernear.core.model.UserPreferences
import com.wandernear.data.CityPackBuilder
import com.wandernear.data.PreferencesRepository
import kotlinx.coroutines.launch

// The options shown as chips. The first item of each pair is the value we store
// (must match the OSM tags used in retrieval); the second is the label shown.
private val DIET_OPTIONS = listOf(
    "halal" to "Halal", "vegetarian" to "Vegetarian", "vegan" to "Vegan",
    "kosher" to "Kosher", "gluten_free" to "Gluten-free",
)
private val INTEREST_OPTIONS = listOf(
    "food" to "🍽️ Food", "worship" to "🛕 Temples & worship",
    "attraction" to "🏛️ Attractions", "outdoor" to "🌲 Outdoors",
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

        Spacer(Modifier.height(24.dp))
        AiSettingsSection(repo)

        Spacer(Modifier.height(24.dp))
        TravelModeSection(repo)

        // TEMPORARY (M6.4b): a dev trigger to verify the on-device pack builder before
        // the real "Download a city" screen arrives in M6.4d. Delete both this and the
        // TempPackBuilderSection composable below when that lands.
        TempPackBuilderSection()
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

// TEMPORARY (M6.4b): remove together with its call site when M6.4d ships the real
// "Download a city" screen. Builds a fixed small city so we can verify the whole
// on-device path end-to-end (then pull + inspect the resulting pack).
@Composable
private fun TempPackBuilderSection() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var building by remember { mutableStateOf(false) }
    var progress by remember { mutableStateOf(0f) }
    var result by remember { mutableStateOf<String?>(null) }

    Section("Dev: build a city pack")
    Button(
        enabled = !building,
        onClick = {
            building = true
            progress = 0f
            result = null
            scope.launch {
                val r = CityPackBuilder.build(context, "Geelong, Victoria, Australia") { progress = it }
                result = when (r) {
                    is CityPackBuilder.Result.Success ->
                        "Built ${r.cityName}: ${r.placeCount} places → ${r.file.name}"
                    is CityPackBuilder.Result.Failure -> "Failed: ${r.message}"
                }
                building = false
            }
        },
    ) { Text(if (building) "Building… ${(progress * 100).toInt()}%" else "Build test pack (Geelong)") }
    result?.let {
        Spacer(Modifier.height(8.dp))
        Text(it, style = MaterialTheme.typography.bodySmall)
    }
}
