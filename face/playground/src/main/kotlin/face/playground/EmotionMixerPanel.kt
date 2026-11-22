package face.playground

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import face.api.Emotion
import face.core.FaceEngine

// Mix emotions by hand to see blends (e.g. happy + curious) without the Director in the way.
@Composable
internal fun EmotionMixerPanel(engine: FaceEngine) {
    val amounts = remember { mutableStateListOf<Float>().apply { repeat(Emotion.entries.size) { add(0f) } } }
    var arousal by remember { mutableFloatStateOf(0.5f) }
    val buffer = remember { FloatArray(Emotion.entries.size) }

    fun push() {
        for (i in buffer.indices) buffer[i] = amounts[i]
        engine.setEmotionMix(buffer, arousal)
    }

    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Emotion mixer", Modifier.weight(1f), style = MaterialTheme.typography.titleSmall)
            OutlinedButton({ push() }) { Text("Use mix") }
            OutlinedButton({ engine.useDirector() }) { Text("Director") }
        }
        for (e in Emotion.entries) {
            if (e == Emotion.Neutral) continue
            MixSlider(e.name, amounts[e.ordinal]) {
                amounts[e.ordinal] = it
                push()
            }
        }
        MixSlider("arousal", arousal) {
            arousal = it
            push()
        }
    }
}

@Composable
private fun MixSlider(label: String, value: Float, onChange: (Float) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            "$label ${"%.2f".format(value)}",
            Modifier.width(150.dp),
            fontFamily = FontFamily.Monospace,
            style = MaterialTheme.typography.bodySmall,
        )
        Slider(value, onChange, Modifier.weight(1f), valueRange = 0f..1f)
    }
}
