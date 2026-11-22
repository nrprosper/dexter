package face.core.expression

import face.core.rig.Channel
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class ExpressionCodecTest {
    @Test
    fun roundTripKeepsDeltas() {
        val original = Expression.of("test", mapOf(Channel.MouthCurve to 0.7f, Channel.Blush to 0.25f))
        val decoded = ExpressionCodec.decode(ExpressionCodec.encode(original))
        assertEquals("test", decoded.id)
        assertArrayEquals(original.channels, decoded.channels)
        assertArrayEquals(original.deltas, decoded.deltas, 1e-6f)
    }

    @Test
    fun encodeOmitsNegligibleDeltasAndSortsByChannel() {
        val text = ExpressionCodec.encode(
            Expression.of("x", mapOf(Channel.Blush to 0.5f, Channel.GazeX to 0.001f, Channel.EyeOpenL to -0.2f)),
        )
        assertFalse(text.contains("gazeX"))
        assertTrue(text.indexOf("eyeOpenL") < text.indexOf("blush"))
    }

    @Test
    fun unknownChannelIsRejected() {
        val error = assertThrows<IllegalArgumentException> {
            ExpressionCodec.decode("""{"format":1,"id":"bad","deltas":{"noseLength":1}}""")
        }
        assertTrue(error.message!!.contains("noseLength"))
    }

    @Test
    fun unsupportedFormatIsRejected() {
        assertThrows<IllegalArgumentException> {
            ExpressionCodec.decode("""{"format":99,"id":"bad","deltas":{}}""")
        }
    }
}
