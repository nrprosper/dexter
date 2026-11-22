package face.core.behavior

public enum class FaceEvent { Blink, DoubleBlink, SlowBlink, Saccade, Behavior, Drowsy, Asleep, Waking, Awake }

// Called on the frame thread, only when something happens; detail is a preexisting string, never built per call.
public fun interface FaceEventListener {
    public fun onFaceEvent(event: FaceEvent, detail: String, time: Double)
}

public enum class Vigilance { Awake, Drowsy, Asleep, Waking }
