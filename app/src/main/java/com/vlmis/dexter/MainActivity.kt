package com.vlmis.dexter

import android.os.Bundle
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import face.core.FaceEngine
import face.core.expression.ExpressionLibrary
import face.core.time.Rng
import face.render.FaceView
import face.senses.PhoneSenses

class MainActivity : ComponentActivity() {
    private val engine by lazy { FaceEngine(ExpressionLibrary.loadBundled(), Rng(System.nanoTime())) }
    private val senses by lazy { PhoneSenses(this, engine) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        enterImmersiveMode()
        setContent { FaceView(engine = engine) }
    }

    override fun onStart() {
        super.onStart()
        senses.start()
    }

    override fun onStop() {
        senses.stop()
        super.onStop()
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        senses.onKeyEvent(event)
        return super.dispatchKeyEvent(event)
    }

    private fun enterImmersiveMode() {
        WindowCompat.getInsetsController(window, window.decorView).apply {
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            hide(WindowInsetsCompat.Type.systemBars())
        }
    }
}
