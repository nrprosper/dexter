package face.api

// Momentary events: queued, then resolved by priority and cooldown.
public sealed interface FaceReaction {
    public data class Surprise(val followUp: Emotion? = null) : FaceReaction
    public data object Delight : FaceReaction
    public data object Startle : FaceReaction
    public data object Acknowledge : FaceReaction
    public data object Oops : FaceReaction
    public data object Laugh : FaceReaction
    public data object Realization : FaceReaction

    // Phone events: always answered playfully.
    public data object PluggedIn : FaceReaction
    public data object Unplugged : FaceReaction
    public data object BatteryFull : FaceReaction
    public data object HeadphonesIn : FaceReaction
    public data object Shaken : FaceReaction
}
