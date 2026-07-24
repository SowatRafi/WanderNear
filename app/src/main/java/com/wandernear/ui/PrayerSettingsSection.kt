package com.wandernear.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.wandernear.core.model.UserPreferences
import com.wandernear.core.prayer.PrayerTimes
import com.wandernear.data.PreferencesRepository
import kotlinx.coroutines.launch

// Short chip labels for the calc conventions (the enum's own .label is the full name,
// shown in the home card's footnote). Kept here, not in core — they're UI text.
private val METHOD_LABELS = listOf(
    "MWL" to "MWL", "ISNA" to "ISNA", "EGYPT" to "Egypt",
    "KARACHI" to "Karachi", "MAKKAH" to "Makkah",
)
private val ASR_LABELS = listOf("STANDARD" to "Standard", "HANAFI" to "Hanafi")

/**
 * The "Prayer times" card in Preferences — opt-in, OFF by default.
 *
 * The times are CALCULATED on the phone (offline, free), and different authorities use
 * different twilight angles, so the traveller picks the convention that matches their
 * community rather than the app asserting one "true" time. Friday (Jumu'ah) is not
 * calculable — each mosque sets it — so the home card points to the mosque's own site.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PrayerSettingsSection(repo: PreferencesRepository) {
    val scope = rememberCoroutineScope()
    val prefs by repo.preferences.collectAsState(initial = UserPreferences())

    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Text("Prayer times", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(4.dp))
            Text(
                "Optional: show today's five prayer times and the nearest mosque. Times are " +
                    "calculated on your phone (they mark when each prayer begins) — a mosque may " +
                    "hold congregation a little later, and Friday times are set by each mosque.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Show prayer times & nearest mosque")
                Switch(
                    checked = prefs.prayerEnabled,
                    onCheckedChange = { on -> scope.launch { repo.setPrayerEnabled(on) } },
                )
            }

            // The calc knobs only matter once it's on, so hide them until then.
            if (prefs.prayerEnabled) {
                Spacer(Modifier.height(12.dp))
                Text("Calculation method", style = MaterialTheme.typography.labelLarge)
                Spacer(Modifier.height(6.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    METHOD_LABELS.forEach { (key, label) ->
                        FilterChip(
                            selected = prefs.prayerMethod == key,
                            onClick = { scope.launch { repo.setPrayerMethod(key) } },
                            label = { Text(label) },
                        )
                    }
                }
                Spacer(Modifier.height(10.dp))
                Text("Asr method", style = MaterialTheme.typography.labelLarge)
                Spacer(Modifier.height(6.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ASR_LABELS.forEach { (key, label) ->
                        FilterChip(
                            selected = prefs.prayerAsr == key,
                            onClick = { scope.launch { repo.setPrayerAsr(key) } },
                            label = { Text(label) },
                        )
                    }
                }
                // Name the selected method in full, so the choice is unambiguous.
                Spacer(Modifier.height(8.dp))
                Text(
                    "Using: ${methodLabel(prefs.prayerMethod)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/** The full name of a method key, from the calc enum (falls back to the key). */
private fun methodLabel(key: String): String =
    runCatching { PrayerTimes.Method.valueOf(key).label }.getOrDefault(key)
