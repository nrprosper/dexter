package face.render

import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import face.core.FaceEngine
import face.core.senses.FaceTouch
import face.core.senses.TouchGesture

// Taps, long presses (petting) and drags, hit-tested against the drawn face and sent to the engine.
// A second tap close in time and place also fires onDoubleTap; single taps are never delayed waiting for it.
internal fun Modifier.faceTouches(
    engine: FaceEngine,
    renderer: FaceRenderer,
    style: () -> FaceStyle,
    onDoubleTap: () -> (() -> Unit)?,
): Modifier {
    fun send(gesture: TouchGesture, at: Offset) {
        val hit = renderer.hitTest(at.x, at.y, engine.pose, style())
        engine.touch(FaceTouch(gesture, hit.part, hit.lookX, hit.lookY))
    }
    return pointerInput(engine) {
        var petting = false
        var lastTapMillis = Long.MIN_VALUE
        var lastTap = Offset.Zero
        detectTapGestures(
            onTap = {
                send(TouchGesture.Tap, it)
                val now = System.currentTimeMillis()
                val near = (it - lastTap).getDistance() < viewConfiguration.touchSlop * DOUBLE_TAP_SLOP
                if (lastTapMillis != Long.MIN_VALUE && now - lastTapMillis <= viewConfiguration.doubleTapTimeoutMillis && near) {
                    lastTapMillis = Long.MIN_VALUE
                    onDoubleTap()?.invoke()
                } else {
                    lastTapMillis = now
                    lastTap = it
                }
            },
            onLongPress = {
                petting = true
                send(TouchGesture.LongPressStart, it)
            },
            onPress = {
                tryAwaitRelease()
                if (petting) {
                    petting = false
                    send(TouchGesture.LongPressEnd, it)
                }
            },
        )
    }.pointerInput(engine) {
        var last = Offset.Zero
        detectDragGestures(
            onDragStart = {
                last = it
                send(TouchGesture.DragStart, it)
            },
            onDrag = { change, _ ->
                last = change.position
                send(TouchGesture.Drag, change.position)
            },
            onDragEnd = { send(TouchGesture.DragEnd, last) },
            onDragCancel = { send(TouchGesture.DragEnd, last) },
        )
    }
}

private const val DOUBLE_TAP_SLOP = 4f
