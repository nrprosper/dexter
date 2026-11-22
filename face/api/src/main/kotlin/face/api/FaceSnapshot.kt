package face.api

// What the face is doing, for debugging, tests and other UI. Never pixels.
public data class FaceSnapshot(
    val activity: Activity = Activity.Idle,
    val emotion: Emotion = Emotion.Neutral,
    val intensity: Float = 0f,
    val speaking: Boolean = false,
    val asleep: Boolean = false,
    val powerMode: PowerMode = PowerMode.Full,
    val lastReaction: FaceReaction? = null,
)
