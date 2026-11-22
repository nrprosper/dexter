package face.core.senses

import face.api.Activity
import face.api.DeviceState
import face.api.FaceReaction
import face.api.Personality
import face.core.behavior.FaceEvent
import face.core.behavior.Vigilance
import face.core.director.FaceFixtures.engine
import face.core.director.FaceFixtures.record
import face.core.director.FaceFixtures.run
import face.core.personality.PersonalityPresets
import face.core.rig.Channel
import face.core.rig.FacePose
import face.core.time.Rng
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.lang.management.ManagementFactory
import kotlin.math.abs

class SensesTest {
    private fun tap(part: FacePart, x: Float = 0f, y: Float = 0f) = FaceTouch(TouchGesture.Tap, part, x, y)

    @Test
    fun lowBatteryTurnsRedAndWorriedUntilCharging() {
        val face = engine()
        face.setDeviceState(DeviceState(batteryLow = true))
        face.run(4f)
        assertTrue(face.pose[Channel.WarnTint] > 0.8f, "should be red")
        assertTrue(face.pose[Channel.BrowTiltL] < -0.2f, "should look worried")
        face.setDeviceState(DeviceState(batteryLow = true, charging = true))
        face.run(5f)
        assertTrue(face.pose[Channel.WarnTint] < 0.1f, "charging should bring the colour back")
    }

    @Test
    fun faceDownSleepsAndPickingUpWakesWithAPeek() {
        val face = engine()
        val events = face.record(FaceEvent.Asleep, FaceEvent.Waking, FaceEvent.Behavior)
        face.setDeviceState(DeviceState(faceDown = true))
        face.run(4f)
        assertEquals(Vigilance.Asleep, face.vigilance)
        face.setDeviceState(DeviceState(faceDown = false))
        face.run(0.2f)
        assertEquals(Vigilance.Waking, face.vigilance)
        assertTrue("quirk peek" in events)
    }

    @Test
    fun phoneEventsPlayTheirReactions() {
        val face = engine()
        val events = face.record(FaceEvent.Reaction)
        val all = listOf(
            FaceReaction.PluggedIn, FaceReaction.Unplugged, FaceReaction.BatteryFull,
            FaceReaction.HeadphonesIn, FaceReaction.Shaken,
        )
        for (r in all) {
            face.react(r)
            face.run(4f)
        }
        for (name in listOf("PluggedIn", "Unplugged", "BatteryFull", "HeadphonesIn", "Shaken")) {
            assertTrue("play $name" in events, "missing $name in $events")
        }
    }

    @Test
    fun shakingMakesItDizzyWithCirclingEyes() {
        val face = engine()
        face.react(FaceReaction.Shaken)
        var dizzy = 0f
        var low = Float.MAX_VALUE
        var high = -Float.MAX_VALUE
        face.run(1.5f) {
            dizzy = maxOf(dizzy, it.pose[Channel.Dizzy])
            low = minOf(low, it.pose[Channel.GazeX])
            high = maxOf(high, it.pose[Channel.GazeX])
        }
        assertTrue(dizzy > 0.6f, "dizzy lines $dizzy")
        assertTrue(high - low > 0.5f, "eyes should circle, range ${high - low}")
    }

    @Test
    fun volumeVisitMakesRoomWatchesTheLevelThenComesBack() {
        fun visit(level: Float): FacePose {
            val face = engine()
            face.showVolume(level)
            face.run(1.2f)
            return FacePose().also { it.copyFrom(face.pose) }
        }
        val quiet = visit(0.2f)
        val loud = visit(0.8f)
        assertTrue(quiet[Channel.HeadY] > 0.25f && quiet[Channel.HeadX] > 0.1f, "should scoot up and right")
        assertTrue(quiet[Channel.HeadScale] < 0.85f, "should shrink to make room")
        assertTrue(loud[Channel.GazeY] > quiet[Channel.GazeY] + 0.3f, "gaze follows the slider level")

        val face = engine()
        repeat(10) {
            face.showVolume(it / 10f)
            face.run(0.3f)
        }
        face.run(5f)
        assertTrue(abs(face.pose[Channel.HeadY]) < 0.05f, "should come home after the panel hides")
    }

    @Test
    fun inPortraitTheVisitGoesAcrossFromThePanel() {
        val face = engine()
        face.setViewport(1080f, 2340f)
        face.showVolume(0.5f)
        face.run(1.2f)
        assertTrue(face.pose[Channel.HeadX] < -0.15f && face.pose[Channel.HeadY] > 0.3f, "should sit near the top-left")
        assertTrue(face.pose[Channel.GazeX] > 0.5f, "should look across at the panel on the right")
    }

