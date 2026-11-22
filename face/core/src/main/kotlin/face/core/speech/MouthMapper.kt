package face.core.speech

import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

// Raw track loudness → mouth openness: gate, soft compression, gain, then a fast-attack/slow-release follower.
// A hysteresis gate holds the mouth shut for at least minClosure after each syllable drops (no flapping)
// and open for at least minOpen; it reopens only on a rising syllable, never on a release tail. Brightness (0 round … 1 wide) is smoothed separately.
internal class MouthMapper(var config: MouthConfig) {
    var level = 0f
        private set
    var shaped = 0f
        private set
    var envelope = 0f
        private set
    var open = 0f
        private set
    var brightness = HALF
        private set
    var isOpen = false
        private set
    private var stateTime = 0f
    private var peak = 0f

    fun reset() {
        level = 0f
        shaped = 0f
        envelope = 0f
        open = 0f
        brightness = HALF
        isOpen = false
        stateTime = config.minClosure
        peak = 0f
    }

    // rawLevel and rawBrightness are 0..1; gain comes from the style and emotion.
    fun step(dt: Float, rawLevel: Float, rawBrightness: Float, gain: Float) {
        val c = config
        level = rawLevel.coerceIn(0f, 1f)
        val gated = if (level <= c.gate) 0f else (level - c.gate) / (1f - c.gate)
        shaped = min(1f, gated.pow(c.compression) * c.gain * gain)
        val tau = if (shaped > envelope) c.attack else c.release
        envelope += (shaped - envelope) * follow(dt, tau)
        stateTime += dt
        if (isOpen) {
            peak = max(envelope, peak - c.peakDecay * dt)
            val dropped = envelope < peak * c.closeRatio || envelope < c.closeLevel
            if (stateTime >= c.minOpen && dropped) {
                isOpen = false
                stateTime = 0f
            }
        } else if (stateTime >= c.minClosure && shaped > c.openLevel && shaped > envelope) {
            isOpen = true
            stateTime = 0f
            peak = envelope
        }
        open = if (isOpen) envelope else 0f
        val bright = if (open > 0f) rawBrightness.coerceIn(0f, 1f) else HALF
        brightness += (bright - brightness) * follow(dt, c.shapeSmoothing)
    }

    private fun follow(dt: Float, tau: Float): Float = 1f - exp(-dt / tau)

    private companion object {
        const val HALF = 0.5f
    }
}
