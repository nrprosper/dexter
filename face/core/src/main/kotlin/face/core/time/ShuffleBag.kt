package face.core.time

// Draws indices without replacement in proportion to their weights, refilling when empty.
public class ShuffleBag(weights: IntArray, private val rng: Rng) {
    private val bag = IntArray(weights.sum()).also { bag ->
        var k = 0
        for (i in weights.indices) repeat(weights[i]) { bag[k++] = i }
    }
    private var remaining = bag.size

    init {
        require(bag.isNotEmpty()) { "shuffle bag needs a positive total weight" }
    }

    public fun next(): Int {
        if (remaining == 0) remaining = bag.size
        val pick = rng.nextInt(remaining)
        val value = bag[pick]
        bag[pick] = bag[remaining - 1]
        bag[remaining - 1] = value
        remaining--
        return value
    }
}
