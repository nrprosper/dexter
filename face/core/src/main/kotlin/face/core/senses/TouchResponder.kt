package face.core.senses

import face.api.Emotion
import face.core.anim.Envelope
import face.core.behavior.BlinkScheduler
import face.core.behavior.FaceEvent
import face.core.behavior.LifeContext
import face.core.director.Director
import face.core.director.ReactionRouter
import face.core.director.Reactions
import face.core.rig.Channel
import face.core.rig.FacePose
import kotlin.math.min

// Touching the face: pokes, boops, giggles and bonks by part; petting while held; the gaze follows a dragging finger;
// pestering escalates from a pout to a grumpy turn-away. Always playful.
internal class TouchResponder(
    private val ctx: LifeContext,
    private val blink: BlinkScheduler,
    private val reactions: ReactionRouter,
    private val director: Director,
) {
    private val taps = DoubleArray(PESTER_GRUMPY) { Double.NEGATIVE_INFINITY }
    private var tapHead = 0
    private var petting = false
    private var petSide = 0f
    private var petWeight = 0f
    private var dragging = false
    private var dragX = 0f
    private var dragY = 0f
    private var dragWeight = 0f
    private var glanceElapsed = -1f
    private var glanceX = 0f
    private var glanceY = 0f

    fun handle(touch: FaceTouch) {
        when (touch.gesture) {
            TouchGesture.Tap -> tap(touch)
            TouchGesture.LongPressStart -> {
                petting = true
                petSide = touch.lookX.coerceIn(-1f, 1f)
                ctx.emit(FaceEvent.Behavior, PET)
            }
            TouchGesture.LongPressEnd -> if (petting) {
                petting = false
                director.force(Emotion.Happy, PET_AFTERGLOW)
            }
            TouchGesture.DragStart, TouchGesture.Drag -> {
                dragging = true
                dragX = touch.lookX.coerceIn(-1f, 1f)
                dragY = touch.lookY.coerceIn(-1f, 1f)
            }
            TouchGesture.DragEnd -> dragging = false
        }
    }

    fun update(dt: Float) {
        petWeight += ((if (petting) 1f else 0f) - petWeight) * min(1f, dt * PET_RATE)
        dragWeight += ((if (dragging) 1f else 0f) - dragWeight) * min(1f, dt * DRAG_RATE)
        if (glanceElapsed >= 0f) {
            glanceElapsed += dt
            if (glanceElapsed >= GLANCE_ATTACK + GLANCE_HOLD + GLANCE_RELEASE) glanceElapsed = -1f
        }
    }

    fun apply(pose: FacePose) {
        if (glanceElapsed >= 0f) {
            val e = Envelope.value(glanceElapsed, GLANCE_ATTACK, GLANCE_HOLD, GLANCE_RELEASE)
            pull(pose, Channel.GazeX, glanceX, e)
            pull(pose, Channel.GazeY, glanceY, e)
        }
        val d = dragWeight
        if (d > MIN_WEIGHT) {
            pull(pose, Channel.GazeX, dragX, d)
            pull(pose, Channel.GazeY, dragY, d)
            pose[Channel.HeadX] = pose[Channel.HeadX] + dragX * DRAG_HEAD * d
            pose[Channel.HeadY] = pose[Channel.HeadY] + dragY * DRAG_HEAD * d
            pose[Channel.HeadTilt] = pose[Channel.HeadTilt] + dragX * DRAG_TILT * d
        }
        val p = petWeight
        if (p > MIN_WEIGHT) {
            pull(pose, Channel.EyeSmileL, PET_SMILE, p)
            pull(pose, Channel.EyeSmileR, PET_SMILE, p)
            pose[Channel.MouthCurve] = pose[Channel.MouthCurve] + PET_MOUTH * p
            pose[Channel.Blush] = pose[Channel.Blush] + PET_BLUSH * p
            pose[Channel.HeadTilt] = pose[Channel.HeadTilt] + petSide * PET_LEAN_TILT * p
            pose[Channel.HeadX] = pose[Channel.HeadX] + petSide * PET_LEAN_X * p
        }
    }

    private fun tap(touch: FaceTouch) {
        taps[tapHead] = ctx.time
        tapHead = (tapHead + 1) % taps.size
        val recent = recentTaps()
        when {
            recent >= PESTER_GRUMPY -> {
                taps.fill(Double.NEGATIVE_INFINITY)
                reactions.play(Reactions.GRUMPY)
            }
            recent >= PESTER_POUT -> reactions.play(Reactions.POUT)
            else -> when (touch.part) {
                FacePart.LeftEye -> reactions.play(Reactions.POKE_LEFT)
                FacePart.RightEye -> reactions.play(Reactions.POKE_RIGHT)
                FacePart.Cheek -> reactions.play(Reactions.GIGGLE)
                FacePart.Mouth -> reactions.play(Reactions.BOOP)
                FacePart.Forehead -> reactions.play(Reactions.BONK)
                FacePart.Face, FacePart.Outside -> glance(touch)
            }
        }
    }

    private fun glance(touch: FaceTouch) {
        glanceX = touch.lookX.coerceIn(-1f, 1f)
        glanceY = touch.lookY.coerceIn(-1f, 1f)
        glanceElapsed = 0f
        blink.trigger()
        ctx.emit(FaceEvent.Behavior, GLANCE)
    }

    private fun recentTaps(): Int {
        var n = 0
        for (t in taps) if (t > ctx.time - PESTER_WINDOW) n++
        return n
    }

    private fun pull(pose: FacePose, channel: Channel, value: Float, weight: Float) {
        pose[channel] = pose[channel] + (value - pose[channel]) * weight
    }

    private companion object {
        const val PET = "touch pet"
        const val GLANCE = "touch glance"
        const val PESTER_WINDOW = 4.0
        const val PESTER_POUT = 5
        const val PESTER_GRUMPY = 9
        const val PET_RATE = 4f
        const val DRAG_RATE = 10f
        const val MIN_WEIGHT = 0.001f
        const val PET_AFTERGLOW = 0.5f
        const val PET_SMILE = 0.9f
        const val PET_MOUTH = 0.6f
        const val PET_BLUSH = 0.6f
        const val PET_LEAN_TILT = 0.15f
        const val PET_LEAN_X = 0.03f
        const val DRAG_HEAD = 0.04f
        const val DRAG_TILT = 0.06f
        const val GLANCE_ATTACK = 0.08f
        const val GLANCE_HOLD = 0.8f
        const val GLANCE_RELEASE = 0.4f
    }
}
