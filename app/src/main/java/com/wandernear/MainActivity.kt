package com.wandernear

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.outlined.Bookmark
import androidx.compose.material.icons.outlined.Explore
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
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

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()                       // draw behind the (transparent) system bars
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
