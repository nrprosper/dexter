package face.core.director

import face.api.Emotion
import face.api.FaceReaction
import face.core.rig.Channel

// A one-shot on top of everything. "set" channels are pulled towards neutral+delta (a mask), "add" channels stack.
internal class ReactionDef(
    val name: String,
    val priority: Int,
    val cooldown: Float,
    val attack: Float,
    val hold: Float,
    val release: Float,
    val set: Map<Channel, Float> = emptyMap(),
    val add: Map<Channel, Float> = emptyMap(),
    val shake: Channel? = null,
    val shakeAmplitude: Float = 0f,
    val shakeFrequency: Float = 0f,
    val followUp: Emotion? = null,
    val followUpIntensity: Float = 0.6f,
    val noBlink: Boolean = false,
    val blinkOnStart: Boolean = false,
) {
    val setChannels = set.keys.map { it.ordinal }.toIntArray()
    val setValues = set.values.toFloatArray()
    val addChannels = add.keys.map { it.ordinal }.toIntArray()
    val addValues = add.values.toFloatArray()
    val duration = attack + hold + release
}

internal object Reactions {
    const val SURPRISE = 0
    const val STARTLE = 1
    const val DELIGHT = 2
    const val ACKNOWLEDGE = 3
    const val OOPS = 4
    const val LAUGH = 5
    const val REALIZATION = 6

    fun indexOf(reaction: FaceReaction): Int = when (reaction) {
        is FaceReaction.Surprise -> SURPRISE
        FaceReaction.Startle -> STARTLE
        FaceReaction.Delight -> DELIGHT
        FaceReaction.Acknowledge -> ACKNOWLEDGE
        FaceReaction.Oops -> OOPS
        FaceReaction.Laugh -> LAUGH
        FaceReaction.Realization -> REALIZATION
    }

    val Defaults: Array<ReactionDef> = arrayOf(
        ReactionDef(
            "Surprise", priority = 3, cooldown = 2.5f, attack = 0.08f, hold = 0.35f, release = 0.6f,
            set = mapOf(
                Channel.EyeOpenL to 0.3f, Channel.EyeOpenR to 0.3f, Channel.PupilSize to -0.3f,
                Channel.BrowHeightL to 0.9f, Channel.BrowHeightR to 0.9f, Channel.MouthOpen to 0.6f,
                Channel.MouthRoundness to 0.8f, Channel.MouthCurve to -0.1f,
            ),
            add = mapOf(Channel.HeadSquash to 0.45f, Channel.HeadScale to 0.08f, Channel.HeadY to 0.03f),
            noBlink = true,
        ),
        ReactionDef(
            "Startle", priority = 4, cooldown = 3f, attack = 0.05f, hold = 0.2f, release = 0.5f,
            set = mapOf(
                Channel.EyeOpenL to 0.32f, Channel.EyeOpenR to 0.32f, Channel.PupilSize to -0.4f,
                Channel.BrowHeightL to 0.7f, Channel.BrowHeightR to 0.7f, Channel.MouthOpen to 0.3f,
                Channel.MouthCurve to -0.4f, Channel.MouthWidth to -0.2f,
            ),
            add = mapOf(Channel.HeadSquash to -0.35f, Channel.HeadY to -0.04f, Channel.Sweat to 0.6f),
            noBlink = true,
        ),
        ReactionDef(
            "Delight", priority = 2, cooldown = 2f, attack = 0.15f, hold = 0.5f, release = 0.6f,
            set = mapOf(
                Channel.EyeSmileL to 1f, Channel.EyeSmileR to 1f, Channel.MouthCurve to 0.85f,
                Channel.MouthOpen to 0.4f, Channel.BrowHeightL to 0.45f, Channel.BrowHeightR to 0.45f,
            ),
            add = mapOf(Channel.Blush to 0.7f, Channel.Sparkle to 0.8f, Channel.HeadSquash to 0.2f),
            shake = Channel.HeadY, shakeAmplitude = 0.015f, shakeFrequency = 4f,
            followUp = Emotion.Happy,
        ),
        ReactionDef(
            "Acknowledge", priority = 1, cooldown = 0.8f, attack = 0.12f, hold = 0.05f, release = 0.3f,
            add = mapOf(
                Channel.HeadY to -0.04f, Channel.HeadSquash to -0.12f,
                Channel.BrowHeightL to 0.15f, Channel.BrowHeightR to 0.15f,
            ),
            blinkOnStart = true,
        ),
        ReactionDef(
            "Oops", priority = 3, cooldown = 3f, attack = 0.12f, hold = 0.7f, release = 0.6f,
            set = mapOf(
                Channel.BrowTiltL to -0.7f, Channel.BrowTiltR to -0.7f, Channel.BrowHeightL to 0.35f,
                Channel.BrowHeightR to 0.35f, Channel.MouthCurve to -0.5f, Channel.MouthAsymmetry to 0.5f,
                Channel.MouthWidth to -0.15f,
            ),
            add = mapOf(Channel.Sweat to 0.9f, Channel.EyeOpenL to -0.1f, Channel.EyeOpenR to -0.1f),
            shake = Channel.HeadX, shakeAmplitude = 0.012f, shakeFrequency = 7f,
            followUp = Emotion.Concerned, followUpIntensity = 0.4f,
        ),
        ReactionDef(
            "Laugh", priority = 2, cooldown = 4f, attack = 0.15f, hold = 1.2f, release = 0.6f,
            set = mapOf(
                Channel.EyeSquint to 1f, Channel.MouthOpen to 1f, Channel.MouthCurve to 0.6f,
                Channel.MouthWidth to 0.45f, Channel.BrowHeightL to 0.6f, Channel.BrowHeightR to 0.6f,
            ),
            add = mapOf(Channel.Blush to 0.5f, Channel.HeadTilt to -0.12f, Channel.HeadSquash to 0.2f),
            shake = Channel.HeadY, shakeAmplitude = 0.025f, shakeFrequency = 6f,
            followUp = Emotion.Happy,
        ),
        ReactionDef(
            "Realization", priority = 2, cooldown = 2.5f, attack = 0.1f, hold = 0.4f, release = 0.5f,
            set = mapOf(
                Channel.EyeOpenL to 0.25f, Channel.EyeOpenR to 0.25f, Channel.BrowHeightL to 0.7f,
                Channel.BrowHeightR to 0.7f, Channel.PupilSize to -0.2f, Channel.MouthOpen to 0.3f,
                Channel.MouthRoundness to 0.5f,
            ),
            add = mapOf(Channel.Sparkle to 1f, Channel.HeadY to 0.03f),
            followUp = Emotion.Happy, followUpIntensity = 0.5f,
        ),
    )
}
