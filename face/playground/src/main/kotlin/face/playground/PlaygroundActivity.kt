package face.playground

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import face.core.expression.ExpressionLibrary

public class PlaygroundActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val store = PoseStore(this)
        setContent {
            val state = remember { PlaygroundState(ExpressionLibrary.loadBundled(), store.loadAll()) }
            MaterialTheme(colorScheme = darkColorScheme()) {
                Surface(Modifier.fillMaxSize().safeDrawingPadding()) {
                    PlaygroundScreen(state) { raw ->
                        val expression = state.export(PoseStore.sanitize(raw))
                        val file = store.save(expression)
                        state.remember(expression)
                        if (file != null) "Saved ${file.name} and copied JSON" else "Copied JSON (no storage)"
                    }
                }
            }
        }
    }
}
