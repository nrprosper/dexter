package face.speech

import kotlin.math.exp
import kotlin.math.log10
import kotlin.math.sqrt

// Analyses PCM as it is queued for playback: one envelope value per hop, stored by sample position in a ring.
// One writer thread (the player) and one reader thread (the face) — the reader only looks at hops behind `written`.
// Pure Kotlin, so it is unit-tested on the JVM with synthetic PCM.
public class EnvelopeBuilder(public val config: EnvelopeConfig = EnvelopeConfig()) {
    private val capacity = config.capacitySeconds * 1_000 / config.hopMillis
    private val loudness = FloatArray(capacity)
    private val brightness = FloatArray(capacity)
    private val emphasis = FloatArray(capacity)

    @Volatile
    public var written: Long = 0
        private set

    public var hopSize: Int = 0
        private set
    public var sampleRate: Int = 0
        private set

    private var lowpassA = 0f
    private var lowState = 0f
    private var sumAll = 0.0
    private var sumLow = 0.0
    private var sumHigh = 0.0
    private var inHop = 0
    private var averageDb = 0f
    private var averageA = 0f

    public fun start(sampleRate: Int) {
        require(sampleRate > 0) { "sampleRate must be positive" }
        this.sampleRate = sampleRate
        hopSize = sampleRate * config.hopMillis / 1_000
        lowpassA = exp(-2.0 * Math.PI * config.splitHz / sampleRate).toFloat()
        averageA = exp(-config.hopMillis.toFloat() / config.averageMillis)
        lowState = 0f
        sumAll = 0.0
        sumLow = 0.0
        sumHigh = 0.0
        inHop = 0
        averageDb = Float.NaN
        written = 0
    }

    public fun analyze(samples: ShortArray, offset: Int = 0, length: Int = samples.size - offset) {
        check(hopSize > 0) { "call start() first" }
        val a = lowpassA
        for (i in offset until offset + length) {
            val x = samples[i] / FULL_SCALE
            lowState = (1f - a) * x + a * lowState
            val high = x - lowState
            sumAll += x * x
            sumLow += lowState * lowState
            sumHigh += high * high
            if (++inHop == hopSize) finishHop()
        }
    }

    // Value at hop index, or -1 when that hop isn't written yet or has been overwritten.
    public fun loudnessAt(hop: Long): Float = if (readable(hop)) loudness[(hop % capacity).toInt()] else -1f

    public fun brightnessAt(hop: Long): Float = if (readable(hop)) brightness[(hop % capacity).toInt()] else HALF

    public fun emphasisAt(hop: Long): Float = if (readable(hop)) emphasis[(hop % capacity).toInt()] else 0f

    private fun readable(hop: Long): Boolean = hop >= 0 && hop < written && hop >= written - capacity + GUARD

    private fun finishHop() {
        val c = config
        val rms = sqrt(sumAll / hopSize)
        val db = if (rms <= 0.0) c.floorDb else (20.0 * log10(rms)).toFloat().coerceAtLeast(c.floorDb)
        val level = ((db - c.floorDb) / -c.floorDb).coerceIn(0f, 1f)
        val low = sqrt(sumLow / hopSize)
        val high = sqrt(sumHigh / hopSize)
        val share = if (low + high > 0.0) (high / (low + high)).toFloat() else 0f
        val bright = ((share - c.brightnessLow) / (c.brightnessHigh - c.brightnessLow)).coerceIn(0f, 1f)
        val voiced = db > c.voicedDb
        if (voiced && averageDb.isNaN()) averageDb = db
        val accent = if (voiced) ((db - averageDb - c.emphasisDb) / c.emphasisRangeDb).coerceIn(0f, 1f) else 0f
        if (voiced) averageDb = averageA * averageDb + (1f - averageA) * db
        val slot = (written % capacity).toInt()
        loudness[slot] = level
        brightness[slot] = bright
        emphasis[slot] = accent
        written++
        sumAll = 0.0
        sumLow = 0.0
        sumHigh = 0.0
        inHop = 0
    }

    private companion object {
        const val FULL_SCALE = 32_768f
        const val HALF = 0.5f
        const val GUARD = 8
    }
}
