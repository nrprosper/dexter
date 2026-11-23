package face.playground

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import face.api.SpeechStyle
import face.core.FaceEngine
import face.core.speech.MouthConfig
import face.speech.PcmBlock
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import voice.api.SynthesisSettings
import voice.piper.model.InstallState
import voice.speaker.VoiceStack
import kotlin.math.PI
import kotlin.math.max
import kotlin.math.sin

@Composable
internal fun VoicePanel(voice: VoiceStack, engine: FaceEngine) {
    val scope = rememberCoroutineScope()
    val speakerState by voice.speaker.state.collectAsState()
    var text by rememberSaveable { mutableStateOf(PRESETS.first().second) }
    var voiceId by remember { mutableStateOf(voice.voiceId ?: voice.store.manifest.defaultVoice?.id) }
    var useSystem by remember { mutableStateOf(voice.engine == "system") }
    val stats by voice.speaker.stats.collectAsState()
    var speed by remember { mutableFloatStateOf(1f) }
    var variation by remember { mutableFloatStateOf(0.667f) }
    var style by remember { mutableStateOf(STYLES.first()) }
    var wifiOnly by remember { mutableStateOf(!voice.allowMeteredDownloads) }
    var download by remember { mutableStateOf("") }
    var downloading by remember { mutableStateOf(false) }
    var installed by remember { mutableIntStateOf(0) }
    var offset by remember { mutableFloatStateOf(voice.syncOffsetMillis.toFloat()) }
    var lead by remember { mutableFloatStateOf(voice.leadMillis.toFloat()) }

    LaunchedEffect(useSystem) {
        voice.engine = if (useSystem) "system" else "piper"
    }

    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text("Voice", style = MaterialTheme.typography.titleSmall)
        Text(
            "speaker: ${describe(speakerState)}\nlast: ${stats.sentences} sentences · worst synth ×%.2f real time · underruns ${stats.underruns}"
                .format(stats.worstRealTime),
            style = MaterialTheme.typography.bodySmall,
            fontFamily = FontFamily.Monospace,
        )
        OutlinedTextField(text, { text = it }, Modifier.fillMaxWidth(), label = { Text("say") }, minLines = 2, maxLines = 4)
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(PRESETS) { (name, line) -> FilterChip(text == line, { text = line }, { Text(name) }) }
        }
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(voice.store.manifest.voices, key = { it.id + installed }) { v ->
                val ready = voice.store.isInstalled(v.id)
                FilterChip(
                    selected = !useSystem && voiceId == v.id,
                    onClick = {
                        useSystem = false
                        voice.engine = "piper"
                        voiceId = v.id
                        voice.voiceId = v.id
                        if (!ready && !downloading) {
                            downloading = true
                            scope.launch {
                                try {
                                    voice.store.install(v.id).collect { s ->
                                        download = when (s) {
                                            is InstallState.Downloading -> "%s %.0f%%".format(s.file, 100f * s.bytes / s.total)
                                            InstallState.Extracting -> "unpacking…"
                                            InstallState.Installed -> "installed ${v.id}"
                                        }
                                    }
                                    installed++
                                } catch (e: Exception) {
                                    download = "download stopped: ${e.message}"
                                } finally {
                                    downloading = false
                                }
                            }
                        }
                    },
                    label = { Text(if (ready) v.name else "${v.name} ↓ ${v.archive.download.size / 1_000_000} MB") },
                )
            }
            item { FilterChip(useSystem, { useSystem = true }, { Text("Phone voice (Google)") }) }
        }
        if (download.isNotEmpty()) Text(download, style = MaterialTheme.typography.bodySmall)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Wi-Fi only downloads", style = MaterialTheme.typography.labelLarge)
            Switch(wifiOnly, {
                wifiOnly = it
                voice.allowMeteredDownloads = !it
            })
        }
        Tune("speed", speed, 0.5f..2f) { speed = it }
        Tune("variation", variation, 0f..1.2f) { variation = it }
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(STYLES) { s -> FilterChip(style == s, { style = s }, { Text(s.first) }) }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button({
                val settings = SynthesisSettings(voiceId = voice.voiceId, speed = speed, variation = variation)
                voice.speaker.say(text, settings, style.second)
            }) { Text("Speak") }
            OutlinedButton({ voice.speaker.stop() }) { Text("Stop") }
            OutlinedButton({ voice.speaker.play(flowOf(bursts()), style.second) }) { Text("Bursts") }
            OutlinedButton({ voice.speaker.play(flowOf(syllables()), style.second) }) { Text("Fast") }
        }
        EnvelopeGraph(engine, Modifier.fillMaxWidth())
        Tune("sync offset ms (+ = mouth earlier)", offset, -200f..300f) {
            offset = it
            voice.syncOffsetMillis = it.toInt()
        }
        Tune("lead ms", lead, 0f..120f) {
            lead = it
            voice.leadMillis = it.toInt()
        }
        MouthTuning(engine)
    }
}

