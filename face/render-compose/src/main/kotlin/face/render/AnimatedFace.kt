package face.render

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
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
) {
    val frame = remember { mutableIntStateOf(0) }
    val scale = rememberUpdatedState(timeScale)
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(engine, lifecycle) {
        val ticker = FrameTicker()
        val onFrame: (Long) -> Unit = { nanos ->
            val dt = ticker.tick(nanos)
            repeat(scale.value) { engine.step(dt) }
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
    FaceCanvas(engine.pose, style, frame, modifier)
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
