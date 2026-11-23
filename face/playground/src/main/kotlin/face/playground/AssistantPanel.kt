package face.playground

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import assistant.android.Assistant
import face.api.StageMove
import face.core.FaceEngine

// Talk to the real assistant (Claude + Piper) from the playground: listen, or type instead of speaking.
@Composable
internal fun AssistantPanel(assistant: Assistant, engine: FaceEngine) {
    val o = assistant.orchestrator
    val state by o.state.collectAsState()
    val heard by o.lastHeard.collectAsState()
    val feeling by o.lastFeeling.collectAsState()
    var text by rememberSaveable { mutableStateOf("Tell me something fun about octopuses.") }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text("Assistant (Claude)", style = MaterialTheme.typography.titleSmall)
        val key = assistant.keys.hint ?: "none: add one in Dexter settings (long-press the app icon)"
        Text(
            "state: $state · model: ${assistant.model}\nkey: $key\nheard: $heard\n" +
                "feeling: ${feeling?.let { "${it.emotion} %.2f conf %.2f · ${it.tone}".format(it.intensity, it.confidence) } ?: "-"}",
            fontFamily = FontFamily.Monospace,
            style = MaterialTheme.typography.bodySmall,
        )
        OutlinedTextField(text, { text = it }, Modifier.fillMaxWidth(), label = { Text("type to Dexter") }, minLines = 1, maxLines = 3)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button({ o.ask(text) }) { Text("Ask") }
            OutlinedButton({ o.onDoubleTap() }) { Text("Listen") }
            OutlinedButton({ o.stop() }) { Text("Stop") }
            OutlinedButton({ o.forget() }) { Text("Forget") }
        }
        Text("Stage moves", style = MaterialTheme.typography.labelLarge)
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(StageMove.entries) { m -> FilterChip(false, { engine.stage(m) }, { Text(m.name) }) }
        }
    }
}
