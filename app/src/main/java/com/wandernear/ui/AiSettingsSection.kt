package com.wandernear.ui

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.wandernear.ai.LlmEngine
import com.wandernear.ai.ModelManager
import com.wandernear.core.model.UserPreferences
import com.wandernear.data.PreferencesRepository
import kotlinx.coroutines.launch

/**
 * The "On-device AI" card in Preferences: download the model once, toggle
 * AI-reworded replies on/off, and a "Test AI" button to prove the model runs.
 * Until the model is downloaded and the toggle is on, the app keeps using the
 * (already working) template replies.
 */
@Composable
fun AiSettingsSection(repo: PreferencesRepository) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val prefs by repo.preferences.collectAsState(initial = UserPreferences())

    var downloaded by remember { mutableStateOf(ModelManager.isDownloaded(context)) }
    var downloading by remember { mutableStateOf(false) }
    var progress by remember { mutableStateOf(0f) }
    var testing by remember { mutableStateOf(false) }
    var testResult by remember { mutableStateOf<String?>(null) }

    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Text("On-device AI (experimental)", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(4.dp))
            Text(
                "Optional: reword replies with an AI model that runs entirely on your phone. " +
                    "Downloads once (~2.6 GB); your data never leaves the device.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(12.dp))

            when {
                downloading -> {
                    LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth())
                    Spacer(Modifier.height(6.dp))
                    Text("Downloading… ${(progress * 100).toInt()}%", style = MaterialTheme.typography.bodySmall)
                }

                !downloaded -> {
                    Button(onClick = {
                        downloading = true
                        progress = 0f
                        scope.launch {
                            val ok = ModelManager.download(context) { progress = it }
                            downloading = false
                            downloaded = ok && ModelManager.isDownloaded(context)
                            if (!ok) {
                                Toast.makeText(context, "Download failed — check Wi-Fi and try again", Toast.LENGTH_LONG).show()
                            }
                        }
                    }) { Text("Download AI model (~2.6 GB)") }
                }

                else -> {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text("Use on-device AI for replies")
                        Switch(checked = prefs.useAi, onCheckedChange = { value -> scope.launch { repo.setUseAi(value) } })
                    }
                    Spacer(Modifier.height(8.dp))
                    Button(
                        onClick = {
                            testing = true
                            testResult = null
                            scope.launch {
                                val ready = LlmEngine.ensureReady(context)
                                testResult = if (!ready) {
                                    "Couldn't load the model."
                                } else {
                                    LlmEngine.generate(
                                        system = "You reword facts warmly in one sentence. Never invent any place or fact.",
                                        prompt = "Reword: Hareruya Pantry is a vegetarian-friendly cafe 0.1 km away.",
                                    ) ?: "(no response)"
                                }
                                testing = false
                            }
                        },
                        enabled = !testing,
                    ) { Text(if (testing) "Thinking…" else "Test AI") }

                    testResult?.let {
                        Spacer(Modifier.height(8.dp))
                        Text(it, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        }
    }
}
