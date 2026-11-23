package com.vlmis.dexter

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import assistant.android.ApiKeyStore
import assistant.claude.ClaudeBrain
import assistant.claude.ClaudeSettings
import assistant.claude.KeyCheck
import kotlinx.coroutines.launch
import voice.piper.model.InstallState
import voice.piper.model.VoiceManifest
import voice.piper.model.VoiceStore
import voice.speaker.AndroidConnectivity

// Reached from the launcher icon's long-press menu. FLAG_SECURE keeps the API key out of screenshots and the
// recents thumbnail; the key field never shows the saved key, only a masked hint.
class SettingsActivity : ComponentActivity() {
    private val keys by lazy { ApiKeyStore(this) }
    private val prefs by lazy { getSharedPreferences(assistant.android.Assistant.PREFS, MODE_PRIVATE) }
    private val voicePrefs by lazy { getSharedPreferences("voice", MODE_PRIVATE) }
    private val store by lazy { VoiceStore(filesDir, VoiceManifest.loadBundled(), AndroidConnectivity(this)) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        enableEdgeToEdge()
        setContent {
            MaterialTheme(colorScheme = darkColorScheme()) {
                Surface(Modifier.fillMaxSize()) {
                    Column(
                        Modifier.safeDrawingPadding().padding(20.dp).verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(14.dp),
                    ) {
                        Text("Dexter settings", style = MaterialTheme.typography.headlineSmall)
                        KeySection()
                        HorizontalDivider()
                        ModelSection()
                        HorizontalDivider()
                        VoiceSection()
                        HorizontalDivider()
                        Text(
                            "Privacy: when you double-tap and talk, your words (as text) and the recent conversation are " +
                                "sent to Anthropic to answer. Dexter keeps the conversation in memory only and forgets it " +
                                "after ten quiet minutes. The voice runs on this phone.",
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            }
        }
    }

    @Composable
    private fun KeySection() {
        val scope = rememberCoroutineScope()
        var entry by remember { mutableStateOf("") }
        var hint by remember { mutableStateOf(keys.hint) }
        var status by remember { mutableStateOf("") }
        Text("Claude API key", style = MaterialTheme.typography.titleMedium)
        Text(
            if (hint != null) "Saved: $hint (encrypted with this phone's keystore)" else "No key saved yet.",
            style = MaterialTheme.typography.bodyMedium,
        )
        OutlinedTextField(
            value = entry,
            onValueChange = { entry = it.trim() },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Paste a new key (sk-ant-…)") },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Password,
                autoCorrectEnabled = false,
                imeAction = ImeAction.Done,
            ),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Button(
                onClick = {
                    status = try {
                        keys.save(entry)
                        hint = keys.hint
                        "Saved. Tip: clear your clipboard if you copied the key."
                    } catch (e: IllegalArgumentException) {
                        e.message ?: "Not saved."
                    } finally {
                        entry = ""
                    }
                },
                enabled = ApiKeyStore.isPlausible(entry),
            ) { Text("Save") }
            OutlinedButton(
                onClick = {
                    val key = keys.load()
                    if (key == null) {
                        status = "No key saved."
                    } else {
                        status = "Checking…"
                        scope.launch {
                            val brain = ClaudeBrain({ null }, { ClaudeSettings(model = currentModel()) })
                            status = when (brain.check(key)) {
                                KeyCheck.Ok -> "The key works."
                                KeyCheck.Rejected -> "Anthropic rejected this key."
                                KeyCheck.ModelUnavailable -> "The key works, but can't use ${currentModel()}."
                                KeyCheck.Offline -> "No connection; try again online."
                                KeyCheck.Error -> "Anthropic returned an error; try again later."
                            }
                        }
                    }
                },
                enabled = hint != null,
            ) { Text("Check") }
            OutlinedButton(
                onClick = {
                    keys.clear()
                    hint = null
                    status = "Key removed."
                },
                enabled = hint != null,
            ) { Text("Remove") }
        }
        if (status.isNotEmpty()) Text(status, style = MaterialTheme.typography.bodySmall)
    }

    @Composable
    private fun ModelSection() {
        var model by remember { mutableStateOf(currentModel()) }
        Text("Model", style = MaterialTheme.typography.titleMedium)
        for (m in ClaudeSettings.MODELS) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                RadioButton(model == m, {
                    model = m
                    prefs.edit().putString("model", m).apply()
                })
                Text(m)
            }
        }
        Text("Opus answers best; Sonnet and Haiku answer faster and cost less.", style = MaterialTheme.typography.bodySmall)
    }

    @Composable
    private fun VoiceSection() {
        val scope = rememberCoroutineScope()
        val voice = store.manifest.defaultVoice
        var installed by remember { mutableStateOf(voice != null && store.isInstalled(voice.id)) }
        var progress by remember { mutableStateOf("") }
        var wifiOnly by remember { mutableStateOf(!voicePrefs.getBoolean("allow_metered_downloads", false)) }
        var offset by remember { mutableFloatStateOf(voicePrefs.getInt("sync_offset_ms", 0).toFloat()) }
        Text("Voice", style = MaterialTheme.typography.titleMedium)
        if (voice != null) {
            Text(
                if (installed) "${voice.name} is installed." else "${voice.name} (${voice.archive.download.size / 1_000_000} MB) isn't downloaded; the phone's own voice is used until then.",
                style = MaterialTheme.typography.bodyMedium,
            )
            if (!installed) {
                Button({
                    store.allowMetered = !wifiOnly
                    scope.launch {
                        try {
                            store.install(voice.id).collect {
                                progress = when (it) {
                                    is InstallState.Downloading -> "%s %.0f%%".format(it.file, 100f * it.bytes / it.total)
                                    InstallState.Extracting -> "Unpacking…"
                                    InstallState.Installed -> "Installed."
                                }
                            }
                            installed = true
                        } catch (e: Exception) {
                            progress = "Download stopped: ${e.message}"
                        }
                    }
                }) { Text("Download voice") }
            }
            if (progress.isNotEmpty()) Text(progress, style = MaterialTheme.typography.bodySmall)
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Download on Wi-Fi only", Modifier.weight(1f))
            Switch(wifiOnly, {
                wifiOnly = it
                voicePrefs.edit().putBoolean("allow_metered_downloads", !it).apply()
            })
        }
        Text("Lip-sync offset: ${offset.toInt()} ms (raise it if the mouth lags on Bluetooth)", style = MaterialTheme.typography.bodyMedium)
        Slider(offset, {
            offset = it
            voicePrefs.edit().putInt("sync_offset_ms", it.toInt()).apply()
        }, valueRange = -200f..300f)
    }

    private fun currentModel(): String =
        prefs.getString("model", null)?.takeIf { it in ClaudeSettings.MODELS } ?: ClaudeSettings.DEFAULT_MODEL
}
