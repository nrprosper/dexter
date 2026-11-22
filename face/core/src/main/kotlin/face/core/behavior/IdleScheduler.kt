package face.core.behavior

import face.core.anim.Envelope
import face.core.rig.FacePose
import face.core.time.Rng
import face.core.time.ShuffleBag

// Picks micro-behaviours from a weighted shuffle bag, respecting cooldowns and an attention budget per minute.
internal class IdleScheduler(
    private val config: IdleConfig,
    private val rng: Rng,
    private val ctx: LifeContext,
    private val gaze: GazeController,
) {
    private val behaviors = config.behaviors.toTypedArray()
    private val channels = Array(behaviors.size) { i -> behaviors[i].deltas.keys.map { it.ordinal }.toIntArray() }
    private val deltas = Array(behaviors.size) { i -> behaviors[i].deltas.values.toFloatArray() }
    private val mirrored = Array(behaviors.size) { i ->
        val b = behaviors[i]
        BooleanArray(b.deltas.size) { k -> b.deltas.keys.elementAt(k) in b.mirrored }
    }
    private val cooldownLeft = FloatArray(behaviors.size)
    private val bag = ShuffleBag(IntArray(behaviors.size) { behaviors[it].weight }, rng)
    private val recentStarts = DoubleArray(config.budgetPerMinute.coerceAtLeast(1)) { Double.NEGATIVE_INFINITY }
    private var recentHead = 0
    private var untilNext = sampleInterval()
    private var active = -1
    private var elapsed = 0f
    private var sign = 1f
    private var last = -1

    fun update(dt: Float) {
        for (i in cooldownLeft.indices) if (cooldownLeft[i] > 0f) cooldownLeft[i] -= dt
        if (active >= 0) {
            elapsed += dt
            val b = behaviors[active]
            if (elapsed >= Envelope.duration(b.attack, b.hold, b.release)) active = -1
        }
        if (ctx.vigilance == Vigilance.Asleep || ctx.vigilance == Vigilance.Waking) return
        untilNext -= dt
        if (untilNext <= 0f) {
            untilNext = sampleInterval()
            if (active < 0) tryStart()
        }
    }

    fun cancel() {
        active = -1
    }

    fun apply(pose: FacePose) {
        val i = active
        if (i < 0) return
        val b = behaviors[i]
        val e = Envelope.value(elapsed, b.attack, b.hold, b.release)
        val ch = channels[i]
        val d = deltas[i]
        val m = mirrored[i]
        val v = pose.values
        for (k in ch.indices) v[ch[k]] += d[k] * e * (if (m[k]) sign else 1f)
    }

    private fun tryStart() {
        repeat(ATTEMPTS) {
            val i = bag.next()
            if (eligible(i)) {
                start(i)
                return
            }
        }
    }

    private fun eligible(i: Int): Boolean {
        val b = behaviors[i]
        if (cooldownLeft[i] > 0f || i == last) return false
        if (b.drowsyOnly && ctx.drowsiness < DROWSY_MIN) return false
        return !b.noticeable || budgetAvailable()
    }

    // The ring holds the last N noticeable start times; the oldest must be outside the window.
    private fun budgetAvailable(): Boolean = recentStarts[recentHead] <= ctx.time - BUDGET_WINDOW

    private fun start(i: Int) {
        val b = behaviors[i]
        active = i
        elapsed = 0f
        last = i
        sign = if (rng.nextFloat() < 0.5f) -1f else 1f
        cooldownLeft[i] = b.cooldown
        if (b.noticeable) {
            recentStarts[recentHead] = ctx.time
            recentHead = (recentHead + 1) % recentStarts.size
        }
        if (b.gazeSweep) gaze.lookAround()
        ctx.emit(FaceEvent.Behavior, b.name)
    }

    private fun sampleInterval(): Float =
        rng.nextLogNormal(config.meanInterval, config.spread) / (1f - ENERGY_DROP * ctx.drowsiness)

    private companion object {
        const val ATTEMPTS = 6
        const val DROWSY_MIN = 0.2f
        const val BUDGET_WINDOW = 60.0
        const val ENERGY_DROP = 0.5f
    }
}
