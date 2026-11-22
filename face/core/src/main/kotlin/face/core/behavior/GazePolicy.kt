package face.core.behavior

// How an activity bends the idle gaze: how often and how far it looks away, and how long it holds.
internal class GazePolicy(
    val awayScale: Float,
    val awayReach: Float,
    val awayHold: Float,
    val fixationScale: Float,
    val refixScale: Float,
) {
    companion object {
        val Default = GazePolicy(awayScale = 1f, awayReach = 1f, awayHold = 1f, fixationScale = 1f, refixScale = 1f)
        val Listening = GazePolicy(awayScale = 0f, awayReach = 0f, awayHold = 1f, fixationScale = 1.4f, refixScale = 0.5f)
        val Speaking = GazePolicy(awayScale = 0.6f, awayReach = 0.55f, awayHold = 0.3f, fixationScale = 0.8f, refixScale = 0.8f)
    }
}
