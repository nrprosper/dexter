package voice.piper

import com.k2fsa.sherpa.onnx.GenerationConfig
import com.k2fsa.sherpa.onnx.OfflineTts
import com.k2fsa.sherpa.onnx.OfflineTtsConfig
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.withContext
import voice.api.SentenceAudio
import voice.api.TtsException
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

// Runs one sherpa-onnx OfflineTts at a time on its own thread (OfflineTts isn't thread-safe), loading it lazily and
// reloading only when the caller's key (voice, lexicon, settings fixed at load time) changes. Shared by Piper and Kokoro.
public class SherpaSynth(threadName: String) {
    private val executor = Executors.newSingleThreadExecutor { Thread(it, threadName) }
    private val dispatcher = executor.asCoroutineDispatcher()
    private var loaded: OfflineTts? = null
    private var loadedKey = ""
    private val cancelled = AtomicBoolean(false)
    private val stopCheck = StopCheck(cancelled)

    public suspend fun generate(key: String, load: () -> OfflineTtsConfig, text: String, config: GenerationConfig): SentenceAudio =
        withContext(dispatcher) {
            val tts = ensure(key, load)
            cancelled.set(false)
            val audio = try {
                tts.generateWithConfigAndCallback(text, config, stopCheck)
            } catch (e: RuntimeException) {
                throw TtsException("synthesis failed on: $text", e)
            }
            SentenceAudio(toPcm16(audio.samples), audio.sampleRate)
        }

    // Asks a sentence in progress to stop at its next sherpa-onnx callback.
    public fun interrupt() {
        cancelled.set(true)
    }

    public suspend fun close() {
        withContext(dispatcher) {
            loaded?.release()
            loaded = null
            loadedKey = ""
        }
        executor.shutdown()
    }

    private fun ensure(key: String, load: () -> OfflineTtsConfig): OfflineTts {
        loaded?.let { if (key == loadedKey) return it }
        loaded?.release()
        loaded = null
        val tts = try {
            OfflineTts(config = load())
        } catch (e: RuntimeException) {
            throw TtsException("could not load the voice model", e)
        }
        loaded = tts
        loadedKey = key
        return tts
    }

    private fun toPcm16(samples: FloatArray): ShortArray =
        ShortArray(samples.size) { (samples[it] * FULL_SCALE).toInt().coerceIn(-32_768, 32_767).toShort() }

    // sherpa-onnx's JNI looks the callback up as invoke(float[]): Integer, which a Kotlin lambda doesn't have;
    // a class implementing the function type does. Returning 0 asks sherpa-onnx to stop.
    internal class StopCheck(private val cancelled: AtomicBoolean) : (FloatArray) -> Int {
        override fun invoke(samples: FloatArray): Int = if (cancelled.get()) 0 else 1
    }

    private companion object {
        const val FULL_SCALE = 32_767f
    }
}
