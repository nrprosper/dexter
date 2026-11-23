package assistant.android

import android.content.Context
import android.os.SystemClock
import assistant.claude.ClaudeBrain
import assistant.claude.ClaudeSettings
import assistant.core.Orchestrator
import face.api.FaceController
import kotlinx.coroutines.CoroutineScope
import voice.api.SynthesisSettings
import voice.speaker.VoiceStack

// The composition root for conversations: recognizer ears, Claude brain, Piper (or system) voice, and the
// orchestrator that drives the face. The face is only ever seen as a FaceController.
public class Assistant(context: Context, face: FaceController, scope: CoroutineScope) {
    private val app = context.applicationContext
    private val prefs = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    public val keys: ApiKeyStore = ApiKeyStore(app)
    public val voice: VoiceStack = VoiceStack(app, face, scope, manageActivity = false)
    public val brain: ClaudeBrain = ClaudeBrain({ keys.load() }, { ClaudeSettings(model = model) })
    public val orchestrator: Orchestrator = Orchestrator(
        face = face,
        ears = RecognizerEars(app),
        brain = brain,
        mouth = SpeakerMouth(voice.speaker) { SynthesisSettings(voiceId = voice.voiceId) },
        scope = scope,
        clock = { SystemClock.elapsedRealtime() },
    )

    public var model: String
        get() = prefs.getString(MODEL, null)?.takeIf { it in ClaudeSettings.MODELS } ?: ClaudeSettings.DEFAULT_MODEL
        set(value) {
            prefs.edit().putString(MODEL, value).apply()
        }

    public companion object {
        public const val PREFS: String = "assistant"
        private const val MODEL = "model"
    }
}
