package com.wandernear.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
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
import com.wandernear.core.model.Faith
import com.wandernear.core.model.UserPreferences
import com.wandernear.core.prayer.PrayerTimes
import com.wandernear.data.PreferencesRepository
import kotlinx.coroutines.launch

// Short chip labels for the Islamic calc conventions (the enum's own .label is the full
// name, shown in the home card's footnote). Kept here, not in core — they're UI text.
private val METHOD_LABELS = listOf(
    "MWL" to "MWL", "ISNA" to "ISNA", "EGYPT" to "Egypt",
    "KARACHI" to "Karachi", "MAKKAH" to "Makkah",
)
private val ASR_LABELS = listOf("STANDARD" to "Standard", "HANAFI" to "Hanafi")

/**
 * The "Faith & worship" card in Preferences — optional, none by default.
 *
 * Pick your faith and the home surfaces the nearest real place of worship for it
 * (temple / church / synagogue / gurdwara / mosque), grounded, with the place's own
 * website/phone for its service times. For Islam it additionally shows today's prayer
 * times, CALCULATED on the phone — so the method (which legitimately differs) is
 * chosen here rather than the app asserting one "true" time. No other faith has a
 * universal calculated daily timetable, so none is invented.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun FaithSettingsSection(repo: PreferencesRepository) {
    val scope = rememberCoroutineScope()
    val prefs by repo.preferences.collectAsState(initial = UserPreferences())

    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Text("Faith & worship", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(4.dp))
            Text(
                "Optional: show the nearest place of worship for your faith. For Islam, also " +
                    "today's calculated prayer times. Service times come from the place itself — " +
                    "we never invent one.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(12.dp))
            Text("Your faith", style = MaterialTheme.typography.labelLarge)
            Spacer(Modifier.height(6.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                // "None" clears the feature; then one chip per supported faith.
                FilterChip(
                    selected = prefs.faith.isBlank(),
                    onClick = { scope.launch { repo.setFaith("") } },
                    label = { Text("None") },
                )
                Faith.entries.forEach { faith ->
                    FilterChip(
                        selected = prefs.faith == faith.key,
                        onClick = { scope.launch { repo.setFaith(faith.key) } },
                        label = { Text(faith.label) },
                    )
                }
            }

            // Prayer-time calc knobs only matter for Islam, so hide them otherwise.
            if (prefs.faith == Faith.MUSLIM.key) {
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
