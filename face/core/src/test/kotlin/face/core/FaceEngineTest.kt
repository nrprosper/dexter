package face.core

import face.core.expression.ExpressionId
import face.core.expression.ExpressionLibrary
import face.core.rig.Channel
import face.core.rig.FacePose
import face.core.time.Rng
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.lang.management.ManagementFactory
import kotlin.math.abs

class FaceEngineTest {
    private val library = ExpressionLibrary.loadBundled()

    // Random expression switches and random raw targets, decided by a seeded Rng.
    private fun randomSession(seed: Long, frames: Int, dt: Float, onFrame: (FacePose) -> Unit) {
        val engine = FaceEngine(library)
        val rng = Rng(seed)
        val raw = FacePose()
        repeat(frames) {
            when (rng.nextInt(20)) {
                0, 1 -> engine.setExpression(ExpressionId.entries[rng.nextInt(ExpressionId.entries.size)])
                2 -> {
                    for (c in Channel.entries) raw[c] = rng.nextFloat(c.min, c.max)
                    engine.setTarget(raw)
                }
            }
            engine.step(dt)
            onFrame(engine.pose)
        }
    }

    @Test
    fun noPopUnderRandomCommands() {
        for (seed in 1L..5L) {
            val previous = FacePose().also { library.poseInto(it, ExpressionId.Neutral) }
            randomSession(seed, 3_600, 1f / 60f) { pose ->
                for (c in Channel.entries) {
                    val delta = abs(pose[c] - previous[c])
                    assertTrue(delta <= POP_FRACTION * (c.max - c.min), "seed $seed $c jumped $delta")
                }
                previous.copyFrom(pose)
            }
        }
    }

    @Test
    fun channelsStayInRangeEveryFrame() {
        randomSession(11, 3_600, 1f / 60f) { pose ->
            for (c in Channel.entries) assertTrue(pose[c] in c.min..c.max, "$c = ${pose[c]}")
        }
    }

    @Test
    fun sameSeedGivesIdenticalPoseSequence() {
        val first = ArrayList<FloatArray>()
        randomSession(42, 600, 1f / 60f) { first.add(it.values.copyOf()) }
        var i = 0
        randomSession(42, 600, 1f / 60f) { assertArrayEquals(first[i++], it.values) }
    }

    @Test
    fun motionMatchesAt30_60_120Hz() {
        val at30 = scripted(30)
        for (rate in intArrayOf(60, 120)) {
            val other = scripted(rate)
            for (sample in at30.indices) {
                for (c in Channel.entries) {
                    val diff = abs(at30[sample][c.ordinal] - other[sample][c.ordinal])
                    assertTrue(diff <= MATCH_FRACTION * (c.max - c.min), "$rate Hz, sample $sample, $c differs by $diff")
                }
            }
        }
    }

    // Changes expression every half second; samples the pose every 1/30 s.
    private fun scripted(rate: Int): List<FloatArray> {
        val engine = FaceEngine(library)
        val script = listOf(ExpressionId.Happy, ExpressionId.Surprised, ExpressionId.Sad, ExpressionId.Laugh, ExpressionId.Determined)
        val perSample = rate / 30
        val samples = ArrayList<FloatArray>()
        for (frame in 0 until rate * 3) {
            if (frame % (rate / 2) == 0) engine.setExpression(script[(frame / (rate / 2)) % script.size])
            engine.step(1f / rate)
            if ((frame + 1) % perSample == 0) samples.add(engine.pose.values.copyOf())
        }
        return samples
    }

    @Test
    fun resumeDoesNotJump() {
        val engine = FaceEngine(library)
        engine.setExpression(ExpressionId.Surprised)
        repeat(6) { engine.step(1f / 60f) }
        engine.onResume()
        val before = engine.pose.values.copyOf()
        engine.step(1f / 60f)
        for (c in Channel.entries) {
            assertTrue(abs(engine.pose[c] - before[c.ordinal]) <= RESUME_FRACTION * (c.max - c.min), "$c")
        }
    }

    @Test
    fun stepDoesNotAllocate() {
        val engine = FaceEngine(library)
        engine.setExpression(ExpressionId.Laugh)
        repeat(20_000) { engine.step(1f / 60f) }
        val threads = ManagementFactory.getThreadMXBean() as com.sun.management.ThreadMXBean
        val before = threads.currentThreadAllocatedBytes
        repeat(10_000) { engine.step(1f / 60f) }
        val allocated = threads.currentThreadAllocatedBytes - before
        assertTrue(allocated < 1_024, "step allocated $allocated bytes over 10k frames")
    }

    private companion object {
        const val POP_FRACTION = 0.35f
        const val MATCH_FRACTION = 0.01f
        const val RESUME_FRACTION = 0.05f
    }
}
