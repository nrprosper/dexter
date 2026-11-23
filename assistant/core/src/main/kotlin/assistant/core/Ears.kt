package assistant.core

import kotlinx.coroutines.flow.Flow

public sealed interface HearingEvent {
    public data object Ready : HearingEvent

    // Microphone level 0..1 while the user talks: drives the face's nods.
    public data class Level(val level: Float) : HearingEvent

    public data class Partial(val text: String) : HearingEvent

    public data class Heard(val text: String) : HearingEvent

    public data object NothingHeard : HearingEvent

    public data class Failed(val failure: HearingFailure) : HearingEvent
}

public enum class HearingFailure { NoPermission, Unavailable, Busy, Network, Other }

// Cold: collecting starts one recognition, which ends with Heard, NothingHeard or Failed; cancelling stops it.
public interface Ears {
    public fun listen(): Flow<HearingEvent>
}
