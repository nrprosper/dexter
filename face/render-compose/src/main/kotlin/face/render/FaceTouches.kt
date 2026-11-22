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
internal fun Modifier.faceTouches(engine: FaceEngine, renderer: FaceRenderer, style: () -> FaceStyle): Modifier {
    fun send(gesture: TouchGesture, at: Offset) {
        val hit = renderer.hitTest(at.x, at.y, engine.pose, style())
        engine.touch(FaceTouch(gesture, hit.part, hit.lookX, hit.lookY))
    }
    return pointerInput(engine) {
        var petting = false
        detectTapGestures(
            onTap = { send(TouchGesture.Tap, it) },
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
