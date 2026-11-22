package face.render

import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

class EyeLayoutTest {
    @Test
    fun pupilStaysInsideEyeForAnyGazeAndShape() {
        val random = Random(7)
        repeat(5_000) {
            val a = 10f + random.nextFloat() * 90f
            val b = 10f + random.nextFloat() * 150f
            val n = 2f + random.nextFloat() * 6f
            val ratio = 0.3f + random.nextFloat() * 0.5f
            val size = 0.5f + random.nextFloat()
            var gx = random.nextFloat() * 2f - 1f
            var gy = random.nextFloat() * 2f - 1f
            val length = sqrt(gx * gx + gy * gy)
            if (length > 1f) { gx /= length; gy /= length }

            val r = EyeLayout.pupilRadius(a, b, ratio, size)
            val px = EyeLayout.pupilOffsetX(a, b, r, gx)
            val py = EyeLayout.pupilOffsetY(a, b, r, gy)
            for (i in 0 until 32) {
                val t = i / 32f * 2f * Math.PI.toFloat()
                val x = px + r * cos(t)
                val y = py + r * sin(t)
                assertTrue("a=$a b=$b n=$n r=$r gaze=($gx,$gy)", EyeLayout.insideSuperellipse(x, y, a, b, n))
            }
        }
    }
}
