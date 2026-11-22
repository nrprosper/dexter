package face.core.inbox

import face.api.Activity
import face.api.FaceReaction
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import java.util.concurrent.CountDownLatch
import kotlin.concurrent.thread

class FaceInboxTest {
    @Test
    fun stateIsLatestWinsAndTakenOnce() {
        val inbox = FaceInbox()
        inbox.setActivity(Activity.Listening)
        inbox.setActivity(Activity.Thinking)
        inbox.setActivity(Activity.Speaking)
        assertEquals(Activity.Speaking, inbox.takeActivity())
        assertNull(inbox.takeActivity())
    }

    @Test
    fun reactionsDrainInArrivalOrder() {
        val inbox = FaceInbox()
        val sent = listOf(FaceReaction.Acknowledge, FaceReaction.Oops, FaceReaction.Surprise(), FaceReaction.Laugh)
        sent.forEach(inbox::react)
        val received = generateSequence { inbox.pollReaction() }.toList()
        assertEquals(sent, received)
    }

    @Test
    fun concurrentWritersLoseNoReactions() {
        val inbox = FaceInbox()
        val kinds = listOf(
            FaceReaction.Delight, FaceReaction.Startle, FaceReaction.Acknowledge,
            FaceReaction.Oops, FaceReaction.Laugh, FaceReaction.Realization,
        )
        val perThread = 20_000
        val start = CountDownLatch(1)
        val writers = kinds.mapIndexed { i, kind ->
            thread {
                start.await()
                repeat(perThread) {
                    inbox.react(kind)
                    inbox.setActivity(Activity.entries[(it + i) % Activity.entries.size])
                    inbox.setVoiceLevel((it % 10) / 10f)
                }
            }
        }
        val counts = HashMap<FaceReaction, Int>()
        start.countDown()
        while (writers.any { it.isAlive } || counts.values.sum() < kinds.size * perThread) {
            val r = inbox.pollReaction() ?: continue
            counts[r] = (counts[r] ?: 0) + 1
            inbox.takeActivity()
        }
        kinds.forEach { assertEquals(perThread, counts[it]) }
    }
}
