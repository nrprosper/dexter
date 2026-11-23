package face.speech

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlin.math.min

public class PcmBlock(public val samples: ShortArray, public val sampleRate: Int)

// The one player every engine feeds. Each block is analysed into the envelope just before it is written,
// so the envelope is always ahead of what is heard. One utterance at a time; stop() works from any thread.
public class PcmPlayer(
    private val sinkFactory: () -> AudioSink,
    envelopeConfig: EnvelopeConfig = EnvelopeConfig(),
    private val io: CoroutineDispatcher = Dispatchers.IO,
) {
    private val envelope = EnvelopeBuilder(envelopeConfig)
    private val mutex = Mutex()

    @Volatile
    private var stopCount = 0

    public val track: AmplitudeSpeechTrack = AmplitudeSpeechTrack(envelope)

    // Returns when the audio has played out (true), or early after stop() or cancellation (false).
    public suspend fun play(blocks: Flow<PcmBlock>, onStart: () -> Unit = {}): Boolean = mutex.withLock {
        val generation = stopCount
        val stopped = { stopCount != generation }
        withContext(io) {
            var sink: AudioSink? = null
            var written = 0L
            var finished = false
            try {
                blocks.collect { block ->
                    if (stopped()) throw Stopped()
                    val s = sink ?: open(block.sampleRate, onStart).also { sink = it }
                    require(block.sampleRate == envelope.sampleRate) {
                        "sample rate changed mid-utterance: ${envelope.sampleRate} → ${block.sampleRate}"
                    }
                    written += write(s, block.samples, stopped)
                }
                val s = sink
                if (s != null && !stopped()) {
                    s.write(ShortArray(envelope.sampleRate * PAD_MILLIS / 1_000), 0, envelope.sampleRate * PAD_MILLIS / 1_000)
                    finished = s.drain(written, written * 1_000 / envelope.sampleRate + DRAIN_SLACK_MILLIS, stopped)
                }
            } catch (_: Stopped) {
            } finally {
                track.end()
                sink?.let {
                    if (!finished) it.fadeOutAndFlush()
                    it.close()
                }
            }
            finished
        }
    }

    // Mouth starts winding down at once; audio fades out within a few milliseconds and the queue is dropped.
    public fun stop() {
        stopCount++
        track.end()
    }

    private fun open(sampleRate: Int, onStart: () -> Unit): AudioSink {
        val sink = sinkFactory()
        sink.open(sampleRate)
        envelope.start(sampleRate)
        track.begin(sink)
        onStart()
        return sink
    }

    private suspend fun write(sink: AudioSink, samples: ShortArray, stopped: () -> Boolean): Long {
        val piece = envelope.hopSize * PIECE_HOPS
        var at = 0
        var total = 0L
        while (at < samples.size) {
            currentCoroutineContext().ensureActive()
            if (stopped()) throw Stopped()
            val n = min(piece, samples.size - at)
            envelope.analyze(samples, at, n)
            var done = 0
            while (done < n) {
                val w = sink.write(samples, at + done, n - done)
                if (w <= 0 || stopped()) throw Stopped()
                done += w
            }
            at += n
            total += n
        }
        return total
    }

    private class Stopped : CancellationException("playback stopped")

    private companion object {
        const val PIECE_HOPS = 2
        const val PAD_MILLIS = 120
        const val DRAIN_SLACK_MILLIS = 2_000L
    }
}
