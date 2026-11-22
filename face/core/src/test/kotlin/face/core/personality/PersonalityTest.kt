package face.core.personality

import face.api.Emotion
import face.api.EmotionSignal
import face.api.FaceReaction
import face.api.IdleSettings
import face.api.Personality
import face.api.TransitionBeat
import face.core.behavior.IdleBehavior
import face.core.director.FaceFixtures.engine
import face.core.director.FaceFixtures.run
import face.core.rig.Channel
import face.core.rig.ChannelGroup
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.lang.management.ManagementFactory
import kotlin.math.abs

class PersonalityTest {
    @Test
    fun codecRoundTrips() {
        val dexter = PersonalityPresets.load("dexter")
        assertEquals(dexter, PersonalityCodec.decode(PersonalityCodec.encode(dexter)))
    }

    @Test
    fun dexterJsonMatchesTheDefaults() {
        assertEquals(Personality(), PersonalityPresets.load("dexter"))
    }

    @Test
    fun unknownKeysEmotionsAndQuirksAreRejected() {
        assertThrows<Exception> { PersonalityCodec.decode("""{"format":1,"id":"x","nose":1}""") }
        assertThrows<IllegalArgumentException> { PersonalityCodec.decode("""{"format":1,"id":"x","emotionGain":{"angry":1}}""") }
        assertThrows<IllegalArgumentException> { PersonalityCodec.decode("""{"format":1,"id":"x","quirks":["sneeze"]}""") }
    }

    @Test
    fun presetsMapToDifferentCharacters() {
        val dexter = PersonalityPresets.load("dexter")
        val sage = PersonalityPresets.load("sage")
        assertEquals(TransitionBeat.Neutral, sage.director.transitionBeat)
        val dd = PersonalityMapper.dynamics(dexter)
        val sd = PersonalityMapper.dynamics(sage)
        assertTrue(sd[ChannelGroup.Head].frequency < dd[ChannelGroup.Head].frequency)
        assertTrue(sd[ChannelGroup.Brows].damping > dd[ChannelGroup.Brows].damping)
        val dl = PersonalityMapper.life(dexter)
        val sl = PersonalityMapper.life(sage)
        assertTrue(sl.blink.meanInterval > dl.blink.meanInterval)
        assertTrue(sl.idle.budgetPerMinute < dl.idle.budgetPerMinute)
        assertTrue(sl.idle.behaviors.none { it.name == "bounce" })
        assertNotEquals(dl.gaze.awayChance, sl.gaze.awayChance)
    }

    @Test
    fun unknownIdleBehaviourIsRejected() {
        assertThrows<IllegalArgumentException> {
            PersonalityMapper.life(Personality(idle = IdleSettings(weights = mapOf("cartwheel" to 3))))
        }
        assertTrue(IdleBehavior.Defaults.any { it.name == "tilt" })
    }

    @Test
    fun switchingPersonalityLiveStaysSmooth() {
        val face = engine()
        face.run(5f)
        val previous = face.pose.values.copyOf()
        face.setPersonality(PersonalityPresets.load("sage"))
        face.run(3f) {
            for (c in Channel.entries) {
                assertTrue(abs(it.pose[c] - previous[c.ordinal]) <= 0.35f * (c.max - c.min), "$c popped")
                assertTrue(it.pose[c] in c.min..c.max)
            }
            it.pose.values.copyInto(previous)
        }
        assertEquals("sage", face.snapshot.value.personality)
    }

    @Test
    fun directorReactionsAndQuirksDoNotAllocatePerFrame() {
        val face = engine()
        face.setEmotion(EmotionSignal(Emotion.Happy, 0.9f))
        face.react(FaceReaction.Laugh)
        face.run(10f)
        face.react(FaceReaction.Oops)
        face.run(0.1f)
        val threads = ManagementFactory.getThreadMXBean() as com.sun.management.ThreadMXBean
        val before = threads.currentThreadAllocatedBytes
        face.run(160f)
        val allocated = threads.currentThreadAllocatedBytes - before
        assertTrue(allocated < 1_024, "allocated $allocated bytes over ${160 * 60} frames")
    }
}
