package com.wandernear

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.outlined.Bookmark
import androidx.compose.material.icons.outlined.Explore
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.wandernear.data.PreferencesRepository
import com.wandernear.reminders.JournalReminders
import com.wandernear.travel.TravelModeService
import com.wandernear.ui.ChatScreen
import com.wandernear.ui.MyTripsScreen
import com.wandernear.ui.PreferencesScreen
import com.wandernear.ui.theme.WanderNearTheme
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/** The three tabs in the bottom navigation bar, each with a filled (selected) and
 *  outlined (unselected) icon so the active tab reads clearly. */
private enum class Tab(val label: String, val selectedIcon: ImageVector, val icon: ImageVector) {
    Explore("Explore", Icons.Filled.Explore, Icons.Outlined.Explore),
    MyTrips("My Trips", Icons.Filled.Bookmark, Icons.Outlined.Bookmark),
    Preferences("Preferences", Icons.Filled.Tune, Icons.Outlined.Tune),
}

/** The place a Travel Mode "story" notification asked us to open — a real grounded
 *  place with its stored Wikipedia summary. Read-only; nothing here is invented. */
private data class StoryArgs(val name: String, val summary: String, val lat: Double, val lng: Double)

class MainActivity : ComponentActivity() {
    // Set when a Travel Mode "read about this place" notification opens the app.
    private val storyState = mutableStateOf<StoryArgs?>(null)

    // On-device text-to-speech, owned by the Activity so "Listen" never has to start a
    // background service (which could linger or be blocked). Offline, free; it only ever
    // reads the grounded stored summary aloud, never anything invented.
    private var tts: TextToSpeech? = null
    private var ttsReady = false
    private var pendingSpeech: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()                       // draw behind the (transparent) system bars
        // On a fresh launch, consume the launching intent (a notification may carry a story).
        // On a config change (rotation), RESTORE the open story instead — never re-parse the
        // intent (which would re-open a dismissed story, or re-read it aloud).
        if (savedInstanceState == null) applyStoryIntent(intent)
        else storyState.value = restoreStory(savedInstanceState)
        // applicationContext keeps the single app-wide DataStore instance.
        val prefsRepo = PreferencesRepository(applicationContext)
        JournalReminders.scheduleDaily(this)   // background daily anniversary check
        // If the switch says Travel Mode is on but no service is actually running
        // (the process was killed, or the phone rebooted), correct it back to off
        // so Preferences never claims it's active when it isn't.
        lifecycleScope.launch {
            if (prefsRepo.preferences.first().travelModeOn && !TravelModeService.isRunning) {
                prefsRepo.setTravelMode(false)
            }
        }
        setContent {
            WanderNearTheme {
                ReminderBootstrap()            // ask once, then run the on-open checks
                AppScaffold(prefsRepo)
                storyState.value?.let { args ->
                    StorySheet(
                        args = args,
                        onListen = { speak("${args.name}. ${args.summary}") },
                        onDismiss = { storyState.value = null },
                    )
                }
            }
        }
    }

    // singleTop: a story notification tapped while the app is open arrives here.
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        applyStoryIntent(intent)
    }

    // Keep an OPEN story across rotation / config changes so the user doesn't lose it.
    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        storyState.value?.let {
            outState.putString(TravelModeService.EXTRA_STORY_NAME, it.name)
            outState.putString(TravelModeService.EXTRA_STORY_SUMMARY, it.summary)
            outState.putDouble(TravelModeService.EXTRA_STORY_LAT, it.lat)
            outState.putDouble(TravelModeService.EXTRA_STORY_LNG, it.lng)
        }
    }

    override fun onDestroy() {
        tts?.stop(); tts?.shutdown(); tts = null
        super.onDestroy()
    }

    /** Show the story an intent carries (if any), and read it aloud when it came from
     *  "Listen". Only overwrites the current story when the intent actually has one, so a
     *  plain launch/banner tap can't close an open sheet. */
    private fun applyStoryIntent(intent: Intent?) {
        val story = parseStory(intent) ?: return
        storyState.value = story
        if (intent?.getBooleanExtra(TravelModeService.EXTRA_SPEAK, false) == true) {
            speak("${story.name}. ${story.summary}")
        }
    }

    private fun parseStory(intent: Intent?): StoryArgs? {
        val name = intent?.getStringExtra(TravelModeService.EXTRA_STORY_NAME) ?: return null
        val summary = intent.getStringExtra(TravelModeService.EXTRA_STORY_SUMMARY) ?: return null
        return StoryArgs(
            name = name,
            summary = summary,
            lat = intent.getDoubleExtra(TravelModeService.EXTRA_STORY_LAT, 0.0),
            lng = intent.getDoubleExtra(TravelModeService.EXTRA_STORY_LNG, 0.0),
        )
    }

    private fun restoreStory(state: Bundle): StoryArgs? {
        val name = state.getString(TravelModeService.EXTRA_STORY_NAME) ?: return null
        val summary = state.getString(TravelModeService.EXTRA_STORY_SUMMARY) ?: return null
        return StoryArgs(
            name = name,
            summary = summary,
            lat = state.getDouble(TravelModeService.EXTRA_STORY_LAT),
            lng = state.getDouble(TravelModeService.EXTRA_STORY_LNG),
        )
    }

    /** Read [text] aloud with the phone's on-device TTS. The engine is created on first use
     *  (async); `ttsReady` guards against calling speak() before it has initialised, and
     *  `pendingSpeech` (last-wins) is spoken the moment it's ready. */
    fun speak(text: String) {
        val engine = tts
        if (engine != null && ttsReady) {
            engine.speak(text, TextToSpeech.QUEUE_FLUSH, null, "wn")
            return
        }
        pendingSpeech = text
        if (engine == null) {
            tts = TextToSpeech(this) { status ->
                if (status == TextToSpeech.SUCCESS) {
                    ttsReady = true
                    tts?.language = Locale.getDefault()
                    pendingSpeech?.let { tts?.speak(it, TextToSpeech.QUEUE_FLUSH, null, "wn") }
                    pendingSpeech = null
                }
            }
        }
    }
}

