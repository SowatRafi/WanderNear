package com.wandernear

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/** The two tabs in the bottom navigation bar. */
private enum class Tab(val label: String, val icon: String) {
    Explore("Ask", "💬"),
    MyTrips("My Trips", "📍"),
    Preferences("Preferences", "⚙️"),
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
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
            MaterialTheme {
                ReminderBootstrap()            // ask once, then run the on-open checks
                AppScaffold(prefsRepo)
            }
        }
    }
}

@Composable
private fun AppScaffold(prefsRepo: PreferencesRepository) {
    var tab by remember { mutableStateOf(Tab.Explore) }
    Scaffold(
        bottomBar = {
            NavigationBar {
                Tab.entries.forEach { t ->
                    NavigationBarItem(
                        selected = tab == t,
                        onClick = { tab = t },
                        icon = { Text(t.icon) },
                        label = { Text(t.label) },
                    )
                }
            }
        },
    ) { innerPadding ->
        Box(Modifier.fillMaxSize().padding(innerPadding)) {
            when (tab) {
                Tab.Explore -> ChatScreen(prefsRepo)
                Tab.MyTrips -> MyTripsScreen()
                Tab.Preferences -> PreferencesScreen(prefsRepo)
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
