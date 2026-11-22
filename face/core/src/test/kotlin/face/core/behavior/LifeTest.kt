package face.core.behavior

import face.core.FaceEngine
import face.core.expression.ExpressionLibrary
import face.core.rig.Channel
import face.core.time.Rng
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class LifeTest {
    private val library = ExpressionLibrary.loadBundled()
    private val dt = 1f / 60f
    private val neverBored = BoredomConfig(drowsyAfter = 1e9f, sleepAfter = 2e9f)

    private fun run(engine: FaceEngine, seconds: Float) {
        repeat((seconds / dt).toInt()) { engine.step(dt) }
    }

    @Test
    fun blinkStatisticsOverAnHourMatchTheConfig() {
        val config = LifeConfig(boredom = neverBored)
        val engine = FaceEngine(library, Rng(2024), life = config)
        run(engine, 3_600f)
        val stats = engine.blinkStats
        val perMinute = stats.spontaneousCount / 60f
        assertEquals(60f / config.blink.meanInterval, perMinute, 60f / config.blink.meanInterval * 0.08f)
        val doubleShare = stats.doubleCount.toFloat() / stats.spontaneousCount
        val expected = config.blink.doubleChance * (1f - config.blink.slowChance)
        assertEquals(expected, doubleShare, 0.04f)
    }

    @Test
    fun attentionBudgetIsNeverExceeded() {
        val budget = 3
        val config = LifeConfig(boredom = neverBored, idle = IdleConfig(meanInterval = 1.5f, budgetPerMinute = budget))
        val noticeable = config.idle.behaviors.filter { it.noticeable }.map { it.name }.toSet()
        val starts = ArrayList<Double>()
        val engine = FaceEngine(library, Rng(77), life = config)
        engine.listener = FaceEventListener { event, detail, time ->
            if (event == FaceEvent.Behavior && detail in noticeable) starts.add(time)
        }
        run(engine, 7_200f)
        for (i in starts.indices) {
            val inWindow = starts.count { it > starts[i] - 60.0 && it <= starts[i] }
            assertTrue(inWindow <= budget, "$inWindow noticeable behaviours within a minute at ${starts[i]}")
        }
        assertTrue(starts.size >= 300, "the budget should be the limit, but only ${starts.size} fired")
    }

    @Test
    fun fallsAsleepOnScheduleAndWakesOnInteraction() {
        val config = LifeConfig.Default
        val events = ArrayList<Pair<FaceEvent, Double>>()
        val engine = FaceEngine(library, Rng(9), life = config)
        engine.listener = FaceEventListener { event, _, time ->
            if (event in setOf(FaceEvent.Drowsy, FaceEvent.Asleep, FaceEvent.Waking, FaceEvent.Awake)) events.add(event to time)
        }
        run(engine, config.boredom.sleepAfter + 10f)
        assertEquals(config.boredom.drowsyAfter.toDouble(), events.first { it.first == FaceEvent.Drowsy }.second, 0.05)
        assertEquals(config.boredom.sleepAfter.toDouble(), events.first { it.first == FaceEvent.Asleep }.second, 0.05)
        assertEquals(Vigilance.Asleep, engine.vigilance)
        assertTrue(engine.pose[Channel.EyeOpenL] < 0.15f && engine.pose[Channel.EyeOpenR] < 0.15f, "eyes should be shut")
        assertTrue(engine.pose[Channel.SleepZ] > 0.5f, "Zzz should show")

        engine.interact()
        engine.step(dt)
        assertEquals(Vigilance.Waking, engine.vigilance)
        run(engine, config.boredom.wakeDuration + 0.1f)
        assertEquals(Vigilance.Awake, engine.vigilance)
        run(engine, 1f)
        assertTrue(engine.pose[Channel.EyeOpenL] > 0.6f, "eyes should be open again")
    }

    @Test
    fun sleepingFaceDoesNotBlinkOrFidget() {
        val engine = FaceEngine(library, Rng(4))
        engine.dozeOff()
        run(engine, 5f)
        var behaviours = 0
        var blinks = 0
        engine.listener = FaceEventListener { event, _, _ ->
            if (event == FaceEvent.Behavior) behaviours++
            if (event == FaceEvent.Blink || event == FaceEvent.DoubleBlink || event == FaceEvent.SlowBlink) blinks++
        }
        run(engine, 120f)
        assertEquals(0, behaviours)
        assertEquals(0, blinks)
    }
}
