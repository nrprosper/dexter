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
    val orbit: Float = 0f,
    val orbitFrequency: Float = 0f,
    val playful: Boolean = false,
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
    const val PLUGGED_IN = 7
    const val UNPLUGGED = 8
    const val BATTERY_FULL = 9
    const val HEADPHONES_IN = 10
    const val SHAKEN = 11
    const val POKE_LEFT = 12
    const val POKE_RIGHT = 13
    const val GIGGLE = 14
    const val BOOP = 15
    const val BONK = 16
    const val POUT = 17
    const val GRUMPY = 18

    fun indexOf(reaction: FaceReaction): Int = when (reaction) {
        is FaceReaction.Surprise -> SURPRISE
        FaceReaction.Startle -> STARTLE
        FaceReaction.Delight -> DELIGHT
        FaceReaction.Acknowledge -> ACKNOWLEDGE
        FaceReaction.Oops -> OOPS
        FaceReaction.Laugh -> LAUGH
        FaceReaction.Realization -> REALIZATION
        FaceReaction.PluggedIn -> PLUGGED_IN
        FaceReaction.Unplugged -> UNPLUGGED
        FaceReaction.BatteryFull -> BATTERY_FULL
        FaceReaction.HeadphonesIn -> HEADPHONES_IN
        FaceReaction.Shaken -> SHAKEN
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
        ReactionDef(
            "PluggedIn", priority = 3, cooldown = 3f, attack = 0.06f, hold = 0.5f, release = 0.7f,
            set = mapOf(
                Channel.EyeOpenL to 0.3f, Channel.EyeOpenR to 0.3f, Channel.PupilSize to -0.15f,
                Channel.BrowHeightL to 0.6f, Channel.BrowHeightR to 0.6f, Channel.MouthCurve to 0.6f, Channel.MouthOpen to 0.35f,
            ),
            add = mapOf(Channel.HeadSquash to 0.45f, Channel.Glow to 0.5f, Channel.Sparkle to 1f),
            shake = Channel.HeadX, shakeAmplitude = 0.02f, shakeFrequency = 14f,
            followUp = Emotion.Happy, followUpIntensity = 0.6f, playful = true,
        ),
        ReactionDef(
            "Unplugged", priority = 2, cooldown = 3f, attack = 0.12f, hold = 0.5f, release = 0.5f,
            set = mapOf(
                Channel.MouthOpen to 0.25f, Channel.MouthRoundness to 0.7f, Channel.BrowHeightL to 0.4f,
                Channel.BrowHeightR to 0.4f, Channel.GazeY to -0.55f,
            ),
            add = mapOf(Channel.EyeOpenL to 0.1f, Channel.EyeOpenR to 0.1f, Channel.HeadY to -0.02f),
            playful = true,
        ),
        ReactionDef(
            "BatteryFull", priority = 2, cooldown = 30f, attack = 0.4f, hold = 0.8f, release = 0.6f,
            set = mapOf(Channel.EyeSmileL to 1f, Channel.EyeSmileR to 1f, Channel.MouthCurve to 0.7f, Channel.MouthOpen to 0.3f),
            add = mapOf(Channel.HeadSquash to 0.3f, Channel.HeadY to 0.03f, Channel.Sparkle to 0.8f, Channel.Blush to 0.4f),
            followUp = Emotion.Happy, followUpIntensity = 0.5f, playful = true,
        ),
        ReactionDef(
            "HeadphonesIn", priority = 2, cooldown = 5f, attack = 0.15f, hold = 1.6f, release = 0.4f,
            set = mapOf(Channel.EyeSmileL to 0.6f, Channel.EyeSmileR to 0.6f, Channel.MouthCurve to 0.55f, Channel.MouthRoundness to 0.3f),
            add = mapOf(Channel.BrowHeightL to 0.2f, Channel.BrowHeightR to 0.2f),
            shake = Channel.HeadTilt, shakeAmplitude = 0.1f, shakeFrequency = 2.2f,
            followUp = Emotion.Happy, followUpIntensity = 0.5f, playful = true,
        ),
        ReactionDef(
            "Shaken", priority = 4, cooldown = 2.5f, attack = 0.1f, hold = 1.8f, release = 0.8f,
            set = mapOf(Channel.MouthCurve to -0.2f, Channel.MouthAsymmetry to 0.5f, Channel.MouthOpen to 0.2f),
            add = mapOf(Channel.Dizzy to 1f),
            shake = Channel.HeadTilt, shakeAmplitude = 0.12f, shakeFrequency = 1.6f,
            orbit = 0.6f, orbitFrequency = 1.6f, noBlink = true, playful = true,
        ),
        ReactionDef(
            "PokeLeft", priority = 2, cooldown = 0.5f, attack = 0.06f, hold = 0.35f, release = 0.5f,
            set = mapOf(
                Channel.EyeOpenL to -1f, Channel.BrowTiltL to 0.6f, Channel.BrowHeightL to -0.3f,
                Channel.MouthCurve to -0.3f, Channel.MouthAsymmetry to -0.4f, Channel.MouthWidth to -0.2f,
            ),
            add = mapOf(Channel.HeadSquash to -0.15f),
            shake = Channel.HeadX, shakeAmplitude = 0.01f, shakeFrequency = 10f, playful = true,
        ),
        ReactionDef(
            "PokeRight", priority = 2, cooldown = 0.5f, attack = 0.06f, hold = 0.35f, release = 0.5f,
            set = mapOf(
                Channel.EyeOpenR to -1f, Channel.BrowTiltR to 0.6f, Channel.BrowHeightR to -0.3f,
                Channel.MouthCurve to -0.3f, Channel.MouthAsymmetry to 0.4f, Channel.MouthWidth to -0.2f,
            ),
            add = mapOf(Channel.HeadSquash to -0.15f),
            shake = Channel.HeadX, shakeAmplitude = 0.01f, shakeFrequency = 10f, playful = true,
        ),
        ReactionDef(
            "Giggle", priority = 2, cooldown = 1f, attack = 0.1f, hold = 0.6f, release = 0.5f,
            set = mapOf(Channel.EyeSmileL to 0.8f, Channel.EyeSmileR to 0.8f, Channel.MouthCurve to 0.8f, Channel.MouthOpen to 0.45f),
            add = mapOf(Channel.Blush to 1f, Channel.HeadSquash to 0.15f),
            shake = Channel.HeadY, shakeAmplitude = 0.015f, shakeFrequency = 7f, playful = true,
        ),
        ReactionDef(
            "Boop", priority = 2, cooldown = 1f, attack = 0.05f, hold = 0.3f, release = 0.4f,
            set = mapOf(
                Channel.MouthOpen to 0.5f, Channel.MouthRoundness to 1f, Channel.EyeOpenL to 0.25f, Channel.EyeOpenR to 0.25f,
                Channel.PupilSize to -0.2f, Channel.BrowHeightL to 0.6f, Channel.BrowHeightR to 0.6f,
            ),
            add = mapOf(Channel.HeadSquash to -0.25f, Channel.HeadY to -0.02f),
            followUp = Emotion.Playful, followUpIntensity = 0.5f, playful = true,
        ),
        ReactionDef(
            "Bonk", priority = 2, cooldown = 1f, attack = 0.04f, hold = 0.25f, release = 0.5f,
            set = mapOf(Channel.EyeSquint to 1f, Channel.MouthCurve to -0.2f, Channel.MouthWidth to -0.2f),
            add = mapOf(Channel.HeadSquash to -0.45f, Channel.HeadY to -0.04f, Channel.BrowHeightL to -0.4f, Channel.BrowHeightR to -0.4f),
            shake = Channel.HeadTilt, shakeAmplitude = 0.05f, shakeFrequency = 6f, playful = true,
        ),
        ReactionDef(
            "Pout", priority = 3, cooldown = 1.5f, attack = 0.15f, hold = 1f, release = 0.6f,
            set = mapOf(
                Channel.BrowTiltL to 0.6f, Channel.BrowTiltR to 0.6f, Channel.BrowHeightL to -0.3f, Channel.BrowHeightR to -0.3f,
                Channel.MouthCurve to -0.45f, Channel.MouthWidth to -0.35f, Channel.MouthRoundness to 0.4f,
                Channel.EyeOpenL to -0.2f, Channel.EyeOpenR to -0.2f,
            ),
            add = mapOf(Channel.Blush to 0.3f, Channel.HeadSquash to -0.1f),
            playful = true,
        ),
        ReactionDef(
            "Grumpy", priority = 4, cooldown = 4f, attack = 0.3f, hold = 1.8f, release = 0.8f,
            set = mapOf(
                Channel.BrowTiltL to 0.8f, Channel.BrowTiltR to 0.8f, Channel.BrowHeightL to -0.5f, Channel.BrowHeightR to -0.5f,
                Channel.MouthCurve to -0.5f, Channel.EyeOpenL to -0.35f, Channel.EyeOpenR to -0.35f, Channel.GazeX to -0.8f,
            ),
            add = mapOf(Channel.HeadX to -0.12f, Channel.HeadTilt to -0.15f, Channel.HeadSquash to -0.1f),
            followUp = Emotion.Playful, followUpIntensity = 0.5f, playful = true,
        ),
    )
}
