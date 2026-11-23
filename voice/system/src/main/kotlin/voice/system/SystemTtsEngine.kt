package voice.system

import android.content.Context
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.speech.tts.Voice
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import voice.api.Availability
import voice.api.EngineCapabilities
import voice.api.EngineLocation
import voice.api.SentenceAudio
import voice.api.SentenceTtsEngine
import voice.api.SynthesisSettings
import voice.api.TtsException
import voice.api.VoiceDescriptor
import voice.api.VoiceQuality
import java.io.File
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

// Fallback when no Piper voice is installed: Android's TextToSpeech, rendered to a WAV per sentence and played
// through our own player, so the face still gets the amplitude envelope. Only offline system voices are offered.
public class SystemTtsEngine(context: Context) : SentenceTtsEngine() {
    private val app = context.applicationContext
    private val cacheDir = File(app.cacheDir, "system-tts")
    private val initLock = Mutex()
    private val pending = ConcurrentHashMap<String, CompletableDeferred<Boolean>>()
    private val counter = AtomicLong()
    private var tts: TextToSpeech? = null
    private var ready = false

    override val id: String = ID
    override val capabilities: EngineCapabilities = EngineCapabilities(EngineLocation.Offline, streamingOutput = true)

    override suspend fun availability(): Availability =
        if (engine() != null) Availability.Ready else Availability.Unavailable("no text-to-speech engine on this phone")

    override suspend fun voices(): List<VoiceDescriptor> {
        val t = engine() ?: return emptyList()
        val voices = withContext(Dispatchers.Main) { runCatching { t.voices }.getOrNull().orEmpty() }
        val language = Locale.getDefault().language
        return voices.filter { !it.isNetworkConnectionRequired && !it.features.orEmpty().contains(NOT_INSTALLED) }
            .sortedWith(compareBy({ it.locale.language != language }, { it.locale.language != "en" }, { -it.quality }, { it.latency }, { it.name }))
            .map {
                VoiceDescriptor(
                    id = it.name,
                    engineId = ID,
                    name = it.name,
                    language = it.locale.toLanguageTag(),
                    sampleRate = 0,
                    quality = when {
                        it.quality >= Voice.QUALITY_HIGH -> VoiceQuality.High
                        it.quality >= Voice.QUALITY_NORMAL -> VoiceQuality.Medium
                        else -> VoiceQuality.Low
                    },
                    license = "System voice",
                )
            }
    }

    override suspend fun synthesizeSentence(text: String, settings: SynthesisSettings): SentenceAudio {
        val t = engine() ?: throw TtsException("no text-to-speech engine")
        cacheDir.mkdirs()
        val utterance = "dexter-${counter.incrementAndGet()}"
        val file = File(cacheDir, "$utterance.wav")
        val done = CompletableDeferred<Boolean>()
        pending[utterance] = done
        try {
            withContext(Dispatchers.Main) {
                val chosen = settings.voiceId?.let { wanted -> t.voices?.firstOrNull { it.name == wanted } } ?: best(t)
                if (chosen != null && t.voice?.name != chosen.name) t.voice = chosen
                t.setSpeechRate(settings.speed)
                t.setPitch(settings.pitch)
                if (t.synthesizeToFile(text, Bundle(), file, utterance) != TextToSpeech.SUCCESS) done.complete(false)
            }
            if (!done.await()) throw TtsException("system TTS failed on: $text")
            val wav = withContext(Dispatchers.IO) { WavReader.read(file.readBytes()) }
            return SentenceAudio(wav.samples, wav.sampleRate)
        } catch (e: kotlinx.coroutines.CancellationException) {
            withContext(kotlinx.coroutines.NonCancellable + Dispatchers.Main) { t.stop() }
            throw e
        } finally {
            pending.remove(utterance)
            file.delete()
        }
    }

    // The best installed offline voice for the phone's language (English otherwise): highest quality, then lowest latency.
    private fun best(t: TextToSpeech): Voice? {
        bestVoice?.let { return it }
        val all = runCatching { t.voices }.getOrNull().orEmpty()
            .filter { !it.isNetworkConnectionRequired && !it.features.orEmpty().contains(NOT_INSTALLED) }
        val language = Locale.getDefault().language
        val pool = all.filter { it.locale.language == language }.ifEmpty { all.filter { it.locale.language == "en" } }.ifEmpty { all }
        return pool.sortedWith(compareBy({ -it.quality }, { it.latency })).firstOrNull()?.also { bestVoice = it }
    }

    private var bestVoice: Voice? = null

    public suspend fun close() {
        withContext(Dispatchers.Main) {
            tts?.shutdown()
            tts = null
            ready = false
        }
    }

    private suspend fun engine(): TextToSpeech? = initLock.withLock {
        if (ready) return tts
        tts?.let { return null }
        val status = CompletableDeferred<Int>()
        val created = withContext(Dispatchers.Main) {
            TextToSpeech(app) { status.complete(it) }.also { it.setOnUtteranceProgressListener(listener) }
        }
        tts = created
        ready = status.await() == TextToSpeech.SUCCESS
        if (!ready) {
            withContext(Dispatchers.Main) { created.shutdown() }
            tts = null
        }
        if (ready) tts else null
    }

    private val listener = object : UtteranceProgressListener() {
        override fun onStart(utteranceId: String?) = Unit

        override fun onDone(utteranceId: String?) {
            pending[utteranceId]?.complete(true)
        }

        @Deprecated("Required override")
        override fun onError(utteranceId: String?) {
            pending[utteranceId]?.complete(false)
        }

        override fun onError(utteranceId: String?, errorCode: Int) {
            pending[utteranceId]?.complete(false)
        }

        override fun onStop(utteranceId: String?, interrupted: Boolean) {
            pending[utteranceId]?.complete(false)
        }
    }

    public companion object {
        public const val ID: String = "system"
        private const val NOT_INSTALLED = TextToSpeech.Engine.KEY_FEATURE_NOT_INSTALLED
    }
}
