package face.core.speech

import face.core.behavior.FaceEvent
import face.core.behavior.LifeContext
import face.core.rig.Channel
import face.core.rig.FacePose
import face.core.time.Rng
import kotlin.math.PI
import kotlin.math.min
import kotlin.math.sin

// Small punctuation gestures on emphasis peaks while the mouth is open: a brow lift, a brow and lid lift, or a nod.
internal class BeatGestures(private val rng: Rng, private val ctx: LifeContext) {
    var config = BeatConfig()
    var value = 0f
        private set
    private var elapsed = -1f
    private var cooldown = 0f
    private var kind = 0
    private var strength = 0f

    fun reset() {
        elapsed = -1f
        cooldown = 0f
        value = 0f
    }

    fun update(dt: Float, emphasis: Float, mouthOpen: Boolean, rate: Float) {
        val c = config
        cooldown -= dt
        if (elapsed >= 0f) {
            elapsed += dt
            if (elapsed >= c.duration) elapsed = -1f
        }
        if (rate > 0f && mouthOpen && elapsed < 0f && cooldown <= 0f && emphasis > c.threshold) {
            elapsed = 0f
            kind = rng.nextInt(KINDS)
            strength = min(1f, emphasis)
            cooldown = c.cooldown / rate
            ctx.emit(FaceEvent.Behavior, BEAT)
        }
        value = if (elapsed >= 0f) strength * sin(PI.toFloat() * elapsed / c.duration) else 0f
    }

    fun apply(pose: FacePose, headScale: Float) {
        val v = value
        if (v == 0f) return
        val c = config
        if (kind == BROW || kind == BROW_LID) {
            pose[Channel.BrowHeightL] = pose[Channel.BrowHeightL] + c.brow * v
            pose[Channel.BrowHeightR] = pose[Channel.BrowHeightR] + c.brow * v
        }
        if (kind == BROW_LID) {
            pose[Channel.EyeOpenL] = pose[Channel.EyeOpenL] + c.lid * v
            pose[Channel.EyeOpenR] = pose[Channel.EyeOpenR] + c.lid * v
        }
        if (kind == NOD) pose[Channel.HeadY] = pose[Channel.HeadY] - c.nod * v * headScale
    }

    private companion object {
        const val BEAT = "speech beat"
        const val KINDS = 3
        const val BROW = 0
        const val BROW_LID = 1
        const val NOD = 2
    }
}
