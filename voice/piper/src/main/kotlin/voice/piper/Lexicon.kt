package voice.piper

import java.io.File

// The words a lexicon can say, for spotting out-of-vocabulary words. Sorted array + binary search keeps it compact
// (~170k words). Reads "word || phones" (sherpa-onnx English) and "word phones" (Kokoro's misaki-based files).
// Multi-word entries are skipped; sherpa-onnx matches those itself.
public class Lexicon private constructor(private val words: Array<String>) {
    public operator fun contains(word: String): Boolean = words.binarySearch(word) >= 0

    // Words the lexicon doesn't know are spelled out letter by letter, so they're heard instead of dropped.
    public fun respell(text: String): String = WORD.replace(text) { m ->
        val w = m.value
        val lower = w.lowercase()
        if (lower in this || lower.trim('\'') in this || w.length > MAX_SPELLED) w else w.toCharArray().joinToString(" ")
    }

    public companion object {
        private val WORD = Regex("""[\p{L}][\p{L}']*""")
        private const val MAX_SPELLED = 12
        private const val SEPARATOR = "||"

        public fun load(file: File): Lexicon {
            val out = ArrayList<String>(INITIAL)
            file.bufferedReader().useLines { lines ->
                for (line in lines) {
                    if (line.startsWith("#")) continue
                    val head = if (SEPARATOR in line) line.substringBefore(SEPARATOR).trim() else line.substringBefore(' ').substringBefore('\t').trim()
                    if (head.isNotEmpty() && ' ' !in head) out.add(head.lowercase())
                }
            }
            return Lexicon(out.distinct().sorted().toTypedArray())
        }

        public fun of(vararg words: String): Lexicon = Lexicon(words.map { it.lowercase() }.distinct().sorted().toTypedArray())

        private const val INITIAL = 180_000
    }
}
