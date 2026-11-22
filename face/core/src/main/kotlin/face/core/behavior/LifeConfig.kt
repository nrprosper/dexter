package face.core.behavior

import face.core.rig.Channel

// All "aliveness" timing and amplitudes; Phase 5 folds this into Personality.
public data class LifeConfig(
    val blink: BlinkConfig = BlinkConfig(),
    val gaze: GazeConfig = GazeConfig(),
    val breath: BreathConfig = BreathConfig(),
    val boredom: BoredomConfig = BoredomConfig(),
    val idle: IdleConfig = IdleConfig(),
) {
    public companion object {
        public val Default: LifeConfig = LifeConfig()
    }
}

public data class BlinkConfig(
    val meanInterval: Float = 4f,
    val spread: Float = 0.45f,
    val minInterval: Float = 0.7f,
    val close: Float = 0.09f,
    val hold: Float = 0.04f,
    val open: Float = 0.16f,
    val doubleChance: Float = 0.12f,
    val doubleGap: Float = 0.12f,
    val slowChance: Float = 0.06f,
    val slowFactor: Float = 2.6f,
    val triggerChance: Float = 0.5f,
    val triggerCooldown: Float = 0.6f,
    val drowsyPartialDepth: Float = 0.75f,
)

public data class GazeConfig(
    val fixationMean: Float = 2.4f,
    val fixationSpread: Float = 0.5f,
    val awayChance: Float = 0.3f,
    val awayMin: Float = 0.35f,
    val awayMax: Float = 0.8f,
    val refixRadius: Float = 0.12f,
    val driftAmplitude: Float = 0.035f,
    val driftFrequency: Float = 0.6f,
    val headFollowX: Float = 0.05f,
    val headFollowY: Float = 0.035f,
    val headFollowTilt: Float = 0.06f,
    val bigSaccade: Float = 0.5f,
    val sweepStep: Float = 0.55f,
    val sweepReach: Float = 0.65f,
)

public data class BreathConfig(
    val period: Float = 4.6f,
    val sleepPeriod: Float = 6.5f,
    val headY: Float = 0.008f,
    val scale: Float = 0.008f,
    val sleepDepth: Float = 2.2f,
)

public data class BoredomConfig(
    val drowsyAfter: Float = 90f,
    val sleepAfter: Float = 180f,
    val sleepFade: Float = 3f,
    val wakeDuration: Float = 1.6f,
)

public data class IdleConfig(
    val meanInterval: Float = 7f,
    val spread: Float = 0.5f,
    val budgetPerMinute: Int = 4,
    val behaviors: List<IdleBehavior> = IdleBehavior.Defaults,
)

public data class IdleBehavior(
    val name: String,
    val weight: Int,
    val cooldown: Float,
    val attack: Float,
    val hold: Float,
    val release: Float,
    val noticeable: Boolean,
    val deltas: Map<Channel, Float>,
    val mirrored: Set<Channel> = emptySet(),
    val drowsyOnly: Boolean = false,
    val gazeSweep: Boolean = false,
) {
    public companion object {
        public val Defaults: List<IdleBehavior> = listOf(
            IdleBehavior(
                "tilt", weight = 3, cooldown = 12f, attack = 0.35f, hold = 0.9f, release = 0.6f, noticeable = true,
                deltas = mapOf(Channel.HeadTilt to 0.14f, Channel.BrowHeightR to 0.25f, Channel.GazeX to 0.15f),
                mirrored = setOf(Channel.HeadTilt, Channel.GazeX),
            ),
            IdleBehavior(
                "hum", weight = 2, cooldown = 20f, attack = 0.3f, hold = 0.8f, release = 0.4f, noticeable = true,
                deltas = mapOf(
                    Channel.MouthRoundness to 0.45f, Channel.MouthOpen to 0.12f, Channel.MouthWidth to -0.25f,
                    Channel.EyeOpenL to -0.1f, Channel.EyeOpenR to -0.1f,
                ),
            ),
            IdleBehavior(
                "squint", weight = 2, cooldown = 18f, attack = 0.25f, hold = 0.8f, release = 0.4f, noticeable = true,
                deltas = mapOf(
                    Channel.EyeOpenL to -0.32f, Channel.EyeOpenR to -0.32f, Channel.LowerLidRaiseL to 0.2f,
                    Channel.LowerLidRaiseR to 0.2f, Channel.BrowTiltL to 0.25f, Channel.BrowTiltR to 0.25f,
                    Channel.GazeX to 0.35f,
                ),
                mirrored = setOf(Channel.GazeX),
            ),
            IdleBehavior(
                "lookAround", weight = 2, cooldown = 25f, attack = 0.1f, hold = 2.2f, release = 0.1f, noticeable = true,
                deltas = emptyMap(), gazeSweep = true,
            ),
            IdleBehavior(
                "bounce", weight = 1, cooldown = 30f, attack = 0.12f, hold = 0.08f, release = 0.35f, noticeable = true,
                deltas = mapOf(
                    Channel.HeadY to 0.035f, Channel.HeadSquash to 0.35f,
                    Channel.BrowHeightL to 0.2f, Channel.BrowHeightR to 0.2f,
                ),
            ),
            IdleBehavior(
                "shift", weight = 3, cooldown = 6f, attack = 1.2f, hold = 3f, release = 1.5f, noticeable = false,
                deltas = mapOf(Channel.HeadX to 0.02f, Channel.HeadTilt to 0.04f),
                mirrored = setOf(Channel.HeadX, Channel.HeadTilt),
            ),
            IdleBehavior(
                "yawn", weight = 4, cooldown = 25f, attack = 0.6f, hold = 1.2f, release = 0.8f, noticeable = true,
                deltas = mapOf(
                    Channel.MouthOpen to 0.85f, Channel.MouthRoundness to 0.45f, Channel.MouthWidth to -0.1f,
                    Channel.EyeOpenL to -0.75f, Channel.EyeOpenR to -0.75f, Channel.BrowHeightL to 0.4f,
                    Channel.BrowHeightR to 0.4f, Channel.HeadSquash to 0.25f, Channel.HeadY to 0.03f,
                ),
                drowsyOnly = true,
            ),
        )
    }
}
