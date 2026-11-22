package face.core.behavior

import face.core.rig.Channel

// Overlays and timing for each activity. Thinking escalates: calm look-up, scanning, effort, then a brief apologetic look.
public data class ActivityConfig(
    val fadeRate: Float = 4f,
    val listening: Map<Channel, Float> = mapOf(
        Channel.HeadScale to 0.06f, Channel.HeadY to -0.015f, Channel.PupilSize to 0.18f,
        Channel.EyeOpenL to 0.08f, Channel.EyeOpenR to 0.08f, Channel.BrowHeightL to 0.15f,
        Channel.BrowHeightR to 0.15f, Channel.MouthCurve to 0.05f, Channel.HeadTilt to 0.05f,
    ),
    val listeningBlinkScale: Float = 1.5f,
    val nodThreshold: Float = 0.15f,
    val nodCooldown: Float = 0.9f,
    val nod: Map<Channel, Float> = mapOf(
        Channel.HeadY to -0.035f, Channel.HeadSquash to -0.12f, Channel.BrowHeightL to 0.12f, Channel.BrowHeightR to 0.12f,
    ),
    val nodAttack: Float = 0.12f,
    val nodHold: Float = 0.05f,
    val nodRelease: Float = 0.3f,
    val thinking: Map<Channel, Float> = mapOf(
        Channel.MouthAsymmetry to 0.3f, Channel.MouthCurve to -0.12f, Channel.MouthWidth to -0.15f,
        Channel.EyeOpenL to -0.05f, Channel.EyeOpenR to -0.05f, Channel.ThinkDots to 0.4f,
    ),
    val thinkingSideTilt: Float = 0.12f,
    val thinkingSideBrow: Float = 0.35f,
    val thinkingGaze: Float = 0.5f,
    val thinkingGazeUp: Float = 0.5f,
    val calmUntil: Float = 3f,
    val scanUntil: Float = 7f,
    val effortUntil: Float = 12f,
    val scanStep: Float = 1.1f,
    val scanning: Map<Channel, Float> = mapOf(Channel.ThinkDots to 0.3f, Channel.BrowHeightL to 0.1f, Channel.BrowHeightR to 0.1f),
    val effort: Map<Channel, Float> = mapOf(
        Channel.ThinkDots to 0.6f, Channel.EyeOpenL to -0.25f, Channel.EyeOpenR to -0.25f, Channel.BrowTiltL to 0.4f,
        Channel.BrowTiltR to 0.4f, Channel.BrowHeightL to -0.3f, Channel.BrowHeightR to -0.3f, Channel.MouthCurve to -0.15f,
        Channel.HeadSquash to -0.06f,
    ),
    val apologyEvery: Float = 8f,
    val apologyFor: Float = 1.4f,
    val apology: Map<Channel, Float> = mapOf(
        Channel.BrowTiltL to -0.9f, Channel.BrowTiltR to -0.9f, Channel.BrowHeightL to 0.5f, Channel.BrowHeightR to 0.5f,
        Channel.MouthCurve to -0.1f, Channel.EyeOpenL to 0.25f, Channel.EyeOpenR to 0.25f, Channel.ThinkDots to -1f,
    ),
    val speaking: Map<Channel, Float> = mapOf(
        Channel.BrowHeightL to 0.1f, Channel.BrowHeightR to 0.1f, Channel.HeadScale to 0.02f,
    ),
)
