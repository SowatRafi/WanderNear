package com.wandernear.ui

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.wandernear.core.model.LatLng
import com.wandernear.core.model.Place
import com.wandernear.core.model.UserPreferences
import com.wandernear.core.response.Recommender
import com.wandernear.core.retrieval.QueryParser
import com.wandernear.data.CityDatabase
import com.wandernear.data.LocationProvider
import com.wandernear.data.PreferencesRepository
import com.wandernear.data.journal.JournalDatabase
import com.wandernear.data.journal.SavedPlace
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

// Fallback origin: roughly the centre of Melbourne's CBD, used when we don't
// have the user's real location (permission declined, or no GPS fix yet).
private val MELBOURNE_CBD = LatLng(-37.8136, 144.9631)

// Example prompts shown on the empty screen to help the user get started.
private val EXAMPLES = listOf("Vegetarian food", "Temples", "Halal food", "Parks & nature", "Museums")

private enum class Role { User, Assistant }
private data class RecCard(val place: Place, val reason: String)
private data class ChatMessage(val role: Role, val text: String, val cards: List<RecCard> = emptyList())

/** The main tab: ask for a place, get real recommendations built from the data. */
@Composable
fun ChatScreen(prefsRepo: PreferencesRepository) {
    val context = LocalContext.current
    val prefs by prefsRepo.preferences.collectAsState(initial = UserPreferences())
    val db = remember { CityDatabase(context) }
    val journalDao = remember { JournalDatabase.get(context).journalDao() }
    val messages = remember { mutableStateListOf<ChatMessage>() }
    var input by remember { mutableStateOf("") }
    var askedLocation by remember { mutableStateOf(false) }
    var pendingQuestion by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()

    // Runs the actual search: uses the real location if we have one, else the
    // city centre. Both parse + search happen off the main thread.
    fun runSearch(question: String) {
        scope.launch {
            val answer = withContext(Dispatchers.IO) {
                val here = LocationProvider.lastKnown(context)   // null ⇒ no permission/fix
                val origin = here ?: MELBOURNE_CBD
                val spec = QueryParser.parse(question, prefs)
                val places = db.search(spec, origin)
                ChatMessage(
                    role = Role.Assistant,
                    text = Recommender.reply(spec, places, nearYou = here != null),
                    cards = places.map { RecCard(it, Recommender.reason(it, spec)) },
                )
            }
            messages += answer
        }
    }

    // Asks for location once; whatever the user chooses, we run the pending search.
    val locationLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { _ ->
        pendingQuestion?.let { runSearch(it); pendingQuestion = null }
    }

    fun ask(text: String) {
        val question = text.trim()
        if (question.isEmpty()) return
        messages += ChatMessage(Role.User, question)
        input = ""
        // On the very first search, request location once so "near me" is real.
        if (!askedLocation && !LocationProvider.hasPermission(context)) {
            askedLocation = true
            pendingQuestion = question
            locationLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
        } else {
            runSearch(question)
        }
    }

    // Saves a recommended place into the journal as a self-contained snapshot.
    fun saveToTrips(place: Place) {
        val now = System.currentTimeMillis()
        scope.launch {
            journalDao.insert(
                SavedPlace(
                    name = place.name,
                    lat = place.lat,
                    lng = place.lng,
                    category = place.category,
                    subcategory = place.subcategory,
                    createdAt = now,
                    updatedAt = now,
                ),
            )
            Toast.makeText(context, "Saved to My Trips", Toast.LENGTH_SHORT).show()
        }
    }

    // Keep the newest message in view.
    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) listState.animateScrollToItem(messages.size - 1)
    }

    Column(Modifier.fillMaxSize().imePadding()) {
        if (messages.isEmpty()) {
            EmptyState(onExample = ::ask, modifier = Modifier.weight(1f))
        } else {
            LazyColumn(
                state = listState,
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(messages) { message ->
                    MessageItem(
                        message,
                        onDirections = { openDirections(context, it) },
                        onSave = { saveToTrips(it) },
                    )
                }
            }
        }

        // Required data credit, always visible where data is shown.
        Text(
            "© OpenStreetMap contributors · Wikipedia CC BY-SA 4.0",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
            textAlign = TextAlign.Center,
        )
        InputBar(value = input, onValueChange = { input = it }, onSend = { ask(input) })
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun EmptyState(onExample: (String) -> Unit, modifier: Modifier) {
    Column(
        modifier = modifier.fillMaxWidth().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text("WanderNear", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        Text(
            "Tell me what you're into and I'll suggest real local spots — all offline.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(20.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            EXAMPLES.forEach { example ->
                AssistChip(onClick = { onExample(example) }, label = { Text(example) })
            }
        }
    }
}

@Composable
private fun MessageItem(message: ChatMessage, onDirections: (Place) -> Unit, onSave: (Place) -> Unit) {
    val isUser = message.role == Role.User
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = if (isUser) Alignment.End else Alignment.Start,
    ) {
        Surface(
            color = if (isUser) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
            contentColor = if (isUser) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
            shape = RoundedCornerShape(16.dp),
        ) {
            Text(message.text, modifier = Modifier.padding(12.dp))
        }
        message.cards.forEach { card ->
            Spacer(Modifier.height(8.dp))
            RecommendationCard(card, onDirections, onSave)
        }
    }
}

@Composable
private fun RecommendationCard(card: RecCard, onDirections: (Place) -> Unit, onSave: (Place) -> Unit) {
    val place = card.place
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Text(place.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            place.subcategory?.let {
                Text(
                    it.replace('_', ' ').replaceFirstChar { c -> c.uppercase() },
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            if (card.reason.isNotBlank()) {
                Spacer(Modifier.height(6.dp))
                Text(card.reason, style = MaterialTheme.typography.bodyMedium)
            }
            place.summary?.let {
                Spacer(Modifier.height(6.dp))
                Text(
                    it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.height(8.dp))
            androidx.compose.foundation.layout.Row {
                TextButton(onClick = { onDirections(place) }) { Text("Directions") }
                Spacer(Modifier.width(8.dp))
                TextButton(onClick = { onSave(place) }) { Text("Save") }
            }
        }
    }
}

@Composable
private fun InputBar(value: String, onValueChange: (String) -> Unit, onSend: () -> Unit) {
    Surface(tonalElevation = 3.dp) {
        androidx.compose.foundation.layout.Row(
            modifier = Modifier.fillMaxWidth().padding(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OutlinedTextField(
                value = value,
                onValueChange = onValueChange,
                modifier = Modifier.weight(1f),
                placeholder = { Text("Ask for a place…") },
                maxLines = 3,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(onSend = { onSend() }),
            )
            Spacer(Modifier.width(8.dp))
            Button(onClick = onSend, enabled = value.isNotBlank()) { Text("Send") }
        }
    }
}

/** Opens the phone's default maps app at the place (no map SDK or API key needed). */
private fun openDirections(context: Context, place: Place) {
    val label = Uri.encode(place.name)
    val uri = Uri.parse("geo:${place.lat},${place.lng}?q=${place.lat},${place.lng}($label)")
    try {
        context.startActivity(Intent(Intent.ACTION_VIEW, uri))
    } catch (e: ActivityNotFoundException) {
        Toast.makeText(context, "No maps app found on this phone", Toast.LENGTH_SHORT).show()
    }
}
