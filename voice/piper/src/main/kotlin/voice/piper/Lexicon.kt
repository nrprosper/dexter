package voice.piper

import java.io.File

// The words the lexicon can say, for spotting out-of-vocabulary words. Sorted array + binary search keeps it compact
// (~170k words). Multi-word entries are skipped; sherpa-onnx matches those itself.
internal class Lexicon private constructor(private val words: Array<String>) {
    operator fun contains(word: String): Boolean = words.binarySearch(word) >= 0

    // Words the lexicon doesn't know are spelled out letter by letter, so they're heard instead of dropped.
    fun respell(text: String): String = WORD.replace(text) { m ->
        val w = m.value
        val lower = w.lowercase()
        if (lower in this || lower.trim('\'') in this || w.length > MAX_SPELLED) w else w.toCharArray().joinToString(" ")
    }

    companion object {
        private val WORD = Regex("""[\p{L}][\p{L}']*""")
        private const val MAX_SPELLED = 12
        private const val SEPARATOR = "||"

        fun load(file: File): Lexicon {
            val out = ArrayList<String>(INITIAL)
            file.bufferedReader().useLines { lines ->
                for (line in lines) {
                    if (line.startsWith("#")) continue
                    val head = line.substringBefore(SEPARATOR, "").trim()
                    if (head.isNotEmpty() && ' ' !in head) out.add(head.lowercase())
                }
            }
            return Lexicon(out.distinct().sorted().toTypedArray())
        }

        fun of(vararg words: String): Lexicon = Lexicon(words.map { it.lowercase() }.distinct().sorted().toTypedArray())

        private const val INITIAL = 180_000
    }
}
