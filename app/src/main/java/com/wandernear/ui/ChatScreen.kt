package com.wandernear.ui

import android.Manifest
import android.content.pm.PackageManager
import androidx.compose.material3.IconButton
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.wandernear.voice.VoiceRecognizer
import android.content.ActivityNotFoundException
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AlertDialog
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
import androidx.compose.runtime.saveable.rememberSaveable
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
import com.wandernear.core.model.CityEvent
import com.wandernear.core.model.CityInfo
import com.wandernear.core.model.CountryFacts
import com.wandernear.core.model.LatLng
import com.wandernear.core.model.Place
import com.wandernear.core.model.UserPreferences
import com.wandernear.core.model.categoryLabel
import com.wandernear.core.model.distanceLabel
import com.wandernear.core.model.fixInCity
import com.wandernear.core.prayer.PrayerTimes
import com.wandernear.travel.TravelModeService
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
import androidx.compose.material3.Icon
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.LiveRegionMode
import android.provider.Settings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

// Fallback origin: roughly the centre of Melbourne's CBD, used when we don't
// have the user's real location (permission declined, or no GPS fix yet).
private val MELBOURNE_CBD = LatLng(-37.8136, 144.9631)

// Example prompts shown on the empty screen to help the user get started.
private val EXAMPLES = listOf(
    "Vegetarian food", "Temples", "Halal food", "Parks & nature", "Museums",
    "Shopping & markets", "Theatres & live music",
)

// Radius for the "worth visiting near you" suggestions — wide enough to surface a
// few notable spots in a city, ranked nearest-first.
private const val NEARBY_RADIUS_KM = 15.0

// "Daily needs" categories shown in the essentials card, in display order.
private val ESSENTIAL_CATEGORIES = listOf("safety", "health", "fuel", "parking")

// How many festivals fit on a card before it stops being readable. The rest are
// counted honestly ("…and 19 more") rather than silently dropped.
private const val FESTIVALS_SHOWN = 6

// The on-device suburb is only shown from a fresh fix within this range of the pack,
// so a stale fix or a fix in another city can never mislabel where you are.
private const val LOCALITY_MAX_KM = 25.0
private const val LOCALITY_FIX_MAX_AGE_MS = 10 * 60 * 1000L   // 10 minutes

