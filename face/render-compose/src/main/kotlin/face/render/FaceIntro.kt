package face.render

// The face grows from the splash icon's size into place on launch: start scale and vertical offset (px) of the eyes.
// It holds at the start until start() is called (when the splash leaves), or after a fallback delay.
public class FaceIntro(public val startScale: Float, public val startOffsetY: Float, public val seconds: Float = 1.1f) {
    @Volatile
    internal var started: Boolean = false
        private set

    public fun start() {
        started = true
    }
}
