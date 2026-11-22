package face.core.behavior

// Shared state the behaviours read each frame; written by the boredom tracker and the engine clock.
internal class LifeContext {
    var time = 0.0
    var vigilance = Vigilance.Awake
    var drowsiness = 0f
    var sleepWeight = 0f
    var listener: FaceEventListener? = null

    fun emit(event: FaceEvent, detail: String = "") {
        listener?.onFaceEvent(event, detail, time)
    }
}
