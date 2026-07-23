package com.wandernear.ui

import android.content.Context
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.wandernear.data.CityDatabase
import com.wandernear.data.CityPackBuilder
import com.wandernear.data.PreferencesRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * The "Cities" card in Preferences — the real M6.4d flow that replaced the
 * temporary dev trigger.
 *
 * Two jobs, in the order you need them:
 *  1. **Your cities** — every pack already on the phone (the bundled city plus
 *     anything downloaded). Tapping one switches the whole app to it; the home
 *     screen reloads on its own because it watches `activePack`.
 *  2. **Add a city** — type a name, we ask OpenStreetMap which real places match,
 *     you confirm the right one, then it's downloaded and built into a pack on the
 *     phone by [CityPackBuilder].
 *
 * Why a confirm step: Nominatim happily returns "Paris, Texas" for "Paris". Showing
 * OSM's own full name for each match — and making you pick — means a several-minute
 * download can never quietly fetch the wrong city.
 *
 * Privacy: the only thing that ever leaves the phone here is the city name YOU type.
 * Your location is never sent, and this field is never pre-filled from your GPS.
 */
@Composable
fun CitiesSection(repo: PreferencesRepository) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val focus = LocalFocusManager.current
    val activePack by repo.activePack.collectAsState(initial = CityDatabase.BUNDLED_PACK)

    // The packs on this phone. Reloaded whenever the active one changes — which
    // covers a finished download too, since that switches the active pack.
    var installed by remember { mutableStateOf<List<InstalledPack>>(emptyList()) }
    LaunchedEffect(activePack) {
        installed = withContext(Dispatchers.IO) { installedPacks(context) }
    }

    var query by remember { mutableStateOf("") }
    var searching by remember { mutableStateOf(false) }
    var matches by remember { mutableStateOf<List<CityPackBuilder.Match>>(emptyList()) }
    // One status line, plus whether it's a problem (so it isn't colour-only).
    var message by remember { mutableStateOf<String?>(null) }
    var isError by remember { mutableStateOf(false) }
    // The match awaiting the "Download data for …?" confirmation, if any.
    var confirming by remember { mutableStateOf<CityPackBuilder.Match?>(null) }
    // Non-null only while a build runs — it doubles as "are we building?".
    var progress by remember { mutableStateOf<Float?>(null) }
    var buildJob by remember { mutableStateOf<Job?>(null) }
    val building = progress != null

    /** Ask OSM which real areas match what was typed. */
    fun runSearch() {
        focus.clearFocus()          // drop the keyboard so results are visible
        searching = true
        message = null
        val typed = query.trim()
        scope.launch {
            val found = CityPackBuilder.find(typed)
            matches = found
            if (found.isEmpty()) {
                // One honest message covering BOTH causes — a typo and being offline
                // look identical from here, so we never claim to know which it was.
                message = "Couldn't find \"$typed\" — check the spelling, and that you're online."
                isError = true
            }
            searching = false
        }
    }

    /** Download + build the confirmed city, then make it the active one. */
    fun startBuild(match: CityPackBuilder.Match) {
        progress = 0f
        message = null
        buildJob = scope.launch {
            try {
                when (val result = CityPackBuilder.build(context, match) { progress = it }) {
                    is CityPackBuilder.Result.Success -> {
                        // Switching here is what makes the home screen reload into the
                        // new city, and refreshes the list above.
                        repo.setActivePack("packs/" + result.file.name)
                        matches = emptyList()
                        query = ""
                        // "%,d" groups thousands — 10,326 reads far better than 10326.
                        message = "${match.shortLabel} is ready — %,d places. ".format(result.placeCount) +
                            "It's now your active city."
                        isError = false
                    }
                    is CityPackBuilder.Result.Failure -> {
                        message = result.message
                        isError = true
                    }
                }
            } finally {
                // Runs on success, failure AND cancellation, so the progress bar can
                // never get stuck on screen.
                progress = null
            }
        }
    }

    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Text("Cities", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(4.dp))
            Text(
                "Switch between the cities you have offline, or add a new one. " +
                    "Adding needs internet once — after that the city works with no signal at all.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            // --- 1. The cities already on this phone -----------------------------
            Spacer(Modifier.height(16.dp))
            Text("Your cities", style = MaterialTheme.typography.labelLarge)
            // selectableGroup + role = RadioButton tells a screen reader this is one
            // "pick exactly one" list, and reads out which is selected.
            Column(Modifier.selectableGroup()) {
                installed.forEach { pack ->
                    val selected = pack.packName == activePack
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp)      // comfortable touch target
                            .selectable(
                                selected = selected,
                                enabled = !building,     // don't switch mid-download
                                role = Role.RadioButton,
                                onClick = { scope.launch { repo.setActivePack(pack.packName) } },
                            ),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        // onClick = null: the whole row handles the tap, so the button
                        // isn't a second, smaller target announced separately.
                        RadioButton(selected = selected, onClick = null, enabled = !building)
                        Spacer(Modifier.width(8.dp))
                        Text(pack.label, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }

            // --- 2. Add a city ---------------------------------------------------
            Spacer(Modifier.height(16.dp))
            Text("Add a city", style = MaterialTheme.typography.labelLarge)
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                // A real label, not just a placeholder — it stays visible while typing.
                label = { Text("City name") },
                placeholder = { Text("e.g. Kyoto, Japan") },
                singleLine = true,
                enabled = !building,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                // The keyboard's own Search key does the same as the button.
                keyboardActions = KeyboardActions(onSearch = { runSearch() }),
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(8.dp))
            Button(
                // Searching only on an explicit tap (never per keystroke) also keeps us
                // inside OpenStreetMap's 1-request-per-second usage policy.
                onClick = { runSearch() },
                enabled = query.isNotBlank() && !searching && !building,
            ) { Text(if (searching) "Searching…" else "Search") }

            // Matches: OSM's full name for each, so the right one is unmistakable.
            // Hidden (not discarded) while building, so Cancel brings them back.
            if (!building) {
                matches.forEach { match ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp)
                            .clickable { confirming = match }
                            .padding(vertical = 8.dp),
                    ) {
                        Column {
                            Text(match.shortLabel, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                            Text(
                                match.label,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }

            // --- Progress --------------------------------------------------------
            progress?.let { fraction ->
                Spacer(Modifier.height(12.dp))
                LinearProgressIndicator(progress = { fraction }, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(6.dp))
                Text(
                    "Building your offline pack… ${(fraction * 100).toInt()}%",
                    style = MaterialTheme.typography.bodySmall,
                )
                // ponytail: the build is tied to this screen, exactly like the AI model
                // download above it. Hoist it into a service if leaving the screen
                // mid-download ever becomes a real annoyance.
                Text(
                    "Keep this screen open — a big city can take a couple of minutes.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                TextButton(onClick = { buildJob?.cancel() }) { Text("Cancel") }
            }

            // --- Status line -----------------------------------------------------
            message?.let {
                Spacer(Modifier.height(8.dp))
                Text(
                    it,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
                    // Announced by a screen reader without stealing focus.
                    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                )
            }
        }
    }

    // The confirmation, named after exactly what it does.
    confirming?.let { match ->
        AlertDialog(
            onDismissRequest = { confirming = null },
            title = { Text("Download data for ${match.shortLabel}?") },
            text = {
                Text(
                    "${match.label}\n\n" +
                        "Downloads map data over the internet — usually a few megabytes, up to a " +
                        "couple of minutes. Only the city name is sent; nothing about you.\n\n" +
                        "New cities come with places and directions, but not yet the Wikipedia " +
                        "descriptions the built-in city has.",
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    confirming = null
                    startBuild(match)
                }) { Text("Download") }
            },
            dismissButton = {
                TextButton(onClick = { confirming = null }) { Text("Cancel") }
            },
        )
    }
}

/** One city pack on this phone: where it lives, and the name to show for it. */
private class InstalledPack(val packName: String, val label: String)

/**
 * Every pack available right now: the bundled city first, then anything downloaded
 * into `filesDir/packs/`. Call from a background thread — it opens each pack.
 */
private fun installedPacks(context: Context): List<InstalledPack> {
    val downloaded = CityPackBuilder.packsDir(context)
        .listFiles { file -> file.name.endsWith(".db") }
        ?.sortedBy { it.name }
        ?.map { InstalledPack("packs/" + it.name, packLabel(context, "packs/" + it.name)) }
        ?: emptyList()
    return listOf(
        InstalledPack(CityDatabase.BUNDLED_PACK, packLabel(context, CityDatabase.BUNDLED_PACK)),
    ) + downloaded
}

/**
 * A pack's display name, read from its own `city` row — grounded in the data rather
 * than prettied up from the filename. Falls back to the filename only if the pack
 * can't be opened, so an unreadable pack still appears (and can be switched away from)
 * instead of vanishing.
 */
private fun packLabel(context: Context, packName: String): String =
    runCatching { CityDatabase(context, packName).cityInfo()?.shortName }.getOrNull()
        ?: packName.substringAfterLast('/')
