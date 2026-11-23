package assistant.core

// Reads the reply JSON as it streams, character by character, and reports the header as soon as its fields are in
// (at the latest when "sentences" starts) and each sentence as soon as its object closes. Tolerant: unknown keys are
// skipped, unknown labels become neutral, and a reply cut short still yields what was complete.
public class ReplyParser(
    private val onHeader: (Feeling) -> Unit,
    private val onSentence: (ReplySentence) -> Unit,
) {
    private enum class Kind { Object, Array }

    private class Frame(val kind: Kind, val key: String?) {
        var pendingKey: String? = null
        var expectKey = kind == Kind.Object
    }

    private val stack = ArrayList<Frame>()
    private val text = StringBuilder()
    private val scalar = StringBuilder()
    private var inString = false
    private var escape = false
    private var unicode = -1
    private var unicodeValue = 0

    private var emotion: String? = null
    private var intensity: Float? = null
    private var confidence: Float? = null
    private var tone: String? = null
    private var headerSent = false

    private var sentenceText: String? = null
    private var sentenceEmotion: String? = null
    private var sentenceMove: String? = null
    private var sentenceCount = 0

    public val sentences: Int get() = sentenceCount

    public fun feed(chunk: CharSequence) {
        for (c in chunk) step(c)
    }

    // Call at the end of the stream; sends the header if the reply never got that far.
    public fun finish() {
        flushScalar()
        sendHeader()
    }

    private fun step(c: Char) {
        if (inString) {
            string(c)
            return
        }
        when (c) {
            '"' -> {
                flushScalar()
                inString = true
                text.setLength(0)
            }
            '{' -> {
                flushScalar()
                open(Kind.Object)
            }
            '[' -> {
                flushScalar()
                open(Kind.Array)
            }
            '}', ']' -> {
                flushScalar()
                close()
            }
            ':' -> {
                flushScalar()
                stack.lastOrNull()?.expectKey = false
            }
            ',' -> {
                flushScalar()
                stack.lastOrNull()?.let { if (it.kind == Kind.Object) it.expectKey = true }
            }
            else -> if (!c.isWhitespace()) scalar.append(c) else flushScalar()
        }
    }

    private fun string(c: Char) {
        if (unicode >= 0) {
            unicodeValue = unicodeValue * HEX + Character.digit(c, HEX).coerceAtLeast(0)
            if (++unicode == UNICODE_DIGITS) {
                text.append(unicodeValue.toChar())
                unicode = -1
            }
            return
        }
        if (escape) {
            escape = false
            when (c) {
                'n' -> text.append('\n')
                't' -> text.append('\t')
                'r' -> text.append('\r')
                'b', 'f' -> text.append(' ')
                'u' -> {
                    unicode = 0
                    unicodeValue = 0
                }
                else -> text.append(c)
            }
            return
        }
        when (c) {
            '\\' -> escape = true
            '"' -> {
                inString = false
                value(text.toString(), isString = true)
            }
            else -> text.append(c)
        }
    }

    private fun flushScalar() {
        if (scalar.isEmpty()) return
        val raw = scalar.toString()
        scalar.setLength(0)
        value(raw, isString = false)
    }

    private fun value(v: String, isString: Boolean) {
        val frame = stack.lastOrNull() ?: return
        if (frame.kind == Kind.Object && frame.expectKey && isString) {
            frame.pendingKey = v
            if (stack.size == 1 && v == SENTENCES) sendHeader()
            return
        }
        val key = frame.pendingKey
        when {
            stack.size == 1 -> when (key) {
                "emotion" -> emotion = v
                "intensity" -> intensity = v.toFloatOrNull()
                "confidence" -> confidence = v.toFloatOrNull()
                "tone" -> tone = v
            }
            inSentence() -> when (key) {
                "text" -> sentenceText = v
                "emotion" -> sentenceEmotion = v
                "move" -> sentenceMove = v
            }
        }
        if (stack.size == 1 && emotion != null && intensity != null && confidence != null && tone != null) sendHeader()
    }

    private fun inSentence(): Boolean =
        stack.size == 3 && stack[1].kind == Kind.Array && stack[1].key == SENTENCES && stack[2].kind == Kind.Object

    private fun open(kind: Kind) {
        val parent = stack.lastOrNull()
        stack.add(Frame(kind, parent?.pendingKey))
        if (stack.size == 3 && inSentence()) {
            sentenceText = null
            sentenceEmotion = null
            sentenceMove = null
        }
    }

    private fun close() {
        if (inSentence()) {
            val t = sentenceText?.trim()
            if (!t.isNullOrEmpty()) {
                sendHeader()
                val e = sentenceEmotion?.let { ReplyContract.emotion(it) }
                onSentence(ReplySentence(sentenceCount++, t, e, ReplyContract.move(sentenceMove)))
            }
        }
        if (stack.isNotEmpty()) stack.removeAt(stack.lastIndex)
    }

    private fun sendHeader() {
        if (headerSent) return
        headerSent = true
        onHeader(
            Feeling(
                emotion = ReplyContract.emotion(emotion),
                intensity = (intensity ?: Feeling.DEFAULT_INTENSITY).coerceIn(0f, 1f),
                confidence = (confidence ?: 1f).coerceIn(0f, 1f),
                tone = ReplyContract.tone(tone),
            ),
        )
    }

    private companion object {
        const val SENTENCES = "sentences"
        const val HEX = 16
        const val UNICODE_DIGITS = 4
    }
}
