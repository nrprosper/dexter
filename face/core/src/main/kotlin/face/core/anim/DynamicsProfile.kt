package face.core.anim

import face.core.rig.ChannelGroup

// frequency in Hz, damping ζ (1 = critical, < 1 overshoots), response r (< 0 anticipates, > 1 overshoots on start).
public data class DynamicsProfile(val frequency: Float, val damping: Float, val response: Float) {
    init {
        require(frequency > 0f) { "frequency must be positive" }
        require(damping >= 0f) { "damping must not be negative" }
    }
}

public class DynamicsConfig(private val profiles: Map<ChannelGroup, DynamicsProfile>) {
    init {
        val missing = ChannelGroup.entries - profiles.keys
        require(missing.isEmpty()) { "missing dynamics for $missing" }
    }

    public operator fun get(group: ChannelGroup): DynamicsProfile = profiles.getValue(group)

    public fun with(group: ChannelGroup, profile: DynamicsProfile): DynamicsConfig =
        DynamicsConfig(profiles + (group to profile))

    public companion object {
        public val Default: DynamicsConfig = DynamicsConfig(
            mapOf(
                ChannelGroup.Pupils to DynamicsProfile(4.5f, 0.8f, 0f),
                ChannelGroup.EyeShape to DynamicsProfile(3.2f, 0.7f, 0f),
                ChannelGroup.Lids to DynamicsProfile(3.2f, 0.75f, 0f),
                ChannelGroup.EyePlacement to DynamicsProfile(2.5f, 0.8f, 0f),
                ChannelGroup.Brows to DynamicsProfile(2.8f, 0.55f, 0f),
                ChannelGroup.Mouth to DynamicsProfile(3.5f, 0.65f, 0f),
                ChannelGroup.Head to DynamicsProfile(1.4f, 0.5f, -0.3f),
                ChannelGroup.Accents to DynamicsProfile(2f, 1f, 0f),
                ChannelGroup.Look to DynamicsProfile(1f, 1f, 0f),
            ),
        )
    }
}
