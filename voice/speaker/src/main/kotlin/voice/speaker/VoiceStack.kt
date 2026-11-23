package voice.speaker

import android.content.Context
import face.api.FaceController
import face.speech.AmplitudeSpeechTrack
import face.speech.AudioTrackSink
import face.speech.PcmPlayer
import kotlinx.coroutines.CoroutineScope
import voice.api.RoutingPolicy
import voice.api.TtsRouter
import voice.piper.PiperEngine
import voice.piper.model.VoiceManifest
import voice.piper.model.VoiceStore
import voice.system.SystemTtsEngine

// The composition root for speech: engines (Piper first, the system voice as fallback), router, player and Speaker.
// A future engine (Kokoro, ElevenLabs) is added to `engines` here; the face, player and Speaker don't change.
// User settings (sync offset, lead, voice, Wi-Fi-only) are kept in SharedPreferences.
public class VoiceStack(context: Context, face: FaceController, scope: CoroutineScope, manageActivity: Boolean = true) {
    private val app = context.applicationContext
    private val prefs = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    public val connectivity: AndroidConnectivity = AndroidConnectivity(app)
    public val store: VoiceStore = VoiceStore(app.filesDir, VoiceManifest.loadBundled(), connectivity)
    public val piper: PiperEngine = PiperEngine(store)
    public val system: SystemTtsEngine = SystemTtsEngine(app)
    public val router: TtsRouter = TtsRouter(listOf(piper, system), RoutingPolicy(), connectivity)
    public val player: PcmPlayer = PcmPlayer({ AudioTrackSink(app) })
    public val speaker: Speaker = Speaker(router, player, face, scope, manageActivity)

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

    public var voiceId: String?
        get() = prefs.getString(VOICE, null)
        set(value) {
            prefs.edit().putString(VOICE, value).apply()
        }

    public var allowMeteredDownloads: Boolean
        get() = store.allowMetered
        set(value) {
            store.allowMetered = value
            prefs.edit().putBoolean(METERED, value).apply()
        }

    init {
        reloadPrefs()
    }

    // Picks up changes made elsewhere (a settings screen) to the saved sync offset, lead and download policy.
    public fun reloadPrefs() {
        player.track.syncOffsetMillis = prefs.getInt(SYNC_OFFSET, 0)
        player.track.leadMillis = prefs.getInt(LEAD, AmplitudeSpeechTrack.DEFAULT_LEAD_MILLIS)
        store.allowMetered = prefs.getBoolean(METERED, false)
    }

    private companion object {
        const val PREFS = "voice"
        const val SYNC_OFFSET = "sync_offset_ms"
        const val LEAD = "lead_ms"
        const val VOICE = "voice_id"
        const val METERED = "allow_metered_downloads"
    }
}
