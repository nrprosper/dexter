package face.core.stage

import face.api.Activity
import face.api.StageMove
import face.core.director.FaceFixtures
import face.core.director.FaceFixtures.run
import face.core.rig.Channel
import face.core.rig.FacePose
import face.core.time.Rng
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.math.abs

class StageTest {
    private fun speakingEngine() = FaceFixtures.engine().apply {
        lifeEnabled = false
        setActivity(Activity.Speaking)
        run(1f)
    }

    @Test
    fun bundledConfigHasEveryPlacement() {
        val c = StageConfig.loadBundled()
        for (m in listOf("center", "left", "right", "up", "down", "closer", "away")) assertTrue(m in c.placements, m)
    }

    @Test
    fun placementsGoWhereTheySay() {
        fun after(move: StageMove, channel: Channel): Float {
            val e = speakingEngine()
            val before = e.pose[channel]
            e.stage(move)
            e.run(1.5f)
            return e.pose[channel] - before
        }
        assertTrue(after(StageMove.Left, Channel.HeadX) < -0.3f)
        assertTrue(after(StageMove.Right, Channel.HeadX) > 0.3f)
        assertTrue(after(StageMove.Up, Channel.HeadY) > 0.25f)
        assertTrue(after(StageMove.Down, Channel.HeadY) < -0.2f)
        assertTrue(after(StageMove.Closer, Channel.HeadScale) > 0.2f)
        assertTrue(after(StageMove.Away, Channel.HeadScale) < -0.2f)
    }

    @Test
    fun aPlacementHoldsWhileTalkingThenDriftsHome() {
        val e = speakingEngine()
        val home = e.pose[Channel.HeadX]
        e.stage(StageMove.Right)
        e.run(4f)
        assertTrue(e.pose[Channel.HeadX] - home > 0.3f, "should still be right while speaking")
        e.setActivity(Activity.Idle)
        e.run(6f)
        assertTrue(abs(e.pose[Channel.HeadX] - home) < 0.05f, "should be home again: ${e.pose[Channel.HeadX]}")
    }

    @Test
    fun beatsPlayOnceAndEnd() {
        val e = speakingEngine()
        val rest = FacePose().also { it.copyFrom(e.pose) }
        e.stage(StageMove.Hop)
        var peak = 0f
        e.run(0.3f) { peak = maxOf(peak, it.pose[Channel.HeadY] - rest[Channel.HeadY]) }
        assertTrue(peak > 0.08f, "hop height $peak")
        e.stage(StageMove.Peek)
        var side = 0f
        e.run(1.2f) { side = maxOf(side, abs(it.pose[Channel.HeadX] - rest[Channel.HeadX])) }
        assertTrue(side > 0.4f, "peek reach $side")
        e.run(3f)
        assertTrue(abs(e.pose[Channel.HeadX] - rest[Channel.HeadX]) < 0.05f)
        assertTrue(abs(e.pose[Channel.HeadY] - rest[Channel.HeadY]) < 0.05f)
    }

    @Test
    fun randomStageDirectionsNeverPopAndStayInRange() {
        for (seed in 1L..4L) {
            val e = FaceFixtures.engine(seed)
            val rng = Rng(seed * 7)
            val previous = FacePose().also { it.copyFrom(e.pose) }
            repeat(3_600) {
                when (rng.nextInt(25)) {
                    0 -> e.stage(StageMove.entries[rng.nextInt(StageMove.entries.size)])
                    1 -> e.setActivity(if (rng.nextInt(2) == 0) Activity.Speaking else Activity.Idle)
                    2 -> e.showVolume(rng.nextFloat())
                }
                e.step(FaceFixtures.DT)
                for (c in Channel.entries) {
                    val v = e.pose[c]
                    assertTrue(v in c.min..c.max, "seed $seed $c = $v")
                    assertTrue(abs(v - previous[c]) <= 0.35f * (c.max - c.min), "seed $seed $c jumped")
                }
                previous.copyFrom(e.pose)
            }
        }
    }

    @Test
    fun theVolumeVisitWinsOverAPlacement() {
        fun headX(withStage: Boolean): Float {
            val e = speakingEngine()
            if (withStage) e.stage(StageMove.Left)
            e.run(1f)
            repeat(10) {
                e.showVolume(0.5f)
                e.run(0.2f)
            }
            return e.pose[Channel.HeadX]
        }
        assertEquals(headX(false), headX(true), 0.05f)
    }

    @Test
    fun asleepIgnoresStageDirections() {
        val e = FaceFixtures.engine().apply { setActivity(Activity.Sleeping) }
        e.run(3f)
        val x = e.pose[Channel.HeadX]
        e.stage(StageMove.Right)
        e.run(1.5f)
        assertEquals(x, e.pose[Channel.HeadX], 0.02f)
    }
}
