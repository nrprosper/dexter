package face.playground

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import face.api.DayPart
import face.api.DeviceState
import face.api.FaceController
import face.api.FaceReaction
import face.api.Heat

// Fakes the phone events PhoneSenses would send, so each reaction can be tried on demand.
@Composable
internal fun PhoneEventsPanel(face: FaceController) {
    var device by remember { mutableStateOf(DeviceState()) }
    var volume by remember { mutableFloatStateOf(0.5f) }
    var tilt by remember { mutableFloatStateOf(0f) }

    fun update(next: DeviceState) {
        device = next
        face.setDeviceState(next)
    }

    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text("Phone events (simulated; the real ones work too)", style = MaterialTheme.typography.titleSmall)
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(EVENTS) { (name, reaction) ->
                OutlinedButton({ face.react(reaction) }) { Text(name) }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(device.batteryLow, { update(device.copy(batteryLow = !device.batteryLow)) }, { Text("Low battery") })
            FilterChip(device.charging, { update(device.copy(charging = !device.charging)) }, { Text("Charging") })
            FilterChip(device.faceDown, { update(device.copy(faceDown = !device.faceDown)) }, { Text("Face down") })
        }
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            item { FilterChip(device.handOver, { update(device.copy(handOver = !device.handOver)) }, { Text("Hand over") }) }
            item { FilterChip(device.inPocket, { update(device.copy(inPocket = !device.inPocket)) }, { Text("Pocket") }) }
            item { FilterChip(device.dark, { update(device.copy(dark = !device.dark)) }, { Text("Dark") }) }
            item { FilterChip(device.batterySaver, { update(device.copy(batterySaver = !device.batterySaver)) }, { Text("Saver") }) }
        }
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(Heat.entries) { h -> FilterChip(device.heat == h, { update(device.copy(heat = h)) }, { Text("Heat ${h.name}") }) }
        }
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(DayPart.entries) { p -> FilterChip(device.dayPart == p, { update(device.copy(dayPart = p)) }, { Text(p.name) }) }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("tilt ${"%.2f".format(tilt)}", Modifier.width(110.dp), style = MaterialTheme.typography.bodySmall)
            Slider(tilt, {
                tilt = it
                face.setTilt(it, 0f)
            }, Modifier.weight(1f), valueRange = -1f..1f)
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("volume ${"%.2f".format(volume)}", Modifier.width(110.dp), style = MaterialTheme.typography.bodySmall)
            Slider(volume, {
                volume = it
                face.showVolume(it)
            }, Modifier.weight(1f), valueRange = 0f..1f)
        }
    }
}

private val EVENTS = listOf(
    "Plug in" to FaceReaction.PluggedIn,
    "Unplug" to FaceReaction.Unplugged,
    "Full" to FaceReaction.BatteryFull,
    "Headphones" to FaceReaction.HeadphonesIn,
    "Shake" to FaceReaction.Shaken,
    "Too bright" to FaceReaction.TooBright,
    "Screenshot" to FaceReaction.Screenshot,
    "Welcome back" to FaceReaction.WelcomeBack(),
    "Back (long)" to FaceReaction.WelcomeBack(longAbsence = true),
    "Morning" to FaceReaction.GoodMorning,
    "Late night" to FaceReaction.LateNight,
)
