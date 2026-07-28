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
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
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
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
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
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.wandernear.StoryArgs
import com.wandernear.StoryRequest
import com.wandernear.core.model.ActiveArea
import com.wandernear.core.model.AWAY_FROM_CITY_KM
import com.wandernear.core.model.HERE_NAME
import com.wandernear.core.model.hereArea
import com.wandernear.core.model.haversineKm
import com.wandernear.core.model.CityEvent
import com.wandernear.core.model.CityInfo
import com.wandernear.core.model.Faith
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
import com.wandernear.core.retrieval.SearchSpec
import com.wandernear.ui.theme.categoryTint
import com.wandernear.data.CityDatabase
import com.wandernear.data.CityPackBuilder
import com.wandernear.data.LiveSource
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Celebration
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.NearMe
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Star
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

// How far you must move before the "around you" box is rebuilt (and the home refetched).
// Well under the box's own 3 km radius, so the places on screen always surround you, but
// far enough that a wobbling fix or a walk to the shops doesn't re-hit the network.
private const val HERE_REBUILD_KM = 1.0

/**
 * What the Explore tab remembers between visits.
 *
 * Switching tabs DESTROYS this screen's composition — the bottom nav swaps the whole
 * screen out — so every `remember` here is thrown away. Without this cache, coming back
 * from Preferences re-acquired a location fix and re-hit OpenStreetMap from scratch:
 * seconds of spinner for a home that hadn't changed, and your conversation gone.
 *
 * Deliberately process-scoped and in-memory: it's a cache, not storage. It dies with the
 * app, so a fresh launch always gets fresh data.
 */
private object HomeCache {
    // How long a fetched home stays usable. Long enough to cover flicking between tabs;
    // short enough that a genuinely new session re-fetches. Moving >HERE_REBUILD_KM
    // invalidates it anyway, because that builds a different area.
    private const val FRESH_MS = 10 * 60 * 1000L

    /** The area we last explored, and the fix it was built from — so returning to the
     *  tab doesn't have to work out where you are all over again. */
    var area: ActiveArea? = null
    var fix: LatLng? = null

    /** The conversation, so switching tabs mid-chat doesn't wipe it. */
    val messages = mutableStateListOf<ChatMessage>()

    private var places: List<Place>? = null
    private var placesArea: ActiveArea? = null
    // The fetch only covers the categories it asked for, which come from your interests
    // and faith. Change those and the cached list is genuinely missing rows, so it's part
    // of the key — otherwise ticking a new interest would silently show nothing new.
    private var placesCategories: List<String> = emptyList()
    private var atMs = 0L

    /** The cached fetch for exactly this area + categories, or null when it can't be reused. */
    fun placesFor(forArea: ActiveArea, categories: List<String>, nowMs: Long): List<Place>? =
        places?.takeIf {
            placesArea == forArea && placesCategories == categories && nowMs - atMs < FRESH_MS
        }

    fun put(forArea: ActiveArea, categories: List<String>, fetched: List<Place>, nowMs: Long) {
        placesArea = forArea
        placesCategories = categories
        places = fetched
        atMs = nowMs
    }
}

// Example prompts shown on the empty screen to help the user get started.
// Shown when you've set no preferences yet — a varied starting set.
private val DEFAULT_EXAMPLES = listOf(
    "Vegetarian food", "Temples", "Halal food", "Parks & nature", "Museums",
    "Shopping & markets", "Theatres & live music",
)

/**
 * The quick-start chips, tailored to your Preferences: your diets, interests and faith
 * become the suggestions, so the home reflects what you picked. Falls back to
 * [DEFAULT_EXAMPLES] when you've set nothing. Each chip is a query the parser understands.
 */
private fun exampleChips(prefs: UserPreferences): List<String> {
    val chips = LinkedHashSet<String>()
    // effectiveDiets, so a Muslim who hasn't picked a diet still gets "Halal food" —
    // and one they DID pick replaces it rather than sitting alongside.
    val diets = prefs.effectiveDiets
    diets.forEach { d -> dietChip(d)?.let { chips += it } }
    prefs.interests.forEach { i ->
        // A diet already implies food, so skip a plain "Food" chip when one is set.
        if (i == "food" && diets.isNotEmpty()) return@forEach
        interestChip(i)?.let { chips += it }
    }
    Faith.fromKey(prefs.faith)?.let { chips += faithChip(it) }
    return if (chips.isEmpty()) DEFAULT_EXAMPLES else chips.toList()
}

private fun dietChip(diet: String): String? = when (diet) {
    "halal" -> "Halal food"
    "vegetarian" -> "Vegetarian food"
    "vegan" -> "Vegan food"
    "kosher" -> "Kosher food"
    "gluten_free" -> "Gluten-free food"
    else -> null
}

private fun interestChip(interest: String): String? = when (interest) {
    "food" -> "Food"
    "worship" -> "Places of worship"
    "attraction" -> "Museums"
    "outdoor" -> "Parks & nature"
    "shopping" -> "Shopping & markets"
    "culture" -> "Theatres & live music"
    else -> null
}

private fun faithChip(faith: Faith): String = when (faith) {
    Faith.MUSLIM -> "Mosques"
    Faith.CHRISTIAN -> "Churches"
    Faith.JEWISH -> "Synagogues"
    Faith.HINDU -> "Hindu temples"
    Faith.BUDDHIST -> "Buddhist temples"
    Faith.SIKH -> "Gurdwaras"
}

/** A warm, time-of-day greeting for the home hero — a buddy saying hi. */
private fun greeting(): String {
    val hour = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)
    return when {
        hour < 12 -> "Good morning"
        hour < 17 -> "Good afternoon"
        else -> "Good evening"
    }
}

/**
 * The categories the LIVE home needs in ONE Overpass fetch: daily-needs + attractions +
 * your chosen interests + (when a faith is set) worship. Every card on the home derives
 * from a single fetch of these, so the whole home costs one network call, not one per card.
 * ponytail: for a very large area this fetches a lot; fine for the town/suburb you're in.
 */
private fun homeCategories(prefs: UserPreferences): List<String> =
    (ESSENTIAL_CATEGORIES + "attraction" + prefs.interests +
        (if (prefs.faith.isNotBlank()) listOf("worship") else emptyList())).distinct()

/** Today's five prayer times, computed ON-DEVICE from [here] + the phone's timezone. */
private fun computePrayerTimes(here: LatLng, prefs: UserPreferences): PrayerTimes.Times {
    val cal = java.util.Calendar.getInstance()
    val tzHours = java.util.TimeZone.getDefault().getOffset(cal.timeInMillis) / 3_600_000.0  // incl. DST
    val method = runCatching { PrayerTimes.Method.valueOf(prefs.prayerMethod) }.getOrDefault(PrayerTimes.Method.MWL)
    val asr = runCatching { PrayerTimes.Asr.valueOf(prefs.prayerAsr) }.getOrDefault(PrayerTimes.Asr.STANDARD)
    return PrayerTimes.compute(
        cal.get(java.util.Calendar.YEAR), cal.get(java.util.Calendar.MONTH) + 1,
        cal.get(java.util.Calendar.DAY_OF_MONTH), here.lat, here.lng, tzHours, method, asr,
    )
}

