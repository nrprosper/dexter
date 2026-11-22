package face.core.behavior

import face.api.MouthFrame
import face.api.SpeechStyle
import face.api.SpeechTrack
import face.core.rig.Channel
import face.core.rig.FacePose

// Phase 4 pass-through: the attached track's mouth frame nudges the mouth channels. Phase 6 replaces it with MouthMapper.
internal class SpeechTrackLayer {
    private val frame = MouthFrame()
    private var track: SpeechTrack? = null
    private var energy = 0.5f
    var nowNanos = 0L

    val speaking: Boolean get() = track != null

    fun attach(track: SpeechTrack, style: SpeechStyle) {
        this.track = track
        energy = style.energy
    }

    fun detach() {
        track = null
    }

    fun apply(pose: FacePose) {
        val t = track ?: return
        if (!t.isActive) return
        frame.clear()
        t.sample(nowNanos, frame)
        val gain = BASE_GAIN + ENERGY_GAIN * energy
        pose[Channel.MouthOpen] = pose[Channel.MouthOpen] + frame.openness * gain
        pose[Channel.MouthRoundness] = pose[Channel.MouthRoundness] + frame.roundness * ROUND_GAIN
        pose[Channel.MouthWidth] = pose[Channel.MouthWidth] + (frame.width - 0.5f) * WIDTH_GAIN
    }

    private companion object {
        const val BASE_GAIN = 0.45f
        const val ENERGY_GAIN = 0.5f
        const val ROUND_GAIN = 0.4f
        const val WIDTH_GAIN = 0.3f
    }
}
