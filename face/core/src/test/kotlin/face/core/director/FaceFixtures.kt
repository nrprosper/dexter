package face.core.director

import face.api.Personality
import face.core.FaceEngine
import face.core.behavior.FaceEvent
import face.core.behavior.FaceEventListener
import face.core.expression.ExpressionLibrary
import face.core.personality.PersonalityPresets
import face.core.time.Rng

internal object FaceFixtures {
    val library: ExpressionLibrary by lazy { ExpressionLibrary.loadBundled() }
    const val DT = 1f / 60f

    fun engine(seed: Long = 1, personality: Personality = PersonalityPresets.load("dexter")) =
        FaceEngine(library, Rng(seed), personality = personality)

    fun FaceEngine.run(seconds: Float, each: (FaceEngine) -> Unit = {}) {
        repeat(Math.round(seconds / DT)) {
            step(DT)
            each(this)
        }
    }

    fun FaceEngine.record(vararg kinds: FaceEvent): MutableList<String> {
        val out = ArrayList<String>()
        listener = FaceEventListener { event, detail, _ -> if (event in kinds) out.add(detail) }
        return out
    }
}
