package voice.speaker

import android.content.Context
import face.api.FaceController
import face.speech.AmplitudeSpeechTrack
import face.speech.AudioTrackSink
import face.speech.PcmPlayer
import kotlinx.coroutines.CoroutineScope
import voice.api.RoutingPolicy
import voice.api.SynthesisSettings
import voice.api.TtsRouter
import kotlinx.coroutines.launch
import voice.elevenlabs.ElevenLabsEngine
import voice.elevenlabs.ElevenLabsSettings
import voice.kokoro.KokoroEngine
import voice.piper.PiperEngine
import voice.piper.model.VoiceManifest
import voice.piper.model.VoiceStore
import voice.system.SystemTtsEngine

// The composition root for speech: engines (the phone's Google voice by default, Piper optional), router, player and Speaker.
// A future engine (Kokoro, ElevenLabs) is added to `engines` here; the face, player and Speaker don't change.
// User settings (sync offset, lead, voice, Wi-Fi-only) are kept in SharedPreferences.
// elevenLabsKey supplies the ElevenLabs key (kept encrypted by the caller); null or blank means ElevenLabs is off.
public class VoiceStack(
    context: Context,
    face: FaceController,
    scope: CoroutineScope,
    manageActivity: Boolean = true,
    elevenLabsKey: () -> String? = { null },
) {
    private val app = context.applicationContext
    private val prefs = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    public val connectivity: AndroidConnectivity = AndroidConnectivity(app)
    public val store: VoiceStore = VoiceStore(app.filesDir, VoiceManifest.loadBundled(), connectivity)
    public val piper: PiperEngine = PiperEngine(store)
    public val kokoro: KokoroEngine = KokoroEngine(VoiceStore(app.filesDir, KokoroEngine.manifest(), connectivity))
    public val system: SystemTtsEngine = SystemTtsEngine(app)
    public val elevenLabs: ElevenLabsEngine = ElevenLabsEngine(elevenLabsKey, { ElevenLabsSettings(model = elevenLabsModel) })
    public val router: TtsRouter = TtsRouter(listOf(elevenLabs, kokoro, piper, system), RoutingPolicy(), connectivity)
    public val player: PcmPlayer = PcmPlayer({ AudioTrackSink(app) })
    public val speaker: Speaker = Speaker(router, player, face, scope, manageActivity)
    private val warmScope = scope

    public var syncOffsetMillis: Int
        get() = player.track.syncOffsetMillis
        set(value) {
            player.track.syncOffsetMillis = value
            prefs.edit().putInt(SYNC_OFFSET, value).apply()
        }

    public var leadMillis: Int
        get() = player.track.leadMillis
        set(value) {
            player.track.leadMillis = value
            prefs.edit().putInt(LEAD, value).apply()
        }

    // Which engine speaks: the phone's Google voice by default, or a downloaded Kokoro or Piper voice.
    public var engine: String
        get() = prefs.getString(ENGINE, null)?.takeIf { it in CHOOSABLE } ?: SystemTtsEngine.ID
        set(value) {
            prefs.edit().putString(ENGINE, value).apply()
            applyEngine()
        }

    // The chosen voice for the current engine (null = that engine's best default).
    public var voiceId: String?
        get() = prefs.getString(voiceKey(engine), null)
        set(value) {
            prefs.edit().putString(voiceKey(engine), value).apply()
        }

    private fun voiceKey(engine: String): String = when (engine) {
        PiperEngine.ID -> VOICE
        KokoroEngine.ID -> KOKORO_VOICE
        ElevenLabsEngine.ID -> ELEVENLABS_VOICE
        else -> SYSTEM_VOICE
    }

    public val elevenLabsModel: String
        get() = prefs.getString(ELEVENLABS_MODEL, null)?.takeIf { it in ElevenLabsSettings.MODELS } ?: ElevenLabsSettings.DEFAULT_MODEL

    public var allowMeteredDownloads: Boolean
        get() = store.allowMetered
        set(value) {
            store.allowMetered = value
            prefs.edit().putBoolean(METERED, value).apply()
        }

    init {
        reloadPrefs()
    }

    // Loads the chosen on-device voice in the background so the first reply starts quickly.
    public fun warmUp() {
        val chosen = engine
        val target = listOf(kokoro, piper).firstOrNull { it.id == chosen } ?: return
        warmScope.launch { target.warmUp(SynthesisSettings(voiceId = voiceId)) }
    }

    // Picks up changes made elsewhere (a settings screen) to the saved sync offset, lead and download policy.
    public fun reloadPrefs() {
        player.track.syncOffsetMillis = prefs.getInt(SYNC_OFFSET, 0)
        player.track.leadMillis = prefs.getInt(LEAD, AmplitudeSpeechTrack.DEFAULT_LEAD_MILLIS)
        store.allowMetered = prefs.getBoolean(METERED, false)
        applyEngine()
    }

    // The chosen engine first, then the phone's voice, so a voice that isn't downloaded yet falls back to the natural one.
    private fun applyEngine() {
        val first = engine
        val order = listOf(first, SystemTtsEngine.ID) + RoutingPolicy.DEFAULT_ORDER
        // Cloud voices only when chosen; then also on mobile data (the voice is the point), with the phone's voice as fallback.
        val online = first == ElevenLabsEngine.ID
        router.policy = RoutingPolicy(order = order.distinct(), allowOnline = online, onlineOnMetered = online)
    }

    private companion object {
        const val PREFS = "voice"
        const val SYNC_OFFSET = "sync_offset_ms"
        const val LEAD = "lead_ms"
        const val VOICE = "voice_id"
        const val SYSTEM_VOICE = "system_voice_id"
        const val KOKORO_VOICE = "kokoro_voice_id"
        const val ELEVENLABS_VOICE = "elevenlabs_voice_id"
        const val ELEVENLABS_MODEL = "elevenlabs_model"
        val CHOOSABLE = setOf(PiperEngine.ID, KokoroEngine.ID, ElevenLabsEngine.ID)
        const val ENGINE = "engine"
        const val METERED = "allow_metered_downloads"
    }
}
