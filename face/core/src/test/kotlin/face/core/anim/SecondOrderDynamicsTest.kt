package face.core.anim

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class SecondOrderDynamicsTest {
    private fun run(profile: DynamicsProfile, dt: Float, seconds: Float, record: (Float) -> Unit = {}): Float {
        val springs = SecondOrderDynamics(1).apply { configure(0, profile); reset(floatArrayOf(0f)) }
        val target = floatArrayOf(1f)
        repeat((seconds / dt).toInt()) {
            springs.step(target, dt)
            record(springs.y[0])
        }
        return springs.y[0]
    }

    @Test
    fun criticallyDampedConvergesWithoutOvershoot() {
        var peak = 0f
        val end = run(DynamicsProfile(3f, 1f, 0f), 1f / 60f, 3f) { peak = maxOf(peak, it) }
        assertEquals(1f, end, 1e-3f)
        assertTrue(peak <= 1f + 1e-4f, "peak $peak")
    }

    @Test
    fun underdampedOvershoots() {
        var peak = 0f
        run(DynamicsProfile(3f, 0.3f, 0f), 1f / 60f, 3f) { peak = maxOf(peak, it) }
        assertTrue(peak > 1.2f, "peak $peak")
    }

    @Test
    fun negativeResponseAnticipates() {
        var low = 0f
        run(DynamicsProfile(2f, 0.6f, -0.5f), 1f / 60f, 0.3f) { low = minOf(low, it) }
        assertTrue(low < -0.01f, "low $low")
    }

    @Test
    fun stiffSpringStaysStableAtTheMaxFrameStep() {
        var worst = 0f
        val end = run(DynamicsProfile(30f, 0.1f, 2f), 0.05f, 20f) { worst = maxOf(worst, kotlin.math.abs(it)) }
        assertTrue(end.isFinite() && worst < 5f, "worst $worst end $end")
    }
}
