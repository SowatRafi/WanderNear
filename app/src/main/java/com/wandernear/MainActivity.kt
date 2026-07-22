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
import com.wandernear.data.PreferencesRepository
import com.wandernear.ui.ChatScreen
import com.wandernear.ui.MyTripsScreen
import com.wandernear.ui.PreferencesScreen

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
        setContent {
            MaterialTheme {
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
