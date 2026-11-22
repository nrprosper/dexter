package face.core.director

import face.api.Emotion
import face.api.FaceReaction
import face.core.behavior.FaceEvent
import face.core.director.FaceFixtures.engine
import face.core.director.FaceFixtures.record
import face.core.director.FaceFixtures.run
import face.core.rig.Channel
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.math.abs

class ReactionTest {
    @Test
    fun cooldownDropsARepeatedSurprise() {
        val face = engine()
        val events = face.record(FaceEvent.Reaction)
        repeat(5) {
            face.react(FaceReaction.Surprise())
            face.run(0.3f)
        }
        assertEquals(1, events.count { it == "play Surprise" })
        assertTrue(events.count { it == "dropped Surprise (cooldown)" } >= 4)
    }

    @Test
    fun higherPriorityInterruptsButNotTheOtherWayRound() {
        val face = engine()
        val events = face.record(FaceEvent.Reaction)
        face.react(FaceReaction.Acknowledge)
        face.run(0.05f)
        face.react(FaceReaction.Startle)
        face.run(0.05f)
        assertEquals(Reactions.STARTLE, face.reactionPlaying)
        face.react(FaceReaction.Acknowledge)
        face.run(0.05f)
        assertEquals(Reactions.STARTLE, face.reactionPlaying)
        assertTrue("dropped Acknowledge (priority)" in events || "dropped Acknowledge (cooldown)" in events)
    }

    @Test
    fun surpriseShowsThenReturnsToBaseline() {
        val face = engine()
        face.lifeEnabled = false
        face.run(3f)
        val rest = face.pose.values.copyOf()
        face.react(FaceReaction.Surprise())
        var widest = 0f
        face.run(0.6f) { widest = maxOf(widest, it.pose[Channel.BrowHeightL]) }
        assertTrue(widest > rest[Channel.BrowHeightL.ordinal] + 0.4f, "brows should shoot up")
        face.run(3f)
        for (c in Channel.entries) assertTrue(abs(face.pose[c] - rest[c.ordinal]) < 0.05f, "$c should settle back")
    }

    @Test
    fun followUpEmotionIsAppliedAndSurpriseSuppressesBlinks() {
        val face = engine()
        val blinks = face.record(FaceEvent.Blink, FaceEvent.DoubleBlink, FaceEvent.SlowBlink)
        face.react(FaceReaction.Surprise(followUp = Emotion.Happy))
        face.run(0.9f)
        assertEquals(0, blinks.size)
        assertEquals(Emotion.Happy, face.snapshot.value.emotion)
    }
}
