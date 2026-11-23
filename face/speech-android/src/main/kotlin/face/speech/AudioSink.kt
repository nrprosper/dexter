package face.speech

// Where the player sends PCM; the Android implementation is AudioTrackSink. Tests use a fake with a scripted clock.
public interface AudioSink {
    public fun open(sampleRate: Int)

    // Blocking; returns how many samples were accepted (fewer when stopped).
    public fun write(samples: ShortArray, offset: Int, length: Int): Int

    // The sample being heard at nowNanos (System.nanoTime base), or -1 before playback has started. Must not allocate.
    public fun playedFrames(nowNanos: Long): Long

    // Lets queued audio play out; true once it has, false when stopped() turned true or timeoutMillis passed.
    public suspend fun drain(writtenFrames: Long, timeoutMillis: Long, stopped: () -> Boolean): Boolean

    // Short fade to silence, then drop whatever is still queued. No click.
    public fun fadeOutAndFlush()

    public fun close()
}