@Composable
private fun MouthTuning(engine: FaceEngine) {
    var config by remember { mutableStateOf(engine.mouthConfig) }
    fun update(next: MouthConfig) {
        config = next
        engine.mouthConfig = next
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text("Mouth tuning", Modifier.weight(1f), style = MaterialTheme.typography.titleSmall)
        OutlinedButton({ update(MouthConfig.loadBundled()) }) { Text("Reset") }
    }
    Tune("gate", config.gate, 0f..0.7f) { update(config.copy(gate = it)) }
    Tune("compression", config.compression, 0.2f..1f) { update(config.copy(compression = it)) }
    Tune("gain", config.gain, 0.3f..2.5f) { update(config.copy(gain = it)) }
    Tune("attack s", config.attack, 0.005f..0.1f) { update(config.copy(attack = it)) }
    Tune("release s", config.release, 0.02f..0.3f) { update(config.copy(release = it)) }
    Tune("min closure s", config.minClosure, 0f..0.15f) { update(config.copy(minClosure = it)) }
    Tune("close ratio", config.closeRatio, 0.2f..0.9f) { update(config.copy(closeRatio = it)) }
    Tune("max open", config.maxOpen, 0.3f..1f) { update(config.copy(maxOpen = it)) }
    Tune("beat threshold", config.beat.threshold, 0.1f..1f) { update(config.copy(beat = config.beat.copy(threshold = it))) }
    Tune("spring boost", config.springBoost.base, 1f..3f) { update(config.copy(springBoost = config.springBoost.copy(base = it))) }
}

@Composable
private fun Tune(label: String, value: Float, range: ClosedFloatingPointRange<Float>, onChange: (Float) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text("$label ${"%.3f".format(value)}", Modifier.width(190.dp), style = MaterialTheme.typography.bodySmall)
        Slider(value, onChange, Modifier.weight(1f), valueRange = range)
    }
}

private fun describe(state: voice.speaker.SpeakerState): String = when (state) {
    voice.speaker.SpeakerState.Idle -> "idle"
    is voice.speaker.SpeakerState.Preparing -> "preparing (${state.engineId})"
    is voice.speaker.SpeakerState.Speaking -> "speaking (${state.engineId})"
    is voice.speaker.SpeakerState.Failed -> "failed: ${state.reason}"
}

private const val TEST_RATE = 22_050

// Test signals, generated rather than recorded: tone bursts, and fast syllable-like pulses with an accent every third one.
private fun bursts(): PcmBlock = tone(3.0) { t -> if (t % 0.5 < 0.25) 0.5 else 0.0 }

private fun syllables(): PcmBlock = tone(3.0) { t ->
    val accent = if ((t * 7).toInt() % 3 == 0) 1.0 else 0.45
    0.5 * accent * max(0.0, sin(2 * PI * 7 * t))
}

private fun tone(seconds: Double, envelope: (Double) -> Double): PcmBlock {
    val samples = ShortArray((seconds * TEST_RATE).toInt()) { i ->
        val t = i.toDouble() / TEST_RATE
        (envelope(t) * sin(2 * PI * 180 * t) * 32_767).toInt().toShort()
    }
    return PcmBlock(samples, TEST_RATE)
}

private val PRESETS = listOf(
    "Hello" to "Hi! I'm Dexter. It's really nice to meet you.",
    "Fast" to "Quick question: did you pack the snacks, the charger, and the tiny blue umbrella?",
    "Numbers" to "Your meeting is at 3:30, it costs $12.50, and you're 2nd in line.",
    "Long" to "Okay, here's the plan. First we check the weather. Then, if it isn't raining, we walk to the park. " +
        "If it is, we stay in and I'll tell you a terrible joke instead.",
    "Unknown words" to "My friend Xyzzy says the qwfp is broken.",
)

private val STYLES = listOf(
    "Neutral" to SpeechStyle(),
    "Calm" to SpeechStyle.Calm,
    "Excited" to SpeechStyle.Excited,
    "Serious" to SpeechStyle.Serious,
    "Funny" to SpeechStyle.Funny,
)
