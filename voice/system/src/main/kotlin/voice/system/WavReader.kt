package voice.system

import java.io.IOException
import java.nio.ByteBuffer
import java.nio.ByteOrder

public class WavAudio(public val samples: ShortArray, public val sampleRate: Int)

// Reads the WAV files TextToSpeech.synthesizeToFile writes: PCM 8/16/24/32-bit or 32-bit float, any channel count,
// downmixed to 16-bit mono. A data chunk with a bogus length (streamed headers) is read to the end of the file.
public object WavReader {
    public fun read(bytes: ByteArray): WavAudio {
        val b = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        if (bytes.size < HEADER || tag(b, 0) != "RIFF" || tag(b, 8) != "WAVE") throw IOException("not a WAV file")
        var at = HEADER
        var format = -1
        var channels = 0
        var rate = 0
        var bits = 0
        while (at + CHUNK_HEADER <= bytes.size) {
            val id = tag(b, at)
            val declared = b.getInt(at + 4).toLong() and 0xFFFF_FFFFL
            val body = at + CHUNK_HEADER
            when (id) {
                "fmt " -> {
                    format = b.getShort(body).toInt() and 0xFFFF
                    channels = b.getShort(body + 2).toInt()
                    rate = b.getInt(body + 4)
                    bits = b.getShort(body + 14).toInt()
                    if (format == EXTENSIBLE && declared >= 26) format = b.getShort(body + 24).toInt() and 0xFFFF
                }
                "data" -> {
                    if (format < 0) throw IOException("data before fmt")
                    val length = if (declared == 0L || body + declared > bytes.size) (bytes.size - body).toLong() else declared
                    return WavAudio(decode(b, body, length.toInt(), format, channels, bits), rate)
                }
            }
            at = body + declared.toInt() + (declared.toInt() and 1)
        }
        throw IOException("WAV file has no data chunk")
    }

    private fun decode(b: ByteBuffer, start: Int, length: Int, format: Int, channels: Int, bits: Int): ShortArray {
        if (channels < 1) throw IOException("bad channel count $channels")
        if (format != PCM && format != FLOAT) throw IOException("unsupported WAV format $format")
        if (format == FLOAT && bits != 32) throw IOException("unsupported float width $bits")
        val width = bits / 8
        if (width !in 1..4) throw IOException("unsupported sample width $bits")
        val frames = length / (width * channels)
        if (format == PCM && width == 2) {
            return ShortArray(frames) { f ->
                var sum = 0
                for (c in 0 until channels) sum += b.getShort(start + (f * channels + c) * 2)
                (sum / channels).toShort()
            }
        }
        return ShortArray(frames) { f ->
            var sum = 0f
            for (c in 0 until channels) sum += sample(b, start + (f * channels + c) * width, width, format == FLOAT)
            (sum / channels * FULL).toInt().coerceIn(-32_768, 32_767).toShort()
        }
    }

    // One sample as -1..1.
    private fun sample(b: ByteBuffer, at: Int, width: Int, float: Boolean): Float = when {
        float -> b.getFloat(at)
        width == 1 -> ((b.get(at).toInt() and 0xFF) - 128) / 128f
        width == 2 -> b.getShort(at) / 32_768f
        width == 3 -> ((b.get(at).toInt() and 0xFF) or ((b.get(at + 1).toInt() and 0xFF) shl 8) or (b.get(at + 2).toInt() shl 16)) / 8_388_608f
        else -> b.getInt(at) / 2_147_483_648f
    }

    private fun tag(b: ByteBuffer, at: Int): String = String(CharArray(4) { (b.get(at + it).toInt() and 0xFF).toChar() })

    private const val HEADER = 12
    private const val CHUNK_HEADER = 8
    private const val PCM = 1
    private const val FLOAT = 3
    private const val EXTENSIBLE = 0xFFFE
    private const val FULL = 32_768f
}
