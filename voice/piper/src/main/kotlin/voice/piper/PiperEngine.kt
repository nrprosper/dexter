package voice.piper

import com.k2fsa.sherpa.onnx.GenerationConfig
import com.k2fsa.sherpa.onnx.OfflineTts
import com.k2fsa.sherpa.onnx.OfflineTtsConfig
import com.k2fsa.sherpa.onnx.OfflineTtsModelConfig
import com.k2fsa.sherpa.onnx.OfflineTtsVitsModelConfig
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.withContext
import voice.api.Availability
import voice.api.EngineCapabilities
import voice.api.EngineLocation
import voice.api.SentenceAudio
import voice.api.SentenceTtsEngine
import voice.api.SynthesisSettings
import voice.api.TextNormalizer
import voice.api.TtsException
import voice.api.VoiceDescriptor
import voice.api.VoiceQuality
import voice.piper.model.VoiceManifest
import voice.piper.model.VoiceStore
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

// Piper voices through sherpa-onnx (Apache-2.0), with the lexicon text frontend: no eSpeak NG, no GPL code.
// sherpa-onnx's OfflineTts isn't thread-safe, so every native call runs on one dedicated thread.
// Changing variation (Piper's noise scale) reloads the model, because sherpa-onnx fixes it at load time.
public class PiperEngine(private val store: VoiceStore, private val threads: Int = DEFAULT_THREADS) : SentenceTtsEngine() {
    private val executor = Executors.newSingleThreadExecutor { Thread(it, "piper-tts") }
    private val dispatcher = executor.asCoroutineDispatcher()
    private var loaded: OfflineTts? = null
    private var loadedKey = ""
    private var lexicon: Lexicon? = null
    private var lexiconPath = ""
    private val cancelled = AtomicBoolean(false)
    private val stopCheck = StopCheck(cancelled)

    override val id: String = ID
    override val capabilities: EngineCapabilities = EngineCapabilities(EngineLocation.Offline, streamingOutput = true)

    override suspend fun availability(): Availability =
        if (store.installedVoices().isNotEmpty()) Availability.Ready else Availability.NeedsDownload

    override suspend fun voices(): List<VoiceDescriptor> = store.manifest.voices.map {
        VoiceDescriptor(
            id = it.id,
            engineId = ID,
            name = it.name,
            language = it.language,
            sampleRate = it.sampleRate,
            speakers = it.speakers,
            quality = when (it.quality) {
                "low" -> VoiceQuality.Low
                "high" -> VoiceQuality.High
                else -> VoiceQuality.Medium
            },
            license = it.license.name,
            installed = store.isInstalled(it.id),
            downloadBytes = it.archive.download.size,
        )
    }

    override fun prepare(text: String): String {
        val normalized = TextNormalizer.normalize(text)
        return lexicon?.respell(normalized) ?: normalized
    }

    override suspend fun synthesizeSentence(text: String, settings: SynthesisSettings): SentenceAudio = withContext(dispatcher) {
        val tts = ensureLoaded(settings)
        cancelled.set(false)
        val config = GenerationConfig(speed = settings.speed, sid = settings.speakerId)
        val audio = try {
            tts.generateWithConfigAndCallback(text, config, stopCheck)
        } catch (e: RuntimeException) {
            throw TtsException("piper failed on: $text", e)
        }
        SentenceAudio(toPcm16(audio.samples), audio.sampleRate)
    }

    // Asks a sentence in progress to stop at its next sherpa-onnx callback.
    override fun interrupt() {
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

    private fun ensureLoaded(settings: SynthesisSettings): OfflineTts {
        val voice = (settings.voiceId?.let { store.manifest.voice(it) }?.takeIf { store.isInstalled(it.id) })
            ?: store.manifest.defaultVoice?.takeIf { store.isInstalled(it.id) }
            ?: store.installedVoices().firstOrNull()
            ?: throw TtsException("no Piper voice installed")
        val key = "${voice.id}@${settings.variation}"
        loaded?.let { if (key == loadedKey) return it }
        loaded?.release()
        loaded = null
        val dir = store.voiceDir(voice.id)
        val lexiconFile = voice.requires.firstNotNullOfOrNull { store.assetFile(it) }
            ?: throw TtsException("voice ${voice.id} has no lexicon")
        if (lexiconFile.path != lexiconPath) {
            lexicon = Lexicon.load(lexiconFile)
            lexiconPath = lexiconFile.path
        }
        val vits = OfflineTtsVitsModelConfig(
            model = dir.resolve(VoiceManifest.MODEL).path,
            tokens = dir.resolve(VoiceManifest.TOKENS).path,
            lexicon = lexiconFile.path,
            noiseScale = settings.variation,
        )
        val config = OfflineTtsConfig(model = OfflineTtsModelConfig(vits = vits, numThreads = threads, provider = "cpu"))
        val tts = try {
            OfflineTts(config = config)
        } catch (e: RuntimeException) {
            throw TtsException("could not load voice ${voice.id}", e)
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

    public companion object {
        public const val ID: String = "piper"
        private const val DEFAULT_THREADS = 2
        private const val FULL_SCALE = 32_767f
    }
}
