package voice.api

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class TextNormalizerTest {
    private fun n(s: String) = TextNormalizer.normalize(s)

    @Test
    fun cardinals() {
        assertEquals("zero", TextNormalizer.cardinal(0))
        assertEquals("forty two", TextNormalizer.cardinal(42))
        assertEquals("one hundred five", TextNormalizer.cardinal(105))
        assertEquals("twelve thousand three hundred forty five", TextNormalizer.cardinal(12_345))
        assertEquals("two million", TextNormalizer.cardinal(2_000_000))
    }

    @Test
    fun ordinals() {
        assertEquals("first", TextNormalizer.ordinal(1))
        assertEquals("twenty second", TextNormalizer.ordinal(22))
        assertEquals("twelfth", TextNormalizer.ordinal(12))
        assertEquals("fortieth", TextNormalizer.ordinal(40))
        assertEquals("the twenty first floor", n("the 21st floor"))
    }

    @Test
    fun numbersInText() {
        assertEquals("I have three cats and one thousand two hundred fish.", n("I have 3 cats and 1,200 fish."))
        assertEquals("pi is three point one four", n("pi is 3.14"))
        assertEquals("minus five degrees", n("-5 degrees"))
        assertEquals("in nineteen ninety nine and two thousand five and twenty twenty six", n("in 1999 and 2005 and 2026"))
    }

    @Test
    fun moneyPercentAndTime() {
        assertEquals("five dollars fifty", n("$5.50"))
        assertEquals("one euro", n("€1"))
        assertEquals("fifty percent", n("50%"))
        assertEquals("at three thirty or seven o'clock or nine oh five", n("at 3:30 or 7:00 or 9:05"))
    }

    @Test
    fun symbolsAbbreviationsAndJunk() {
        assertEquals("salt and pepper, for example", n("salt & pepper, e.g."))
        assertEquals("Doctor Who", n("Dr. Who"))
        assertEquals("hello world", n("hello 🌍 world"))
    }
}
