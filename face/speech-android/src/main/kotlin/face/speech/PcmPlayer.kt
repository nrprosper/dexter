package face.speech

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.min

// tag is the caller's label for the block (a sentence index); heardTag reports it once the block is audible.
public class PcmBlock(public val samples: ShortArray, public val sampleRate: Int, public val tag: Int = -1)

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

    private val heard = MutableStateFlow(-1)

    // The tag of the block being heard now (-1 before the first one plays, and after playback ends).
    public val heardTag: StateFlow<Int> = heard

    // Returns when the audio has played out (true), or early after stop() or cancellation (false).
    public suspend fun play(blocks: Flow<PcmBlock>, onStart: () -> Unit = {}): Boolean = mutex.withLock {
        val generation = stopCount
        val stopped = { stopCount != generation }
        withContext(io) {
            var sink: AudioSink? = null
            var written = 0L
            var finished = false
            val starts = LongArray(MAX_TAGGED)
            val tags = IntArray(MAX_TAGGED)
            val tagged = AtomicInteger(0)
            val watcher = launch {
                while (true) {
                    delay(HEARD_POLL_MILLIS)
                    val head = sink?.headFrames() ?: -1L
                    if (head < 0) continue
                    var i = tagged.get() - 1
                    while (i >= 0 && starts[i] > head) i--
                    if (i >= 0 && tags[i] != heard.value) heard.value = tags[i]
                }
            }
            try {
                blocks.collect { block ->
                    if (stopped()) throw Stopped()
                    val s = sink ?: open(block.sampleRate, onStart).also { sink = it }
                    require(block.sampleRate == envelope.sampleRate) {
                        "sample rate changed mid-utterance: ${envelope.sampleRate} → ${block.sampleRate}"
                    }
                    val n = tagged.get()
                    if (block.tag >= 0 && n < MAX_TAGGED) {
                        starts[n] = written
                        tags[n] = block.tag
                        tagged.set(n + 1)
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
                watcher.cancel()
                heard.value = -1
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
        const val MAX_TAGGED = 256
        const val HEARD_POLL_MILLIS = 40L
        const val PAD_MILLIS = 120
        const val DRAIN_SLACK_MILLIS = 2_000L
    }
}
