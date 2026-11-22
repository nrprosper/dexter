package voice.api

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class TtsRouterTest {
    private class Fake(
        override val id: String,
        var state: Availability = Availability.Ready,
        location: EngineLocation = EngineLocation.Offline,
        timestamps: AlignmentUnit? = null,
        private val voiceIds: List<String> = emptyList(),
    ) : TtsEngine {
        override val capabilities = EngineCapabilities(location, timestamps = timestamps)
        override suspend fun availability() = state
        override suspend fun voices() = voiceIds.map { VoiceDescriptor(it, id, it, "en-US", 22_050) }
        override fun synthesize(input: TtsInput, settings: SynthesisSettings): Flow<PcmChunk> = emptyFlow()
    }

    private var network = Connectivity.Unmetered

    private fun router(vararg engines: TtsEngine, policy: RoutingPolicy = RoutingPolicy()) = TtsRouter(engines.toList(), policy) { network }

    private fun chosen(r: TtsRouter, settings: SynthesisSettings = SynthesisSettings(), timestamps: Boolean = false) =
        runBlocking { (r.choose(settings, timestamps) as RouteChoice.Use).engine.id }

    @Test
    fun installedPiperBeatsSystem() {
        assertEquals("piper", chosen(router(Fake("system"), Fake("piper"))))
    }

    @Test
    fun systemIsTheFallbackWhenNoPiperVoiceIsInstalled() {
        val r = router(Fake("piper", Availability.NeedsDownload), Fake("system"))
        assertEquals("system", chosen(r))
    }

    @Test
    fun onlineEnginesNeedPolicyAndNetwork() {
        val cloud = Fake("elevenlabs", location = EngineLocation.Online)
        val engines = arrayOf(cloud, Fake("system"))
        assertEquals("system", chosen(router(*engines)))
        val online = RoutingPolicy(order = listOf("elevenlabs", "system"), allowOnline = true)
        assertEquals("elevenlabs", chosen(router(*engines, policy = online)))
        network = Connectivity.Metered
        assertEquals("system", chosen(router(*engines, policy = online)))
        assertEquals("elevenlabs", chosen(router(*engines, policy = online.copy(onlineOnMetered = true))))
        network = Connectivity.Offline
        assertEquals("system", chosen(router(*engines, policy = online.copy(onlineOnMetered = true))))
    }

    @Test
    fun requestedVoicePicksItsEngine() {
        val r = router(Fake("piper", voiceIds = listOf("lj")), Fake("kokoro", voiceIds = listOf("af_heart")))
        assertEquals("kokoro", chosen(r, SynthesisSettings(voiceId = "af_heart")))
        assertEquals("piper", chosen(r, SynthesisSettings(voiceId = "missing")))
    }

    @Test
    fun timestampsPreferAnEngineThatHasThem() {
        val r = router(Fake("piper"), Fake("kokoro", timestamps = AlignmentUnit.Word))
        assertEquals("kokoro", chosen(r, timestamps = true))
        assertEquals("piper", chosen(r))
    }

    @Test
    fun excludedEnginesAreSkippedForFailover() {
        val r = router(Fake("piper"), Fake("system"))
        val choice = runBlocking { r.choose(exclude = setOf("piper")) }
        assertEquals("system", (choice as RouteChoice.Use).engine.id)
    }

    @Test
    fun nothingUsableExplainsWhy() {
        val r = router(Fake("piper", Availability.NeedsDownload), Fake("system", Availability.Unavailable("no TTS engine")))
        val choice = runBlocking { r.choose() } as RouteChoice.NoEngine
        assertEquals(mapOf("piper" to "no voice installed", "system" to "no TTS engine"), choice.reasons)
    }

    @Test
    fun unknownEnginesComeAfterTheOrderedOnes() {
        val r = router(Fake("custom"), Fake("system"))
        assertEquals("system", chosen(r))
        assertTrue(runBlocking { r.choose(exclude = setOf("system")) } is RouteChoice.Use)
    }
}
