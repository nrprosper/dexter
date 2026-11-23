package voice.api

// order lists engine ids from most to least preferred; engines it doesn't name come after, in registration order.
public data class RoutingPolicy(
    val order: List<String> = DEFAULT_ORDER,
    val allowOnline: Boolean = false,
    val onlineOnMetered: Boolean = false,
) {
    public companion object {
        public val DEFAULT_ORDER: List<String> = listOf("piper", "kokoro", "elevenlabs", "system")
    }
}

public sealed interface RouteChoice {
    public data class Use(val engine: TtsEngine, val voiceId: String?) : RouteChoice

    public data class NoEngine(val reasons: Map<String, String>) : RouteChoice
}

// Picks the engine for an utterance from availability, connectivity and policy. Holds no state between calls.
public class TtsRouter(
    engines: List<TtsEngine>,
    public var policy: RoutingPolicy = RoutingPolicy(),
    private val connectivity: () -> Connectivity,
) {
    private val engines: List<TtsEngine> = engines.toList()

    init {
        val ids = engines.map { it.id }
        require(ids.size == ids.toSet().size) { "duplicate engine ids: $ids" }
    }

    public suspend fun choose(
        settings: SynthesisSettings = SynthesisSettings(),
        needTimestamps: Boolean = false,
        exclude: Set<String> = emptySet(),
    ): RouteChoice {
        val reasons = LinkedHashMap<String, String>()
        val network = connectivity()
        val eligible = ArrayList<TtsEngine>()
        for (engine in ordered()) {
            val reason = refusal(engine, network, exclude)
            if (reason != null) reasons[engine.id] = reason else eligible.add(engine)
        }
        if (eligible.isEmpty()) return RouteChoice.NoEngine(reasons)
        val wanted = settings.voiceId
        if (wanted != null) {
            for (engine in eligible) {
                if (engine.voices().any { it.id == wanted && it.installed }) return RouteChoice.Use(engine, wanted)
            }
        }
        val pick = if (needTimestamps) eligible.firstOrNull { it.capabilities.timestamps != null } ?: eligible[0] else eligible[0]
        return RouteChoice.Use(pick, null)
    }

    private suspend fun refusal(engine: TtsEngine, network: Connectivity, exclude: Set<String>): String? {
        if (engine.id in exclude) return "excluded"
        if (engine.capabilities.location == EngineLocation.Online) {
            if (!policy.allowOnline) return "online engines are off"
            if (network == Connectivity.Offline) return "offline"
            if (network == Connectivity.Metered && !policy.onlineOnMetered) return "metered network"
        }
        return when (val a = engine.availability()) {
            Availability.Ready -> null
            Availability.NeedsDownload -> "no voice installed"
            is Availability.Unavailable -> a.reason
        }
    }

    private fun ordered(): List<TtsEngine> {
        val rank = policy.order.withIndex().associate { it.value to it.index }
        return engines.withIndex()
            .sortedWith(compareBy({ rank[it.value.id] ?: Int.MAX_VALUE }, { it.index }))
            .map { it.value }
    }
}
