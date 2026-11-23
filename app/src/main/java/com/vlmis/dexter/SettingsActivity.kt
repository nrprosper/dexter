package com.vlmis.dexter

import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import assistant.android.ApiKeyStore
import assistant.android.Assistant
import assistant.claude.ClaudeBrain
import assistant.claude.ClaudeSettings
import assistant.claude.KeyCheck
import com.vlmis.dexter.settings.ButtonRow
import com.vlmis.dexter.settings.Dot
import com.vlmis.dexter.settings.FloatingPill
import com.vlmis.dexter.settings.Glass
import com.vlmis.dexter.settings.GlassBackdrop
import com.vlmis.dexter.settings.GlassButton
import com.vlmis.dexter.settings.GlassCard
import com.vlmis.dexter.settings.GlassIcon
import com.vlmis.dexter.settings.GlassIconView
import com.vlmis.dexter.settings.GlassRow
import com.vlmis.dexter.settings.GlassSecretField
import com.vlmis.dexter.settings.GlassSwitch
import com.vlmis.dexter.settings.LargeTitle
import com.vlmis.dexter.settings.Note
import com.vlmis.dexter.settings.RowSeparator
import com.vlmis.dexter.settings.SectionLabel
import face.core.FaceEngine
import face.core.expression.ExpressionLibrary
import face.core.time.Rng
import face.render.AnimatedFace
import kotlinx.coroutines.launch
import voice.api.VoiceDescriptor
import voice.piper.PiperEngine
import voice.piper.model.InstallState
import voice.piper.model.VoiceManifest
import voice.piper.model.VoiceStore
import voice.speaker.AndroidConnectivity
import voice.system.SystemTtsEngine

private enum class Page { Home, Key, Model, Voice, Privacy }

// Reached from the launcher icon's long-press menu. FLAG_SECURE keeps the API key out of screenshots and the
// recents thumbnail; the key is never shown, only a masked hint.
class SettingsActivity : ComponentActivity() {
    private val keys by lazy { ApiKeyStore(this) }
    private val prefs by lazy { getSharedPreferences(Assistant.PREFS, MODE_PRIVATE) }
    private val voicePrefs by lazy { getSharedPreferences("voice", MODE_PRIVATE) }
    private val store by lazy { VoiceStore(filesDir, VoiceManifest.loadBundled(), AndroidConnectivity(this)) }
    private val systemEngine by lazy { SystemTtsEngine(this) }
    private var previewTts: TextToSpeech? = null
    private var pendingPreview: String? = null

