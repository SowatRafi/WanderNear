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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import com.wandernear.data.LocationProvider
import com.wandernear.data.PackCatalogue
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

    // One status line, plus whether it's a problem (so it isn't colour-only).
    var message by remember { mutableStateOf<String?>(null) }
    var isError by remember { mutableStateOf(false) }
    // The pack awaiting the "Download …?" confirmation, if any.
    var confirming by remember { mutableStateOf<PackCatalogue.Pack?>(null) }
    // The installed pack awaiting a "Delete …?" confirmation, if any.
    var deleting by remember { mutableStateOf<InstalledPack?>(null) }
    // Non-null only while a download runs — it doubles as "are we downloading?".
    var progress by remember { mutableStateOf<Float?>(null) }
    var buildJob by remember { mutableStateOf<Job?>(null) }
    val building = progress != null

    // What's available to download. Loaded once; null until we know, so the UI can tell
    // "still loading" apart from "couldn't reach the catalogue".
    var catalogue by remember { mutableStateOf<List<PackCatalogue.Pack>?>(null) }
    var catalogueFailed by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        // Ordered for where you are, so the pack covering you is the first thing you see.
        // The fix is used ON-DEVICE only, to sort — it is never sent anywhere.
        val fix = withContext(Dispatchers.IO) { LocationProvider.lastKnown(context) }
        val loaded = PackCatalogue.load(context)
        catalogueFailed = loaded == null
        catalogue = loaded?.let { PackCatalogue.forUser(it, fix) }
    }

    /** Download the confirmed pack, then make it the active city. */
    fun startDownload(pack: PackCatalogue.Pack) {
        progress = 0f
        message = null
        buildJob = scope.launch {
            try {
                when (val result = PackCatalogue.download(context, pack) { progress = it }) {
                    is PackCatalogue.Result.Success -> {
                        repo.setActivePack("packs/" + result.file.name)
                        // "%,d" groups thousands — 21,149 reads far better than 21149.
                        message = "${pack.name} is ready — %,d places, and now works offline too."
                            .format(pack.places)
                        isError = false
                    }
                    is PackCatalogue.Result.Failure -> {
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

    WnCard {
            SectionHeader(Icons.Filled.Public, "Offline cities")
            Spacer(Modifier.height(6.dp))
            Text(
                "You don't need any of this day to day — WanderNear finds places around you live. " +
                    "Download a city only if you'll be somewhere without signal; it then works with " +
                    "no connection at all. Only the city name you type is ever sent.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            // --- 1. The cities already downloaded to this phone ------------------
            Spacer(Modifier.height(16.dp))
            Text("Downloaded", style = MaterialTheme.typography.labelLarge)
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
                        Text(pack.label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                        // ANY city can be removed — including the built-in Melbourne, and even the
                        // last one (the home then shows an "add a city" welcome).
                        IconButton(onClick = { deleting = pack }, enabled = !building) {
                            Icon(Icons.Outlined.Delete, "Delete ${pack.label}", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }

            // Nothing downloaded is the NORMAL state — say so, rather than leaving a blank gap
            // that reads like something failed to load.
            if (installed.isEmpty()) {
                Text(
                    "Nothing downloaded — you're exploring live.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            // --- 2. Available to download ----------------------------------------
            // Ready-made packs we publish. No typing and no geocoding: the one covering
            // where you are is simply first in the list.
            Spacer(Modifier.height(16.dp))
            Text("Available to download", style = MaterialTheme.typography.labelLarge)
            Spacer(Modifier.height(4.dp))

            val available = catalogue
            when {
                available == null && !catalogueFailed -> Text(
                    "Checking what's available…",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                catalogueFailed -> Text(
                    "Couldn't reach the list of cities — check your connection and reopen this screen.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                )
                available.isNullOrEmpty() -> Text(
                    "No cities published yet.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                // Hidden (not discarded) while downloading, so Cancel brings the list back.
                !building -> available.forEach { pack ->
                    val alreadyHave = installed.any { it.packName == "packs/" + pack.fileName }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp)
                            .clickable(enabled = !alreadyHave) { confirming = pack }
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                listOfNotNull(pack.name, pack.country).joinToString(", "),
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium,
                            )
                            Text(
                                // Size and place count are what decide whether it's worth
                                // downloading, so they're on the row, not behind a tap.
                                if (alreadyHave) "Already downloaded" else pack.summary,
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
                    "Downloading… ${(fraction * 100).toInt()}%",
                    style = MaterialTheme.typography.bodySmall,
                )
                // ponytail: the download is tied to this screen, exactly like the AI model
                // download above it. Hoist it into a service if leaving the screen
                // mid-download ever becomes a real annoyance.
                Text(
                    "Keep this screen open until it finishes.",
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

    // Confirm before spending someone's data allowance — the size is the fact that
    // decides it, so it leads.
    confirming?.let { pack ->
        AlertDialog(
            onDismissRequest = { confirming = null },
            title = { Text("Download ${pack.name}?") },
            text = {
                Text(
                    "${pack.summary}\n\n" +
                        "Saves this city's places to your phone so it works with no signal at " +
                        "all. You don't need this to explore while you're online." +
                        (pack.built?.let { "\n\nData from $it." } ?: ""),
                )
            },
            confirmButton = {
                TextButton(onClick = { confirming = null; startDownload(pack) }) { Text("Download") }
            },
            dismissButton = {
                TextButton(onClick = { confirming = null }) { Text("Cancel") }
            },
        )
    }

    // Confirm removing a city's offline data.
    deleting?.let { pack ->
        val isBundled = pack.packName == CityDatabase.BUNDLED_PACK
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text("Delete ${pack.label}?") },
            text = {
                Text(
                    if (isBundled)
                        "Melbourne is a leftover sample that shipped with the app. Removing it frees " +
                            "space and changes nothing about exploring live."
                    // ponytail: the bundled Melbourne asset (5 MB) now has no way back once
                    // deleted, since nothing advertises it any more. Drop the asset and its
                    // install/restore code the next time this file is touched.
                    else
                        "Its offline data will be removed from this phone. You can download it again any time.",
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    val pk = pack
                    deleting = null
                    scope.launch {
                        // If it's the active city, switch to ANOTHER installed one first, so the app
                        // is never left pointing at a pack that no longer exists. The "keep at least
                        // one city" rule guarantees another exists.
                        if (pk.packName == activePack) {
                            // Switch to another installed city, or reset to the default name if this
                            // was the LAST one — the home then shows an "add a city" welcome, and it
                            // all works again the moment a city exists (download, or Restore Melbourne).
                            val next = installed.firstOrNull { it.packName != pk.packName }?.packName
                                ?: CityDatabase.BUNDLED_PACK
                            repo.setActivePack(next)
                        }
                        withContext(Dispatchers.IO) {
                            if (pk.packName == CityDatabase.BUNDLED_PACK) CityDatabase.deleteBundled(context)
                            else CityPackBuilder.deleteInstalled(context, pk.packName)
                        }
                        installed = withContext(Dispatchers.IO) { installedPacks(context) }
                        message = "${pk.label} removed."
                        isError = false
                    }
                }) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text("Cancel") } },
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
    // The built-in Melbourne comes first, but only when it's actually installed.
    val bundled = if (CityDatabase.isBundledInstalled(context))
        listOf(InstalledPack(CityDatabase.BUNDLED_PACK, packLabel(context, CityDatabase.BUNDLED_PACK)))
    else emptyList()
    return bundled + downloaded
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
