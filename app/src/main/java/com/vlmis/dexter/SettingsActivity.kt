package com.vlmis.dexter

import android.os.Bundle
import android.speech.tts.TextToSpeech
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
import androidx.compose.runtime.LaunchedEffect
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
import voice.api.VoiceDescriptor
import voice.piper.PiperEngine
import voice.speaker.AndroidConnectivity
import voice.system.SystemTtsEngine

// Reached from the launcher icon's long-press menu. FLAG_SECURE keeps the API key out of screenshots and the
// recents thumbnail; the key field never shows the saved key, only a masked hint.
class SettingsActivity : ComponentActivity() {
    private val keys by lazy { ApiKeyStore(this) }
    private val prefs by lazy { getSharedPreferences(assistant.android.Assistant.PREFS, MODE_PRIVATE) }
    private val voicePrefs by lazy { getSharedPreferences("voice", MODE_PRIVATE) }
    private val store by lazy { VoiceStore(filesDir, VoiceManifest.loadBundled(), AndroidConnectivity(this)) }
    private val systemEngine by lazy { SystemTtsEngine(this) }

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
        var engine by remember { mutableStateOf(voicePrefs.getString("engine", null) ?: SystemTtsEngine.ID) }
        var offset by remember { mutableFloatStateOf(voicePrefs.getInt("sync_offset_ms", 0).toFloat()) }
        Text("Voice", style = MaterialTheme.typography.titleMedium)
        for ((id, label) in listOf(SystemTtsEngine.ID to "Phone voice (Google): natural, no download", PiperEngine.ID to "Piper: fully offline download, more robotic")) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                RadioButton(engine == id, {
                    engine = id
                    voicePrefs.edit().putString("engine", id).apply()
                })
                Text(label)
            }
        }
        if (engine == SystemTtsEngine.ID) SystemVoices() else PiperVoice()
        Text("Lip-sync offset: ${offset.toInt()} ms (raise it if the mouth lags on Bluetooth)", style = MaterialTheme.typography.bodyMedium)
        Slider(offset, {
            offset = it
            voicePrefs.edit().putInt("sync_offset_ms", it.toInt()).apply()
        }, valueRange = -200f..300f)
    }

    @Composable
    private fun SystemVoices() {
        var voices by remember { mutableStateOf<List<VoiceDescriptor>?>(null) }
        var chosen by remember { mutableStateOf(voicePrefs.getString("system_voice_id", null)) }
        LaunchedEffect(Unit) { voices = systemEngine.voices().filter { it.language.startsWith("en") }.take(MAX_VOICES) }
        val list = voices
        when {
            list == null -> Text("Loading the phone's voices…", style = MaterialTheme.typography.bodySmall)
            list.isEmpty() -> Text(
                "No offline English voices found. Install one in Android Settings → Text-to-speech → Google → Install voice data.",
                style = MaterialTheme.typography.bodySmall,
            )
            else -> {
                VoiceRow("Automatic (best available)", chosen == null, null) {
                    chosen = null
                    voicePrefs.edit().remove("system_voice_id").apply()
                }
                for (v in list) {
                    VoiceRow("${v.name} · ${v.language} · ${v.quality.name.lowercase()}", chosen == v.id, v.id) {
                        chosen = v.id
                        voicePrefs.edit().putString("system_voice_id", v.id).apply()
                    }
                }
            }
        }
    }

    @Composable
    private fun VoiceRow(label: String, selected: Boolean, previewId: String?, onSelect: () -> Unit) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            RadioButton(selected, onSelect)
            Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
            if (previewId != null) OutlinedButton({ preview(previewId) }) { Text("▶") }
        }
    }

    @Composable
    private fun PiperVoice() {
        val scope = rememberCoroutineScope()
        val voice = store.manifest.defaultVoice ?: return
        var installed by remember { mutableStateOf(store.isInstalled(voice.id)) }
        var progress by remember { mutableStateOf("") }
        var wifiOnly by remember { mutableStateOf(!voicePrefs.getBoolean("allow_metered_downloads", false)) }
        Text(
            if (installed) "${voice.name} is installed." else "${voice.name} (${voice.archive.download.size / 1_000_000} MB) isn't downloaded; the phone's voice is used until then.",
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
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Download on Wi-Fi only", Modifier.weight(1f))
            Switch(wifiOnly, {
                wifiOnly = it
                voicePrefs.edit().putBoolean("allow_metered_downloads", !it).apply()
            })
        }
    }

    // Plain TextToSpeech for previews: no lip-sync needed on this screen.
    private fun preview(voiceId: String) {
        val tts = previewTts ?: TextToSpeech(this) { status ->
            if (status == TextToSpeech.SUCCESS) pendingPreview?.let { speakPreview(it) }
        }.also { previewTts = it }
        pendingPreview = voiceId
        speakPreview(voiceId, tts)
    }

    private fun speakPreview(voiceId: String, tts: TextToSpeech? = previewTts) {
        val t = tts ?: return
        val v = runCatching { t.voices }.getOrNull()?.firstOrNull { it.name == voiceId } ?: return
        t.voice = v
        t.speak("Hi! I'm Dexter. Do I sound good like this?", TextToSpeech.QUEUE_FLUSH, null, "preview")
        pendingPreview = null
    }

    private var previewTts: TextToSpeech? = null
    private var pendingPreview: String? = null

    override fun onDestroy() {
        previewTts?.shutdown()
        super.onDestroy()
    }

    private fun currentModel(): String =
        prefs.getString("model", null)?.takeIf { it in ClaudeSettings.MODELS } ?: ClaudeSettings.DEFAULT_MODEL
}

private const val MAX_VOICES = 24
