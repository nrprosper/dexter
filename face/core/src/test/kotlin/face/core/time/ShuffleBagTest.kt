package face.core.time

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class ShuffleBagTest {
    @Test
    fun everyRefillHoldsExactlyTheWeights() {
        val weights = intArrayOf(3, 1, 2)
        val bag = ShuffleBag(weights, Rng(3))
        repeat(50) {
            val counts = IntArray(weights.size)
            repeat(weights.sum()) { counts[bag.next()]++ }
            assertEquals(weights.toList(), counts.toList())
        }
    }
}