// `fixInCity` (the "are you actually in this city?" guard) lives in core/ so the
// Travel Mode service applies exactly the same rule — see core/model/Place.kt.

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
    // The active city pack — re-open the data whenever it changes (a download or reset).
    val activePack by prefsRepo.activePack.collectAsState(initial = CityDatabase.BUNDLED_PACK)
    val db = remember(activePack) { CityDatabase(context, activePack) }
    val journalDao = remember { JournalDatabase.get(context).journalDao() }
    val messages = remember { mutableStateListOf<ChatMessage>() }
    var input by remember { mutableStateOf("") }
    var askedLocation by remember { mutableStateOf(false) }
    var pendingQuestion by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()
    // The active city's facts (name, country, population) for the City Info card.
    var cityInfo by remember(activePack) { mutableStateOf<CityInfo?>(null) }
    // Nearest essentials (police/hospital/fuel/parking) for the daily-needs card.
    var essentials by remember(activePack) { mutableStateOf<List<Place>>(emptyList()) }
    // The active city's centre — the "near me" fallback when we have no GPS fix.
    var cityCenter by remember(activePack) { mutableStateOf<LatLng?>(null) }
    // The traveller's actual locality (on-device nearest suburb) for the header,
    // and grounded "worth visiting near you" suggestions — both loaded below.
    var locality by remember(activePack) { mutableStateOf<String?>(null) }
    var notable by remember(activePack) { mutableStateOf<List<Place>>(emptyList()) }
    // Today's prayer times (calculated on-device) + the nearest mosque — only when the
    // user opted in AND we can confirm they're in this city (so the timezone is right).
    var prayerTimes by remember(activePack) { mutableStateOf<PrayerTimes.Times?>(null) }
    var mosque by remember(activePack) { mutableStateOf<Place?>(null) }
    // The city's annual festivals (no dates — see FestivalsCard). Pack-wide, not
    // location-based, so it doesn't depend on having a fix.
    var festivals by remember(activePack) { mutableStateOf<List<CityEvent>>(emptyList()) }
    // "Around you now" comes from the Travel Mode service's own fixes — the screen never
    // asks for location itself, so Travel Mode stays the one place that watches you.
    // Empty (and the card hidden) whenever Travel Mode is off. Digests carry the pack
    // they came from and anything from a different city is dropped: a digest only
    // refreshes when you move, so after a city switch the old one would otherwise linger
    // and list another city's places.
    val aroundState by TravelModeService.around.collectAsState()
    val around = aroundState?.takeIf { it.packName == activePack }?.places.orEmpty()

    // --- Voice input (offline, Vosk) ---
    // Idle → Preparing (loading the model) → Listening (actually capturing).
    // Keeping "Preparing" separate means we never show "Listening…" before the
    // mic is really on, so the user's first words can't be lost during the slow
    // first-time model load.
    var voiceState by remember { mutableStateOf(VoiceState.Idle) }
    // Did Vosk return any words this turn? Lets us nudge gently if the user stops
    // the mic but nothing was heard, instead of failing silently.
    var heardSpeech by remember { mutableStateOf(false) }
    // Shown only when the mic permission is denied for good — offers a way to Settings.
    // Saveable so the recovery dialog survives a rotation / dark-mode change mid-read.
    var showMicSettingsDialog by rememberSaveable { mutableStateOf(false) }

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
        if (granted) {
            startVoice()
        } else {
            // If we can still ask again, just explain and let them retry. If not
            // (permanently denied — the system won't show the dialog anymore), offer
            // a route into Settings so they aren't stuck with a dead mic button.
            val activity = context.findActivity()
            val canAskAgain = activity != null &&
                ActivityCompat.shouldShowRequestPermissionRationale(activity, Manifest.permission.RECORD_AUDIO)
            if (canAskAgain) {
                Toast.makeText(context, "Microphone is needed to speak your search — tap the mic to allow", Toast.LENGTH_SHORT).show()
            } else {
                showMicSettingsDialog = true
            }
        }
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
                // null ⇒ no permission/fix, OR we're nowhere near this city (see fixInCity)
                val here = fixInCity(LocationProvider.lastKnown(context), cityCenter)
                val origin = here ?: cityCenter ?: MELBOURNE_CBD
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
                        aiText?.takeIf { it.isNotBlank() && GroundingCheck.isGrounded(it, places, cityInfo?.name) }
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

    // Load the active city's facts + centre + nearest police, off the main thread,
    // and RELOAD whenever the active pack changes (a download or reset). Origin for
    // the Safety card is the real fix if we have permission, else the city centre.
    LaunchedEffect(activePack) {
        val center = withContext(Dispatchers.IO) { db.cityCenter() }
        cityCenter = center
        cityInfo = withContext(Dispatchers.IO) { db.cityInfo() }
        festivals = withContext(Dispatchers.IO) { db.festivals() }
        // Read location off the main thread (binder IPC). A stale fix is fine for
        // ranking; the "you are here" label below uses a FRESH fix only.
        val fix = withContext(Dispatchers.IO) { LocationProvider.lastKnown(context) }
        val origin = fixInCity(fix, center) ?: center ?: MELBOURNE_CBD
        essentials = withContext(Dispatchers.IO) { db.nearestEssentials(origin, ESSENTIAL_CATEGORIES) }
        notable = withContext(Dispatchers.IO) { db.nearbyNotable(origin, NEARBY_RADIUS_KM) }
        // Which suburb am I in? Derived ON-DEVICE from the pack (no GPS ever leaves the
        // phone), from a FRESH fix within the pack's area — else null (show the city).
        val freshFix = withContext(Dispatchers.IO) { LocationProvider.recentLastKnown(context, LOCALITY_FIX_MAX_AGE_MS) }
        locality = freshFix?.let { withContext(Dispatchers.IO) { db.nearestSuburb(it, LOCALITY_MAX_KM) } }
    }

    // Prayer times + nearest mosque — its own effect so toggling the setting (or changing
    // method) updates without needing a pack switch. Shown whenever opted in: from your
    // real fix when we have one, else the city centre, using the phone's timezone.
    // ponytail: a far pack viewed from another timezone with location OFF would use the
    // phone's tz — fine for the normal "I'm in this city" case; a tz-from-coordinates
    // lookup would fix the rare planning-ahead edge.
    LaunchedEffect(activePack, prefs.prayerEnabled, prefs.prayerMethod, prefs.prayerAsr) {
        if (!prefs.prayerEnabled) { prayerTimes = null; mosque = null; return@LaunchedEffect }
        val center = withContext(Dispatchers.IO) { db.cityCenter() }
        val here = fixInCity(withContext(Dispatchers.IO) { LocationProvider.lastKnown(context) }, center) ?: center
        if (here == null) { prayerTimes = null; mosque = null; return@LaunchedEffect }  // empty pack
        val cal = java.util.Calendar.getInstance()
        val tzHours = java.util.TimeZone.getDefault().getOffset(cal.timeInMillis) / 3_600_000.0  // incl. DST
        val method = runCatching { PrayerTimes.Method.valueOf(prefs.prayerMethod) }.getOrDefault(PrayerTimes.Method.MWL)
        val asr = runCatching { PrayerTimes.Asr.valueOf(prefs.prayerAsr) }.getOrDefault(PrayerTimes.Asr.STANDARD)
        prayerTimes = PrayerTimes.compute(
            cal.get(java.util.Calendar.YEAR), cal.get(java.util.Calendar.MONTH) + 1,
            cal.get(java.util.Calendar.DAY_OF_MONTH), here.lat, here.lng, tzHours, method, asr,
        )
        mosque = withContext(Dispatchers.IO) { db.nearestMosque(here).firstOrNull() }
    }

    // Keep the newest message in view.
    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) listState.animateScrollToItem(messages.size - 1)
    }

    // Recovery path when the microphone has been turned off for good.
    if (showMicSettingsDialog) {
        MicPermissionDialog(
            onOpenSettings = { openAppSettings(context); showMicSettingsDialog = false },
            onDismiss = { showMicSettingsDialog = false },
        )
    }

    Column(Modifier.fillMaxSize().imePadding()) {
        if (messages.isEmpty()) {
            EmptyState(
                onExample = ::ask,
                city = cityInfo,
                locality = locality,
                essentials = essentials,
                around = around,
                notable = notable,
                festivals = festivals,
                prayerTimes = prayerTimes,
                prayerMethod = prefs.prayerMethod,
                mosque = mosque,
                onOpenUrl = { openUrl(context, it) },
                onCallEmergency = { openDialer(context, it) },
                onCall = { openDialer(context, it) },
                onDirections = { openDirections(context, it) },
                onSave = { saveToTrips(it) },
                modifier = Modifier.weight(1f),
            )
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
private fun EmptyState(
    onExample: (String) -> Unit,
    city: CityInfo?,
    locality: String?,
    essentials: List<Place>,
    around: List<Place>,
    notable: List<Place>,
    festivals: List<CityEvent>,
    prayerTimes: PrayerTimes.Times?,
    prayerMethod: String,
    mosque: Place?,
    onOpenUrl: (String) -> Unit,
    onCallEmergency: (String) -> Unit,
    onCall: (String) -> Unit,
    onDirections: (Place) -> Unit,
    onSave: (Place) -> Unit,
    modifier: Modifier,
) {
    Column(
        // Scrollable: with the City Info + suggestions + police cards always present,
        // the welcome text and example chips can sit below the fold on smaller screens
        // — without this they'd be clipped and unreachable.
        modifier = modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Top,
    ) {
        // Where you are (your actual suburb when we have a fix), plus a one-tap dialer
        // for the local emergency number.
        city?.let {
            CityInfoCard(it, locality, onCallEmergency)
            Spacer(Modifier.height(24.dp))
        }
        // Prayer times + nearest mosque, when opted in and you're in this city. Right
        // under City Info because it's time-of-day critical for those who enabled it.
        prayerTimes?.let {
            PrayerCard(it, prayerMethod, mosque, onDirections, onCall, onOpenUrl)
            Spacer(Modifier.height(24.dp))
        }
        // Travel Mode only: the nearest food / shopping / outdoors, refreshed from the
        // service's own location fixes. Sits right under "where you are" because while
        // you're out walking it's the most useful thing on the screen. Absent whenever
        // Travel Mode is off or nothing grounded is in range.
        if (around.isNotEmpty()) {
            NearbyCard("Around you now", around, onDirections, onCall)
            Spacer(Modifier.height(24.dp))
        }
        // Grounded travel suggestions: notable places worth visiting near you. Hidden
        // when the pack has none nearby, so we never show an empty section.
        if (notable.isNotEmpty()) {
            NotableCard(notable, onDirections, onSave)
            Spacer(Modifier.height(24.dp))
        }
        // Daily needs near you: nearest police / hospital / fuel / parking. Hidden
        // entirely when the pack has none, so we never show an empty section.
        if (essentials.isNotEmpty()) {
            NearbyCard("Daily needs near you", essentials, onDirections, onCall)
            Spacer(Modifier.height(24.dp))
        }
        // The city's annual festivals. City-wide rather than distance-ranked, so it sits
        // below the "near you" cards. Absent when Wikipedia lists none for this city.
        if (festivals.isNotEmpty()) {
            FestivalsCard(festivals, onOpenUrl)
            Spacer(Modifier.height(24.dp))
        }
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

/**
 * "Prayer times today": the five daily prayers computed ON-DEVICE (offline, free), plus
 * the nearest mosque. Honest about being CALCULATED — the times mark when a prayer
 * begins, the method used is named, and Friday (set by each mosque) points to the
 * mosque's own website. Every mosque row is a real retrieved place, never invented.
 */
@Composable
private fun PrayerCard(
    times: PrayerTimes.Times,
    methodKey: String,
    mosque: Place?,
    onDirections: (Place) -> Unit,
    onCall: (String) -> Unit,
    onOpenUrl: (String) -> Unit,
) {
    val method = runCatching { PrayerTimes.Method.valueOf(methodKey) }.getOrNull()
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Text("Prayer times today", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(10.dp))
            PrayerRow("Fajr", times.fajr)
            PrayerRow("Sunrise", times.sunrise)
            PrayerRow("Dhuhr", times.dhuhr)
            PrayerRow("Asr", times.asr)
            PrayerRow("Maghrib", times.maghrib)
            PrayerRow("Isha", times.isha)
            Spacer(Modifier.height(8.dp))
            Text(
                "Calculated (${method?.label ?: methodKey}) — the start of each prayer; a mosque " +
                    "may hold congregation a little later.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            // Nearest real mosque — grounded. Its Friday time isn't calculable, so we
            // point the user to the mosque's own website/phone rather than invent one.
            mosque?.let { m ->
                Spacer(Modifier.height(14.dp))
                Text("Nearest mosque", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                Text(m.name, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                distanceLabel(m.distanceKm)?.let {
                    Text("$it away", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Text(
                    "Friday prayer is set by the mosque — tap its website or call to confirm.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = { onDirections(m) }) { Text("Directions") }
                    m.phone?.let { p -> TextButton(onClick = { onCall(p) }) { Text("Call") } }
                    m.website?.let { w -> TextButton(onClick = { onOpenUrl(w) }) { Text("Website") } }
                }
            }
        }
    }
}

/** One "Fajr    05:56" line in the prayer card. */
@Composable
private fun PrayerRow(name: String, hour: Double) {
    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
        Text(name, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.width(104.dp))
        Text(PrayerTimes.format(hour), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
    }
}

/**
 * "Annual festivals here": the city's real festivals, each a Wikipedia article stored
 * in the pack — so the list works offline and can never be invented.
 *
 * There are deliberately NO dates. No free source publishes a trustworthy date for a
 * recurring festival, so rather than guess "usually early November" the card says
 * outright that dates change each year. Tapping one opens its Wikipedia article, which
 * is also how we credit CC BY-SA.
 */
@Composable
private fun FestivalsCard(events: List<CityEvent>, onOpen: (String) -> Unit) {
    // Show a few by default; tap "…and N more" to reveal the rest. The rows are already
    // in memory, so expanding is free — and without it the app tells you it has N more
    // festivals with no way to ever see them.
    var expanded by remember { mutableStateOf(false) }
    val shown = if (expanded) events else events.take(FESTIVALS_SHOWN)
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Text("Annual festivals here", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(
                "Dates change each year — check before you go.",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(10.dp))
            shown.forEachIndexed { index, event ->
                if (index > 0) Spacer(Modifier.height(12.dp))
                val url = event.summaryUrl
                Column(
                    Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                        // Only clickable when we actually have an article to open.
                        .let { if (url != null) it.clickable { onOpen(url) } else it },
                ) {
                    Text(event.name, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                    event.summary?.let { text ->
                        Text(
                            if (text.length > 150) text.take(150).trimEnd() + "…" else text,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            if (!expanded && events.size > FESTIVALS_SHOWN) {
                Spacer(Modifier.height(10.dp))
                Text(
                    "…and ${events.size - FESTIVALS_SHOWN} more",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                        .clickable { expanded = true },
                )
            }
            Spacer(Modifier.height(8.dp))
            Text(
                "Tap a festival to read about it on Wikipedia (CC BY-SA 4.0).",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * "Worth visiting nearby": grounded travel suggestions — real notable places near you
 * (each a retrieved DB row, with its Wikipedia "why" where the pack has one). Never
 * invented; the summary text is real Wikipedia, credited in the footer.
 */
@Composable
private fun NotableCard(places: List<Place>, onDirections: (Place) -> Unit, onSave: (Place) -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Text("Worth visiting nearby", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(10.dp))
            places.forEachIndexed { index, place ->
                if (index > 0) Spacer(Modifier.height(14.dp))
                Text(place.name, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                val sub = place.subcategory?.replace('_', ' ')?.replaceFirstChar { it.uppercase() }
                val dist = distanceLabel(place.distanceKm)?.let { "$it away" }
                val meta = listOfNotNull(sub, dist).joinToString(" · ")
                if (meta.isNotBlank()) {
                    Text(meta, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                // The real Wikipedia "why", trimmed — only shown when the pack has one.
                place.summary?.let { s ->
                    Spacer(Modifier.height(2.dp))
                    val why = s.trim().let { if (it.length > 160) it.take(160).trimEnd() + "…" else it }
                    Text(why, style = MaterialTheme.typography.bodySmall)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = { onDirections(place) }) { Text("Directions") }
                    TextButton(onClick = { onSave(place) }) { Text("Save") }
                }
            }
        }
    }
}

/** Compact facts about the place you're in, shown on the empty screen. */
@Composable
private fun CityInfoCard(city: CityInfo, locality: String?, onCallEmergency: (String) -> Unit) {
    val facts = CountryFacts.forCountry(city.country)
    // Show the traveller's actual locality (reverse-geocoded suburb) as the heading
    // when we have it and it differs from the pack city; otherwise the pack city. Null
    // just means "no fix / offline" — we never invent a place name.
    val here = locality?.takeIf { it.isNotBlank() && !it.equals(city.shortName, ignoreCase = true) }
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Text(here ?: city.shortName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            // If we're showing a suburb, name the wider city underneath; else the country.
            val subtitle = if (here != null) listOfNotNull(city.shortName, city.country).joinToString(", ") else city.country
            subtitle?.let {
                Text(it, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
            }
            Spacer(Modifier.height(10.dp))
            // Population is the pack CITY's figure, so only show it under the city
            // heading — under a suburb it would misread as the suburb's population.
            if (here == null) city.population?.let { CityFactRow("Population", "%,d".format(it)) }
            facts?.let {
                CityFactRow("Currency", it.currency)
                CityFactRow("Emergency", it.emergency)
                Spacer(Modifier.height(8.dp))
                // Opens the dialer pre-filled — the user still taps call themselves.
                TextButton(onClick = { onCallEmergency(it.emergency) }, contentPadding = PaddingValues(0.dp)) {
                    Text("Call emergency (${it.emergency})")
                }
            }
        }
    }
}

/**
 * One card listing the nearest place of each kind — used for BOTH "Around you now"
 * (food/shopping/outdoors while Travel Mode is on) and "Daily needs near you"
 * (police/hospital/fuel/parking). Sharing it keeps the two looking identical, and
 * keeps each entry to three tight lines so a screen with several cards stays scannable.
 *
 * Every row is a real retrieved place, so we never invent one. Call only appears when
 * OSM actually lists a number — never a dead button.
 */
@Composable
private fun NearbyCard(
    title: String,
    places: List<Place>,
    onDirections: (Place) -> Unit,
    onCall: (String) -> Unit,
) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(10.dp))
            places.forEachIndexed { index, place ->
                if (index > 0) Spacer(Modifier.height(12.dp))
                Text(place.name, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                // Kind and distance share one line — same shape as the "Worth visiting"
                // rows, and it saves a line per entry over spelling them out separately.
                val meta = listOfNotNull(
                    categoryLabel(place.category),
                    distanceLabel(place.distanceKm)?.let { "$it away" },
                ).joinToString(" · ")
                Text(
                    meta,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = { onDirections(place) }) { Text("Directions") }
                    place.phone?.let { phone ->
                        TextButton(onClick = { onCall(phone) }) { Text("Call") }
                    }
                }
            }
        }
    }
}

/** One "Label   value" line inside the City Info card. */
@Composable
private fun CityFactRow(label: String, value: String) {
    androidx.compose.foundation.layout.Row(Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(104.dp),
        )
        Text(value, style = MaterialTheme.typography.bodyMedium)
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
            MicButton(voiceState = voiceState, onClick = onMicToggle)
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

/**
 * The mic button, which shows what voice is doing right now:
 *  - Idle:      a mic icon — tap to speak.
 *  - Preparing: a small spinner while the model loads (taps are ignored).
 *  - Listening: a red stop icon wrapped in a soft, expanding "sonar" pulse so
 *               it's obvious the mic is live; tap to stop.
 * The pulse is skipped when the phone's "remove animations" setting is on, and
 * the button carries a spoken label for screen readers.
 */
@Composable
private fun MicButton(voiceState: VoiceState, onClick: () -> Unit) {
    val context = LocalContext.current
    // Honour the system accessibility setting that turns animations off.
    val animationsOn = remember {
        Settings.Global.getFloat(
            context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f,
        ) != 0f
    }
    // liveRegion = Polite → a screen reader announces each Idle→Preparing→Listening
    // change without stealing focus, so a blind user knows when to start speaking.
    IconButton(
        onClick = onClick,
        enabled = voiceState != VoiceState.Preparing,
        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
    ) {
        Box(contentAlignment = Alignment.Center) {
            // Soft "sonar" halo behind the stop icon — only while listening. It's a
            // separate composable so the infinite animation exists ONLY on screen
            // (no wasted frames while idle).
            if (voiceState == VoiceState.Listening && animationsOn) PulseHalo()
            when (voiceState) {
                VoiceState.Preparing ->
                    // The spinner has no glyph, so give it a spoken label of its own.
                    CircularProgressIndicator(
                        Modifier
                            .size(20.dp)
                            .semantics { contentDescription = "Preparing voice" },
                        strokeWidth = 2.dp,
                    )
                VoiceState.Listening ->
                    Icon(WnStopIcon, "Stop listening", tint = MaterialTheme.colorScheme.error)
                VoiceState.Idle ->
                    Icon(WnMicIcon, "Speak your search", tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

/**
 * The expanding, fading red halo shown while actively listening. Kept in its own
 * composable so the infinite pulse animation is created only while it's on screen
 * and is disposed the moment listening stops — no frame-clock cost when idle.
 */
@Composable
private fun PulseHalo() {
    val pulse = rememberInfiniteTransition(label = "mic-pulse")
    val pulseScale by pulse.animateFloat(
        initialValue = 1f, targetValue = 1.7f,
        animationSpec = infiniteRepeatable(tween(1100, easing = FastOutSlowInEasing)),
        label = "scale",
    )
    val pulseAlpha by pulse.animateFloat(
        initialValue = 0.35f, targetValue = 0f,
        animationSpec = infiniteRepeatable(tween(1100, easing = FastOutSlowInEasing)),
        label = "alpha",
    )
    Box(
        Modifier
            .size(22.dp)
            .graphicsLayer {
                scaleX = pulseScale
                scaleY = pulseScale
                alpha = pulseAlpha
            }
            .background(MaterialTheme.colorScheme.error, CircleShape),
    )
}

// Small inline vector icons, so we don't pull in the heavy material-icons-extended
// dependency just for a mic and a stop square. These are the standard Material
// glyph paths; they tint with the current colour and stay crisp at any size.
private val WnMicIcon: ImageVector by lazy {
    ImageVector.Builder("Mic", 24.dp, 24.dp, 24f, 24f).apply {
        addPath(
            PathParser().parsePathString(
                "M12 14c1.66 0 3-1.34 3-3V5c0-1.66-1.34-3-3-3S9 3.34 9 5v6c0 1.66 1.34 3 3 3z" +
                    "M17 11c0 2.76-2.24 5-5 5s-5-2.24-5-5H5c0 3.53 2.61 6.43 6 6.92V21h2v-3.08c3.39-.49 6-3.39 6-6.92h-2z",
            ).toNodes(),
            fill = SolidColor(Color.Black),
        )
    }.build()
}

private val WnStopIcon: ImageVector by lazy {
    ImageVector.Builder("Stop", 24.dp, 24.dp, 24f, 24f).apply {
        addPath(
            PathParser().parsePathString("M6 6h12v12H6z").toNodes(),
            fill = SolidColor(Color.Black),
        )
    }.build()
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

/** Opens the phone's dialer pre-filled with a number (never auto-dials — the
 *  user taps call themselves). Used for the local emergency number. */
/**
 * Opens a festival's Wikipedia article in the browser. Needs a connection, so it fails
 * with a toast rather than silently — the offline part (name + summary) is already in
 * the pack and stays readable either way.
 */
private fun openUrl(context: Context, url: String) {
    // OSM website tags are sometimes schemeless ("www.example.org"); Uri.parse would
    // treat that as a relative link. Default to https so the browser opens it properly.
    val full = if (url.startsWith("http://") || url.startsWith("https://")) url else "https://$url"
    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(full))
    try {
        context.startActivity(intent)
    } catch (e: ActivityNotFoundException) {
        Toast.makeText(context, "No app available to open that link.", Toast.LENGTH_SHORT).show()
    }
}

private fun openDialer(context: Context, number: String) {
    try {
        context.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:$number")))
    } catch (e: ActivityNotFoundException) {
        Toast.makeText(context, "No dialer app found on this phone", Toast.LENGTH_SHORT).show()
    }
}

/** Asks the user to enable the mic in system Settings when it's been denied for good. */
@Composable
private fun MicPermissionDialog(onOpenSettings: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Microphone is off") },
        text = {
            Text(
                "Voice input needs microphone access, and it's currently turned off. " +
                    "Enable it in Settings to speak your searches — you can always type instead.",
            )
        },
        confirmButton = { TextButton(onClick = onOpenSettings) { Text("Open Settings") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Not now") } },
    )
}

/** Opens this app's system settings page, where the user can change permissions. */
private fun openAppSettings(context: Context) {
    val intent = Intent(
        Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
        Uri.fromParts("package", context.packageName, null),
    )
    try {
        context.startActivity(intent)
    } catch (e: ActivityNotFoundException) {
        Toast.makeText(context, "Couldn't open settings", Toast.LENGTH_SHORT).show()
    }
}

/** Walks up the Context wrappers to find the hosting Activity — needed to check
 *  whether we're still allowed to ask for a permission again. */
private fun Context.findActivity(): Activity? {
    var ctx: Context = this
    while (ctx is ContextWrapper) {
        if (ctx is Activity) return ctx
        ctx = ctx.baseContext
    }
    return null
}
