package face.speech

import kotlin.math.abs

// Applied to each block before it is played, in place: a soft limiter (scales the block down when its peak would clip,
// instead of hard-clipping into crackle) and short linear fades at both ends, so sentence edges never click.
internal object BlockShaping {
    const val FADE_MILLIS = 5
    const val LIMIT = 0.9f

    fun shape(samples: ShortArray, sampleRate: Int) {
        if (samples.isEmpty()) return
        var peak = 0
        for (s in samples) peak = maxOf(peak, abs(s.toInt()))
        val ceiling = (LIMIT * FULL).toInt()
        if (peak > ceiling) {
            val gain = ceiling.toFloat() / peak
            for (i in samples.indices) samples[i] = (samples[i] * gain).toInt().toShort()
        }
        val fade = minOf(sampleRate * FADE_MILLIS / 1_000, samples.size / 2)
        for (i in 0 until fade) {
            val g = i.toFloat() / fade
            samples[i] = (samples[i] * g).toInt().toShort()
            val j = samples.size - 1 - i
            samples[j] = (samples[j] * g).toInt().toShort()
        }
    }

    private const val FULL = 32_767f
}
