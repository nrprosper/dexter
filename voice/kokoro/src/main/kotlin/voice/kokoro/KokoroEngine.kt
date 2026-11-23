package voice.kokoro

import com.k2fsa.sherpa.onnx.GenerationConfig
import com.k2fsa.sherpa.onnx.OfflineTtsConfig
import com.k2fsa.sherpa.onnx.OfflineTtsKokoroModelConfig
import com.k2fsa.sherpa.onnx.OfflineTtsModelConfig
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
import voice.piper.Lexicon
import voice.piper.SherpaSynth
import voice.piper.model.VoiceManifest
import voice.piper.model.VoiceStore
import java.io.File

// Kokoro-82M (Apache-2.0) through sherpa-onnx: a much more natural offline voice than Piper, as an optional ~130 MB
// download. English only, with misaki's US/UK lexicons (no eSpeak NG). Voices are speakers inside one model, so the
// voice id ("kokoro:af_heart") picks the speaker id, and British speakers switch the model to the UK lexicon.
// For speed on phones: 4 inference threads, a warm-up when the app opens, and an early first sentence.
public class KokoroEngine(public val store: VoiceStore, private val threads: Int = DEFAULT_THREADS) : SentenceTtsEngine() {
    private val synth = SherpaSynth("kokoro-tts")

    @Volatile
    private var lexicon: Lexicon? = null
    private var lexiconPath = ""

    override val id: String = ID
    override val capabilities: EngineCapabilities = EngineCapabilities(EngineLocation.Offline, streamingOutput = true)
    override val quickStartChars: Int = QUICK_START_CHARS

    public val modelId: String get() = store.manifest.defaultVoice?.id ?: MODEL_ID

    public val installed: Boolean get() = store.isInstalled(modelId)

    override suspend fun availability(): Availability = if (installed) Availability.Ready else Availability.NeedsDownload

    override suspend fun voices(): List<VoiceDescriptor> {
        val ready = installed
        return KokoroVoices.english.map {
            VoiceDescriptor(
                id = it.id,
                engineId = ID,
                name = it.label,
                language = if (it.british) "en-GB" else "en-US",
                sampleRate = SAMPLE_RATE,
                quality = VoiceQuality.High,
                license = "Apache-2.0",
                installed = ready,
            )
        }
    }

    override fun prepare(text: String): String {
        val normalized = TextNormalizer.normalize(text)
        return lexicon?.respell(normalized) ?: normalized
    }

    override suspend fun synthesizeSentence(text: String, settings: SynthesisSettings): SentenceAudio {
        if (!installed) throw TtsException("Kokoro isn't downloaded")
        val speaker = KokoroVoices.byId(settings.voiceId) ?: KokoroVoices.default
        val dir = store.voiceDir(modelId)
        val lexiconFile = File(dir, if (speaker.british) GB_LEXICON else US_LEXICON)
        val config = GenerationConfig(speed = settings.speed, sid = speaker.sid)
        return synth.generate("${modelId}@${lexiconFile.name}", {
            if (lexiconFile.path != lexiconPath) {
                lexicon = Lexicon.load(lexiconFile)
                lexiconPath = lexiconFile.path
            }
            val kokoro = OfflineTtsKokoroModelConfig(
                model = File(dir, VoiceManifest.MODEL).path,
                voices = File(dir, VOICES).path,
                tokens = File(dir, VoiceManifest.TOKENS).path,
                lexicon = lexiconFile.path,
            )
            OfflineTtsConfig(model = OfflineTtsModelConfig(kokoro = kokoro, numThreads = threads, provider = "cpu"))
        }, text, config)
    }

    override fun interrupt() {
        synth.interrupt()
    }

    // Loads the model and lexicon with a tiny, discarded sentence, so the first real reply doesn't wait seconds.
    override suspend fun warmUp(settings: SynthesisSettings) {
        if (!installed) return
        runCatching { synthesizeSentence(WARM_UP, settings) }
    }

    public suspend fun close() {
        synth.close()
    }

    public companion object {
        public const val ID: String = "kokoro"
        public const val MODEL_ID: String = "kokoro-v1_0-int8"
        public const val SAMPLE_RATE: Int = 24_000
        private const val RESOURCE = "/voice/kokoro/kokoro.json"
        private const val VOICES = "voices.bin"
        private const val US_LEXICON = "lexicon-us-en.txt"
        private const val GB_LEXICON = "lexicon-gb-en.txt"
        private const val DEFAULT_THREADS = 4
        private const val QUICK_START_CHARS = 60
        private const val WARM_UP = "Hi."

        public fun manifest(): VoiceManifest {
            val text = KokoroEngine::class.java.getResourceAsStream(RESOURCE)?.bufferedReader()?.use { it.readText() }
                ?: throw IllegalStateException("missing bundled $RESOURCE")
            return VoiceManifest.decode(text)
        }
    }
}
