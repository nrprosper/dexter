package voice.piper

import com.k2fsa.sherpa.onnx.GenerationConfig
import com.k2fsa.sherpa.onnx.OfflineTtsConfig
import com.k2fsa.sherpa.onnx.OfflineTtsModelConfig
import com.k2fsa.sherpa.onnx.OfflineTtsVitsModelConfig
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

// Piper voices through sherpa-onnx (Apache-2.0), with the lexicon text frontend: no eSpeak NG, no GPL code.
// Changing variation (Piper's noise scale) reloads the model, because sherpa-onnx fixes it at load time.
public class PiperEngine(private val store: VoiceStore, private val threads: Int = DEFAULT_THREADS) : SentenceTtsEngine() {
    private val synth = SherpaSynth("piper-tts")

    @Volatile
    private var lexicon: Lexicon? = null
    private var lexiconPath = ""

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

    override suspend fun synthesizeSentence(text: String, settings: SynthesisSettings): SentenceAudio {
        val voice = (settings.voiceId?.let { store.manifest.voice(it) }?.takeIf { store.isInstalled(it.id) })
            ?: store.manifest.defaultVoice?.takeIf { store.isInstalled(it.id) }
            ?: store.installedVoices().firstOrNull()
            ?: throw TtsException("no Piper voice installed")
        val dir = store.voiceDir(voice.id)
        val lexiconFile = voice.requires.firstNotNullOfOrNull { store.assetFile(it) }
            ?: throw TtsException("voice ${voice.id} has no lexicon")
        val config = GenerationConfig(speed = settings.speed, sid = settings.speakerId)
        return synth.generate("${voice.id}@${settings.variation}", {
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
            OfflineTtsConfig(model = OfflineTtsModelConfig(vits = vits, numThreads = threads, provider = "cpu"))
        }, text, config)
    }

    // Asks a sentence in progress to stop at its next sherpa-onnx callback.
    override fun interrupt() {
        synth.interrupt()
    }

    override suspend fun warmUp(settings: SynthesisSettings) {
        if (store.installedVoices().isEmpty()) return
        runCatching { synthesizeSentence("Hi.", settings) }
    }

    public suspend fun close() {
        synth.close()
    }

    public companion object {
        public const val ID: String = "piper"
        private const val DEFAULT_THREADS = 2
    }
}