    @Test
    fun touchingPartsGivesTheirReactions() {
        val face = engine()
        face.run(1f)
        face.touch(tap(FacePart.LeftEye))
        var leftShut = false
        face.run(0.4f) { if (it.pose[Channel.EyeOpenL] < 0.3f && it.pose[Channel.EyeOpenR] > 0.6f) leftShut = true }
        assertTrue(leftShut, "poking the left eye shuts only that eye")
        face.run(2f)
        face.touch(tap(FacePart.Cheek))
        var blush = 0f
        face.run(0.8f) { blush = maxOf(blush, it.pose[Channel.Blush]) }
        assertTrue(blush > 0.5f, "cheek giggle blushes")
        val events = face.record(FaceEvent.Reaction)
        face.run(2f)
        face.touch(tap(FacePart.Mouth))
        face.run(1f)
        face.touch(tap(FacePart.Forehead))
        face.run(0.2f)
        assertTrue("play Boop" in events && "play Bonk" in events, "$events")
    }

    @Test
    fun pesteringEscalatesFromPoutToGrumpy() {
        val face = engine()
        face.run(1f)
        val events = face.record(FaceEvent.Reaction)
        repeat(10) {
            face.touch(tap(FacePart.Face))
            face.run(0.2f)
        }
        assertTrue("play Pout" in events, "$events")
        assertTrue("play Grumpy" in events, "$events")
    }

    @Test
    fun pettingAndDraggingAreSustained() {
        val face = engine()
        face.run(1f)
        face.touch(FaceTouch(TouchGesture.LongPressStart, FacePart.Face, 0.5f, 0f))
        face.run(1f)
        assertTrue(face.pose[Channel.EyeSmileL] > 0.5f, "petting closes the eyes happily")
        face.touch(FaceTouch(TouchGesture.LongPressEnd, FacePart.Face, 0.5f, 0f))
        face.run(1.5f)
        assertTrue(face.pose[Channel.EyeSmileL] < 0.3f)
        face.touch(FaceTouch(TouchGesture.DragStart, FacePart.Outside, -0.9f, 0.6f))
        face.touch(FaceTouch(TouchGesture.Drag, FacePart.Outside, -0.9f, 0.6f))
        face.run(0.6f)
        assertTrue(face.pose[Channel.GazeX] < -0.5f && face.pose[Channel.GazeY] > 0.3f, "eyes follow the finger")
    }

    @Test
    fun aSleepingFaceJustWakesOnTouch() {
        val face = engine()
        face.dozeOff()
        face.run(5f)
        val events = face.record(FaceEvent.Reaction, FaceEvent.Waking)
        face.touch(tap(FacePart.LeftEye))
        face.run(0.3f)
        assertTrue(FaceEvent.Reaction.name !in events && "play PokeLeft" !in events, "$events")
        assertEquals(Vigilance.Waking, face.vigilance)
    }

    @Test
    fun sageReactsMoreMildly() {
        fun blushPeak(p: Personality): Float {
            val face = engine(personality = p)
            face.run(1f)
            face.touch(tap(FacePart.Cheek))
            var peak = 0f
            face.run(1f) { peak = maxOf(peak, it.pose[Channel.Blush]) }
            return peak
        }
        assertTrue(blushPeak(PersonalityPresets.load("sage")) < blushPeak(PersonalityPresets.load("dexter")) - 0.2f)
    }

    @Test
    fun randomPhoneAndTouchEventsNeverPop() {
        val face = engine(seed = 4)
        val rng = Rng(8)
        val parts = FacePart.entries
        val reactions = listOf(FaceReaction.PluggedIn, FaceReaction.Unplugged, FaceReaction.Shaken, FaceReaction.HeadphonesIn)
        val previous = FacePose().also { it.copyFrom(face.pose) }
        repeat(6_000) {
            when (rng.nextInt(40)) {
                0 -> face.touch(tap(parts[rng.nextInt(parts.size)], rng.nextFloat(-1f, 1f), rng.nextFloat(-1f, 1f)))
                1 -> face.react(reactions[rng.nextInt(reactions.size)])
                2 -> face.showVolume(rng.nextFloat())
                3 -> face.setDeviceState(DeviceState(batteryLow = rng.nextInt(2) == 0, charging = rng.nextInt(2) == 0))
                4 -> face.setActivity(Activity.entries[rng.nextInt(Activity.entries.size)])
            }
            face.step(1f / 60f)
            for (c in Channel.entries) {
                assertTrue(abs(face.pose[c] - previous[c]) <= 0.35f * (c.max - c.min), "$c popped")
                assertTrue(face.pose[c] in c.min..c.max)
            }
            previous.copyFrom(face.pose)
        }
    }

    @Test
    fun sensesDoNotAllocatePerFrame() {
        val face = engine()
        face.setDeviceState(DeviceState(batteryLow = true))
        face.showVolume(0.6f)
        face.react(FaceReaction.Shaken)
        face.touch(FaceTouch(TouchGesture.LongPressStart, FacePart.Face, 0.3f, 0f))
        face.run(1f)
        val threads = ManagementFactory.getThreadMXBean() as com.sun.management.ThreadMXBean
        val before = threads.currentThreadAllocatedBytes
        face.run(30f)
        val allocated = threads.currentThreadAllocatedBytes - before
        assertTrue(allocated < 1_024, "allocated $allocated bytes")
    }
}
