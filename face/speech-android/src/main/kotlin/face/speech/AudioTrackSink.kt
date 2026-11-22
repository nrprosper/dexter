package face.speech

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTimestamp
import android.media.AudioTrack
import android.media.VolumeShaper
import kotlinx.coroutines.delay
import kotlin.math.max

// AudioTrack in streaming mode. The heard position comes from AudioTrack.getTimestamp() (it accounts for output latency,
// including most Bluetooth routes), polled at most every POLL_NANOS and extrapolated in between; head position as fallback.
public class AudioTrackSink(context: Context) : AudioSink {
    private val audio = context.getSystemService(AudioManager::class.java)
    private val stamp = AudioTimestamp()
    private val attributes = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_ASSISTANT)
        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
        .build()
    private var focus: AudioFocusRequest? = null
    private var rate = 0

    @Volatile
    private var track: AudioTrack? = null
    private var stampFrames = -1L
    private var stampNanos = 0L
    private var lastPoll = Long.MIN_VALUE

    override fun open(sampleRate: Int) {
        rate = sampleRate
        val format = AudioFormat.Builder()
            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
            .setSampleRate(sampleRate)
            .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
            .build()
        val min = AudioTrack.getMinBufferSize(sampleRate, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT)
        val t = AudioTrack.Builder()
            .setAudioAttributes(attributes)
            .setAudioFormat(format)
            .setBufferSizeInBytes(max(min, sampleRate * BYTES_PER_SAMPLE * BUFFER_MILLIS / 1_000))
            .setTransferMode(AudioTrack.MODE_STREAM)
            .build()
        val request = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
            .setAudioAttributes(attributes)
            .build()
        audio?.requestAudioFocus(request)
        focus = request
        t.play()
        track = t
    }

    override fun write(samples: ShortArray, offset: Int, length: Int): Int {
        val t = track ?: return 0
        val n = t.write(samples, offset, length, AudioTrack.WRITE_BLOCKING)
        return if (n < 0) 0 else n
    }

    override fun playedFrames(nowNanos: Long): Long {
        val t = track ?: return -1
        return try {
            if (nowNanos - lastPoll > POLL_NANOS || lastPoll == Long.MIN_VALUE) {
                lastPoll = nowNanos
                if (t.getTimestamp(stamp) && stamp.framePosition > 0) {
                    stampFrames = stamp.framePosition
                    stampNanos = stamp.nanoTime
                }
            }
            if (stampFrames > 0) {
                max(0L, stampFrames + (nowNanos - stampNanos) * rate / NANOS_PER_SECOND)
            } else {
                val head = t.playbackHeadPosition.toLong() and UINT_MASK
                if (head > 0) head else -1
            }
        } catch (_: IllegalStateException) {
            -1
        }
    }

    override suspend fun drain(writtenFrames: Long, timeoutMillis: Long, stopped: () -> Boolean): Boolean {
        val t = track ?: return true
        var waited = 0L
        while (waited < timeoutMillis) {
            if (stopped()) return false
            val head = t.playbackHeadPosition.toLong() and UINT_MASK
            if (head >= writtenFrames) return true
            delay(DRAIN_POLL_MILLIS)
            waited += DRAIN_POLL_MILLIS
        }
        return false
    }

    override fun fadeOutAndFlush() {
        val t = track ?: return
        try {
            if (t.playState == AudioTrack.PLAYSTATE_PLAYING) {
                val shaper = t.createVolumeShaper(FADE)
                shaper.apply(VolumeShaper.Operation.PLAY)
                Thread.sleep(FADE_MILLIS + FADE_MARGIN_MILLIS)
                shaper.close()
            }
            t.pause()
            t.flush()
        } catch (_: IllegalStateException) {
        }
    }

    override fun close() {
        val t = track ?: return
        track = null
        try {
            t.stop()
        } catch (_: IllegalStateException) {
        }
        t.release()
        focus?.let { audio?.abandonAudioFocusRequest(it) }
        focus = null
    }

    private companion object {
        const val BYTES_PER_SAMPLE = 2
        const val BUFFER_MILLIS = 120
        const val POLL_NANOS = 50_000_000L
        const val NANOS_PER_SECOND = 1_000_000_000L
        const val UINT_MASK = 0xFFFF_FFFFL
        const val DRAIN_POLL_MILLIS = 10L
        const val FADE_MILLIS = 15L
        const val FADE_MARGIN_MILLIS = 5L
        val FADE: VolumeShaper.Configuration = VolumeShaper.Configuration.Builder()
            .setDuration(FADE_MILLIS)
            .setCurve(floatArrayOf(0f, 1f), floatArrayOf(1f, 0f))
            .setInterpolatorType(VolumeShaper.Configuration.INTERPOLATOR_TYPE_LINEAR)
            .build()
    }
}
