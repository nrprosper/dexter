package face.playground

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import face.core.rig.ChannelGroup

@Composable
internal fun DynamicsPanel(state: PlaygroundState) {
    state.dynamicsRevision.intValue
    val group = state.tunedGroup
    val profile = state.profile(group)
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Dynamics", Modifier.weight(1f), style = MaterialTheme.typography.titleSmall)
            OutlinedButton({ state.resetDynamics() }) { Text("Reset") }
        }
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(ChannelGroup.entries) { g ->
                FilterChip(g == group, { state.tunedGroup = g }, { Text(g.name) })
            }
        }
        DynamicsSlider("f (Hz)", profile.frequency, 0.3f..10f) { state.setProfile(group, profile.copy(frequency = it)) }
        DynamicsSlider("ζ damping", profile.damping, 0f..1.5f) { state.setProfile(group, profile.copy(damping = it)) }
        DynamicsSlider("r response", profile.response, -2f..3f) { state.setProfile(group, profile.copy(response = it)) }
    }
}

@Composable
private fun DynamicsSlider(label: String, value: Float, range: ClosedFloatingPointRange<Float>, onChange: (Float) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            "$label ${"%.2f".format(value)}",
            Modifier.width(150.dp).padding(end = 4.dp),
            fontFamily = FontFamily.Monospace,
            style = MaterialTheme.typography.bodySmall,
        )
        Slider(value, onChange, Modifier.weight(1f), valueRange = range)
    }
}
