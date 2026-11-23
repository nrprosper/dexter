package face.speech

import kotlin.math.PI
import kotlin.math.sin

internal object Synthetic {
    // A tone whose amplitude follows envelope(t), as 16-bit PCM.
    fun tone(seconds: Double, rate: Int, hz: Double = 220.0, envelope: (Double) -> Double): ShortArray =
        ShortArray((seconds * rate).toInt()) { i ->
            val t = i.toDouble() / rate
            (envelope(t) * sin(2 * PI * hz * t) * 32_767).toInt().coerceIn(-32_768, 32_767).toShort()
        }
}

// Plays instantly; the heard position follows a clock the test controls (or real time from open()).
internal class FakeSink(private val clock: () -> Long = { System.nanoTime() }) : AudioSink {
    var rate = 0
    var startNanos = 0L
    var written = 0L
    var flushed = false
    var closed = false
    var writeDelayMillis = 0L

    override fun open(sampleRate: Int) {
        rate = sampleRate
        startNanos = clock()
    }

    override fun write(samples: ShortArray, offset: Int, length: Int): Int {
        if (writeDelayMillis > 0) Thread.sleep(writeDelayMillis)
        written += length
        return length
    }

    override fun playedFrames(nowNanos: Long): Long =
        if (nowNanos < startNanos) -1 else (nowNanos - startNanos) * rate / 1_000_000_000L

    override suspend fun drain(writtenFrames: Long, timeoutMillis: Long, stopped: () -> Boolean): Boolean = !stopped()

    override fun fadeOutAndFlush() {
        flushed = true
    }

    override fun close() {
        closed = true
    }
}
