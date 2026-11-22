package face.core.speech

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class MouthConfigTest {
    @Test
    fun bundledMatchesDefaultsAndRoundTrips() {
        val bundled = MouthConfig.loadBundled()
        assertEquals(MouthConfig(), bundled)
        assertEquals(bundled, MouthConfig.decode(MouthConfig.encode(bundled)))
    }

    @Test
    fun rejectsBadValuesAndUnknownKeys() {
        assertThrows<IllegalArgumentException> { MouthConfig.decode("""{"format": 2}""") }
        assertThrows<IllegalArgumentException> { MouthConfig.decode("""{"format": 1, "openLevel": 0.01, "closeLevel": 0.2}""") }
        assertThrows<Exception> { MouthConfig.decode("""{"format": 1, "loudness": 3}""") }
    }
}
