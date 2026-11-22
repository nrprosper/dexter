package voice.api

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

// range is inclusive and points into the full utterance text (the concatenated stream for TtsInput.Stream).
public class Sentence(public val text: String, public val range: IntRange)

// Splits text into sentences so synthesis of the first one can start (and play) before the rest is done.
// Long sentences are broken at the last clause mark (or space) before maxChars.
public class SentenceSplitter(private val maxChars: Int = DEFAULT_MAX_CHARS) {
    init {
        require(maxChars >= MIN_MAX_CHARS) { "maxChars must be at least $MIN_MAX_CHARS" }
    }

    public fun split(text: String): List<Sentence> {
        val out = ArrayList<Sentence>()
        scan(text, 0, final = true, out)
        return out
    }

    // Emits each sentence as soon as the text after it shows the boundary is real; the remainder goes out at the end.
    public fun stream(parts: Flow<String>): Flow<Sentence> = flow {
        val buffer = StringBuilder()
        var consumed = 0
        val found = ArrayList<Sentence>()
        parts.collect { part ->
            buffer.append(part)
            found.clear()
            val used = scan(buffer.toString(), consumed, final = false, found)
            for (s in found) emit(s)
            buffer.delete(0, used)
            consumed += used
        }
        found.clear()
        scan(buffer.toString(), consumed, final = true, found)
        for (s in found) emit(s)
    }

    // Returns how many characters of text were consumed into sentences.
    private fun scan(text: String, offset: Int, final: Boolean, out: MutableList<Sentence>): Int {
        var start = 0
        var i = 0
        while (i < text.length) {
            val end = boundaryAfter(text, i, final)
            if (end > 0) {
                add(text, start, end, offset, out)
                start = end
                i = end
                continue
            }
            if (i - start + 1 >= maxChars) {
                val cut = clauseCut(text, start, i)
                add(text, start, cut, offset, out)
                start = cut
            }
            i++
        }
        if (final) {
            add(text, start, text.length, offset, out)
            return text.length
        }
        return start
    }

    // If text[i] ends a sentence, the index just past it (and any closing quotes or brackets); otherwise -1.
    private fun boundaryAfter(text: String, i: Int, final: Boolean): Int {
        val c = text[i]
        if (c == '\n' && i + 1 < text.length && text[i + 1] == '\n') return i + 2
        if (c != '.' && c != '!' && c != '?' && c != '…') return -1
        var end = i + 1
        while (end < text.length && (text[end] == '.' || text[end] == '!' || text[end] == '?' || text[end] == '…')) end++
        while (end < text.length && text[end] in CLOSERS) end++
        if (end >= text.length) return if (final) end else -1
        if (!text[end].isWhitespace()) return -1
        var next = end
        while (next < text.length && text[next].isWhitespace()) next++
        if (next >= text.length) return if (final) end else -1
        val following = text[next]
        if (c == '.' || c == '…') {
            if (following.isLowerCase() || following.isDigit()) return -1
            if (end - i == 1 && c == '.' && isAbbreviation(text, i)) return -1
        }
        return end
    }

    private fun isAbbreviation(text: String, dot: Int): Boolean {
        var s = dot
        while (s > 0 && (text[s - 1].isLetter() || text[s - 1] == '.')) s--
        val word = text.substring(s, dot).lowercase()
        if (word.length == 1 && word[0].isLetter()) return true
        return word in ABBREVIATIONS || word.contains('.')
    }

    private fun clauseCut(text: String, start: Int, i: Int): Int {
        var j = i
        while (j > start + MIN_PIECE) {
            if (text[j] in CLAUSE_MARKS && j + 1 < text.length && text[j + 1].isWhitespace()) return j + 1
            j--
        }
        j = i
        while (j > start + MIN_PIECE) {
            if (text[j].isWhitespace()) return j
            j--
        }
        return i + 1
    }

    private fun add(text: String, from: Int, until: Int, offset: Int, out: MutableList<Sentence>) {
        var a = from
        var b = until
        while (a < b && text[a].isWhitespace()) a++
        while (b > a && text[b - 1].isWhitespace()) b--
        if (b <= a || text.substring(a, b).none { it.isLetterOrDigit() }) return
        out.add(Sentence(text.substring(a, b), (offset + a) until (offset + b)))
    }

    public companion object {
        public const val DEFAULT_MAX_CHARS: Int = 220
        private const val MIN_MAX_CHARS = 20
        private const val MIN_PIECE = 8
        private const val CLOSERS = "\"'”’)]}»"
        private const val CLAUSE_MARKS = ",;:—–"
        private val ABBREVIATIONS = setOf(
            "mr", "mrs", "ms", "dr", "prof", "sr", "jr", "st", "mt", "vs", "etc", "approx", "dept", "inc", "ltd",
            "co", "jan", "feb", "mar", "apr", "jun", "jul", "aug", "sep", "sept", "oct", "nov", "dec",
        )
    }
}
