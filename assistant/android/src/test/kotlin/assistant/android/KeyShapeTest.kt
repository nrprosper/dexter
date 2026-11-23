package assistant.android

import assistant.core.BrainProvider
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class KeyShapeTest {
    @Test
    fun geminiKeysOfEitherFormatAreAccepted() {
        assertTrue(ApiKeyStore.isPlausible(BrainProvider.Gemini, "AIzaSyD-abcdefghijklmnopqrstuvwxyz012345"))
        assertTrue(ApiKeyStore.isPlausible(BrainProvider.Gemini, "AQ.Ab8RN6Kx_example.key-with.dots_0123456789"))
        assertTrue(ApiKeyStore.isPlausible(BrainProvider.Gemini, "  AIzaSyD-abcdefghijklmnopqrstuvwxyz012345\n"))
        assertFalse(ApiKeyStore.isPlausible(BrainProvider.Gemini, "short"))
        assertFalse(ApiKeyStore.isPlausible(BrainProvider.Gemini, "has a space in the middle of it 123456"))
    }

    @Test
    fun anthropicKeysStayStrict() {
        assertTrue(ApiKeyStore.isPlausible(BrainProvider.Claude, "sk-ant-api03-abcdefghijklmnopqrstuvwxyz"))
        assertFalse(ApiKeyStore.isPlausible(BrainProvider.Claude, "AIzaSyD-abcdefghijklmnopqrstuvwxyz012345"))
    }
}