// Radius for the "worth visiting near you" suggestions — wide enough to surface a
// few notable spots in a city, ranked nearest-first.
private const val NEARBY_RADIUS_KM = 15.0

// "Daily needs" categories shown in the essentials card, in display order.
private val ESSENTIAL_CATEGORIES = listOf("safety", "health", "fuel", "parking")

// How many rows a glanceable card shows before it gets heavy. The rest stay one tap
// away — festivals via "…and N more", places by asking. Kept small on purpose so the
// home reads as a light summary, not a wall.
private const val FESTIVALS_SHOWN = 3
private const val NOTABLE_SHOWN = 3

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

/**
 * The downloaded pack to read when we're NOT live: the one matching the area you're exploring
 * (by osm id), or — when no area is set — the active pack (bundled Melbourne, or a pack chosen
 * in Cities). Null ⇒ nothing downloaded for this view (you're offline and haven't saved this
 * area), so the caller shows an offline state instead of a different city's data.
 */
private suspend fun resolveOfflinePack(context: Context, area: ActiveArea?, activePack: String): String? =
    withContext(Dispatchers.IO) {
        when {
            // An area you picked BY NAME (to download before a trip): only the pack
            // downloaded for that exact area will do, matched by its OSM id.
            area != null && !area.isAroundYou -> CityPackBuilder.packForOsmId(context, area.osmId)
            // The box around YOU corresponds to no OSM feature, so match by GEOGRAPHY
            // instead: the downloaded city you're actually standing in.
            area != null -> packContaining(context, area.center)
            else -> if (CityDatabase.hasAnyCity(context)) activePack else null
        }
    }

/**
 * The downloaded pack you are actually INSIDE — the installed city whose centre is
 * nearest your position and within [AWAY_FROM_CITY_KM] — or null if you're not inside
 * any of them. Deliberately strict: showing another city's cafés because it's the only
 * thing downloaded would be worse than an honest "you're offline here".
 *
 * Opens each pack to read its centre, so call it off the main thread.
 */
private fun packContaining(context: Context, fix: LatLng): String? {
    val packs = buildList {
        if (CityDatabase.isBundledInstalled(context)) add(CityDatabase.BUNDLED_PACK)
        CityPackBuilder.packsDir(context).listFiles { f -> f.name.endsWith(".db") }
            ?.forEach { add("packs/" + it.name) }
    }
    return packs
        .mapNotNull { name ->
            val center = runCatching { CityDatabase(context, name).cityCenter() }.getOrNull()
            center?.let { name to haversineKm(fix, it) }
        }
        .filter { it.second <= AWAY_FROM_CITY_KM }
        .minByOrNull { it.second }
        ?.first
}

/** Turn retrieved [places] into a chat reply: the AI reword when enabled AND grounded, else the
 *  template. Empty in → the honest refusal, and the AI is never called. Shared by the live and
 *  the offline-pack paths so both answer identically. */
private suspend fun buildRecommendation(
    context: Context, question: String, rawPlaces: List<Place>, spec: SearchSpec,
    nearYou: Boolean, cityName: String?, aiEnabled: Boolean,
): ChatMessage {
    // OSM frequently holds one real place twice — a node AND a way (or a building) — which
    // showed up as the same name listed three times in a row. The list is ranked nearest
    // first, so keeping the first of each name keeps the closest copy. Same rule the home
    // cards already use; doing it here covers the live and pack paths at once.
    val places = rawPlaces.distinctBy { it.name }
    if (places.isEmpty()) return ChatMessage(Role.Assistant, Recommender.NO_RESULTS)
    val cards = places.map { RecCard(it, Recommender.reason(it, spec)) }
    val intro = if (aiEnabled) {
        val ready = LlmEngine.ensureReady(context)
        val aiText = if (ready) {
            LlmEngine.generate(Recommender.AI_SYSTEM, Recommender.aiPrompt(question, places, nearYou))
        } else {
            null
        }
        // Use the AI reply only if it's non-empty AND names only retrieved places; else the
        // template. This is the enforced never-hallucinate guardrail.
        aiText?.takeIf { it.isNotBlank() && GroundingCheck.isGrounded(it, places, cityName) }
            ?: Recommender.reply(spec, places, nearYou = nearYou)
    } else {
        Recommender.reply(spec, places, nearYou = nearYou)
    }
    return ChatMessage(Role.Assistant, intro, cards)
}

/** The main tab: ask for a place, get real recommendations built from the data.
 *  [onAddCity] jumps to Preferences → Cities (used by the "no city yet" welcome). */
