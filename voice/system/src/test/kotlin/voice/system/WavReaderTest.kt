package voice.system

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder

class WavReaderTest {
    private fun wav(format: Int, channels: Int, rate: Int, bits: Int, data: ByteArray, declaredData: Int = data.size, extra: ByteArray = ByteArray(0)): ByteArray {
        val b = ByteBuffer.allocate(12 + 24 + extra.size + 8 + data.size).order(ByteOrder.LITTLE_ENDIAN)
        b.put("RIFF".toByteArray()).putInt(0).put("WAVE".toByteArray())
        b.put("fmt ".toByteArray()).putInt(16).putShort(format.toShort()).putShort(channels.toShort()).putInt(rate)
            .putInt(rate * channels * bits / 8).putShort((channels * bits / 8).toShort()).putShort(bits.toShort())
        b.put(extra)
        b.put("data".toByteArray()).putInt(declaredData).put(data)
        return b.array()
    }

    private fun pcm16(vararg v: Short): ByteArray = ByteBuffer.allocate(v.size * 2).order(ByteOrder.LITTLE_ENDIAN).apply { v.forEach { putShort(it) } }.array()

    @Test
    fun reads16BitMono() {
        val audio = WavReader.read(wav(1, 1, 22_050, 16, pcm16(0, 1_000, -1_000, 32_767)))
        assertEquals(22_050, audio.sampleRate)
        assertArrayEquals(shortArrayOf(0, 1_000, -1_000, 32_767), audio.samples)
    }

    @Test
    fun downmixesStereoAndSkipsUnknownChunks() {
        val junk = ByteBuffer.allocate(12).order(ByteOrder.LITTLE_ENDIAN).put("LIST".toByteArray()).putInt(4).putInt(7).array()
        val audio = WavReader.read(wav(1, 2, 16_000, 16, pcm16(1_000, 3_000, -2_000, -2_000), extra = junk))
        assertArrayEquals(shortArrayOf(2_000, -2_000), audio.samples)
    }

    @Test
    fun readsFloatAndStreamedHeaders() {
        val floats = ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN).putFloat(0.5f).putFloat(-1f).array()
        val audio = WavReader.read(wav(3, 1, 24_000, 32, floats, declaredData = 0))
        assertEquals(2, audio.samples.size)
        assertEquals(16_384, audio.samples[0].toInt())
        assertEquals(-32_768, audio.samples[1].toInt())
    }

    @Test
    fun rejectsGarbage() {
        assertTrue(runCatching { WavReader.read(ByteArray(40)) }.isFailure)
        assertTrue(runCatching { WavReader.read(wav(2, 1, 8_000, 4, ByteArray(4))) }.isFailure)
    }
}
