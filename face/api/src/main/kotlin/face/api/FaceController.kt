package face.api

import kotlinx.coroutines.flow.StateFlow

// Everything an orchestrator needs; thread-safe. State is latest-wins, reactions are queued.
public interface FaceController {
    public fun setActivity(activity: Activity)

    public fun setEmotion(signal: EmotionSignal)

    public fun react(event: FaceReaction)

    public fun attachSpeech(track: SpeechTrack, style: SpeechStyle)

    public fun detachSpeech()

    public fun setUserVoiceLevel(level: Float)

    public fun setPowerMode(mode: PowerMode)

    public val snapshot: StateFlow<FaceSnapshot>
}
