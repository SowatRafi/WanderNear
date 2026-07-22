package com.wandernear.ui

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import coil.compose.AsyncImage
import com.wandernear.data.journal.BucketItem
import com.wandernear.data.journal.JournalDao
import com.wandernear.data.journal.JournalDatabase
import com.wandernear.data.journal.Photo
import com.wandernear.data.journal.PhotoStorage
import com.wandernear.data.journal.SavedPlace
import com.wandernear.data.journal.VisitDate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * The "My Trips" tab. Shows the list of saved places; tapping one opens its
 * detail (notes, visits, bucket list, photos, delete). We switch between list
 * and detail with a simple state flag instead of a navigation library.
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TripDetail(dao: JournalDao, placeId: Long, onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val place by dao.savedPlace(placeId).collectAsState(initial = null)
    val visits by dao.visits(placeId).collectAsState(initial = emptyList())
    val bucket by dao.bucketItems(placeId).collectAsState(initial = emptyList())
    val photos by dao.photos(placeId).collectAsState(initial = emptyList())

    val loaded = place
    if (loaded == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        return
    }

    // Editable copy of the notes, reset only when a different place is opened.
    var noteText by remember(loaded.id) { mutableStateOf(loaded.notes) }
    var confirmDelete by remember { mutableStateOf(false) }
    var showDatePicker by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
    ) {
        Text(loaded.name, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        loaded.subcategory?.let {
            Text(pretty(it), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
        }

        // --- Notes ---
        SectionTitle("Your notes")
        OutlinedTextField(
            value = noteText,
            onValueChange = { noteText = it },
            modifier = Modifier.fillMaxWidth(),
            minLines = 3,
            placeholder = { Text("What do you want to remember about this place?") },
        )
        Spacer(Modifier.height(8.dp))
        Button(onClick = {
            scope.launch {
                dao.update(loaded.copy(notes = noteText, updatedAt = System.currentTimeMillis()))
                Toast.makeText(context, "Notes saved", Toast.LENGTH_SHORT).show()
            }
        }) { Text("Save notes") }

        // --- Visits ---
        SectionTitle("Visits")
        if (visits.isEmpty()) {
            Text("No visits logged yet.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        visits.forEach { visit ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(formatDate(visit.visitedOn), style = MaterialTheme.typography.bodyLarge)
                TextButton(onClick = { scope.launch { dao.delete(visit) } }) { Text("Remove") }
            }
        }
        TextButton(onClick = { showDatePicker = true }) { Text("+ Add a visit") }

        // --- Bucket list ---
        val left = bucket.count { it.status == "todo" }
        SectionTitle(if (bucket.isEmpty()) "Bucket list" else "Bucket list · $left left")
        BucketAddRow(onAdd = { text ->
            val now = System.currentTimeMillis()
            scope.launch { dao.insert(BucketItem(savedPlaceId = placeId, text = text, createdAt = now, updatedAt = now)) }
        })
        bucket.forEach { item ->
            val done = item.status == "done"
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Checkbox(
                    checked = done,
                    onCheckedChange = {
                        val now = System.currentTimeMillis()
                        scope.launch {
                            dao.update(item.copy(
                                status = if (done) "todo" else "done",
                                doneOn = if (done) null else now,
                                updatedAt = now,
                            ))
                        }
                    },
                )
                Text(
                    item.text,
                    modifier = Modifier.weight(1f),
                    textDecoration = if (done) TextDecoration.LineThrough else null,
                    color = if (done) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                )
                TextButton(onClick = { scope.launch { dao.delete(item) } }) { Text("Remove") }
            }
        }

        // --- Photos ---
        SectionTitle("Photos")
        PhotosSection(dao, placeId, photos)

        // --- Delete the whole saved place ---
        Spacer(Modifier.height(24.dp))
        TextButton(
            onClick = { confirmDelete = true },
            colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
        ) { Text("Delete this place") }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete this place?") },
            text = { Text("It and all its notes, visits, bucket items, and photos will be removed. This can't be undone.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    scope.launch {
                        // Delete the photo files too, so nothing is left orphaned.
                        photos.forEach { PhotoStorage.delete(it.filePath) }
                        dao.delete(loaded)
                        onBack()
                    }
                }) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } },
        )
    }

    if (showDatePicker) {
        val pickerState = rememberDatePickerState(initialSelectedDateMillis = System.currentTimeMillis())
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    val millis = pickerState.selectedDateMillis
                    showDatePicker = false
                    if (millis != null) {
                        scope.launch {
                            dao.insert(VisitDate(savedPlaceId = placeId, visitedOn = millis, createdAt = System.currentTimeMillis()))
                        }
                    }
                }) { Text("Add") }
            },
            dismissButton = { TextButton(onClick = { showDatePicker = false }) { Text("Cancel") } },
        ) {
            DatePicker(state = pickerState)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PhotosSection(dao: JournalDao, placeId: Long, photos: List<Photo>) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var viewing by remember { mutableStateOf<Photo?>(null) }

    // The Android Photo Picker needs no permission and returns one photo Uri.
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) {
            scope.launch {
                val path = withContext(Dispatchers.IO) { PhotoStorage.save(context, uri) }
                if (path != null) {
                    dao.insert(Photo(savedPlaceId = placeId, filePath = path, createdAt = System.currentTimeMillis()))
                } else {
                    Toast.makeText(context, "Couldn't add that photo", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        photos.forEach { photo ->
            AsyncImage(
                model = File(photo.filePath),
                contentDescription = photo.caption ?: "Saved photo",
                contentScale = ContentScale.Crop,
                modifier = Modifier.size(96.dp).clip(RoundedCornerShape(12.dp)).clickable { viewing = photo },
            )
        }
        // The "+ Add" tile launches the photo picker.
        Box(
            modifier = Modifier
                .size(96.dp)
                .clip(RoundedCornerShape(12.dp))
                .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(12.dp))
                .clickable {
                    picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                },
            contentAlignment = Alignment.Center,
        ) {
            Text("+ Add", style = MaterialTheme.typography.labelMedium)
        }
    }

    viewing?.let { photo ->
        PhotoViewer(dao = dao, photo = photo, onClose = { viewing = null })
    }
}

@Composable
private fun PhotoViewer(dao: JournalDao, photo: Photo, onClose: () -> Unit) {
    val scope = rememberCoroutineScope()
    var caption by remember(photo.id) { mutableStateOf(photo.caption ?: "") }
    var confirmDelete by remember { mutableStateOf(false) }

    Dialog(onDismissRequest = onClose) {
        Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surface) {
            Column(Modifier.padding(16.dp)) {
                AsyncImage(
                    model = File(photo.filePath),
                    contentDescription = photo.caption ?: "Saved photo",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxWidth().heightIn(max = 360.dp),
                )
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = caption,
                    onValueChange = { caption = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    placeholder = { Text("Add a caption…") },
                )
                Spacer(Modifier.height(12.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    TextButton(
                        onClick = { confirmDelete = true },
                        colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                    ) { Text("Delete") }
                    Row {
                        TextButton(onClick = onClose) { Text("Close") }
                        Spacer(Modifier.width(8.dp))
                        Button(onClick = {
                            scope.launch { dao.update(photo.copy(caption = caption.ifBlank { null })) }
                            onClose()
                        }) { Text("Save") }
                    }
                }
            }
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete this photo?") },
            text = { Text("It will be removed from this place. This can't be undone.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    val path = photo.filePath
                    scope.launch { dao.delete(photo) }
                    PhotoStorage.delete(path)
                    onClose()
                }) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } },
        )
    }
}

