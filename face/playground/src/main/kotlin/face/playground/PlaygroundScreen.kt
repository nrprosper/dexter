package face.playground

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import face.core.expression.ExpressionId
import face.core.rig.Channel
import face.core.time.Rng
import kotlinx.coroutines.delay
import face.render.Accents
import face.render.Backdrop
import face.render.AnimatedFace
import face.render.FaceStyle
import face.render.FrameStats
import face.render.MouthStyle

@Composable
internal fun PlaygroundScreen(state: PlaygroundState, onSave: (String) -> String) {
    var fullscreen by rememberSaveable { mutableStateOf(false) }
    val faceGestures = Modifier.pointerInput(Unit) {
        detectTapGestures(
            onTap = {
                state.poke()
                if (fullscreen) state.load(nextExpression(state.selected)) else fullscreen = true
            },
            onLongPress = { fullscreen = false },
        )
    }
    LaunchedEffect(state.spamming) {
        if (!state.spamming) return@LaunchedEffect
        val rng = Rng(System.nanoTime())
        while (true) {
            delay((rng.nextFloat(SPAM_MIN_SECONDS, SPAM_MAX_SECONDS) * 1000f).toLong())
            state.randomTarget(rng)
        }
    }
    if (fullscreen) {
        AnimatedFace(state.engine, Modifier.fillMaxSize().then(faceGestures), state.style, state.stats, if (state.fastTime) FAST_TIME else 1)
        return
    }
    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Box(Modifier.fillMaxWidth().weight(0.42f)) {
            AnimatedFace(state.engine, Modifier.fillMaxSize().then(faceGestures), state.style, state.stats, if (state.fastTime) FAST_TIME else 1)
            Text(
                state.selected ?: "custom",
                Modifier.align(Alignment.TopStart).padding(12.dp),
                color = Color.White.copy(alpha = 0.5f),
                style = MaterialTheme.typography.labelMedium,
            )
            FrameOverlay(state.stats, Modifier.align(Alignment.TopEnd).padding(12.dp))
            Text(
                state.vigilance.name + if (state.fastTime) " · ×$FAST_TIME" else "",
                Modifier.align(Alignment.BottomStart).padding(12.dp),
                color = Color.White.copy(alpha = 0.5f),
                style = MaterialTheme.typography.labelMedium,
            )
        }
        Controls(state, onSave, Modifier.weight(0.58f))
    }
}

