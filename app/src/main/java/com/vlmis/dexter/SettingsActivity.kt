package com.vlmis.dexter

import android.content.Intent
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import assistant.android.ApiKeyStore
import assistant.android.Assistant
import assistant.claude.ClaudeBrain
import assistant.claude.ClaudeSettings
import assistant.core.BrainProvider
import assistant.core.KeyCheck
import assistant.gemini.GeminiBrain
import assistant.gemini.GeminiSettings
import com.vlmis.dexter.settings.ButtonRow
import com.vlmis.dexter.settings.CardGap
import com.vlmis.dexter.settings.Dot
import com.vlmis.dexter.settings.FloatingPill
import com.vlmis.dexter.settings.Glass
import com.vlmis.dexter.settings.GlassButton
import com.vlmis.dexter.settings.GlassCard
import com.vlmis.dexter.settings.GlassIcon
import com.vlmis.dexter.settings.GlassIconView
import com.vlmis.dexter.settings.GlassRow
import com.vlmis.dexter.settings.GlassSecretField
import com.vlmis.dexter.settings.GlassSlider
import com.vlmis.dexter.settings.GlassSwitch
import com.vlmis.dexter.settings.LargeTitle
import com.vlmis.dexter.settings.Note
import com.vlmis.dexter.settings.PageHeader
import com.vlmis.dexter.settings.RowSeparator
import com.vlmis.dexter.settings.SectionLabel
import face.core.FaceEngine
import face.core.expression.ExpressionLibrary
import face.core.time.Rng
import face.render.AnimatedFace
import kotlinx.coroutines.launch
import voice.api.VoiceDescriptor
import face.speech.AudioTrackSink
import face.speech.PcmBlock
import face.speech.PcmPlayer
import kotlinx.coroutines.Job
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.map
import voice.api.SynthesisSettings
import voice.api.TtsInput
import voice.kokoro.KokoroEngine
import voice.kokoro.KokoroVoices
import voice.piper.PiperEngine
import voice.piper.model.InstallState
import voice.piper.model.VoiceManifest
import voice.piper.model.VoiceStore
import voice.speaker.AndroidConnectivity
import voice.system.SystemTtsEngine

private enum class Page { Home, Brain, Key, Model, Voice, Privacy }