@Composable
fun ChatScreen(prefsRepo: PreferencesRepository, onAddCity: () -> Unit = {}) {
    val context = LocalContext.current
    val prefs by prefsRepo.preferences.collectAsState(initial = UserPreferences())
    // The active city pack — re-open the data whenever it changes (a download or reset).
    val activePack by prefsRepo.activePack.collectAsState(initial = CityDatabase.BUNDLED_PACK)
    // WHERE ARE WE EXPLORING? Always the box around YOU (see the effect below) — you never
    // type a city, and there is deliberately NO fallback to some city you picked once: if we
    // can't work out where you are we say so, because showing another city's cafés under
    // "around you" would be a lie dressed up as a feature.
    // Seeded from the cache so returning from another tab doesn't start from nothing.
    var aroundYou by remember { mutableStateOf(HomeCache.area) }
    // The fix the current `aroundYou` box was built from, so we only rebuild it once you've
    // actually MOVED — otherwise every resume would refetch the whole home.
    var aroundYouFix by remember { mutableStateOf(HomeCache.fix) }
    val activeArea = aroundYou
    // Bumped whenever it's worth re-checking where you are: app resumed, permission granted.
    var locationEpoch by remember { mutableStateOf(0) }
    // Do we have location permission? Re-read on every epoch so granting it updates the UI.
    val hasLocation = remember(locationEpoch) { LocationProvider.hasPermission(context) }
    // True once we've looked for a fix and come back empty — lets the welcome say "turn on
    // location" instead of spinning forever.
    var locationFailed by remember { mutableStateOf(false) }
    // Have we actually shown the location request yet? The system can't tell us: its
    // "should show rationale" flag is false both BEFORE the first ask and after a permanent
    // denial, so we have to remember. Saveable so a rotation doesn't lose the distinction.
    var askedForLocation by rememberSaveable { mutableStateOf(false) }
    val journalDao = remember { JournalDatabase.get(context).journalDao() }
    // Lives in the cache, not in `remember`, so leaving the tab doesn't erase the chat.
    val messages = HomeCache.messages
    var input by remember { mutableStateOf("") }
    var askedLocation by remember { mutableStateOf(false) }
    var pendingQuestion by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()
    // Bumped to force the home's load effects to re-run when nothing they key on has changed
    // — e.g. "Try again" after the map service was busy, where the area is already correct.
    var cityEpoch by remember { mutableStateOf(0) }
    // Is there a usable city at all? False on a fresh install and after you delete your last
    // city — the screen then shows an "add a city" welcome instead of querying (no pack to open).
    var hasCity by remember(activePack, cityEpoch) { mutableStateOf(CityDatabase.hasAnyCity(context)) }
    // True when you're OFFLINE, exploring an area you haven't downloaded — the home then shows
    // an "offline, connect or download" state for THAT area, not a different downloaded city.
    // Written ONLY by the load effect below (no remember key), so a re-emit of activeArea can't
    // reset it out from under the value the effect just set.
    var offlineArea by remember { mutableStateOf(false) }
    // True when we WERE online but the live fetch still came back empty-handed — i.e. it's
    // OpenStreetMap that's unreachable or busy, not you. Worth telling apart: "you're
    // offline" is a lie when the phone is plainly connected, and it sends the user off to
    // fix a connection that isn't broken.
    var liveFetchFailed by remember { mutableStateOf(false) }
    // The active city's facts (name, country, population) for the City Info card.
    var cityInfo by remember(activePack, activeArea) { mutableStateOf<CityInfo?>(null) }
    // Nearest essentials (police/hospital/fuel/parking) for the daily-needs card.
    var essentials by remember(activePack, activeArea) { mutableStateOf<List<Place>>(emptyList()) }
    // The active city's centre — the "near me" fallback when we have no GPS fix.
    var cityCenter by remember(activePack, activeArea) { mutableStateOf<LatLng?>(null) }
    // The traveller's actual locality (on-device nearest suburb) for the header,
    // and grounded "worth visiting near you" suggestions — both loaded below.
    var locality by remember(activePack, activeArea) { mutableStateOf<String?>(null) }
    var notable by remember(activePack, activeArea) { mutableStateOf<List<Place>>(emptyList()) }
    // The ONE broad live fetch the home derives from (daily needs, worth-visiting, for-you,
    // worship) when online with an area set. Null = not live → the pack path fills the cards.
    var liveHome by remember(activePack, activeArea, cityEpoch) { mutableStateOf<List<Place>?>(null) }
    // "For you": nearby places matching the interests you picked in Preferences. Empty
    // (and the card hidden) when you've selected no interests.
    var forYou by remember(activePack, activeArea) { mutableStateOf<List<Place>>(emptyList()) }
    // The nearest place of worship for the user's faith (all faiths), plus — for Islam
    // only — today's calculated prayer times. Shown only when a faith is picked.
    var prayerTimes by remember(activePack, activeArea) { mutableStateOf<PrayerTimes.Times?>(null) }
    var worship by remember(activePack, activeArea) { mutableStateOf<Place?>(null) }
    // The city's annual festivals (no dates — see FestivalsCard). Pack-wide, not
    // location-based, so it doesn't depend on having a fix.
    var festivals by remember(activePack, activeArea) { mutableStateOf<List<CityEvent>>(emptyList()) }
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

    // Runs the actual search. LIVE (online + an area set) fetches fresh from OSM; else
    // we fall back to a downloaded pack. "Near you" ranking uses the real fix if we have
    // one, else the area/city centre. Parse + fetch/search happen off the main thread.
    fun runSearch(question: String) {
        val area = activeArea
        val online = LiveSource.isOnline(context)
        val useLive = online && area != null
        // What to CALL where we are in a sentence: the locality the live fetch worked out,
        // else the area you picked by name. Null ⇒ we don't know yet, so we don't pretend to.
        val areaLabel = locality ?: area?.takeIf { !it.isAroundYou }?.shortName
        // Nothing to search: no live area+signal AND no downloaded pack.
        if (!useLive && !hasCity) {
            messages += ChatMessage(
                Role.Assistant,
                when {
                    area != null && areaLabel != null ->
                        "You're offline — reconnect and I'll explore $areaLabel live, or download it in Preferences → Cities so it works with no signal."
                    area != null ->
                        "You're offline — reconnect and I'll find real places around you, or download a city in Preferences → Cities for no-signal travel."
                    else ->
                        "I need to know where you are before I can find anything — turn on location and I'll explore what's around you."
                },
            )
            return
        }
        val aiEnabled = prefs.useAi && ModelManager.isDownloaded(context)
        // Show a temporary loading bubble for anything with a wait — the AI (slow first
        // load) OR a live fetch (a network round-trip) — and replace it when ready.
        val placeholderIndex = if (aiEnabled || useLive) {
            val text = when {
                aiEnabled && !LlmEngine.isLoaded() -> "Warming up the on-device AI (first time, about a minute)…"
                aiEnabled -> "Thinking…"
                else -> "Searching ${areaLabel ?: "around you"}…"   // live, no AI
            }
            messages += ChatMessage(Role.Assistant, text, loading = true)
            messages.lastIndex
        } else {
            null
        }

        scope.launch {
            val answer = withContext(Dispatchers.IO) {
                val spec = QueryParser.parse(question, prefs)
                // Try LIVE first when we think we're online. A null result = the fetch actually
                // failed (dead VPN, dropped WiFi) → fall through to the offline pack.
                if (useLive) {
                    val center = area.center
                    val here = fixInCity(LocationProvider.lastKnown(context), center)
                    val places = LiveSource.search(area, spec, here ?: center)
                    if (places != null) {
                        // Add grounded Wikipedia "stories" to the handful we'll show (only the
                        // wiki-linked ones fetch; the rest pass straight through).
                        val enriched = LiveSource.enrichStories(places)
                        return@withContext buildRecommendation(
                            context, question, enriched, spec, here != null, areaLabel, aiEnabled,
                        )
                    }
                    // else: live failed → fall through to the offline pack below.
                }
                // Offline (or a failed-live fallback): read the pack backing this area (or the
                // active pack) — never a different downloaded city.
                val pack = resolveOfflinePack(context, area, activePack)
                    ?: return@withContext ChatMessage(
                        Role.Assistant,
                        when {
                            // We got here from a FAILED live fetch while online: it's the free
                            // map service that's busy, not the user's connection.
                            useLive ->
                                "OpenStreetMap didn't answer just then — its free servers get busy. Ask me again in a moment, or download this area in Preferences → Cities to stop depending on them."
                            areaLabel != null ->
                                "You're offline — reconnect and I'll explore $areaLabel live, or download it in Preferences → Cities so it works with no signal."
                            area != null ->
                                "You're offline — reconnect and I'll find real places around you, or download a city in Preferences → Cities for no-signal travel."
                            else -> Recommender.NO_RESULTS
                        },
                    )
                val pdb = CityDatabase(context, pack)
                val center = pdb.cityCenter()
                val here = fixInCity(LocationProvider.lastKnown(context), center)
                // No fix AND no centre means the pack holds no places at all — refuse
                // honestly rather than ranking from some other city's coordinates.
                val origin = here ?: center
                    ?: return@withContext ChatMessage(Role.Assistant, Recommender.NO_RESULTS)
                val places = pdb.search(spec, origin)
                // We only got here on a FAILED live fetch, so an empty result doesn't mean
                // "this doesn't exist" — it means we quietly searched your smaller offline
                // copy instead. Say that, rather than let the app look like it's missing
                // places that are really out there.
                if (places.isEmpty() && useLive) {
                    val packName = pdb.cityInfo()?.name
                    return@withContext ChatMessage(
                        Role.Assistant,
                        "I couldn't reach OpenStreetMap just then, so I searched your downloaded " +
                            (packName?.let { "$it data" } ?: "offline data") +
                            " instead and found nothing matching. Ask me again in a moment and I'll search live.",
                    )
                }
                buildRecommendation(context, question, places, spec, here != null, pdb.cityInfo()?.name, aiEnabled)
            }
            if (placeholderIndex != null) messages[placeholderIndex] = answer else messages += answer
        }
    }

    // Asks for location once; whatever the user chooses, we run the pending search. The
    // epoch bump also lets the home work out where you are the moment you allow it.
    val locationLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { _ ->
        locationEpoch++
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

    // WHERE AM I? The heart of the app: take a fix and make the box around it the area we
    // explore. Nothing is typed and no service is asked where you are — the box is pure
    // arithmetic on your own coordinates, and what it's CALLED is worked out below from the
    // OSM data that comes back.
    LaunchedEffect(locationEpoch) {
        val fix = withContext(Dispatchers.IO) { LocationProvider.currentFix(context) }
        if (fix == null) {
            locationFailed = true
            return@LaunchedEffect
        }
        locationFailed = false
        // Only rebuild the area once you've genuinely moved — a new box means a new fetch,
        // and a fix wobbling by a few metres must not refetch the whole home.
        val previous = aroundYouFix
        if (previous != null && aroundYou != null && haversineKm(previous, fix) < HERE_REBUILD_KM) return@LaunchedEffect
        aroundYouFix = fix
        aroundYou = hereArea(fix, country = withContext(Dispatchers.IO) { LocationProvider.countryName(context) })
        // Remember it for the next visit to this tab, so we don't re-locate from scratch.
        HomeCache.fix = fix
        HomeCache.area = aroundYou
    }

    // Come back to the app after travelling and the home re-checks where you are. The
    // moved-check above means standing still costs nothing.
    // ponytail: re-detects on resume, not continuously — Travel Mode is the feature that
    // already watches you while you move; wire this to its fixes if that's ever wanted.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) locationEpoch++
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // Asking for location from the welcome screen. Granting it immediately re-runs the
    // effect above, so the home fills in without any further tap.
    val homeLocationLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { locationEpoch++ }

    // Load the home's facts + centre + cards. LIVE (online + an area set) fetches them
    // fresh from OSM in ONE call and derives every card; otherwise the downloaded-pack
    // path fills them. Reloads when the area/pack changes, or the interests/faith that
    // decide what the single live fetch needs to include.
    LaunchedEffect(activePack, cityEpoch, activeArea, prefs.interests, prefs.faith) {
        // No idea where you are yet ⇒ nothing to show. We deliberately do NOT open whatever
        // pack happens to be installed: "around you" has to mean around YOU.
        val area = activeArea ?: run {
            hasCity = false; offlineArea = false; liveFetchFailed = false
            liveHome = null; cityInfo = null; cityCenter = null; locality = null
            essentials = emptyList(); notable = emptyList(); festivals = emptyList()
            return@LaunchedEffect
        }
        // Already fetched this exact area a moment ago (you just came back from another
        // tab)? Reuse it. Re-fetching an unchanged home is the difference between the tab
        // appearing instantly and several seconds of spinner.
        val categories = homeCategories(prefs)
        val cached = HomeCache.placesFor(area, categories, System.currentTimeMillis())
        val live = cached != null || withContext(Dispatchers.IO) { LiveSource.isOnline(context) }
        if (live) {
            val fix = withContext(Dispatchers.IO) { LocationProvider.lastKnown(context) }
            val origin = fixInCity(fix, area.center) ?: area.center
            // ONE live fetch feeds every card (daily needs, worth-visiting, for-you, worship).
            // null ⇒ the fetch FAILED despite isOnline (e.g. a "connected" VPN with a dead
            // tunnel) → fall through to the offline path rather than showing an empty home.
            val home = cached
                ?: withContext(Dispatchers.IO) { LiveSource.places(area, categories, origin) }
                    ?.also { HomeCache.put(area, categories, it, System.currentTimeMillis()) }
            if (home != null) {
                hasCity = true
                offlineArea = false
                liveFetchFailed = false
                cityCenter = area.center
                festivals = emptyList()   // festivals need a Wikipedia call — deferred for live
                // WHAT IS THIS PLACE CALLED? Worked out ON-DEVICE from the data we just
                // fetched: `home` is ranked nearest-first, so the first place carrying a
                // locality is the one you're standing next to. No reverse-geocode and no
                // name lookup — and when nothing carries one we say nothing rather than
                // invent a name for where you are.
                val named = if (area.isAroundYou) home.firstNotNullOfOrNull { it.suburb } else null
                locality = named
                cityInfo = if (area.isAroundYou) CityInfo(named ?: HERE_NAME, area.country, null)
                else CityInfo(area.displayName, area.country, area.population)
                liveHome = home
                // `home` is already ranked nearest-first, so firstOrNull of a category = nearest.
                essentials = ESSENTIAL_CATEGORIES.mapNotNull { c -> home.firstOrNull { it.category == c } }
                notable = home.filter { it.category == "attraction" }.take(NOTABLE_SHOWN)
                // Enrich the worth-visiting attractions with grounded Wikipedia stories — set in
                // place, so the card shows the names immediately and the "why" fills in a moment later.
                notable = withContext(Dispatchers.IO) { LiveSource.enrichStories(notable) }
                return@LaunchedEffect
            }
            // else: live fetch failed → fall through to the offline path below. Remember that
            // we WERE online, so the empty state can say "OpenStreetMap didn't answer" rather
            // than sending the user to fix a connection that's working fine.
            liveFetchFailed = true
        }
        // --- Not live (no connection, or the fetch failed): read a DOWNLOADED pack ---
        liveHome = null
        // The downloaded city you're actually standing in, if you have one.
        val offlinePack = resolveOfflinePack(context, area, activePack)
        if (offlinePack == null) {
            // You're not inside any downloaded city, so there is honestly nothing to show —
            // never another city's data dressed up as "around you".
            hasCity = false
            offlineArea = true
            cityCenter = null; cityInfo = null; festivals = emptyList()
            essentials = emptyList(); notable = emptyList(); locality = null
            return@LaunchedEffect
        }
        hasCity = true
        offlineArea = false
        liveFetchFailed = false
        val pdb = CityDatabase(context, offlinePack)
        val center = withContext(Dispatchers.IO) { pdb.cityCenter() }
        cityCenter = center
        cityInfo = withContext(Dispatchers.IO) { pdb.cityInfo() }
        festivals = withContext(Dispatchers.IO) { pdb.festivals() }
        // Read location off the main thread (binder IPC). A stale fix is fine for
        // ranking; the "you are here" label below uses a FRESH fix only.
        val fix = withContext(Dispatchers.IO) { LocationProvider.lastKnown(context) }
        // An empty pack has no centre — show nothing rather than rank from a made-up origin.
        val origin = fixInCity(fix, center) ?: center ?: run {
            essentials = emptyList(); notable = emptyList(); locality = null
            return@LaunchedEffect
        }
        essentials = withContext(Dispatchers.IO) { pdb.nearestEssentials(origin, ESSENTIAL_CATEGORIES) }
        notable = withContext(Dispatchers.IO) { pdb.nearbyNotable(origin, NEARBY_RADIUS_KM) }
        // Which suburb am I in? Derived ON-DEVICE from the pack (no GPS ever leaves the
        // phone), from a FRESH fix within the pack's area — else null (show the city).
        val freshFix = withContext(Dispatchers.IO) { LocationProvider.recentLastKnown(context, LOCALITY_FIX_MAX_AGE_MS) }
        locality = freshFix?.let { withContext(Dispatchers.IO) { pdb.nearestSuburb(it, LOCALITY_MAX_KM) } }
    }

    // Nearest place of worship for the chosen faith, plus Islam's calculated prayer
    // times — its own effect so changing faith/method updates without a pack switch.
    // Uses your real fix when we have one, else the city centre + phone timezone.
    // ponytail: a far pack viewed from another timezone with location OFF would use the
    // phone's tz — fine for the normal "I'm in this city" case; a tz-from-coordinates
    // lookup would fix the rare planning-ahead edge.
    LaunchedEffect(activePack, cityEpoch, activeArea, prefs.faith, prefs.prayerMethod, prefs.prayerAsr, liveHome) {
        val faith = Faith.fromKey(prefs.faith)
        if (faith == null) { prayerTimes = null; worship = null; return@LaunchedEffect }
        val area = activeArea
        // Live succeeded ⇒ liveHome is set; derive worship from it (it includes worship when a
        // faith is set). Prayer times are computed ON-DEVICE from the area centre + phone tz.
        val home = liveHome
        if (home != null && area != null) {
            val here = fixInCity(withContext(Dispatchers.IO) { LocationProvider.lastKnown(context) }, area.center) ?: area.center
            worship = home.firstOrNull { it.category == "worship" && it.religion == faith.key }
            prayerTimes = if (faith == Faith.MUSLIM) computePrayerTimes(here, prefs) else null
            return@LaunchedEffect
        }
        // Pack path — the pack backing this area, or the active pack.
        val offlinePack = resolveOfflinePack(context, area, activePack)
        if (offlinePack == null) { prayerTimes = null; worship = null; return@LaunchedEffect }
        val pdb = CityDatabase(context, offlinePack)
        val center = withContext(Dispatchers.IO) { pdb.cityCenter() }
        val here = fixInCity(withContext(Dispatchers.IO) { LocationProvider.lastKnown(context) }, center) ?: center
        if (here == null) { prayerTimes = null; worship = null; return@LaunchedEffect }  // empty pack
        worship = withContext(Dispatchers.IO) { pdb.nearestWorship(here, faith.key).firstOrNull() }
        // Calculated daily times exist only for Islam; other faiths show the place only.
        prayerTimes = if (faith == Faith.MUSLIM) computePrayerTimes(here, prefs) else null
    }

    // "For you" — nearby places in your selected interests. Its own effect so it updates
    // the moment you change preferences, without needing a pack switch.
    LaunchedEffect(activePack, cityEpoch, activeArea, prefs.interests, prefs.diets, liveHome) {
        if (prefs.interests.isEmpty()) { forYou = emptyList(); return@LaunchedEffect }
        val area = activeArea
        // Live succeeded ⇒ derive from the ONE home fetch — no extra network call.
        val home = liveHome
        if (home != null) {
            // Diet filters food only.
            // effectiveDiets: a saved diet, else the one your faith implies (Muslim ⇒ halal).
            val diets = prefs.effectiveDiets
            val picks = home
                .filter { it.category in prefs.interests }
                .filter { it.category != "food" || diets.isEmpty() || diets.any { d -> d in it.diets } }
                .take(5)
            forYou = picks
            // Enrich with grounded Wikipedia stories (only the wiki-linked ones fetch), in place —
            // the card shows names immediately and the "why" fills in a moment later.
            forYou = withContext(Dispatchers.IO) { LiveSource.enrichStories(picks) }
            return@LaunchedEffect
        }
        // Pack path — the pack backing this area, or the active pack.
        val offlinePack = resolveOfflinePack(context, area, activePack)
        if (offlinePack == null) { forYou = emptyList(); return@LaunchedEffect }
        val pdb = CityDatabase(context, offlinePack)
        val center = withContext(Dispatchers.IO) { pdb.cityCenter() }
        val origin = fixInCity(withContext(Dispatchers.IO) { LocationProvider.lastKnown(context) }, center)
            ?: center ?: run { forYou = emptyList(); return@LaunchedEffect }
        forYou = withContext(Dispatchers.IO) { pdb.forYou(origin, prefs.interests.toList(), prefs.effectiveDiets) }
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
        if (messages.isEmpty() && !hasCity) {
            // Nothing to show yet — almost always because we don't know where you are.
            // `shouldShowRequestPermissionRationale` is false BOTH before the first ask and
            // after a permanent denial, so `askedForLocation` is what tells the two apart.
            val activity = context.findActivity()
            val canAskAgain = activity == null || ActivityCompat.shouldShowRequestPermissionRationale(
                activity, Manifest.permission.ACCESS_FINE_LOCATION,
            )
            NoCityState(
                state = when {
                    // "Connected but the map service didn't answer" first — telling someone
                    // with working WiFi that they're offline just sends them chasing nothing.
                    offlineArea && liveFetchFailed -> Welcome.ServerBusy
                    offlineArea -> Welcome.Offline
                    !hasLocation && askedForLocation && !canAskAgain -> Welcome.Blocked
                    !hasLocation -> Welcome.NeedsLocation
                    locationFailed -> Welcome.NoFix
                    else -> Welcome.Locating
                },
                onEnableLocation = {
                    askedForLocation = true
                    homeLocationLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
                },
                // Retry has to nudge BOTH: the fix (for "can't find you") and the home's own
                // load (for "the map service didn't answer", where the area is already known).
                onRetry = { locationFailed = false; locationEpoch++; cityEpoch++ },
                onOpenSettings = { openAppSettings(context) },
                onAddCity = onAddCity,
                modifier = Modifier.weight(1f),
            )
        } else if (messages.isEmpty()) {
            EmptyState(
                onExample = ::ask,
                city = cityInfo,
                locality = locality,
                essentials = essentials,
                around = around,
                notable = notable,
                forYou = forYou,
                examples = remember(prefs) { exampleChips(prefs) },
                festivals = festivals,
                faith = Faith.fromKey(prefs.faith),
                prayerTimes = prayerTimes,
                prayerMethod = prefs.prayerMethod,
                worship = worship,
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

/**
 * Why the home has nothing to show yet. The app is location-first, so all but one of
 * these are about knowing where you are — there is no "pick a city" gate any more.
 */
private enum class Welcome {
    /** We have permission and are waiting for a fix. */
    Locating,

    /** We've never been allowed to look, or the user can still be asked again. */
    NeedsLocation,

    /** Permission denied for good — the system won't show the dialog again. */
    Blocked,

    /** Allowed, but no fix arrived (location services off, or indoors with no signal). */
    NoFix,

    /** No connection AND nothing downloaded that covers where you are. */
    Offline,

    /** Connected, but OpenStreetMap didn't answer — its free servers are often busy. */
    ServerBusy,
}

/**
 * The first thing you see before the home can fill in. One primary action per state, and
 * downloading a city is always the quiet secondary — it's for travelling without signal,
 * never a prerequisite for using the app.
 */
@Composable
private fun NoCityState(
    state: Welcome,
    onEnableLocation: () -> Unit,
    onRetry: () -> Unit,
    onOpenSettings: () -> Unit,
    onAddCity: () -> Unit,
    modifier: Modifier,
) {
    Column(
        // Scrollable + centred so nothing clips at large system font sizes on a small screen.
        modifier = modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        // Warm hero mark. While we're locating it holds a spinner instead of an icon, so
        // the wait reads as progress rather than a screen that failed to load.
        Box(
            Modifier.size(96.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center,
        ) {
            if (state == Welcome.Locating) {
                CircularProgressIndicator(
                    modifier = Modifier.size(38.dp),
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    strokeWidth = 3.dp,
                )
            } else {
                Icon(
                    if (state == Welcome.Offline || state == Welcome.ServerBusy) Icons.Filled.Public
                    else Icons.Filled.MyLocation,
                    null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(46.dp),
                )
            }
        }
        Spacer(Modifier.height(22.dp))
        Text(
            when (state) {
                Welcome.Locating -> "Finding what's around you…"
                Welcome.NeedsLocation -> "See what's around you"
                Welcome.Blocked -> "Location is turned off"
                Welcome.NoFix -> "Can't find you yet"
                Welcome.Offline -> "You're offline"
                Welcome.ServerBusy -> "OpenStreetMap didn't answer"
            },
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(10.dp))
        Text(
            when (state) {
                Welcome.Locating ->
                    "Working out where you are, then finding real places nearby."
                Welcome.NeedsLocation ->
                    "WanderNear is a local guide for wherever you happen to be. Allow location and " +
                        "it finds real places around you — food, worship, landmarks — matched to your taste."
                Welcome.Blocked ->
                    "WanderNear needs location to know where \"around you\" is. You can turn it back " +
                        "on for this app in Settings."
                Welcome.NoFix ->
                    "Location is allowed, but no position has come through — check location is switched " +
                        "on in your phone's settings, or step somewhere with a clearer signal."
                Welcome.Offline ->
                    "Reconnect and I'll find real places around you. Travelling without signal? " +
                        "Download a city first and it all works offline."
                Welcome.ServerBusy ->
                    "Your connection is fine — the free map service is just busy right now. Give it " +
                        "a moment and try again. Downloading a city avoids the wait for good."
            },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        // ONE primary action per state. "Locating" gets none — there's nothing to do but wait.
        if (state != Welcome.Locating) {
            Spacer(Modifier.height(28.dp))
            Button(
                onClick = when (state) {
                    Welcome.NeedsLocation -> onEnableLocation
                    Welcome.Blocked -> onOpenSettings
                    else -> onRetry
                },
                // 52dp: comfortably above the 48dp minimum touch target.
                modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
            ) {
                Icon(
                    when (state) {
                        Welcome.NeedsLocation -> Icons.Filled.MyLocation
                        Welcome.Blocked -> Icons.Filled.Settings
                        else -> Icons.Filled.Refresh
                    },
                    null,
                    modifier = Modifier.size(20.dp),
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    when (state) {
                        Welcome.NeedsLocation -> "Turn on location"
                        Welcome.Blocked -> "Open Settings"
                        else -> "Try again"
                    },
                )
            }
        }
        // Always subordinate: downloading a city is for no-signal travel, never a gate.
        Spacer(Modifier.height(6.dp))
        TextButton(onClick = onAddCity) {
            Text("Download a city for offline")
        }
        Spacer(Modifier.height(18.dp))
        // Say plainly what does and doesn't leave the phone — no vague reassurance.
        Text(
            "To find places, only a small area around you is sent to OpenStreetMap. No account, " +
                "no history, and the AI runs on your phone.",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
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
    forYou: List<Place>,
    examples: List<String>,
    festivals: List<CityEvent>,
    faith: Faith?,
    prayerTimes: PrayerTimes.Times?,
    prayerMethod: String,
    worship: Place?,
    onOpenUrl: (String) -> Unit,
    onCallEmergency: (String) -> Unit,
    onCall: (String) -> Unit,
    onDirections: (Place) -> Unit,
    onSave: (Place) -> Unit,
    modifier: Modifier,
) {
    Column(
        // Scrollable so nothing is ever clipped on a small screen. Left-aligned and
        // tightly spaced for a calm, glanceable home rather than a wall of cards.
        modifier = modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(20.dp),
        horizontalAlignment = Alignment.Start,
        verticalArrangement = Arrangement.Top,
    ) {
        // A warm welcome: where you are + the city's essentials — a hero, not a grey box.
        city?.let {
            HomeHeader(it, locality, onCallEmergency)
            Spacer(Modifier.height(24.dp))
        }
        // Primary action FIRST: an "explore" app should show what you can ask right away.
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.AutoAwesome, null, tint = MaterialTheme.colorScheme.tertiary, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
            Text("What are you in the mood for?", style = MaterialTheme.typography.titleLarge)
        }
        Spacer(Modifier.height(14.dp))
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            examples.forEach { example ->
                MoodChip(example) { onExample(example) }
            }
        }
        Spacer(Modifier.height(24.dp))

        // Glanceable extras below — each trimmed to stay light; tap through for detail.
        // Faith card: prayer times (Islam) and/or the nearest place of worship.
        if (faith != null && (prayerTimes != null || worship != null)) {
            FaithCard(faith, prayerTimes, prayerMethod, worship, onDirections, onCall, onOpenUrl)
            Spacer(Modifier.height(16.dp))
        }
        // These three cards can surface the same nearby place, so we de-duplicate top to
        // bottom: a place shown once is never repeated in a card below it.
        val shown = HashSet<Int>()
        // Travel Mode only: nearest food / shopping / outdoors from the live fix.
        if (around.isNotEmpty()) {
            NearbyCard("Around you now", Icons.Filled.MyLocation, around, onDirections, onCall)
            shown += around.map { it.id }
            Spacer(Modifier.height(16.dp))
        }
        // Your picks first: nearby places matching your selected interests. Shown only
        // when you've chosen interests in Preferences.
        // distinctBy name: OSM often has the same place as two nodes (e.g. a church as a
        // node AND a building), so without this the same suggestion can appear twice.
        val forYouShown = forYou.filterNot { it.id in shown }.distinctBy { it.name }
        if (forYouShown.isNotEmpty()) {
            NotableCard("For you", Icons.Filled.Favorite, forYouShown, onDirections, onSave)
            // Only the rows actually rendered (NotableCard shows NOTABLE_SHOWN) count as
            // "shown" — otherwise an un-displayed 4th+ "For you" place would be wrongly
            // removed from "Worth visiting nearby" below.
            shown += forYouShown.take(NOTABLE_SHOWN).map { it.id }
            Spacer(Modifier.height(16.dp))
        }
        // Notable places worth visiting near you. Hidden when the pack has none nearby
        // that a card above didn't already show.
        val notableShown = notable.filterNot { it.id in shown }.distinctBy { it.name }
        if (notableShown.isNotEmpty()) {
            NotableCard("Worth visiting nearby", Icons.Filled.Star, notableShown, onDirections, onSave)
            Spacer(Modifier.height(16.dp))
        }
        // Nearest police / hospital / fuel / parking. Hidden when the pack has none.
        if (essentials.isNotEmpty()) {
            NearbyCard("Daily needs near you", Icons.Filled.NearMe, essentials, onDirections, onCall)
            Spacer(Modifier.height(16.dp))
        }
        // The city's annual festivals. Absent when Wikipedia lists none for this city.
        if (festivals.isNotEmpty()) {
            FestivalsCard(festivals, onOpenUrl)
        }
    }
}

/**
 * The faith card: for any picked faith, the nearest real place of worship (grounded);
 * for Islam, also today's ON-DEVICE calculated prayer times. Honest about what's what —
 * the times are CALCULATED (start of each prayer) with the method named, and the
 * place's own service/Friday time (which no free source lists) is left to its website.
 */
@Composable
private fun FaithCard(
    faith: Faith,
    times: PrayerTimes.Times?,
    methodKey: String,
    place: Place?,
    onDirections: (Place) -> Unit,
    onCall: (String) -> Unit,
    onOpenUrl: (String) -> Unit,
) {
    val method = runCatching { PrayerTimes.Method.valueOf(methodKey) }.getOrNull()
    WnCard {
        // Prayer times: Islam only.
        times?.let {
            SectionHeader(Icons.Filled.Schedule, "Prayer times today")
            Spacer(Modifier.height(12.dp))
            PrayerRow("Fajr", it.fajr)
            PrayerRow("Sunrise", it.sunrise)
            PrayerRow("Dhuhr", it.dhuhr)
            PrayerRow("Asr", it.asr)
            PrayerRow("Maghrib", it.maghrib)
            PrayerRow("Isha", it.isha)
            Spacer(Modifier.height(8.dp))
            Text(
                "Calculated · ${method?.label ?: methodKey}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        // Nearest real place of worship for this faith — grounded. Its service time
        // isn't calculable, so we point to the place's own website/phone, never invent.
        place?.let { p ->
            if (times != null) {
                Spacer(Modifier.height(14.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
                Spacer(Modifier.height(14.dp))
            }
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
                CategoryBadge("worship", p.religion)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(p.name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                    val meta = listOfNotNull(
                        "Nearest ${faith.placeType}",
                        distanceLabel(p.distanceKm)?.let { "$it away" },
                    ).joinToString(" · ")
                    Spacer(Modifier.height(1.dp))
                    Text(meta, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(faith.serviceNote, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(8.dp))
                    ActionRow {
                        DirectionsButton { onDirections(p) }
                        p.phone?.let { ph -> CallButton { onCall(ph) } }
                        p.website?.let { w -> WebsiteButton { onOpenUrl(w) } }
                    }
                }
            }
        }
    }
}

/** One "Fajr … 05:56" line: the prayer on the left, its calculated time (in the brand
 *  colour) on the right. */
@Composable
private fun PrayerRow(name: String, hour: Double) {
    Row(Modifier.fillMaxWidth().heightIn(min = 32.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(name, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        Text(
            PrayerTimes.format(hour),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
        )
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
    WnCard {
        SectionHeader(Icons.Filled.Celebration, "Annual festivals here")
        Spacer(Modifier.height(4.dp))
        Text(
            "Dates change each year — check before you go.",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(10.dp))
        // Names only — glanceable; tap one to read it on Wikipedia (which also credits
        // CC BY-SA). A chevron marks the ones you can open. No paragraphs on the home.
        shown.forEach { event ->
            val url = event.summaryUrl
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 44.dp)
                    .let { if (url != null) it.clickable { onOpen(url) } else it },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    event.name,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    color = if (url != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f),
                )
                if (url != null) {
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, "Open article", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                }
            }
        }
        if (!expanded && events.size > FESTIVALS_SHOWN) {
            Text(
                "…and ${events.size - FESTIVALS_SHOWN} more",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 44.dp)
                    .clickable { expanded = true }
                    .wrapContentHeight(),
            )
        }
    }
}

/**
 * A few grounded place suggestions — real DB rows near you, glanceable (name + kind +
 * distance, no Wikipedia paragraph). Used for both "Worth visiting nearby" (notable
 * places) and "For you" (places matching your chosen interests). Never invented.
 */
@Composable
private fun NotableCard(
    title: String,
    icon: ImageVector,
    places: List<Place>,
    onDirections: (Place) -> Unit,
    onSave: (Place) -> Unit,
) {
    WnCard {
        SectionHeader(icon, title)
        Spacer(Modifier.height(14.dp))
        places.take(NOTABLE_SHOWN).forEachIndexed { index, place ->
            if (index > 0) Spacer(Modifier.height(16.dp))
            val sub = place.subcategory?.replace('_', ' ')?.replaceFirstChar { it.uppercase() }
            val dist = distanceLabel(place.distanceKm)?.let { "$it away" }
            val meta = listOfNotNull(sub, dist).joinToString(" · ")
            PlaceRow(place, meta, snippet = place.summary) {
                DirectionsButton { onDirections(place) }
                SaveButton { onSave(place) }
                // Only offered when this place really has a write-up — see openStory.
                if (!place.summary.isNullOrBlank()) StoryButton { openStory(place) }
            }
        }
    }
}

/**
 * One place inside a card: a colourful category badge, the name + a glanceable meta line,
 * and its actions. The badge is what turns a wall of text into something you can scan.
 */
@Composable
private fun PlaceRow(place: Place, meta: String, snippet: String? = null, actions: @Composable () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
        CategoryBadge(place.category, place.religion)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(place.name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            if (meta.isNotBlank()) {
                Spacer(Modifier.height(1.dp))
                Text(meta, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            // A grounded one-liner from Wikipedia — the "why it's worth a visit". Two lines
            // max keeps the home glanceable; the full story is in the chat / Travel Mode.
            snippet?.takeIf { it.isNotBlank() }?.let {
                Spacer(Modifier.height(4.dp))
                Text(
                    it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.height(8.dp))
            ActionRow(content = actions)
        }
    }
}

/**
 * The home hero: a warm teal banner welcoming the traveller to where they are — their
 * actual suburb when we have a fresh on-device fix, otherwise the city — with the city's
 * essentials (currency, population, a one-tap emergency dial) as neat pills. Replaces the
 * old flat grey info box so the app opens with personality instead of a wall of facts.
 */
@Composable
private fun HomeHeader(city: CityInfo, locality: String?, onCallEmergency: (String) -> Unit) {
    val facts = CountryFacts.forCountry(city.country)
    // The traveller's actual locality (on-device nearest suburb) as the heading when we
    // have it and it differs from the pack city; otherwise the pack city. Null just means
    // "no fix / offline" — we never invent a place name.
    val here = locality?.takeIf { it.isNotBlank() && !it.equals(city.shortName, ignoreCase = true) }
    val title = here ?: city.shortName
    val subtitle = if (here != null) listOfNotNull(city.shortName, city.country).joinToString(", ") else city.country

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
    ) {
        Column(Modifier.padding(22.dp)) {
            // A warm, time-aware greeting instead of a clinical "WHERE YOU ARE" label —
            // the app opens like a friend saying hi.
            Text(
                remember { greeting() },
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.82f),
            )
            Spacer(Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.LocationOn, null, modifier = Modifier.size(22.dp))
                Spacer(Modifier.width(8.dp))
                Text(title, style = MaterialTheme.typography.headlineMedium)
            }
            subtitle?.let {
                Spacer(Modifier.height(2.dp))
                Text(
                    it,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.72f),
                    modifier = Modifier.padding(start = 30.dp),
                )
            }
            if (facts != null || (here == null && city.population != null)) {
                Spacer(Modifier.height(18.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    // Population is the pack CITY's figure, so only show it under the city
                    // heading — under a suburb it would misread as the suburb's population.
                    if (here == null) city.population?.let { HeaderPill(Icons.Filled.Groups, "%,d".format(it)) }
                    facts?.let {
                        HeaderPill(Icons.Filled.Payments, it.currency)
                        HeaderPill(Icons.Filled.Call, "Emergency ${it.emergency}", emphasis = true) { onCallEmergency(it.emergency) }
                    }
                }
            }
        }
    }
}

/**
 * A small pill inside the hero: an icon + a fact. Optionally tappable (the emergency
 * dial), and `emphasis` gives that one a stronger, "call me" red so it stands out.
 */
@Composable
private fun HeaderPill(icon: ImageVector, text: String, emphasis: Boolean = false, onClick: (() -> Unit)? = null) {
    // In dark mode a plain `surface` pill barely separates from the teal hero, so use a
    // lighter container there for definition.
    val container = when {
        emphasis -> MaterialTheme.colorScheme.error
        isSystemInDarkTheme() -> MaterialTheme.colorScheme.surfaceContainerHighest
        else -> MaterialTheme.colorScheme.surface
    }
    val content = if (emphasis) MaterialTheme.colorScheme.onError else MaterialTheme.colorScheme.onSurface
    val base = Modifier.clip(RoundedCornerShape(50)).background(container)
    val shaped = if (onClick != null) base.clickable { onClick() } else base
    Row(
        // 44dp min so the tappable "Emergency" dial is a comfortable, safe target.
        modifier = shaped.heightIn(min = 44.dp).padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = content, modifier = Modifier.size(15.dp))
        Spacer(Modifier.width(6.dp))
        Text(text, style = MaterialTheme.typography.labelMedium, color = content)
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
    icon: ImageVector,
    places: List<Place>,
    onDirections: (Place) -> Unit,
    onCall: (String) -> Unit,
) {
    WnCard {
        SectionHeader(icon, title)
        Spacer(Modifier.height(14.dp))
        places.forEachIndexed { index, place ->
            if (index > 0) Spacer(Modifier.height(16.dp))
            // Kind and distance share one line — same shape as the "Worth visiting" rows.
            val meta = listOfNotNull(
                categoryLabel(place.category),
                distanceLabel(place.distanceKm)?.let { "$it away" },
            ).joinToString(" · ")
            PlaceRow(place, meta) {
                DirectionsButton { onDirections(place) }
                place.phone?.let { phone -> CallButton { onCall(phone) } }
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
            color = if (isUser) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface,
            contentColor = if (isUser) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
            // A little "tail" corner so the bubbles point to their sender.
            shape = if (isUser) RoundedCornerShape(20.dp, 20.dp, 6.dp, 20.dp)
            else RoundedCornerShape(20.dp, 20.dp, 20.dp, 6.dp),
            shadowElevation = if (isUser) 0.dp else 1.dp,
            border = if (isUser) null else BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        ) {
            if (message.loading) {
                Row(
                    // Announce "Thinking…"/"Warming up…" to a screen reader without stealing focus.
                    modifier = Modifier.padding(14.dp).semantics { liveRegion = LiveRegionMode.Polite },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(10.dp))
                    Text(message.text, style = MaterialTheme.typography.bodyMedium)
                }
            } else {
                Text(message.text, modifier = Modifier.padding(14.dp), style = MaterialTheme.typography.bodyLarge)
            }
        }
        message.cards.forEach { card ->
            Spacer(Modifier.height(10.dp))
            RecommendationCard(card, onDirections, onSave)
        }
    }
}

@Composable
private fun RecommendationCard(card: RecCard, onDirections: (Place) -> Unit, onSave: (Place) -> Unit) {
    val place = card.place
    val tint = categoryTint(place.category, isSystemInDarkTheme())
    WnCard {
        Row(verticalAlignment = Alignment.Top) {
            CategoryBadge(place.category, place.religion, size = 46.dp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(place.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                place.subcategory?.let {
                    Text(
                        it.replace('_', ' ').replaceFirstChar { c -> c.uppercase() },
                        style = MaterialTheme.typography.labelMedium,
                        color = tint.icon,
                        fontWeight = FontWeight.Medium,
                    )
                }
            }
        }
        if (card.reason.isNotBlank()) {
            Spacer(Modifier.height(12.dp))
            Text(card.reason, style = MaterialTheme.typography.bodyMedium)
        }
        place.summary?.let {
            Spacer(Modifier.height(8.dp))
            Text(
                it,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 4,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.height(14.dp))
        ActionRow {
            DirectionsButton { onDirections(place) }
            SaveButton { onSave(place) }
            // "Tell me the history" — the full write-up, but only where one really exists.
            if (!place.summary.isNullOrBlank()) StoryButton { openStory(place) }
        }
    }
}

/**
 * Opens the reader sheet on a place's real Wikipedia write-up (full text, CC BY-SA
 * credit, Listen, Directions). The card only shows the button when `summary` is present,
 * so this can never be asked to tell a history we don't actually have — for a place with
 * no article we say nothing rather than invent one.
 */
private fun openStory(place: Place) {
    val text = place.summary?.takeIf { it.isNotBlank() } ?: return
    StoryRequest.open.value = StoryArgs(place.name, text, place.lat, place.lng)
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
    val canSend = value.isNotBlank()
    Surface(color = MaterialTheme.colorScheme.surface, shadowElevation = 10.dp) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // A soft pill holds the mic + the text field, so the input reads as one thing.
            Row(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(26.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                MicButton(voiceState = voiceState, onClick = onMicToggle)
                TextField(
                    value = value,
                    onValueChange = onValueChange,
                    modifier = Modifier.weight(1f),
                    placeholder = { Text(placeholder) },
                    maxLines = 3,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                    keyboardActions = KeyboardActions(onSend = { onSend() }),
                    // Transparent so the pill behind shows through — no double box.
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = Color.Transparent,
                        unfocusedContainerColor = Color.Transparent,
                        disabledContainerColor = Color.Transparent,
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent,
                        disabledIndicatorColor = Color.Transparent,
                    ),
                )
            }
            Spacer(Modifier.width(10.dp))
            // Circular send button — lights up in the brand colour once there's something to send.
            Surface(
                onClick = onSend,
                enabled = canSend,
                shape = CircleShape,
                color = if (canSend) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                contentColor = if (canSend) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(52.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(Icons.AutoMirrored.Filled.Send, "Send", modifier = Modifier.size(22.dp))
                }
            }
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
