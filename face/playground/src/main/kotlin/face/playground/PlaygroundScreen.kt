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
import face.render.Accents
import face.render.FaceCanvas
import face.render.FaceStyle

@Composable
internal fun PlaygroundScreen(state: PlaygroundState, onSave: (String) -> String) {
    var fullscreen by rememberSaveable { mutableStateOf(false) }
    val faceGestures = Modifier.pointerInput(Unit) {
        detectTapGestures(
            onTap = { if (fullscreen) state.load(nextExpression(state.selected)) else fullscreen = true },
            onLongPress = { fullscreen = false },
        )
    }
    if (fullscreen) {
        FaceCanvas(state.display, state.style, state.revision, Modifier.fillMaxSize().then(faceGestures))
        return
    }
    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Box(Modifier.fillMaxWidth().weight(0.42f)) {
            FaceCanvas(state.display, state.style, state.revision, Modifier.fillMaxSize().then(faceGestures))
            Text(
                state.selected ?: "custom",
                Modifier.align(Alignment.TopStart).padding(12.dp),
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
                        onClick = { state.style = silhouette.copy(accent = state.style.accent, showBrows = state.style.showBrows) },
                        label = { Text(silhouette.name) },
                    )
                }
                item {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Brows", style = MaterialTheme.typography.labelLarge)
                        Spacer(Modifier.width(6.dp))
                        Switch(state.style.showBrows, { state.style = state.style.copy(showBrows = it) })
                    }
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
                OutlinedButton({ state.load(ExpressionId.Neutral) }) { Text("Neutral") }
                OutlinedTextField(name, { name = it }, Modifier.weight(1f), label = { Text("pose name") }, singleLine = true)
                Button({ status = onSave(name) }, enabled = PoseStore.sanitize(name).isNotEmpty()) { Text("Save") }
            }
            if (status.isNotEmpty()) Text(status, style = MaterialTheme.typography.bodySmall)
        }
        for (group in Channel.entries.groupBy { it.group }) {
            item(key = group.key.name) {
                Text(group.key.name, Modifier.padding(top = 8.dp), style = MaterialTheme.typography.titleSmall)
            }
            items(group.value, key = { it.key }) { channel -> ChannelSlider(state, channel) }
        }
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

private fun nextExpression(current: String?): ExpressionId {
    val index = ExpressionId.entries.indexOfFirst { it.key == current }
    return ExpressionId.entries[(index + 1) % ExpressionId.entries.size]
}
