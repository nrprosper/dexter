package com.vlmis.dexter

import android.Manifest
import android.content.pm.PackageManager
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import assistant.core.AssistantState
import com.vlmis.dexter.talk.TalkPill
import assistant.android.Assistant
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import face.core.FaceEngine
import face.core.expression.ExpressionLibrary
import face.core.time.Rng
import face.render.FaceView
import face.senses.PhoneSenses
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    companion object {
        const val EXTRA_LISTEN: String = "listen"
        private const val INTRO_LISTEN_DELAY = 1_200L
        private const val RETURN_LISTEN_DELAY = 350L
    }

    private val engine by lazy { FaceEngine(ExpressionLibrary.loadBundled(), Rng(System.nanoTime())) }
    private val senses by lazy { PhoneSenses(this, engine) }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main + CoroutineExceptionHandler { _, e -> Log.w(TAG, "assistant", e) })
    private val assistant by lazy { Assistant(this, engine, scope) }

    // Whatever the answer, the turn goes ahead: without the mic, Dexter says so out loud.
    private val micPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) {
        assistant.orchestrator.onDoubleTap()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val intro = if (savedInstanceState == null) SplashIntro.forWindow(this) else null
        splashScreen.setOnExitAnimationListener {
            it.remove()
            intro?.start()
        }
        enableEdgeToEdge()
        enterImmersiveMode()
        setContent {
            Box(Modifier.fillMaxSize()) {
                FaceView(engine = engine, intro = intro, onDoubleTap = ::onDoubleTap)
                TalkPill(
                    assistant.orchestrator,
                    onTap = ::onPillTap,
                    modifier = Modifier.align(Alignment.BottomCenter).padding(start = 24.dp, end = 24.dp, bottom = 40.dp),
                )
            }
        }
        if (intent?.getBooleanExtra(EXTRA_LISTEN, false) == true) listenSoon(INTRO_LISTEN_DELAY)
    }

    override fun onStart() {
        super.onStart()
        senses.start()
        assistant.voice.reloadPrefs()
        assistant.voice.warmUp()
    }

    override fun onStop() {
        senses.stop()
        assistant.orchestrator.stop()
        super.onStop()
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    // From Settings' "Talk to Dexter": bring the face forward and start listening.
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (intent.getBooleanExtra(EXTRA_LISTEN, false)) listenSoon(RETURN_LISTEN_DELAY)
    }

    private fun listenSoon(delayMillis: Long) {
        scope.launch {
            delay(delayMillis)
            onDoubleTap()
        }
    }

    private fun onPillTap() {
        if (assistant.orchestrator.state.value == AssistantState.Listening) assistant.orchestrator.stop() else onDoubleTap()
    }

    private fun onDoubleTap() {
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            assistant.orchestrator.onDoubleTap()
        } else {
            micPermission.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        senses.onKeyEvent(event)
        return super.onKeyDown(keyCode, event)
    }

    private fun enterImmersiveMode() {
        WindowCompat.getInsetsController(window, window.decorView).apply {
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            hide(WindowInsetsCompat.Type.systemBars())
        }
    }
}

private const val TAG = "Dexter"