/** Text field + Add button for a new bucket-list item; clears itself after adding. */
@Composable
private fun BucketAddRow(onAdd: (String) -> Unit) {
    var text by remember { mutableStateOf("") }
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        OutlinedTextField(
            value = text,
            onValueChange = { text = it },
            modifier = Modifier.weight(1f),
            singleLine = true,
            placeholder = { Text("Add something to do…") },
        )
        Spacer(Modifier.width(8.dp))
        Button(
            onClick = { onAdd(text.trim()); text = "" },
            enabled = text.isNotBlank(),
        ) { Text("Add") }
    }
}

@Composable
private fun SectionTitle(title: String) {
    Spacer(Modifier.height(24.dp))
    Text(title, style = MaterialTheme.typography.titleMedium)
    Spacer(Modifier.height(8.dp))
}

/** "place_of_worship" → "Place of worship". */
private fun pretty(raw: String): String =
    raw.replace('_', ' ').replaceFirstChar { it.uppercase() }

/** Epoch millis → a friendly date like "23 Jul 2026". */
private fun formatDate(millis: Long): String =
    Instant.ofEpochMilli(millis)
        .atZone(ZoneId.systemDefault())
        .toLocalDate()
        .format(DateTimeFormatter.ofPattern("d MMM yyyy"))
