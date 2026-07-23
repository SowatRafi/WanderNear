package com.wandernear.ui

import android.Manifest
import android.content.pm.PackageManager
import androidx.compose.material3.IconButton
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.wandernear.voice.VoiceRecognizer
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
import com.wandernear.core.response.GroundingCheck
import com.wandernear.core.response.Recommender
import com.wandernear.core.retrieval.QueryParser
import com.wandernear.data.CityDatabase
import com.wandernear.data.LocationProvider
import com.wandernear.data.PreferencesRepository
import com.wandernear.data.journal.JournalDatabase
import com.wandernear.data.journal.SavedPlace
import com.wandernear.ai.LlmEngine
import com.wandernear.ai.ModelManager
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.foundation.layout.size
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

// Fallback origin: roughly the centre of Melbourne's CBD, used when we don't
// have the user's real location (permission declined, or no GPS fix yet).
private val MELBOURNE_CBD = LatLng(-37.8136, 144.9631)

// Example prompts shown on the empty screen to help the user get started.
private val EXAMPLES = listOf("Vegetarian food", "Temples", "Halal food", "Parks & nature", "Museums")

private enum class Role { User, Assistant }
// Voice goes through three explicit states so the UI can be honest about what's
// happening: Idle (not using voice) → Preparing (loading the model) → Listening.
private enum class VoiceState { Idle, Preparing, Listening }
private data class RecCard(val place: Place, val reason: String)
private data class ChatMessage(
    val role: Role,
    val text: String,
    val cards: List<RecCard> = emptyList(),
    val loading: Boolean = false,
)

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

    // --- Voice input (offline, Vosk) ---
    // Idle → Preparing (loading the model) → Listening (actually capturing).
    // Keeping "Preparing" separate means we never show "Listening…" before the
    // mic is really on, so the user's first words can't be lost during the slow
    // first-time model load.
    var voiceState by remember { mutableStateOf(VoiceState.Idle) }
    // Did Vosk return any words this turn? Lets us nudge gently if the user stops
    // the mic but nothing was heard, instead of failing silently.
    var heardSpeech by remember { mutableStateOf(false) }

    fun startVoice() {
        voiceState = VoiceState.Preparing
        input = ""
        heardSpeech = false
        scope.launch {
            if (!VoiceRecognizer.ensureModel(context)) {
                voiceState = VoiceState.Idle
                Toast.makeText(context, "Voice model couldn't load", Toast.LENGTH_SHORT).show()
                return@launch
            }
            val began = VoiceRecognizer.start(
                onPartial = { input = it; heardSpeech = true },
                onFinal = { text ->
                    input = text
                    heardSpeech = true
                    VoiceRecognizer.stop()          // release the mic as soon as we have the phrase
                    voiceState = VoiceState.Idle
                },
                onFail = { voiceState = VoiceState.Idle; Toast.makeText(context, it, Toast.LENGTH_SHORT).show() },
            )
            // Only now are we truly capturing audio.
            if (voiceState == VoiceState.Preparing) {
                voiceState = if (began) VoiceState.Listening else VoiceState.Idle
            } else if (began) {
                // The user cancelled (typed & sent) while the model was loading —
                // never leave the mic quietly recording. Privacy first.
                VoiceRecognizer.stop()
            }
        }
    }

    val micPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) startVoice()
        else Toast.makeText(context, "Microphone permission is needed for voice input", Toast.LENGTH_SHORT).show()
    }

    fun toggleMic() {
        when (voiceState) {
            VoiceState.Preparing -> Unit   // busy loading the model — ignore taps
            VoiceState.Listening -> {
                VoiceRecognizer.stop()
                voiceState = VoiceState.Idle
                // Nothing recognised the whole time → say so, don't fail silently.
                if (!heardSpeech) {
                    Toast.makeText(context, "Didn't catch that — tap the mic to try again", Toast.LENGTH_SHORT).show()
                }
            }
            VoiceState.Idle -> {
                val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
                    PackageManager.PERMISSION_GRANTED
                if (granted) startVoice() else micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
            }
        }
    }

    // Runs the actual search: uses the real location if we have one, else the
    // city centre. Both parse + search happen off the main thread.
    fun runSearch(question: String) {
        val aiEnabled = prefs.useAi && ModelManager.isDownloaded(context)
        // For the AI path (which can be slow, especially the first model load),
        // show a temporary loading bubble and replace it when the reply is ready.
        val placeholderIndex = if (aiEnabled) {
            val warming = !LlmEngine.isLoaded()
            messages += ChatMessage(
                Role.Assistant,
                if (warming) "Warming up the on-device AI (first time, about a minute)…" else "Thinking…",
                loading = true,
            )
            messages.lastIndex
        } else {
            null
        }

        scope.launch {
            val answer = withContext(Dispatchers.IO) {
                val here = LocationProvider.lastKnown(context)   // null ⇒ no permission/fix
                val origin = here ?: MELBOURNE_CBD
                val spec = QueryParser.parse(question, prefs)
                val places = db.search(spec, origin)

                if (places.isEmpty()) {
                    // Nothing retrieved → honest refusal; the AI is never called.
                    ChatMessage(Role.Assistant, Recommender.NO_RESULTS)
                } else {
                    val cards = places.map { RecCard(it, Recommender.reason(it, spec)) }
                    val intro = if (aiEnabled) {
                        val ready = LlmEngine.ensureReady(context)
                        val aiText = if (ready) {
                            LlmEngine.generate(Recommender.AI_SYSTEM, Recommender.aiPrompt(question, places, here != null))
                        } else {
                            null
                        }
                        // Use the AI reply only if it isn't empty AND names only
                        // places we actually retrieved; otherwise fall back to the
                        // template. This is the enforced never-hallucinate guardrail.
                        aiText?.takeIf { it.isNotBlank() && GroundingCheck.isGrounded(it, places) }
                            ?: Recommender.reply(spec, places, nearYou = here != null)
                    } else {
                        Recommender.reply(spec, places, nearYou = here != null)
                    }
                    ChatMessage(Role.Assistant, intro, cards)
                }
            }
            if (placeholderIndex != null) messages[placeholderIndex] = answer else messages += answer
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
        if (voiceState == VoiceState.Listening) VoiceRecognizer.stop()   // stop the mic on send
        voiceState = VoiceState.Idle
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
        InputBar(
            value = input,
            onValueChange = { input = it },
            onSend = { ask(input) },
            voiceState = voiceState,
            onMicToggle = { toggleMic() },
        )
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
            if (message.loading) {
                androidx.compose.foundation.layout.Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(8.dp))
                    Text(message.text)
                }
            } else {
                Text(message.text, modifier = Modifier.padding(12.dp))
            }
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
private fun InputBar(
    value: String,
    onValueChange: (String) -> Unit,
    onSend: () -> Unit,
    voiceState: VoiceState,
    onMicToggle: () -> Unit,
) {
    val listening = voiceState == VoiceState.Listening
    // The hint text is our clearest signal of which voice state we're in.
    val placeholder = when (voiceState) {
        VoiceState.Preparing -> "Preparing voice…"
        VoiceState.Listening -> "Listening…"
        VoiceState.Idle -> "Ask for a place…"
    }
    Surface(tonalElevation = 3.dp) {
        androidx.compose.foundation.layout.Row(
            modifier = Modifier.fillMaxWidth().padding(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Mic: tap to speak (offline). Turns into a red stop while listening.
            IconButton(onClick = onMicToggle) {
                Text(
                    if (listening) "■" else "🎤",
                    fontSize = 20.sp,
                    color = if (listening) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            OutlinedTextField(
                value = value,
                onValueChange = onValueChange,
                modifier = Modifier.weight(1f),
                placeholder = { Text(placeholder) },
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
