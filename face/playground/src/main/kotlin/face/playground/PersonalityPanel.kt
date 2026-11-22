package face.playground

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import face.api.FaceController
import face.core.personality.PersonalityCodec
import face.core.personality.PersonalityPresets
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// Presets live as editable JSON in the app's external files; the active one is watched and hot-reloaded.
@Composable
internal fun PersonalityPanel(face: FaceController) {
    val context = LocalContext.current
    var selected by rememberSaveable { mutableStateOf(PersonalityPresets.ids.first()) }
    var status by remember { mutableStateOf("") }

    LaunchedEffect(selected) {
        val file = withContext(Dispatchers.IO) { presetFile(context, selected) }
        var seen = 0L
        while (true) {
            val modified = withContext(Dispatchers.IO) { file.lastModified() }
            if (modified != seen) {
                seen = modified
                status = withContext(Dispatchers.IO) {
                    runCatching { PersonalityCodec.decode(file.readText()) }
                        .map {
                            face.setPersonality(it)
                            "${it.name} loaded ${TIME.format(Date())}"
                        }
                        .getOrElse { "${file.name}: ${it.message}" }
                }
            }
            delay(WATCH_MILLIS)
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text("Personality", style = MaterialTheme.typography.titleSmall)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            for (id in PersonalityPresets.ids) {
                FilterChip(selected == id, { selected = id }, { Text(id.replaceFirstChar { it.uppercase() }) })
            }
        }
        if (status.isNotEmpty()) Text(status, style = MaterialTheme.typography.bodySmall)
    }
}

private fun presetFile(context: Context, id: String): File {
    val dir = context.getExternalFilesDir(DIR) ?: File(context.filesDir, DIR).apply { mkdirs() }
    val file = File(dir, "$id.json")
    if (!file.exists()) file.writeText(PersonalityPresets.text(id))
    return file
}

private const val DIR = "personality"
private const val WATCH_MILLIS = 1_000L
private val TIME = SimpleDateFormat("HH:mm:ss", Locale.US)
