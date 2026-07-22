package com.wandernear.ui

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.wandernear.data.journal.JournalDao
import com.wandernear.data.journal.JournalDatabase
import com.wandernear.data.journal.SavedPlace
import kotlinx.coroutines.launch

/**
 * The "My Trips" tab. Shows the list of saved places; tapping one opens its
 * detail (editable notes + delete). We switch between list and detail with a
 * simple state flag instead of pulling in a navigation library.
 */
@Composable
fun MyTripsScreen() {
    val context = LocalContext.current
    val dao = remember { JournalDatabase.get(context).journalDao() }
    val places by dao.savedPlaces().collectAsState(initial = emptyList())
    var openId by remember { mutableStateOf<Long?>(null) }

    val current = openId
    if (current == null) {
        TripList(places, onOpen = { openId = it.id })
    } else {
        BackHandler { openId = null }              // phone back button → list
        TripDetail(dao, current, onBack = { openId = null })
    }
}

@Composable
private fun TripList(places: List<SavedPlace>, onOpen: (SavedPlace) -> Unit) {
    if (places.isEmpty()) {
        Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("No saved places yet", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(8.dp))
                Text(
                    "Tap “Save” on any recommendation to keep it here.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        return
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        items(places) { place ->
            Card(Modifier.fillMaxWidth().clickable { onOpen(place) }) {
                Column(Modifier.padding(16.dp)) {
                    Text(place.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    place.subcategory?.let {
                        Text(pretty(it), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                    }
                    if (place.notes.isNotBlank()) {
                        Spacer(Modifier.height(4.dp))
                        Text(
                            place.notes,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TripDetail(dao: JournalDao, placeId: Long, onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val place by dao.savedPlace(placeId).collectAsState(initial = null)

    val loaded = place
    if (loaded == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        return
    }

    // Editable copy of the notes, reset only when a different place is opened.
    var noteText by remember(loaded.id) { mutableStateOf(loaded.notes) }
    var confirmDelete by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
    ) {
        Text(loaded.name, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        loaded.subcategory?.let {
            Text(pretty(it), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
        }

        Spacer(Modifier.height(20.dp))
        Text("Your notes", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = noteText,
            onValueChange = { noteText = it },
            modifier = Modifier.fillMaxWidth(),
            minLines = 4,
            placeholder = { Text("What do you want to remember about this place?") },
        )

        Spacer(Modifier.height(16.dp))
        Row {
            Button(onClick = {
                scope.launch {
                    dao.update(loaded.copy(notes = noteText, updatedAt = System.currentTimeMillis()))
                    Toast.makeText(context, "Notes saved", Toast.LENGTH_SHORT).show()
                }
            }) { Text("Save notes") }

            Spacer(Modifier.width(8.dp))
            TextButton(
                onClick = { confirmDelete = true },
                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
            ) { Text("Delete") }
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete this place?") },
            text = { Text("It will be removed from My Trips. This can't be undone.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    scope.launch { dao.delete(loaded); onBack() }
                }) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } },
        )
    }
}

/** "place_of_worship" → "Place of worship". */
private fun pretty(raw: String): String =
    raw.replace('_', ' ').replaceFirstChar { it.uppercase() }
