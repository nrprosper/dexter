package face.core.stage

import face.api.Activity
import face.api.StageMove
import face.core.anim.Envelope
import face.core.anim.Layer
import face.core.behavior.FaceEvent
import face.core.behavior.LifeContext
import face.core.rig.Channel
import face.core.rig.FacePose
import face.core.time.Rng
import kotlin.math.exp

// Stage directions turned into head targets; the springs do the actual travel, so moves never pop.
// A placement holds while the character is talking, then it drifts home once it has been idle for returnAfter seconds.
// Gives way to the volume visit, which also moves the head.
internal class Stage(private val rng: Rng, private val ctx: LifeContext) {
    var config: StageConfig = StageConfig()
        set(value) {
            field = value
            resolve()
        }
    var amplitude = 1f

    private var target: Placement = HOME
    private val current = FloatArray(PARTS)
    private val goal = FloatArray(PARTS)
    private var idleFor = 0f
    private var hop = -1f
    private var lean = -1f
    private var peek = -1f
    private var peekSide = 1f
    private var byMove = arrayOfNulls<Placement>(StageMove.entries.size)

    init {
        resolve()
    }

    fun move(move: StageMove) {
        idleFor = 0f
        when (move) {
            StageMove.Hop -> hop = 0f
            StageMove.LeanIn -> lean = 0f
            StageMove.Peek -> {
                peek = 0f
                peekSide = if (rng.nextInt(2) == 0) -1f else 1f
            }
            else -> target = byMove[move.ordinal] ?: HOME
        }
        ctx.emit(FaceEvent.Behavior, NAMES[move.ordinal])
    }

    fun update(dt: Float, activity: Activity, yield: Float) {
        if (activity == Activity.Speaking || activity == Activity.Listening) idleFor = 0f else idleFor += dt
        if (idleFor > config.returnAfter) target = HOME
        val t = target
        goal[X] = t.x
        goal[Y] = t.y
        goal[SCALE] = t.scale
        goal[TILT] = t.tilt
        goal[LOOK_X] = t.lookX
        goal[LOOK_Y] = t.lookY
        val k = 1f - exp(-dt * config.rate)
        val keep = 1f - yield.coerceIn(0f, 1f)
        for (i in 0 until PARTS) {
            current[i] += (goal[i] * keep - current[i]) * k
            if (kotlin.math.abs(current[i]) < EPSILON && goal[i] == 0f) current[i] = 0f
        }
        hop = advance(hop, dt, config.hop.attack + config.hop.hold + config.hop.release)
        lean = advance(lean, dt, config.leanIn.attack + config.leanIn.hold + config.leanIn.release)
        peek = advance(peek, dt, config.peek.attack + config.peek.hold + config.peek.release)
    }

    fun apply(pose: FacePose) {
        val a = amplitude
        add(pose, Channel.HeadX, current[X] * a)
        add(pose, Channel.HeadY, current[Y] * a)
        add(pose, Channel.HeadScale, current[SCALE] * a)
        add(pose, Channel.HeadTilt, current[TILT] * a)
        add(pose, Channel.GazeX, current[LOOK_X])
        add(pose, Channel.GazeY, current[LOOK_Y])
        if (hop >= 0f) {
            val h = config.hop
            val e = Envelope.value(hop, h.attack, h.hold, h.release)
            add(pose, Channel.HeadY, h.height * e * a)
            if (hop > h.attack + h.hold) add(pose, Channel.HeadSquash, h.squash * e)
        }
        if (lean >= 0f) {
            val l = config.leanIn
            val e = Envelope.value(lean, l.attack, l.hold, l.release)
            add(pose, Channel.HeadScale, l.scale * e * a)
            add(pose, Channel.HeadTilt, l.tilt * e)
            add(pose, Channel.BrowHeightL, l.brows * e)
            add(pose, Channel.BrowHeightR, l.brows * e)
        }
        if (peek >= 0f) {
            val p = config.peek
            val e = Envelope.value(peek, p.attack, p.hold, p.release)
            add(pose, Channel.HeadX, (p.x * peekSide - current[X]) * e)
            add(pose, Channel.HeadTilt, -p.tilt * peekSide * e)
            add(pose, Channel.GazeX, -p.lookX * peekSide * e)
        }
    }

    private fun resolve() {
        val out = arrayOfNulls<Placement>(StageMove.entries.size)
        for (m in StageMove.entries) out[m.ordinal] = config.placements[KEYS[m.ordinal]]
        byMove = out
    }

    private fun advance(t: Float, dt: Float, duration: Float): Float {
        if (t < 0f) return t
        val next = t + dt
        return if (next >= duration) -1f else next
    }

    private fun add(pose: FacePose, channel: Channel, delta: Float) {
        pose[channel] = pose[channel] + delta
    }

    private companion object {
        val HOME = Placement()
        const val PARTS = 6
        const val X = 0
        const val Y = 1
        const val SCALE = 2
        const val TILT = 3
        const val LOOK_X = 4
        const val LOOK_Y = 5
        const val EPSILON = 1e-4f
        val KEYS: Array<String> = StageMove.entries.map { it.name.replaceFirstChar(Char::lowercaseChar) }.toTypedArray()
        val NAMES: Array<String> = StageMove.entries.map { "stage ${it.name}" }.toTypedArray()
    }
}

internal class StageLayer(private val stage: Stage) : Layer() {
    override fun apply(target: FacePose) {
        stage.apply(target)
    }
}
