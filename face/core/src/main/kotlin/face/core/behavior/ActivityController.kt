package face.core.behavior

import face.api.Activity
import face.core.anim.Envelope
import face.core.rig.Channel
import face.core.rig.FacePose
import face.core.time.Rng
import kotlin.math.min

// Turns the orchestrator's activity into overlays, gaze and blink policies, vigilance, and listening nods.
internal class ActivityController(
    private val config: ActivityConfig,
    private val rng: Rng,
    private val ctx: LifeContext,
    private val gaze: GazeController,
    private val blink: BlinkScheduler,
    private val boredom: BoredomTracker,
    private val wake: () -> Unit,
) {
    private val weights = FloatArray(Activity.entries.size).also { it[Activity.Idle.ordinal] = 1f }
    private val listening = Overlay(config.listening)
    private val thinking = Overlay(config.thinking)
    private val scanning = Overlay(config.scanning)
    private val effort = Overlay(config.effort)
    private val apology = Overlay(config.apology)
    private val speaking = Overlay(config.speaking)
    private val nod = Overlay(config.nod)

    var current = Activity.Idle
        private set
    private var elapsed = 0f
    private var side = 1f
    private var scanLeft = 0f
    private var apologyLeft = 0f
    private var sinceApology = 0f
    private var apologized = false
    private var voiceFast = 0f
    private var voiceSlow = 0f
    private var nodElapsed = -1f
    private var nodCooldown = 0f

    fun set(activity: Activity) {
        if (activity == current) return
        val previous = current
        current = activity
        elapsed = 0f
        gaze.release()
        when (activity) {
            Activity.Listening -> {
                blink.trigger()
                gaze.lookAtUser()
            }
            Activity.Thinking -> {
                side = if (rng.nextFloat() < 0.5f) -1f else 1f
                scanLeft = config.scanStep
                sinceApology = 0f
                apologyLeft = 0f
                apologized = false
            }
            Activity.Speaking -> gaze.lookAtUser()
            Activity.Sleeping, Activity.Idle -> Unit
        }
        if (previous == Activity.Sleeping) wake()
        ctx.emit(FaceEvent.Activity, NAMES[activity.ordinal])
    }

    fun update(dt: Float, voiceLevel: Float) {
        elapsed += dt
        val fade = min(1f, dt * config.fadeRate)
        for (i in weights.indices) {
            val goal = if (i == current.ordinal) 1f else 0f
            weights[i] += (goal - weights[i]) * fade
        }
        when (current) {
            Activity.Sleeping -> boredom.dozeOff()
            Activity.Idle -> Unit
            else -> {
                if (ctx.vigilance == Vigilance.Asleep || ctx.vigilance == Vigilance.Drowsy) wake()
                boredom.keepAwake()
            }
        }
        gaze.policy = when (current) {
            Activity.Listening -> GazePolicy.Listening
            Activity.Speaking -> GazePolicy.Speaking
            else -> GazePolicy.Default
        }
        blink.intervalScale = if (current == Activity.Listening) config.listeningBlinkScale else 1f
        if (current == Activity.Thinking) updateThinking(dt)
        updateNod(dt, if (current == Activity.Listening) voiceLevel else 0f)
    }

    fun apply(pose: FacePose) {
        listening.add(pose, weights[Activity.Listening.ordinal])
        speaking.add(pose, weights[Activity.Speaking.ordinal])
        val think = weights[Activity.Thinking.ordinal]
        if (think > 0f) {
            thinking.add(pose, think)
            pose[Channel.HeadTilt] = pose[Channel.HeadTilt] + side * config.thinkingSideTilt * think
            val brow = if (side > 0f) Channel.BrowHeightR else Channel.BrowHeightL
            pose[brow] = pose[brow] + config.thinkingSideBrow * think
            if (current == Activity.Thinking) {
                when (stage()) {
                    Stage.Scan -> scanning.add(pose, think)
                    Stage.Effort, Stage.Apologetic -> effort.add(pose, think)
                    Stage.Calm -> Unit
                }
                if (apologyLeft > 0f) apology.add(pose, think)
            }
        }
        if (nodElapsed >= 0f) nod.add(pose, Envelope.value(nodElapsed, config.nodAttack, config.nodHold, config.nodRelease))
    }

    private fun updateThinking(dt: Float) {
        val up = config.thinkingGazeUp
        val reach = config.thinkingGaze
        when (stage()) {
            Stage.Calm -> gaze.holdAt(side * reach, up)
            Stage.Scan -> {
                scanLeft -= dt
                if (scanLeft <= 0f) {
                    side = -side
                    scanLeft = config.scanStep * rng.nextFloat(0.8f, 1.2f)
                }
                gaze.holdAt(side * reach * 1.1f, up * 0.9f)
            }
            Stage.Effort -> gaze.holdAt(side * reach * 0.6f, up * 1.1f)
            Stage.Apologetic -> {
                sinceApology += dt
                if (apologyLeft > 0f) {
                    apologyLeft -= dt
                    gaze.holdAt(0f, -0.05f)
                } else {
                    gaze.holdAt(side * reach * 0.6f, up * 1.1f)
                    if (!apologized || sinceApology >= config.apologyEvery) {
                        apologized = true
                        sinceApology = 0f
                        apologyLeft = config.apologyFor
                    }
                }
            }
        }
    }

    private fun stage(): Stage = when {
        elapsed < config.calmUntil -> Stage.Calm
        elapsed < config.scanUntil -> Stage.Scan
        elapsed < config.effortUntil -> Stage.Effort
        else -> Stage.Apologetic
    }

    // A nod fires when the user's voice jumps above its recent average, like a syllable or an emphasis.
    private fun updateNod(dt: Float, voice: Float) {
        voiceFast += (voice - voiceFast) * min(1f, dt * VOICE_FAST)
        voiceSlow += (voice - voiceSlow) * min(1f, dt * VOICE_SLOW)
        if (nodCooldown > 0f) nodCooldown -= dt
        if (nodElapsed >= 0f) {
            nodElapsed += dt
            if (nodElapsed >= config.nodAttack + config.nodHold + config.nodRelease) nodElapsed = -1f
        }
        if (nodElapsed < 0f && nodCooldown <= 0f && voiceFast - voiceSlow > config.nodThreshold) {
            nodElapsed = 0f
            nodCooldown = config.nodCooldown * rng.nextFloat(0.8f, 1.6f)
            ctx.emit(FaceEvent.Nod)
        }
    }

    private enum class Stage { Calm, Scan, Effort, Apologetic }

    private class Overlay(deltas: Map<Channel, Float>) {
        private val channels = deltas.keys.map { it.ordinal }.toIntArray()
        private val values = deltas.values.toFloatArray()

        fun add(pose: FacePose, weight: Float) {
            if (weight <= 0f) return
            val v = pose.values
            for (k in channels.indices) v[channels[k]] += values[k] * weight
        }
    }

    private companion object {
        val NAMES = Activity.entries.map { it.name }.toTypedArray()
        const val VOICE_FAST = 25f
        const val VOICE_SLOW = 2f
    }
}
