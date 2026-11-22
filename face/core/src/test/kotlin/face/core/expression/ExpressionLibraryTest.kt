package face.core.expression

import face.core.rig.Channel
import face.core.rig.FacePose
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ExpressionLibraryTest {
    private val library = ExpressionLibrary.loadBundled()

    @Test
    fun everyBundledExpressionLoadsWithMatchingId() {
        for (id in ExpressionId.entries) assertEquals(id.key, library[id].id)
    }

    @Test
    fun everyExpressionPoseStaysInRange() {
        val pose = FacePose()
        for (id in ExpressionId.entries) {
            library.poseInto(pose, id)
            for (c in Channel.entries) assertTrue(pose[c] in c.min..c.max, "$id $c = ${pose[c]}")
        }
    }

    @Test
    fun nonNeutralExpressionsDifferFromNeutral() {
        val neutral = FacePose().also { library.poseInto(it, ExpressionId.Neutral) }
        val pose = FacePose()
        for (id in ExpressionId.entries - ExpressionId.Neutral) {
            library.poseInto(pose, id)
            assertTrue(Channel.entries.any { pose[it] != neutral[it] }, "$id")
        }
    }
}
