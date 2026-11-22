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

    public fun setPersonality(personality: Personality)

    public fun setDeviceState(state: DeviceState)

    // The system volume panel is showing this level (0..1): the face makes room and watches it.
    public fun showVolume(level: Float)

    // How the phone is tilted away from how it's usually held, -1..1 (x: right side down, y: top towards the user).
    public fun setTilt(x: Float, y: Float)

    public val snapshot: StateFlow<FaceSnapshot>
}
