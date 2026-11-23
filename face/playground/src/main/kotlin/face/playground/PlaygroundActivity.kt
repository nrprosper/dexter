package face.playground

import android.os.Bundle
import android.util.Log
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.darkColorScheme
import androidx.compose.ui.Modifier
import face.core.expression.ExpressionLibrary
import face.senses.PhoneSenses
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import voice.speaker.VoiceStack

public class PlaygroundActivity : ComponentActivity() {
    private val store by lazy { PoseStore(this) }
    private val state by lazy { PlaygroundState(ExpressionLibrary.loadBundled(), store.loadAll()) }
    private val senses by lazy { PhoneSenses(this, state.engine) }
    private val voiceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main + CoroutineExceptionHandler { _, e -> Log.w(TAG, "speech failed", e) })
    private val voice by lazy { VoiceStack(this, state.engine, voiceScope) }

    override fun onStart() {
        super.onStart()
        senses.start()
    }

    override fun onStop() {
        senses.stop()
        voice.speaker.stop()
        super.onStop()
    }

    override fun onDestroy() {
        voiceScope.cancel()
        super.onDestroy()
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        senses.onKeyEvent(event)
        return super.onKeyDown(keyCode, event)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MaterialTheme(colorScheme = darkColorScheme()) {
                Surface(Modifier.fillMaxSize().safeDrawingPadding()) {
                    PlaygroundScreen(state, voice) { raw ->
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

private const val TAG = "Playground"