    // Bumped whenever a setting changes, so the home rows re-read their values.
    private val revision = mutableIntStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        enableEdgeToEdge()
        setContent {
            var page by remember { mutableStateOf(Page.Home) }
            BackHandler(page != Page.Home) { page = Page.Home }
            Box(Modifier.fillMaxSize()) {
                GlassBackdrop()
                AnimatedContent(
                    targetState = page,
                    transitionSpec = {
                        val forward = targetState != Page.Home
                        (slideInHorizontally(tween(320)) { if (forward) it / 3 else -it / 3 } + fadeIn(tween(240))) togetherWith
                            (slideOutHorizontally(tween(320)) { if (forward) -it / 3 else it / 3 } + fadeOut(tween(200)))
                    },
                    label = "page",
                ) { p ->
                    Column(
                        Modifier.fillMaxSize().statusBarsPadding().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
                    ) {
                        if (p != Page.Home) {
                            Spacer(Modifier.height(8.dp))
                            FloatingPill(onClick = { page = Page.Home }) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    GlassIconView(GlassIcon.Back, Glass.Text, Modifier.size(18.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text("Settings", color = Glass.Text, fontSize = 15.sp)
                                }
                            }
                        }
                        when (p) {
                            Page.Home -> Home { page = it }
                            Page.Key -> KeyPage()
                            Page.Model -> ModelPage()
                            Page.Voice -> VoicePage()
                            Page.Privacy -> PrivacyPage()
                        }
                        Spacer(Modifier.height(120.dp))
                    }
                }
                FloatingPill(
                    modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 16.dp),
                    onClick = { finish() },
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        GlassIconView(GlassIcon.Face, Glass.Accent, Modifier.size(20.dp))
                        Spacer(Modifier.width(10.dp))
                        Text("Talk to Dexter", color = Glass.Text, fontSize = 16.sp, letterSpacing = 0.4.sp)
                    }
                }
            }
        }
    }

    @Composable
    private fun Home(open: (Page) -> Unit) {
        revision.intValue
        LargeTitle("Settings")
        HeroCard()
        SectionLabel("Brain")
        GlassCard {
            val hint = keys.hint
            GlassRow(
                "API key",
                GlassIcon.Key,
                listOf(Color(0xFFFFB347), Color(0xFFFF7A3D)),
                value = if (hint != null) "••••" + hint.takeLast(4) else "Not set",
                chevron = true,
                onClick = { open(Page.Key) },
                trailing = if (hint == null) ({ Dot(Glass.Danger) }) else null,
            )
            RowSeparator()
            GlassRow("Model", GlassIcon.Spark, listOf(Color(0xFFB07CFF), Color(0xFF6A5CFF)), value = modelLabel(currentModel()), chevron = true, onClick = { open(Page.Model) })
        }
        SectionLabel("Voice")
        GlassCard {
            GlassRow("Voice", GlassIcon.Speaker, listOf(Color(0xFF34D399), Color(0xFF0EA37A)), value = voiceLabel(), chevron = true, onClick = { open(Page.Voice) })
        }
        SectionLabel("Privacy")
        GlassCard {
            GlassRow("What leaves the phone", GlassIcon.Shield, listOf(Color(0xFF60A5FA), Color(0xFF2563EB)), chevron = true, onClick = { open(Page.Privacy) })
        }
    }

    @Composable
    private fun HeroCard() {
        val engine = remember { FaceEngine(ExpressionLibrary.loadBundled(), Rng(System.nanoTime())) }
        GlassCard {
            Row(Modifier.padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(84.dp).clip(CircleShape).background(Color.Black)) {
                    AnimatedFace(engine, Modifier.fillMaxSize(), touchable = false)
                }
                Spacer(Modifier.width(18.dp))
                Column {
                    Text("Dexter", color = Glass.Text, fontSize = 22.sp, fontWeight = FontWeight.Light, letterSpacing = 0.8.sp)
                    Text("${modelLabel(currentModel())} · ${voiceLabel()}", color = Glass.Secondary, fontSize = 14.sp, modifier = Modifier.padding(top = 4.dp))
                    Text("Double-tap my face to talk", color = Glass.Accent, fontSize = 13.sp, modifier = Modifier.padding(top = 6.dp))
                }
            }
        }
    }

    @Composable
    private fun KeyPage() {
        val scope = rememberCoroutineScope()
        var entry by remember { mutableStateOf("") }
        var hint by remember { mutableStateOf(keys.hint) }
        var status by remember { mutableStateOf("") }
        var statusColor by remember { mutableStateOf(Glass.Secondary) }
        LargeTitle("API key", "Claude needs your own Anthropic key")
        GlassCard {
            GlassRow(
                if (hint != null) "Saved" else "No key yet",
                GlassIcon.Key,
                listOf(Color(0xFFFFB347), Color(0xFFFF7A3D)),
                detail = hint ?: "Paste one below",
            )
            RowSeparator(18.dp)
            Box(Modifier.padding(16.dp)) {
                GlassSecretField(entry, { entry = it.trim() }, "sk-ant-…")
            }
            ButtonRow {
                GlassButton(
                    "Save",
                    {
                        try {
                            keys.save(entry)
                            hint = keys.hint
                            revision.intValue++
                            status = "Saved and encrypted. Clear your clipboard if you copied it."
                            statusColor = Glass.Accent
                        } catch (e: IllegalArgumentException) {
                            status = e.message ?: "Not saved."
                            statusColor = Glass.Danger
                        } finally {
                            entry = ""
                        }
                    },
                    Modifier.weight(1f),
                    primary = true,
                    enabled = ApiKeyStore.isPlausible(entry),
                )
                GlassButton(
                    "Check",
                    {
                        val key = keys.load()
                        if (key == null) {
                            status = "No key saved."
                        } else {
                            status = "Checking…"
                            statusColor = Glass.Secondary
                            scope.launch {
                                val result = ClaudeBrain({ null }, { ClaudeSettings(model = currentModel()) }).check(key)
                                status = when (result) {
                                    KeyCheck.Ok -> "The key works."
                                    KeyCheck.Rejected -> "Anthropic rejected this key."
                                    KeyCheck.ModelUnavailable -> "The key works, but can't use ${modelLabel(currentModel())}."
                                    KeyCheck.Offline -> "No connection; try again online."
                                    KeyCheck.Error -> "Anthropic returned an error; try again later."
                                }
                                statusColor = if (result == KeyCheck.Ok) Glass.Accent else Glass.Danger
                            }
                        }
                    },
                    Modifier.weight(1f),
                    enabled = hint != null,
                )
                GlassButton(
                    "Remove",
                    {
                        keys.clear()
                        hint = null
                        revision.intValue++
                        status = "Key removed."
                        statusColor = Glass.Secondary
                    },
                    Modifier.weight(1f),
                    enabled = hint != null,
                )
            }
            if (status.isNotEmpty()) Note(status, statusColor)
        }
        SectionLabel("How it's kept")
        GlassCard {
            GlassRow(
                "Encrypted on this phone",
                GlassIcon.Shield,
                listOf(Color(0xFF60A5FA), Color(0xFF2563EB)),
                detail = "AES-256 with a key locked in the phone's secure keystore. Never backed up, never shown again.",
            )
        }
    }

    @Composable
    private fun ModelPage() {
        var model by remember { mutableStateOf(currentModel()) }
        LargeTitle("Model", "Which Claude answers")
        GlassCard {
            val options = listOf(
                "claude-opus-5-5" to "Best answers",
                "claude-sonnet-5-5" to "Fast and smart",
                "claude-haiku-5-5" to "Fastest, cheapest",
            )
            options.forEachIndexed { i, (id, detail) ->
                if (i > 0) RowSeparator(18.dp)
                GlassRow(
                    modelLabel(id),
                    detail = detail,
                    onClick = {
                        model = id
                        prefs.edit().putString("model", id).apply()
                        revision.intValue++
                    },
                    trailing = { if (model == id) GlassIconView(GlassIcon.Check, Glass.Accent, Modifier.size(22.dp)) },
                )
            }
        }
        Note("Haiku replies quickest, which suits talking; Opus thinks hardest. You can switch any time.")
    }

    @Composable
    private fun VoicePage() {
        var engineId by remember { mutableStateOf(voicePrefs.getString("engine", null) ?: SystemTtsEngine.ID) }
        var offset by remember { mutableFloatStateOf(voicePrefs.getInt("sync_offset_ms", 0).toFloat()) }
        LargeTitle("Voice", "How Dexter sounds")
        GlassCard {
            for ((i, pair) in listOf(
                SystemTtsEngine.ID to ("Phone voice" to "Google's voices: natural, no download"),
                PiperEngine.ID to ("Piper" to "Fully offline download, more robotic"),
            ).withIndex()) {
                if (i > 0) RowSeparator(18.dp)
                val (id, text) = pair
                GlassRow(
                    text.first,
                    detail = text.second,
                    onClick = {
                        engineId = id
                        voicePrefs.edit().putString("engine", id).apply()
                        revision.intValue++
                    },
                    trailing = { if (engineId == id) GlassIconView(GlassIcon.Check, Glass.Accent, Modifier.size(22.dp)) },
                )
            }
        }
        if (engineId == SystemTtsEngine.ID) SystemVoices() else PiperVoice()
        SectionLabel("Lip-sync")
        GlassCard {
            GlassRow("Mouth timing", GlassIcon.Wave, listOf(Color(0xFF34D399), Color(0xFF0EA37A)), value = "${offset.toInt()} ms")
            Slider(
                offset,
                {
                    offset = it
                    voicePrefs.edit().putInt("sync_offset_ms", it.toInt()).apply()
                },
                valueRange = -200f..300f,
                modifier = Modifier.padding(horizontal = 18.dp),
                colors = SliderDefaults.colors(thumbColor = Color.White, activeTrackColor = Glass.Accent, inactiveTrackColor = Color.White.copy(alpha = 0.15f)),
            )
            Note("Raise it if the mouth lags behind the voice, for example on Bluetooth headphones.")
        }
    }

    @Composable
    private fun SystemVoices() {
        var voices by remember { mutableStateOf<List<VoiceDescriptor>?>(null) }
        var chosen by remember { mutableStateOf(voicePrefs.getString("system_voice_id", null)) }
        LaunchedEffect(Unit) { voices = systemEngine.voices().filter { it.language.startsWith("en") }.take(MAX_VOICES) }
        SectionLabel("Phone voices")
        GlassCard {
            val list = voices
            when {
                list == null -> Note("Loading the phone's voices…")
                list.isEmpty() -> Note("No offline English voices found. Install one in Android Settings → Text-to-speech → Google → Install voice data.")
                else -> {
                    GlassRow(
                        "Automatic",
                        detail = "The best one installed",
                        onClick = {
                            chosen = null
                            voicePrefs.edit().remove("system_voice_id").apply()
                            revision.intValue++
                        },
                        trailing = { if (chosen == null) GlassIconView(GlassIcon.Check, Glass.Accent, Modifier.size(22.dp)) },
                    )
                    for (v in list) {
                        RowSeparator(18.dp)
                        GlassRow(
                            friendlyVoice(v),
                            detail = "${v.language} · ${v.quality.name.lowercase()} quality",
                            onClick = {
                                chosen = v.id
                                voicePrefs.edit().putString("system_voice_id", v.id).apply()
                                revision.intValue++
                            },
                            trailing = {
                                PlayButton { preview(v.id) }
                                Spacer(Modifier.width(10.dp))
                                Box(Modifier.size(22.dp)) {
                                    if (chosen == v.id) GlassIconView(GlassIcon.Check, Glass.Accent, Modifier.fillMaxSize())
                                }
                            },
                        )
                    }
                }
            }
        }
    }

    @Composable
    private fun PlayButton(onClick: () -> Unit) {
        Box(
            Modifier.size(36.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.1f)).clickable(onClick = onClick),
            contentAlignment = Alignment.Center,
        ) {
            GlassIconView(GlassIcon.Play, Glass.Text, Modifier.size(14.dp))
        }
    }

    @Composable
    private fun PiperVoice() {
        val scope = rememberCoroutineScope()
        val voice = store.manifest.defaultVoice ?: return
        var installed by remember { mutableStateOf(store.isInstalled(voice.id)) }
        var progress by remember { mutableStateOf("") }
        var wifiOnly by remember { mutableStateOf(!voicePrefs.getBoolean("allow_metered_downloads", false)) }
        SectionLabel("Piper voice")
        GlassCard {
            GlassRow(
                voice.name,
                GlassIcon.Download,
                listOf(Color(0xFF94A3B8), Color(0xFF475569)),
                detail = if (installed) "Installed" else "${voice.archive.download.size / 1_000_000} MB download",
            )
            if (!installed) {
                ButtonRow {
                    GlassButton("Download", {
                        store.allowMetered = !wifiOnly
                        scope.launch {
                            try {
                                store.install(voice.id).collect {
                                    progress = when (it) {
                                        is InstallState.Downloading -> "Downloading %.0f%%".format(100f * it.bytes / it.total)
                                        InstallState.Extracting -> "Unpacking…"
                                        InstallState.Installed -> "Installed."
                                    }
                                }
                                installed = true
                            } catch (e: Exception) {
                                progress = "Download stopped: ${e.message}"
                            }
                        }
                    }, Modifier.fillMaxWidth(), primary = true)
                }
            }
            if (progress.isNotEmpty()) Note(progress)
            RowSeparator(18.dp)
            GlassRow("Wi-Fi only", trailing = {
                GlassSwitch(wifiOnly) {
                    wifiOnly = it
                    voicePrefs.edit().putBoolean("allow_metered_downloads", !it).apply()
                }
            })
        }
    }

    @Composable
    private fun PrivacyPage() {
        LargeTitle("Privacy", "What leaves the phone")
        GlassCard {
            GlassRow("Your words", GlassIcon.Brain, listOf(Color(0xFFB07CFF), Color(0xFF6A5CFF)), detail = "When you double-tap and talk, the text of what you said and the recent conversation go to Anthropic to answer.")
            RowSeparator()
            GlassRow("Your voice", GlassIcon.Wave, listOf(Color(0xFF34D399), Color(0xFF0EA37A)), detail = "Turned into text by the phone's recognizer (on the phone where available). No recording is kept.")
            RowSeparator()
            GlassRow("Memory", GlassIcon.Shield, listOf(Color(0xFF60A5FA), Color(0xFF2563EB)), detail = "The conversation stays in memory only and is forgotten after ten quiet minutes.")
            RowSeparator()
            GlassRow("Dexter's voice", GlassIcon.Speaker, listOf(Color(0xFFFFB347), Color(0xFFFF7A3D)), detail = "Made on this phone.")
        }
    }

    private fun currentModel(): String =
        prefs.getString("model", null)?.takeIf { it in ClaudeSettings.MODELS } ?: ClaudeSettings.DEFAULT_MODEL

    private fun modelLabel(id: String): String = when (id) {
        "claude-opus-5-5" -> "Opus 5.5"
        "claude-sonnet-5-5" -> "Sonnet 5.5"
        "claude-haiku-5-5" -> "Haiku 5.5"
        else -> id
    }

    private fun voiceLabel(): String =
        if ((voicePrefs.getString("engine", null) ?: SystemTtsEngine.ID) == PiperEngine.ID) "Piper" else "Phone voice"

    private fun friendlyVoice(v: VoiceDescriptor): String = v.name.removeSuffix("-local").removeSuffix("-network")

    // Plain TextToSpeech for previews: no lip-sync needed on this screen.
    private fun preview(voiceId: String) {
        pendingPreview = voiceId
        val tts = previewTts
        if (tts == null) {
            previewTts = TextToSpeech(this) { status -> if (status == TextToSpeech.SUCCESS) pendingPreview?.let { speakPreview(it) } }
        } else {
            speakPreview(voiceId)
        }
    }

    private fun speakPreview(voiceId: String) {
        val t = previewTts ?: return
        val v = runCatching { t.voices }.getOrNull()?.firstOrNull { it.name == voiceId } ?: return
        t.voice = v
        t.speak("Hi! I'm Dexter. Do I sound good like this?", TextToSpeech.QUEUE_FLUSH, null, "preview")
        pendingPreview = null
    }

    override fun onDestroy() {
        previewTts?.shutdown()
        super.onDestroy()
    }
}

private const val MAX_VOICES = 24
