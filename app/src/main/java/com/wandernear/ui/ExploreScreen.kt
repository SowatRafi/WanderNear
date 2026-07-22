package com.wandernear.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wandernear.data.CityDatabase
import com.wandernear.data.CityStats
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** The first tab: proves the data pack loaded and shows what's inside it. */
@Composable
fun ExploreScreen() {
    val context = LocalContext.current

    // Load the data pack off the main thread so the UI never freezes.
    val stats by produceState<CityStats?>(initialValue = null) {
        value = withContext(Dispatchers.IO) {
            runCatching { CityDatabase(context).stats() }.getOrNull()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(24.dp))
        Text("WanderNear", fontSize = 34.sp, fontWeight = FontWeight.Bold)
        Text("Your on-device local guide", style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.height(32.dp))

        val current = stats
        if (current == null) {
            CircularProgressIndicator()
            Spacer(Modifier.height(12.dp))
            Text("Loading the Melbourne data pack…")
        } else {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(20.dp)) {
                    Text(
                        "✓ Data pack loaded",
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "${current.total} places in Melbourne",
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold,
                    )
                    Spacer(Modifier.height(16.dp))
                    current.byCategory.forEach { (category, count) ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Text(labelFor(category))
                            Text("$count", fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }
            Spacer(Modifier.height(24.dp))
            Text(
                current.attribution,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private fun labelFor(category: String): String = when (category) {
    "food" -> "🍽️  Food & drink"
    "worship" -> "🛕  Places of worship"
    "attraction" -> "🏛️  Attractions"
    "outdoor" -> "🌲  Outdoor & nature"
    else -> category
}
