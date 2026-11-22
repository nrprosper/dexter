package face.playground

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import face.api.Activity
import face.api.Emotion
import face.api.FaceController
import face.api.FaceReaction

@Composable
internal fun OrchestratorPanel(face: FaceController) {
    val scope = rememberCoroutineScope()
    val orchestrator = remember(face) { FakeOrchestrator(face, scope) }
    val snapshot by face.snapshot.collectAsState()
    var intensity by remember { mutableFloatStateOf(0.7f) }
    var talking by remember { mutableStateOf(false) }
    var noisy by remember { mutableStateOf(false) }

    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Orchestrator (FaceController only)", Modifier.weight(1f), style = MaterialTheme.typography.titleSmall)
            Button({ orchestrator.runScenario() }) { Text("Run scenario") }
        }
        Text(
            "${snapshot.personality} · ${snapshot.activity} · ${snapshot.emotion} ${"%.1f".format(snapshot.intensity)}" +
                " · mood ${snapshot.mood} · arousal ${"%.1f".format(snapshot.arousal)}" +
                (if (snapshot.speaking) " · speaking" else "") + (if (snapshot.asleep) " · asleep" else ""),
            style = MaterialTheme.typography.bodySmall,
        )
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(Activity.entries) { a ->
                FilterChip(snapshot.activity == a, { orchestrator.activity(a) }, { Text(a.name) })
            }
        }
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(Emotion.entries) { e ->
                FilterChip(snapshot.emotion == e, { orchestrator.emotion(e, intensity) }, { Text(e.name) })
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("intensity ${"%.2f".format(intensity)}", Modifier.width(120.dp), style = MaterialTheme.typography.bodySmall)
            Slider(intensity, { intensity = it }, Modifier.weight(1f), valueRange = 0f..1f)
        }
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(REACTIONS) { (name, reaction) ->
                OutlinedButton({ orchestrator.react(reaction) }) { Text(name) }
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("User talking", style = MaterialTheme.typography.labelLarge)
            Switch(talking, {
                talking = it
                orchestrator.userTalking(it)
            })
            Text("  Noisy LLM", style = MaterialTheme.typography.labelLarge)
            Switch(noisy, {
                noisy = it
                orchestrator.noisyLlm(it)
            })
        }
    }
}

private val REACTIONS = listOf(
    "Surprise" to FaceReaction.Surprise(Emotion.Happy),
    "Delight" to FaceReaction.Delight,
    "Startle" to FaceReaction.Startle,
    "Ack" to FaceReaction.Acknowledge,
    "Oops" to FaceReaction.Oops,
    "Laugh" to FaceReaction.Laugh,
    "Aha" to FaceReaction.Realization,
)
