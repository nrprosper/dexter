package assistant.android

import android.content.Context
import android.os.SystemClock
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale
import assistant.claude.ClaudeBrain
import assistant.claude.ClaudeSettings
import assistant.core.Brain
import assistant.core.BrainEvent
import assistant.core.BrainProvider
import assistant.core.Orchestrator
import assistant.core.Turn
import assistant.gemini.GeminiBrain
import assistant.gemini.GeminiSettings
import kotlinx.coroutines.flow.Flow
import face.api.FaceController
import kotlinx.coroutines.CoroutineScope
import voice.api.SynthesisSettings
import voice.speaker.VoiceStack

// The composition root for conversations: recognizer ears, Claude brain, Piper (or system) voice, and the
// orchestrator that drives the face. The face is only ever seen as a FaceController.
public class Assistant(context: Context, face: FaceController, scope: CoroutineScope) {
    private val app = context.applicationContext
    private val prefs = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    public val claudeKeys: ApiKeyStore = ApiKeyStore(app, BrainProvider.Claude)
    public val geminiKeys: ApiKeyStore = ApiKeyStore(app, BrainProvider.Gemini)
    public val elevenLabsKeys: ApiKeyStore = ApiKeyStore(app, KeySlot.ElevenLabs)
    public val voice: VoiceStack = VoiceStack(app, face, scope, manageActivity = false, elevenLabsKey = { elevenLabsKeys.load() })
    public val claude: ClaudeBrain = ClaudeBrain({ claudeKeys.load() }, { ClaudeSettings(model = claudeModel) })
    public val gemini: GeminiBrain = GeminiBrain({ geminiKeys.load() }, { GeminiSettings(model = geminiModel) })

    // Asks whichever provider is chosen right now, so switching takes effect on the next turn.
    public val brain: Brain = object : Brain {
        override fun reply(history: List<Turn>, user: String): Flow<BrainEvent> =
            if (provider == BrainProvider.Gemini) gemini.reply(history, user) else claude.reply(history, user)
    }
    public val orchestrator: Orchestrator = Orchestrator(
        face = face,
        ears = RecognizerEars(app),
        brain = brain,
        mouth = SpeakerMouth(voice.speaker) { SynthesisSettings(voiceId = voice.voiceId) },
        scope = scope,
        clock = { SystemClock.elapsedRealtime() },
        context = ::moment,
    )

    // "Sunday evening, 7:40 pm": lets Dexter say good morning or notice it's late, without reading it out.
    private fun moment(): String {
        val now = LocalDateTime.now()
        val part = when (now.hour) {
            in 5..11 -> "morning"
            in 12..16 -> "afternoon"
            in 17..21 -> "evening"
            else -> "night"
        }
        val day = now.dayOfWeek.getDisplayName(TextStyle.FULL, Locale.getDefault())
        val time = now.format(DateTimeFormatter.ofPattern("h:mm a", Locale.getDefault())).lowercase(Locale.getDefault())
        return "$day $part, $time"
    }

    public val provider: BrainProvider get() = BrainProvider.of(prefs.getString(PROVIDER, null))

    public val keys: ApiKeyStore get() = if (provider == BrainProvider.Gemini) geminiKeys else claudeKeys

    public val claudeModel: String
        get() = prefs.getString(MODEL, null)?.takeIf { it in ClaudeSettings.MODELS } ?: ClaudeSettings.DEFAULT_MODEL

    public val geminiModel: String
        get() = prefs.getString(GEMINI_MODEL, null)?.takeIf { it in GeminiSettings.MODELS } ?: GeminiSettings.DEFAULT_MODEL

    public val model: String get() = if (provider == BrainProvider.Gemini) geminiModel else claudeModel

    public companion object {
        public const val PREFS: String = "assistant"
        public const val MODEL: String = "model"
        public const val GEMINI_MODEL: String = "gemini_model"
        public const val PROVIDER: String = "brain"
    }
}