// Reached from the launcher icon's long-press menu. FLAG_SECURE keeps the API key out of screenshots and the
// recents thumbnail; the key is never shown, only a masked hint.
class SettingsActivity : ComponentActivity() {
    private val claudeKeys by lazy { ApiKeyStore(this, BrainProvider.Claude) }
    private val geminiKeys by lazy { ApiKeyStore(this, BrainProvider.Gemini) }
    private val keys: ApiKeyStore get() = if (currentProvider() == BrainProvider.Gemini) geminiKeys else claudeKeys
    private val prefs by lazy { getSharedPreferences(Assistant.PREFS, MODE_PRIVATE) }
    private val voicePrefs by lazy { getSharedPreferences("voice", MODE_PRIVATE) }
    private val store by lazy { VoiceStore(filesDir, VoiceManifest.loadBundled(), AndroidConnectivity(this)) }
    private val systemEngine by lazy { SystemTtsEngine(this) }
    private val kokoro by lazy { KokoroEngine(VoiceStore(filesDir, KokoroEngine.manifest(), AndroidConnectivity(this))) }
    private val previewPlayer by lazy { PcmPlayer({ AudioTrackSink(this) }) }
    private val uiScope = MainScope()
    private var previewJob: Job? = null
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
            Box(Modifier.fillMaxSize().background(Glass.Background)) {
                AnimatedContent(
                    targetState = page,
                    transitionSpec = {
                        val forward = targetState != Page.Home
                        (slideInHorizontally(tween(300)) { if (forward) it / 3 else -it / 3 } + fadeIn(tween(220))) togetherWith
                            (slideOutHorizontally(tween(300)) { if (forward) -it / 3 else it / 3 } + fadeOut(tween(180)))
                    },
                    label = "page",
                ) { p ->
                    Column(
                        Modifier.fillMaxSize().statusBarsPadding().verticalScroll(rememberScrollState()).padding(horizontal = Glass.Margin),
                    ) {
                        when (p) {
                            Page.Home -> Home { page = it }
                            Page.Brain -> {
                                PageHeader("Brain") { page = Page.Home }
                                BrainPage()
                            }
                            Page.Key -> {
                                PageHeader("API key") { page = Page.Home }
                                KeyPage()
                            }
                            Page.Model -> {
                                PageHeader("Model") { page = Page.Home }
                                ModelPage()
                            }
                            Page.Voice -> {
                                PageHeader("Voice") { page = Page.Home }
                                VoicePage()
                            }
                            Page.Privacy -> {
                                PageHeader("Privacy") { page = Page.Home }
                                PrivacyPage()
                            }
                        }
                        Spacer(Modifier.height(110.dp))
                    }
                }
                FloatingPill(
                    modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().navigationBarsPadding()
                        .padding(start = 31.dp, end = 31.dp, bottom = 14.dp),
                    onClick = {
                        startActivity(
                            Intent(this@SettingsActivity, MainActivity::class.java)
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                                .putExtra(MainActivity.EXTRA_LISTEN, true),
                        )
                        finish()
                    },
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        GlassIconView(GlassIcon.Face, Glass.Text, Modifier.size(22.dp))
                        Spacer(Modifier.width(14.dp))
                        Text("Talk to Dexter", color = Glass.Secondary, fontSize = 16.sp, letterSpacing = 0.4.sp)
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
        CardGap()
        GlassCard {
            val hint = keys.hint
            GlassRow("Brain", GlassIcon.Brain, Color(0xFFE5484D), value = currentProvider().label, chevron = true, onClick = { open(Page.Brain) })
            RowSeparator()
            GlassRow(
                "API key",
                GlassIcon.Key,
                Color(0xFFFF8A1F),
                value = if (hint != null) "Saved" else "Not set",
                chevron = true,
                onClick = { open(Page.Key) },
                trailing = if (hint == null) ({ Dot(Glass.Danger) }) else null,
            )
            RowSeparator()
            GlassRow("Model", GlassIcon.Spark, Color(0xFF7C5CFF), value = modelLabel(currentModel()), chevron = true, onClick = { open(Page.Model) })
        }
        CardGap()
        GlassCard {
            GlassRow("Voice", GlassIcon.Speaker, Color(0xFF1FBF5B), value = voiceLabel(), chevron = true, onClick = { open(Page.Voice) })
        }
        CardGap()
        GlassCard {
            GlassRow("Privacy", GlassIcon.Shield, Color(0xFF2F7BFF), chevron = true, onClick = { open(Page.Privacy) })
        }
    }

    // Like the phone's account card: a 61 dp round avatar (Dexter, scaled to fit whole) and two lines of text.
    @Composable
    private fun HeroCard() {
        val engine = remember { FaceEngine(ExpressionLibrary.loadBundled(), Rng(System.nanoTime())) }
        GlassCard {
            Row(Modifier.fillMaxWidth().height(87.dp).padding(horizontal = Glass.Margin), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(61.dp).clip(CircleShape).background(Color(0xFF0B1620)), contentAlignment = Alignment.Center) {
                    AnimatedFace(
                        engine,
                        Modifier.size(61.dp).graphicsLayer {
                            scaleX = AVATAR_SCALE
                            scaleY = AVATAR_SCALE
                        },
                        touchable = false,
                    )
                }
                Spacer(Modifier.width(17.dp))
                Column(Modifier.weight(1f)) {
                    Text("Dexter", color = Glass.Text, fontSize = 17.sp, letterSpacing = 0.3.sp)
                    Text("${currentProvider().label} ${modelLabel(currentModel())} · ${voiceLabel()}", color = Glass.Secondary, fontSize = 14.sp, modifier = Modifier.padding(top = 3.dp))
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
        GlassCard {
            GlassRow(if (hint != null) "Saved key" else "No key yet", value = hint ?: "Paste one below")
            RowSeparator(withIcon = false)
            Box(Modifier.padding(horizontal = Glass.Margin, vertical = 12.dp)) {
                GlassSecretField(entry, { entry = it.trim() }, if (currentProvider() == BrainProvider.Gemini) "Gemini API key" else "sk-ant-…")
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
                            statusColor = Glass.Secondary
                        } catch (e: IllegalArgumentException) {
                            status = e.message ?: "Not saved."
                            statusColor = Glass.Danger
                        } finally {
                            entry = ""
                        }
                    },
                    Modifier.weight(1f),
                    primary = true,
                    enabled = entry.isNotBlank(),
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
                                val provider = currentProvider()
                                val result = if (provider == BrainProvider.Gemini) {
                                    GeminiBrain({ null }, { GeminiSettings(model = currentModel()) }).check(key)
                                } else {
                                    ClaudeBrain({ null }, { ClaudeSettings(model = currentModel()) }).check(key)
                                }
                                status = when (result) {
                                    KeyCheck.Ok -> "The key works."
                                    KeyCheck.Rejected -> "${if (provider == BrainProvider.Gemini) "Google" else "Anthropic"} rejected this key."
                                    KeyCheck.ModelUnavailable -> "The key works, but can't use ${modelLabel(currentModel())}."
                                    KeyCheck.Offline -> "No connection; try again online."
                                    KeyCheck.Error -> "The service returned an error; try again later."
                                }
                                statusColor = if (result == KeyCheck.Ok) Glass.Secondary else Glass.Danger
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
        if (currentProvider() == BrainProvider.Gemini) {
            Note("Get a free key at aistudio.google.com → Get API key.")
        }
        Note("Your key is encrypted with a key locked in this phone's secure keystore. It's never backed up and never shown again.")
    }

    @Composable
    private fun BrainPage() {
        var provider by remember { mutableStateOf(currentProvider()) }
        GlassCard {
            listOf(
                Triple(BrainProvider.Claude, "Claude", "Anthropic · needs paid API credits"),
                Triple(BrainProvider.Gemini, "Gemini", "Google · free tier available"),
            ).forEachIndexed { i, (p, title, detail) ->
                if (i > 0) RowSeparator(withIcon = false)
                GlassRow(
                    title,
                    detail = detail,
                    value = if (keysFor(p).hasKey) "Key saved" else "No key",
                    onClick = {
                        provider = p
                        prefs.edit().putString(Assistant.PROVIDER, p.id).apply()
                        revision.intValue++
                    },
                    trailing = { Check(provider == p) },
                )
            }
        }
        if (provider == BrainProvider.Gemini) {
            Note(
                "On Gemini's free tier, Google may use what you say and Dexter's replies to improve its products, and human " +
                    "reviewers may read them. Don't tell Dexter anything sensitive or personal while using a free key.",
                Glass.Danger,
            )
        }
    }

    @Composable
    private fun ModelPage() {
        var model by remember { mutableStateOf(currentModel()) }
        val gemini = currentProvider() == BrainProvider.Gemini
        GlassCard {
            val options = if (gemini) {
                listOf(
                    "gemini-4-hydrogen" to "Fast, everyday (free tier)",
                    "gemini-3.8-flash" to "Smarter Flash (free tier)",
                    "gemini-3.5-flash-lite" to "Fastest (free tier)",
                )
            } else {
                listOf(
                    "claude-opus-5-5" to "Best answers",
                    "claude-sonnet-5-5" to "Fast and smart",
                    "claude-haiku-5-5" to "Fastest, cheapest",
                )
            }
            options.forEachIndexed { i, (id, detail) ->
                if (i > 0) RowSeparator(withIcon = false)
                GlassRow(
                    modelLabel(id),
                    detail = detail,
                    onClick = {
                        model = id
                        prefs.edit().putString(if (gemini) Assistant.GEMINI_MODEL else Assistant.MODEL, id).apply()
                        revision.intValue++
                    },
                    trailing = { Check(model == id) },
                )
            }
        }
        Note(if (gemini) "Hydrogen is a good default for talking." else "Haiku replies quickest, which suits talking; Opus thinks hardest.")
    }

    @Composable
    private fun VoicePage() {
        var engineId by remember { mutableStateOf(voicePrefs.getString("engine", null) ?: SystemTtsEngine.ID) }
        var offset by remember { mutableFloatStateOf(voicePrefs.getInt("sync_offset_ms", 0).toFloat()) }
        GlassCard {
            listOf(
                Triple(SystemTtsEngine.ID, "Phone voice", "Google's voices, no download"),
                Triple(KokoroEngine.ID, "Kokoro", "Most natural, fully offline · 132 MB download"),
                Triple(PiperEngine.ID, "Piper", "Fully offline, more robotic"),
            ).forEachIndexed { i, (id, title, detail) ->
                if (i > 0) RowSeparator(withIcon = false)
                GlassRow(
                    title,
                    detail = detail,
                    onClick = {
                        engineId = id
                        voicePrefs.edit().putString("engine", id).apply()
                        revision.intValue++
                    },
                    trailing = { Check(engineId == id) },
                )
            }
        }
        when (engineId) {
            SystemTtsEngine.ID -> SystemVoices()
            KokoroEngine.ID -> KokoroVoice()
            else -> PiperVoice()
        }
        SectionLabel("Lip-sync")
        GlassCard {
            GlassRow("Mouth timing", value = "${offset.toInt()} ms")
            Box(Modifier.padding(start = Glass.Margin, end = Glass.Margin, bottom = 14.dp)) {
                GlassSlider(offset, -200f..300f, {
                    offset = it
                    voicePrefs.edit().putInt("sync_offset_ms", it.toInt()).apply()
                })
            }
        }
        Note("Raise it if the mouth lags behind the voice, for example on Bluetooth headphones.")
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
                        trailing = { Check(chosen == null) },
                    )
                    for (v in list) {
                        RowSeparator(withIcon = false)
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
                                Spacer(Modifier.width(12.dp))
                                Check(chosen == v.id)
                            },
                        )
                    }
                }
            }
        }
    }

    @Composable
    private fun Check(on: Boolean) {
        Box(Modifier.size(20.dp)) {
            if (on) GlassIconView(GlassIcon.Check, Glass.Accent, Modifier.fillMaxSize())
        }
    }

    @Composable
    private fun PlayButton(onClick: () -> Unit) {
        Box(
            Modifier.size(32.dp).clip(CircleShape).background(Color(0xFF2C2C2C)).clickable(onClick = onClick),
            contentAlignment = Alignment.Center,
        ) {
            GlassIconView(GlassIcon.Play, Glass.Text, Modifier.size(13.dp))
        }
    }

    @Composable
    private fun KokoroVoice() {
        val voice = kokoro.store.manifest.defaultVoice ?: return
        var installed by remember { mutableStateOf(kokoro.installed) }
        var progress by remember { mutableStateOf("") }
        var downloading by remember { mutableStateOf(false) }
        var wifiOnly by remember { mutableStateOf(!voicePrefs.getBoolean("allow_metered_downloads", false)) }
        var chosen by remember { mutableStateOf(voicePrefs.getString("kokoro_voice_id", null) ?: KokoroVoices.default.id) }
        SectionLabel("Kokoro")
        GlassCard {
            GlassRow(
                voice.name,
                detail = if (installed) "Installed · works offline" else "Downloads once (${voice.archive.download.size / 1_000_000} MB), then works offline",
            )
            if (!installed) {
                ButtonRow {
                    GlassButton(if (downloading) "Downloading…" else "Download", {
                        downloading = true
                        kokoro.store.allowMetered = !wifiOnly
                        uiScope.launch {
                            try {
                                kokoro.store.install(voice.id).collect {
                                    progress = when (it) {
                                        is InstallState.Downloading -> "Downloading %.0f%% of %d MB".format(100f * it.bytes / it.total, it.total / 1_000_000)
                                        InstallState.Extracting -> "Unpacking…"
                                        InstallState.Installed -> "Installed."
                                    }
                                }
                                installed = true
                                revision.intValue++
                            } catch (e: Exception) {
                                progress = "Download paused: ${e.message}. Tap Download to resume."
                            } finally {
                                downloading = false
                            }
                        }
                    }, Modifier.fillMaxWidth(), primary = true, enabled = !downloading)
                }
            }
            if (progress.isNotEmpty()) Note(progress)
            RowSeparator(withIcon = false)
            GlassRow("Wi-Fi only", trailing = {
                GlassSwitch(wifiOnly) {
                    wifiOnly = it
                    voicePrefs.edit().putBoolean("allow_metered_downloads", !it).apply()
                }
            })
            if (installed) {
                RowSeparator(withIcon = false)
                GlassRow("Remove download", detail = "Frees the space Kokoro uses; you can download it again later", onClick = {
                    previewPlayer.stop()
                    kokoro.store.delete(voice.id)
                    installed = false
                    progress = ""
                    revision.intValue++
                })
            }
        }
        if (installed) {
            SectionLabel("Kokoro voices")
            GlassCard {
                KokoroVoices.english.forEachIndexed { i, speaker ->
                    if (i > 0) RowSeparator(withIcon = false)
                    GlassRow(
                        speaker.label,
                        onClick = {
                            chosen = speaker.id
                            voicePrefs.edit().putString("kokoro_voice_id", speaker.id).apply()
                        },
                        trailing = {
                            PlayButton { previewKokoro(speaker.id) }
                            Spacer(Modifier.width(12.dp))
                            Check(chosen == speaker.id)
                        },
                    )
                }
            }
            Note("The first preview takes a few seconds while the voice loads.")
        }
    }

    // Plays a sample through our own player (Kokoro isn't a system voice).
    private fun previewKokoro(voiceId: String) {
        previewJob?.cancel()
        previewPlayer.stop()
        previewJob = uiScope.launch {
            runCatching {
                val blocks = kokoro.synthesize(TtsInput.Text(PREVIEW_LINE), SynthesisSettings(voiceId = voiceId))
                    .map { PcmBlock(it.samples, it.sampleRate) }
                previewPlayer.play(blocks)
            }
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
            GlassRow(voice.name, value = if (installed) "Installed" else "${voice.archive.download.size / 1_000_000} MB")
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
            RowSeparator(withIcon = false)
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
        GlassCard {
            GlassRow(
                "Your words",
                detail = if (currentProvider() == BrainProvider.Gemini) {
                    "When you talk, the text of what you said and the recent conversation go to Google to answer. On the free tier " +
                        "Google may use them to improve its products and human reviewers may read them."
                } else {
                    "When you double-tap and talk, the text of what you said and the recent conversation go to Anthropic to answer."
                },
            )
            RowSeparator(withIcon = false)
            GlassRow("Your voice", detail = "Turned into text by the phone's recognizer, on the phone where available. No recording is kept.")
            RowSeparator(withIcon = false)
            GlassRow("Memory", detail = "The conversation stays in memory only and is forgotten after ten quiet minutes.")
            RowSeparator(withIcon = false)
            GlassRow("Dexter's voice", detail = "Made on this phone.")
        }
    }

    private fun currentProvider(): BrainProvider = BrainProvider.of(prefs.getString(Assistant.PROVIDER, null))

    private fun keysFor(p: BrainProvider): ApiKeyStore = if (p == BrainProvider.Gemini) geminiKeys else claudeKeys

    private fun currentModel(): String = if (currentProvider() == BrainProvider.Gemini) {
        prefs.getString(Assistant.GEMINI_MODEL, null)?.takeIf { it in GeminiSettings.MODELS } ?: GeminiSettings.DEFAULT_MODEL
    } else {
        prefs.getString(Assistant.MODEL, null)?.takeIf { it in ClaudeSettings.MODELS } ?: ClaudeSettings.DEFAULT_MODEL
    }

    private fun modelLabel(id: String): String = when (id) {
        "claude-opus-5-5" -> "Opus 5.5"
        "claude-sonnet-5-5" -> "Sonnet 5.5"
        "claude-haiku-5-5" -> "Haiku 5.5"
        "gemini-4-hydrogen" -> "4 Hydrogen"
        "gemini-3.8-flash" -> "3.8 Flash"
        "gemini-3.5-flash-lite" -> "3.5 Flash-Lite"
        else -> id
    }

    private fun voiceLabel(): String = when (voicePrefs.getString("engine", null)) {
        PiperEngine.ID -> "Piper"
        KokoroEngine.ID -> "Kokoro"
        else -> "Phone voice"
    }

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
        t.speak(PREVIEW_LINE, TextToSpeech.QUEUE_FLUSH, null, "preview")
        pendingPreview = null
    }

    override fun onDestroy() {
        previewPlayer.stop()
        uiScope.cancel()
        previewTts?.shutdown()
        super.onDestroy()
    }
}

private const val MAX_VOICES = 24
private const val PREVIEW_LINE = "Hi! I'm Dexter. Do I sound good like this?"
private const val AVATAR_SCALE = 0.62f
