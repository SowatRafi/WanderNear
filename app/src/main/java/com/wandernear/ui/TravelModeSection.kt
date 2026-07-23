package com.wandernear.ui

import android.Manifest
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.wandernear.core.model.UserPreferences
import com.wandernear.data.PreferencesRepository
import com.wandernear.reminders.Notifier
import com.wandernear.travel.TravelModeService
import kotlinx.coroutines.launch

/**
 * The "Travel Mode" card in Preferences. Turning it on starts a foreground service
 * that watches for worth-visiting spots near you and notifies you, shown by a
 * visible "Travel Mode is on" banner the whole time. It uses location ONLY while
 * on, needs both location + notification permission (the banner is what keeps it
 * honest), and nothing ever leaves the phone.
 */
@Composable
fun TravelModeSection(repo: PreferencesRepository) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val prefs by repo.preferences.collectAsState(initial = UserPreferences())

    fun turnOn() {
        TravelModeService.start(context)
        scope.launch { repo.setTravelMode(true) }
    }

    // The visible banner IS the privacy guarantee, so never start the location
    // service if the Travel Mode notification channel has been muted — send the
    // user to re-enable it instead.
    fun startIfBannerVisible() {
        if (travelChannelBlocked(context)) {
            Toast.makeText(
                context,
                "Turn the Travel Mode notification back on so its banner can show.",
                Toast.LENGTH_LONG,
            ).show()
            openChannelSettings(context)
        } else {
            turnOn()
        }
    }

    // We ask for location (for the "near you" checks) + notifications (for the
    // visible banner). Only start if BOTH are granted — no silent tracking.
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { result ->
        val fine = result[Manifest.permission.ACCESS_FINE_LOCATION] == true
        val notif = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            result[Manifest.permission.POST_NOTIFICATIONS] == true
        if (fine && notif) {
            startIfBannerVisible()
        } else {
            Toast.makeText(
                context,
                "Travel Mode needs location + notifications so it can alert you and stay visible.",
                Toast.LENGTH_LONG,
            ).show()
        }
    }

    fun enable() {
        val fineGranted = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED
        val notifGranted = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
        if (fineGranted && notifGranted) {
            startIfBannerVisible()
        } else {
            val ask = mutableListOf(Manifest.permission.ACCESS_FINE_LOCATION)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                ask += Manifest.permission.POST_NOTIFICATIONS
            }
            permissionLauncher.launch(ask.toTypedArray())
        }
    }

    fun disable() {
        TravelModeService.stop(context)
        scope.launch { repo.setTravelMode(false) }
    }

    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Text("Travel Mode", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(4.dp))
            Text(
                "While on, WanderNear watches for worth-visiting spots near you and notifies you — " +
                    "with a visible \"Travel Mode is on\" banner the whole time. It uses location only " +
                    "while on; nothing leaves your phone. Turn it off any time.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Notify me about nearby spots")
                Switch(
                    checked = prefs.travelModeOn,
                    onCheckedChange = { on -> if (on) enable() else disable() },
                )
            }
        }
    }
}

/** True if the user has muted the Travel Mode notification channel (importance
 *  NONE). We refuse to run the location service without its visible banner. */
private fun travelChannelBlocked(context: Context): Boolean {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return false
    Notifier.createTravelChannel(context)   // idempotent; never un-mutes a user's choice
    val channel = context.getSystemService(NotificationManager::class.java)
        ?.getNotificationChannel(Notifier.TRAVEL_CHANNEL_ID)
    return channel != null && channel.importance == NotificationManager.IMPORTANCE_NONE
}

/** Opens system settings for the Travel Mode notification channel. */
private fun openChannelSettings(context: Context) {
    val intent = Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS)
        .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
        .putExtra(Settings.EXTRA_CHANNEL_ID, Notifier.TRAVEL_CHANNEL_ID)
    try {
        context.startActivity(intent)
    } catch (e: Exception) {
        // No matching settings screen — the toast already told the user what to do.
    }
}