/**
 * The "story" reader: opens from a Travel Mode notification and shows a place's real
 * Wikipedia write-up, with the option to have it read aloud (on-device TTS) or to get
 * directions. The text is the grounded stored summary — never invented — and Wikipedia
 * is credited (CC BY-SA), as required wherever its text is shown.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun StorySheet(args: StoryArgs, onListen: () -> Unit, onDismiss: () -> Unit) {
    val context = LocalContext.current
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            Modifier
                .padding(horizontal = 24.dp)
                .padding(bottom = 36.dp)
                .verticalScroll(rememberScrollState()),
        ) {
            Text(args.name, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(12.dp))
            Text(args.summary, style = MaterialTheme.typography.bodyLarge)
            Spacer(Modifier.height(12.dp))
            Text(
                "From Wikipedia · CC BY-SA 4.0",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(20.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(onClick = onListen) {
                    Icon(Icons.AutoMirrored.Filled.VolumeUp, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Listen")
                }
                OutlinedButton(onClick = {
                    val uri = Uri.parse("geo:${args.lat},${args.lng}?q=${args.lat},${args.lng}(${Uri.encode(args.name)})")
                    try {
                        context.startActivity(Intent(Intent.ACTION_VIEW, uri))
                    } catch (e: ActivityNotFoundException) {
                        Toast.makeText(context, "No maps app found on this phone", Toast.LENGTH_SHORT).show()
                    }
                }) {
                    Icon(Icons.Filled.Navigation, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Directions")
                }
            }
        }
    }
}

@Composable
private fun AppScaffold(prefsRepo: PreferencesRepository) {
    var tab by remember { mutableStateOf(Tab.Explore) }
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
                Tab.entries.forEach { t ->
                    val selected = tab == t
                    NavigationBarItem(
                        selected = selected,
                        onClick = { tab = t },
                        icon = {
                            Icon(
                                if (selected) t.selectedIcon else t.icon,
                                contentDescription = t.label,
                            )
                        },
                        label = { Text(t.label) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
                            indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                            selectedTextColor = MaterialTheme.colorScheme.onSurface,
                            unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        ),
                    )
                }
            }
        },
    ) { innerPadding ->
        // A soft cross-fade between tabs so switching feels smooth, not a hard cut.
        AnimatedContent(
            targetState = tab,
            transitionSpec = { fadeIn() togetherWith fadeOut() using SizeTransform(clip = false) },
            label = "tab",
        ) { current ->
            Box(Modifier.fillMaxSize().padding(innerPadding)) {
                when (current) {
                    Tab.Explore -> ChatScreen(prefsRepo)
                    Tab.MyTrips -> MyTripsScreen()
                    Tab.Preferences -> PreferencesScreen(prefsRepo)
                }
            }
        }
    }
}

/**
 * On app open: request the notification permission once (Android 13+), then run
 * the anniversary + "you're back nearby" checks. If the permission is declined,
 * the checks still run but post nothing.
 */
@Composable
private fun ReminderBootstrap() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    fun runChecks() {
        scope.launch(Dispatchers.IO) {
            JournalReminders.checkAnniversaries(context)
            JournalReminders.checkNearbyNudge(context)
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { runChecks() }

    LaunchedEffect(Unit) {
        val needsAsk = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        if (needsAsk) {
            permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            runChecks()
        }
    }
}