@Composable
private fun Controls(state: PlaygroundState, onSave: (String) -> String, modifier: Modifier) {
    var name by rememberSaveable { mutableStateOf("") }
    var status by remember { mutableStateOf("") }
    LazyColumn(modifier.fillMaxWidth(), contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        item {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                items(FaceStyle.Silhouettes) { silhouette ->
                    FilterChip(
                        selected = state.style.name == silhouette.name,
                        onClick = {
                            state.style = silhouette.copy(
                                accent = state.style.accent,
                                showBrows = state.style.showBrows,
                                mouthStyle = state.style.mouthStyle,
                                backdrop = state.style.backdrop,
                            )
                        },
                        label = { Text(silhouette.name) },
                    )
                }
            }
        }
        item {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Toggle("Brows", state.style.showBrows) { state.style = state.style.copy(showBrows = it) }
                Toggle("Filled", state.style.mouthStyle == MouthStyle.Filled) {
                    state.style = state.style.copy(mouthStyle = if (it) MouthStyle.Filled else MouthStyle.Stroke)
                }
                Toggle("Glow", state.style.backdrop == Backdrop.Glow) {
                    state.style = state.style.copy(backdrop = if (it) Backdrop.Glow else Backdrop.Dark)
                }
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                for (accent in Accents.Candidates) {
                    val selected = state.style.accent == accent
                    Box(
                        Modifier.size(32.dp)
                            .background(accent, CircleShape)
                            .border(if (selected) 3.dp else 0.dp, Color.White, CircleShape)
                            .clickable { state.style = state.style.copy(accent = accent) },
                    )
                }
            }
        }
        item {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(ExpressionId.entries) { id ->
                    FilterChip(state.selected == id.key, { state.load(id) }, { Text(id.name) })
                }
                items(state.savedPoses, key = { "saved-${it.id}" }) { saved ->
                    FilterChip(state.selected == saved.id, { state.load(saved) }, { Text("★ ${saved.id}") })
                }
            }
        }
        item {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton({ state.center() }) { Text("Center") }
                OutlinedTextField(name, { name = it }, Modifier.weight(1f), label = { Text("pose name") }, singleLine = true)
                Button({ status = onSave(name) }, enabled = PoseStore.sanitize(name).isNotEmpty()) { Text("Save") }
            }
            if (status.isNotEmpty()) Text(status, style = MaterialTheme.typography.bodySmall)
        }
        item {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Toggle("Life", state.lifeOn) { state.toggleLife(it) }
                Toggle("×$FAST_TIME", state.fastTime) { state.fastTime = it }
                OutlinedButton({ state.dozeOff() }) { Text("Doze") }
            }
        }
        item {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Toggle("Spam random targets", state.spamming) { state.spamming = it }
            }
        }
        item { BehaviorLog(state.log) }
        item { DynamicsPanel(state) }
        for (group in Channel.entries.groupBy { it.group }) {
            item(key = group.key.name) {
                Text(group.key.name, Modifier.padding(top = 8.dp), style = MaterialTheme.typography.titleSmall)
            }
            items(group.value, key = { it.key }) { channel -> ChannelSlider(state, channel) }
        }
    }
}

@Composable
private fun Toggle(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.labelLarge)
        Spacer(Modifier.width(4.dp))
        Switch(checked, onChange)
    }
}

@Composable
private fun ChannelSlider(state: PlaygroundState, channel: Channel) {
    val value = state.values[channel.ordinal]
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            "${channel.key} ${"%+.2f".format(value)}",
            Modifier.width(170.dp).clickable { state.set(channel, channel.rest) },
            fontFamily = FontFamily.Monospace,
            style = MaterialTheme.typography.bodySmall,
        )
        Slider(value, { state.set(channel, it) }, Modifier.weight(1f), valueRange = channel.min..channel.max)
    }
}

// Polls the frame stats twice a second, so the overlay itself never recomposes per frame.
@Composable
private fun FrameOverlay(stats: FrameStats, modifier: Modifier) {
    var text by remember { mutableStateOf("") }
    LaunchedEffect(stats) {
        var lastFrames = stats.frames
        var lastNanos = System.nanoTime()
        while (true) {
            delay(OVERLAY_PERIOD_MILLIS)
            val now = System.nanoTime()
            val fps = (stats.frames - lastFrames) * 1e9f / (now - lastNanos)
            text = "%.0f fps · worst %.1f ms".format(fps, stats.takeWorstFrameMillis())
            lastFrames = stats.frames
            lastNanos = now
        }
    }
    Text(text, modifier, color = Color.White.copy(alpha = 0.6f), fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.labelSmall)
}

@Composable
private fun BehaviorLog(log: List<String>) {
    Column {
        Text("Behaviour log", style = MaterialTheme.typography.titleSmall)
        for (line in log.take(LOG_LINES)) {
            Text(line, fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall)
        }
    }
}

private const val FAST_TIME = 10
private const val LOG_LINES = 12
private const val SPAM_MIN_SECONDS = 0.12f
private const val SPAM_MAX_SECONDS = 0.45f
private const val OVERLAY_PERIOD_MILLIS = 500L

private fun nextExpression(current: String?): ExpressionId {
    val index = ExpressionId.entries.indexOfFirst { it.key == current }
    return ExpressionId.entries[(index + 1) % ExpressionId.entries.size]
}
