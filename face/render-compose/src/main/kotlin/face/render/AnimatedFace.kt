package face.render

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import face.core.FaceEngine
import face.core.time.FrameTicker

// One withFrameNanos loop, alive only while STARTED; the frame counter is read only in the draw phase.
// timeScale > 1 runs several engine steps per frame (debug time acceleration).
@Composable
public fun AnimatedFace(
    engine: FaceEngine,
    modifier: Modifier = Modifier,
    style: FaceStyle = FaceStyle.Default,
    stats: FrameStats? = null,
    timeScale: Int = 1,
    touchable: Boolean = true,
    intro: FaceIntro? = null,
) {
    val renderer = remember {
        FaceRenderer().apply {
            if (intro != null) {
                zoom = intro.startScale
                zoomOffsetY = intro.startOffsetY
            }
        }
    }
    val introElapsed = remember { floatArrayOf(if (intro != null) 0f else -1f, 0f) }
    val currentStyle = rememberUpdatedState(style)
    val clock = remember(engine) { { engine.time.toFloat() } }
    val frame = remember { mutableIntStateOf(0) }
    val scale = rememberUpdatedState(timeScale)
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(engine, lifecycle) {
        val ticker = FrameTicker()
        val onFrame: (Long) -> Unit = { nanos ->
            val dt = ticker.tick(nanos)
            repeat(scale.value) { engine.step(dt, nanos) }
            if (intro != null && introElapsed[0] >= 0f) {
                introElapsed[1] += dt
                if (intro.started || introElapsed[1] > INTRO_FALLBACK) introElapsed[0] += dt
                val t = (introElapsed[0] / intro.seconds).coerceAtMost(1f)
                val e = easeOutBack(t)
                renderer.zoom = intro.startScale + (1f - intro.startScale) * e
                renderer.zoomOffsetY = intro.startOffsetY * (1f - e)
                if (t >= 1f) {
                    renderer.zoom = 1f
                    renderer.zoomOffsetY = 0f
                    introElapsed[0] = -1f
                }
            }
            stats?.record(nanos)
            frame.intValue++
        }
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            ticker.reset()
            engine.onResume()
            stats?.start()
            try {
                while (true) withFrameNanos(onFrame)
            } finally {
                stats?.stop()
            }
        }
    }
    val touches = if (touchable) Modifier.faceTouches(engine, renderer) { currentStyle.value } else Modifier
    val viewport = Modifier.onSizeChanged { engine.setViewport(it.width.toFloat(), it.height.toFloat()) }
    FaceCanvas(engine.pose, style, frame, modifier.then(viewport).then(touches), renderer, clock)
}

public class FrameStats {
    public var frames: Long = 0
        private set
    public var running: Boolean = false
        private set
    private var lastNanos = 0L
    private var worstNanos = 0L

    internal fun start() {
        running = true
        lastNanos = 0L
    }

    internal fun stop() {
        running = false
    }

    internal fun record(nanos: Long) {
        if (lastNanos != 0L) worstNanos = maxOf(worstNanos, nanos - lastNanos)
        lastNanos = nanos
        frames++
    }

    public fun takeWorstFrameMillis(): Float {
        val worst = worstNanos
        worstNanos = 0L
        return worst / 1_000_000f
    }
}

private const val INTRO_FALLBACK = 1.5f

// Grows a little past full size and settles back: a small playful overshoot.
private fun easeOutBack(t: Float): Float {
    val c1 = 1.1f
    val c3 = c1 + 1f
    val x = t - 1f
    return 1f + c3 * x * x * x + c1 * x * x
}
