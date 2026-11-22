package voice.api

// Spells out what a dictionary-based (lexicon) engine can't read: numbers, money, times, symbols and common abbreviations.
// English only. Engines with their own text frontend (system TTS, cloud) don't need it.
public object TextNormalizer {
    public fun normalize(text: String): String {
        var s = text
        for ((pattern, replacement) in ABBREVIATIONS) s = pattern.replace(s, replacement)
        s = TIME.replace(s) { time(it.groupValues[1].toLong(), it.groupValues[2]) }
        s = MONEY.replace(s) { money(it.groupValues[1], it.groupValues[2], it.groupValues[3]) }
        s = PERCENT.replace(s) { number(it.groupValues[1]) + " percent" }
        s = ORDINAL.replace(s) { ordinal(it.groupValues[1].toLong()) }
        s = NUMBER.replace(s) { number(it.value) }
        for ((symbol, word) in SYMBOLS) s = s.replace(symbol, word)
        s = UNSPEAKABLE.replace(s, " ")
        return SPACES.replace(s, " ").trim()
    }

    public fun cardinal(n: Long): String {
        if (n < 0) return "minus " + cardinal(-n)
        if (n < 20) return ONES[n.toInt()]
        if (n < 100) {
            val tens = TENS[(n / 10).toInt()]
            return if (n % 10 == 0L) tens else tens + " " + ONES[(n % 10).toInt()]
        }
        if (n < 1000) {
            val rest = n % 100
            val head = ONES[(n / 100).toInt()] + " hundred"
            return if (rest == 0L) head else head + " " + cardinal(rest)
        }
        for ((value, name) in SCALES) {
            if (n >= value) {
                val rest = n % value
                val head = cardinal(n / value) + " " + name
                return if (rest == 0L) head else head + " " + cardinal(rest)
            }
        }
        return n.toString()
    }

    public fun ordinal(n: Long): String {
        val words = cardinal(n)
        val last = words.substringAfterLast(' ')
        val head = words.dropLast(last.length)
        val irregular = IRREGULAR_ORDINALS[last]
        return head + when {
            irregular != null -> irregular
            last.endsWith("y") -> last.dropLast(1) + "ieth"
            else -> last + "th"
        }
    }

    private fun number(raw: String): String {
        val negative = raw.startsWith("-")
        val body = raw.removePrefix("-").replace(",", "")
        val whole = body.substringBefore('.')
        val fraction = if ('.' in body) body.substringAfter('.') else ""
        if (whole.length > MAX_DIGITS) return digits(body)
        val value = whole.ifEmpty { "0" }.toLong()
        val words = if (fraction.isEmpty() && !negative && !raw.contains(',') && whole.length == 4 && value in YEARS) {
            year(value)
        } else {
            cardinal(value)
        }
        val signed = if (negative) "minus $words" else words
        return if (fraction.isEmpty()) signed else signed + " point " + digits(fraction)
    }

    private fun year(n: Long): String {
        val hi = n / 100
        val lo = n % 100
        if (n in 2000..2009) return cardinal(n)
        return when {
            lo == 0L -> cardinal(hi) + " hundred"
            lo < 10 -> cardinal(hi) + " oh " + cardinal(lo)
            else -> cardinal(hi) + " " + cardinal(lo)
        }
    }

    private fun time(hour: Long, minutes: String): String {
        val m = minutes.toLong()
        return when {
            m == 0L -> cardinal(hour) + " o'clock"
            m < 10 -> cardinal(hour) + " oh " + cardinal(m)
            else -> cardinal(hour) + " " + cardinal(m)
        }
    }

    private fun money(symbol: String, whole: String, cents: String): String {
        val unit = CURRENCIES.getValue(symbol)
        val amount = whole.replace(",", "").toLong()
        val main = cardinal(amount) + " " + if (amount == 1L) unit.first else unit.second
        if (cents.isEmpty() || cents.toLong() == 0L) return main
        return main + " " + cardinal(cents.toLong())
    }

    private fun digits(s: String): String = s.filter { it.isDigit() }.map { ONES[it - '0'] }.joinToString(" ")

    private const val MAX_DIGITS = 15
    private val YEARS = 1100L..2099L
    private val ONES = arrayOf(
        "zero", "one", "two", "three", "four", "five", "six", "seven", "eight", "nine", "ten", "eleven", "twelve",
        "thirteen", "fourteen", "fifteen", "sixteen", "seventeen", "eighteen", "nineteen",
    )
    private val TENS = arrayOf("", "", "twenty", "thirty", "forty", "fifty", "sixty", "seventy", "eighty", "ninety")
    private val SCALES = listOf(1_000_000_000_000L to "trillion", 1_000_000_000L to "billion", 1_000_000L to "million", 1_000L to "thousand")
    private val IRREGULAR_ORDINALS = mapOf(
        "one" to "first", "two" to "second", "three" to "third", "five" to "fifth", "eight" to "eighth",
        "nine" to "ninth", "twelve" to "twelfth",
    )
    private val CURRENCIES = mapOf("$" to ("dollar" to "dollars"), "€" to ("euro" to "euros"), "£" to ("pound" to "pounds"))
    private val TIME = Regex("""\b(\d{1,2}):([0-5]\d)\b""")
    private val MONEY = Regex("""([$€£])(\d{1,3}(?:,\d{3})+|\d+)(?:\.(\d{1,2}))?""")
    private val PERCENT = Regex("""(-?\d+(?:\.\d+)?)\s?%""")
    private val ORDINAL = Regex("""\b(\d+)(?:st|nd|rd|th)\b""")
    private val NUMBER = Regex("""(?<![\w.])-?(?:\d{1,3}(?:,\d{3})+|\d+)(?:\.\d+)?""")
    private val SPACES = Regex("""\s+""")
    private val UNSPEAKABLE = Regex("""[^\p{L}\p{N}\s.,!?;:'"’“”()\-—–…]""")
    private val SYMBOLS = listOf("&" to " and ", "+" to " plus ", "=" to " equals ", "@" to " at ", "/" to " slash ", "#" to " number ")
    private val ABBREVIATIONS = listOf(
        Regex("""\be\.g\.""", RegexOption.IGNORE_CASE) to "for example",
        Regex("""\bi\.e\.""", RegexOption.IGNORE_CASE) to "that is",
        Regex("""\betc\.""", RegexOption.IGNORE_CASE) to "et cetera.",
        Regex("""\bvs\.""", RegexOption.IGNORE_CASE) to "versus",
        Regex("""\bMr\.""") to "Mister",
        Regex("""\bMrs\.""") to "Missus",
        Regex("""\bMs\.""") to "Miz",
        Regex("""\bDr\.""") to "Doctor",
    )
}
