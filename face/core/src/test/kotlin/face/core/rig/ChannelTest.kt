package face.core.rig

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ChannelTest {
    @Test
    fun restLiesWithinRange() {
        for (c in Channel.entries) assertTrue(c.min <= c.rest && c.rest <= c.max, "$c")
    }

    @Test
    fun keysAreUniqueAndResolve() {
        assertEquals(Channel.count, Channel.entries.map { it.key }.toSet().size)
        for (c in Channel.entries) assertEquals(c, Channel.fromKey(c.key))
    }

    @Test
    fun newPoseIsAtRest() {
        val pose = FacePose()
        for (c in Channel.entries) assertEquals(c.rest, pose[c])
    }
}
