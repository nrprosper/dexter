package face.core.senses

import face.api.DayPart
import face.api.DeviceState
import face.api.FaceReaction
import face.api.Heat
import face.core.behavior.FaceEvent
import face.core.behavior.Vigilance
import face.core.director.FaceFixtures.engine
import face.core.director.FaceFixtures.record
import face.core.director.FaceFixtures.run
import face.core.rig.Channel
import face.core.rig.FacePose
import face.core.time.Rng
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.lang.management.ManagementFactory
import kotlin.math.abs

class MoreSensesTest {
    @Test
    fun heatEscalatesFromFlushToMelting() {
        fun at(heat: Heat): FacePose {
            val face = engine()
            face.setDeviceState(DeviceState(heat = heat))
            face.run(3f)
            return FacePose().also { it.copyFrom(face.pose) }
        }
        val warm = at(Heat.Warm)
        val hot = at(Heat.Hot)
        val critical = at(Heat.Critical)
        assertTrue(warm[Channel.Blush] > 0.2f)
        assertTrue(hot[Channel.Sweat] > 0.5f && hot[Channel.EyeSquint] > 0.2f)
        assertTrue(critical[Channel.HeadSquash] < -0.2f && critical[Channel.HeadY] < -0.04f, "critical heat melts")
    }

    @Test
    fun aHandOverTheScreenShutsTheEyesThenOnePeeks() {
        val face = engine()
        face.lifeEnabled = false
        face.setDeviceState(DeviceState(handOver = true))
        face.run(0.5f)
        assertTrue(face.pose[Channel.EyeOpenL] < 0.25f && face.pose[Channel.EyeOpenR] < 0.25f, "both shut")
        face.run(1.2f)
        assertTrue(face.pose[Channel.EyeOpenR] > face.pose[Channel.EyeOpenL] + 0.25f, "right eye peeks")
    }

    @Test
    fun thePocketSleepsAndTakingItOutWakes() {
        val face = engine()
        face.setDeviceState(DeviceState(handOver = true, dark = true, inPocket = true))
        face.run(4f)
        assertEquals(Vigilance.Asleep, face.vigilance)
        face.setDeviceState(DeviceState())
        face.run(0.2f)
        assertEquals(Vigilance.Waking, face.vigilance)
    }

    @Test
    fun darkNightAndSaverMakeItSleepierSooner() {
        fun asleepAfter(state: DeviceState): Float {
            val face = engine()
            face.setDeviceState(state)
            var t = 0f
            while (face.vigilance != Vigilance.Asleep && t < 400f) {
                face.step(1f / 30f)
                t += 1f / 30f
            }
            return t
        }
        val normal = asleepAfter(DeviceState())
        val sleepy = asleepAfter(DeviceState(dark = true, dayPart = DayPart.Night, batterySaver = true))
        assertTrue(sleepy < normal * 0.5f, "normal $normal, sleepy $sleepy")
    }

    @Test
    fun batterySaverLooksLowEnergy() {
        val normal = engine().apply {
            lifeEnabled = false
            run(3f)
        }
        val saver = engine().apply {
            lifeEnabled = false
            setDeviceState(DeviceState(batterySaver = true))
            run(3f)
        }
        assertTrue(saver.pose[Channel.EyeOpenL] < normal.pose[Channel.EyeOpenL] - 0.08f)
        assertTrue(saver.pose[Channel.Glow] < normal.pose[Channel.Glow] - 0.1f)
    }

    @Test
    fun tiltSlidesAndLooksDownhillWithinBounds() {
        val face = engine()
        face.lifeEnabled = false
        face.setTilt(1f, 0f)
        face.run(1.5f)
        assertTrue(face.pose[Channel.GazeX] > 0.25f && face.pose[Channel.HeadX] > 0.05f)
        face.setTilt(-1f, 1f)
        face.run(1.5f)
        assertTrue(face.pose[Channel.GazeX] < -0.25f && face.pose[Channel.GazeY] < -0.2f)
        face.setTilt(5f, -5f)
        face.run(1f)
        for (c in Channel.entries) assertTrue(face.pose[c] in c.min..c.max)
    }

    @Test
    fun newPhoneReactionsPlay() {
        val face = engine()
        val events = face.record(FaceEvent.Reaction)
        val all = listOf(
            FaceReaction.TooBright, FaceReaction.Screenshot, FaceReaction.WelcomeBack(),
            FaceReaction.WelcomeBack(longAbsence = true), FaceReaction.GoodMorning, FaceReaction.LateNight,
        )
        for (r in all) {
            face.react(r)
            face.run(4f)
        }
        for (name in listOf("TooBright", "Screenshot", "WelcomeBack", "WelcomeBackLong", "GoodMorning", "LateNight")) {
            assertTrue("play $name" in events, "missing $name in $events")
        }
    }

    @Test
    fun randomNewSensesNeverPop() {
        val face = engine(seed = 9)
        val rng = Rng(21)
        val previous = FacePose().also { it.copyFrom(face.pose) }
        repeat(6_000) {
            when (rng.nextInt(30)) {
                0 -> face.setDeviceState(
                    DeviceState(
                        heat = Heat.entries[rng.nextInt(Heat.entries.size)], handOver = rng.nextInt(3) == 0,
                        dark = rng.nextInt(2) == 0, batterySaver = rng.nextInt(2) == 0,
                    ),
                )
                1 -> face.setTilt(rng.nextFloat(-1f, 1f), rng.nextFloat(-1f, 1f))
                2 -> face.react(FaceReaction.Screenshot)
                3 -> face.react(FaceReaction.TooBright)
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
    fun newSensesDoNotAllocatePerFrame() {
        val face = engine()
        face.setDeviceState(DeviceState(heat = Heat.Hot, dark = true, batterySaver = true, handOver = true))
        face.setTilt(0.4f, -0.2f)
        face.run(2f)
        val threads = ManagementFactory.getThreadMXBean() as com.sun.management.ThreadMXBean
        val before = threads.currentThreadAllocatedBytes
        face.run(30f)
        val allocated = threads.currentThreadAllocatedBytes - before
        assertTrue(allocated < 1_024, "allocated $allocated bytes")
    }
}
