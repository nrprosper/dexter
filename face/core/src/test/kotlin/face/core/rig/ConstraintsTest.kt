package face.core.rig

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.math.sqrt
import kotlin.random.Random

class ConstraintsTest {
    private fun randomPose(random: Random): FacePose = FacePose().also { pose ->
        for (c in Channel.entries) {
            val span = c.max - c.min
            pose[c] = c.min - span + random.nextFloat() * span * 3f
        }
    }

    @Test
    fun everyChannelEndsInRange() = repeatRandom { pose ->
        for (c in Channel.entries) assertTrue(pose[c] in c.min..c.max, "$c = ${pose[c]}")
    }

    @Test
    fun gazeStaysInsideUnitDisc() = repeatRandom { pose ->
        val x = pose[Channel.GazeX]
        val y = pose[Channel.GazeY]
        assertTrue(sqrt(x * x + y * y) <= 1f + 1e-6f)
    }

    @Test
    fun apertureNeverNegative() = repeatRandom { pose ->
        assertTrue(pose[Channel.EyeOpenL] >= 0f && pose[Channel.EyeOpenR] >= 0f)
        assertTrue(pose[Channel.EyeOpenL] - pose[Channel.LowerLidRaiseL] >= 0f)
        assertTrue(pose[Channel.EyeOpenR] - pose[Channel.LowerLidRaiseR] >= 0f)
    }

    private fun repeatRandom(check: (FacePose) -> Unit) {
        val random = Random(42)
        repeat(10_000) {
            val pose = randomPose(random)
            Constraints.apply(pose)
            check(pose)
        }
    }
}
