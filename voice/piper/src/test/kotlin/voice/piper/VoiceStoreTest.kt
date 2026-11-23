package voice.piper

import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.apache.commons.compress.archivers.tar.TarArchiveEntry
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream
import org.apache.commons.compress.compressors.bzip2.BZip2CompressorOutputStream
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import voice.api.Connectivity
import voice.piper.model.Archive
import voice.piper.model.AssetEntry
import voice.piper.model.Download
import voice.piper.model.InstallState
import voice.piper.model.License
import voice.piper.model.NetworkNotAllowedException
import voice.piper.model.VoiceEntry
import voice.piper.model.VoiceManifest
import voice.piper.model.VoiceStore
import java.io.ByteArrayOutputStream
import java.io.File

class VoiceStoreTest {
    @get:Rule
    val tmp = TemporaryFolder()

    private val model = ByteArray(4_000) { it.toByte() }
    private val lexicon = "hello || h ə l ˈoʊ\nworld || w ˈɜː l d\n".toByteArray()

    private fun tarBz2(entries: Map<String, ByteArray>): ByteArray {
        val out = ByteArrayOutputStream()
        TarArchiveOutputStream(BZip2CompressorOutputStream(out)).use { tar ->
            for ((name, data) in entries) {
                tar.putArchiveEntry(TarArchiveEntry(name).apply { size = data.size.toLong() })
                tar.write(data)
                tar.closeArchiveEntry()
            }
        }
        return out.toByteArray()
    }

    private val archive = tarBz2(
        mapOf(
            "voice/en_US-test.onnx" to model,
            "voice/tokens.txt" to "a 1\n".toByteArray(),
            "voice/espeak-ng-data/en_dict" to ByteArray(10),
            "../escape.txt" to "nope".toByteArray(),
        ),
    )

    private fun manifest(server: TestServer, files: Map<String, String> = FILES) = VoiceManifest(
        format = 1,
        assets = listOf(
            AssetEntry(
                "lexicon", "lexicon.txt",
                Download(server.put("/lexicon.txt", lexicon), TestServer.sha256(lexicon), lexicon.size.toLong()),
                License("test", "https://example.com"),
            ),
        ),
        voices = listOf(
            VoiceEntry(
                id = "test-voice", name = "Test", language = "en-US", sampleRate = 22_050,
                license = License("test", "https://example.com"),
                archive = Archive(Download(server.put("/voice.tar.bz2", archive), TestServer.sha256(archive), archive.size.toLong()), files = files),
                requires = listOf("lexicon"), default = true,
            ),
        ),
    )

    @Test
    fun installsOnlyTheListedFilesAndMarksTheVoice() = runBlocking {
        TestServer().use { server ->
            val root = tmp.newFolder("app")
            val store = VoiceStore(root, manifest(server), { Connectivity.Unmetered })
            assertFalse(store.isInstalled("test-voice"))
            val states = store.install("test-voice").toList()
            assertEquals(InstallState.Installed, states.last())
            assertTrue(store.isInstalled("test-voice"))
            val dir = store.voiceDir("test-voice")
            assertArrayEquals(model, File(dir, "model.onnx").readBytes())
            assertEquals(setOf("model.onnx", "tokens.txt", ".installed"), dir.list()!!.toSet())
            assertFalse(File(root, "escape.txt").exists() || File(root.parentFile, "escape.txt").exists())
            assertArrayEquals(lexicon, store.assetFile("lexicon")!!.readBytes())
            store.delete("test-voice")
            assertFalse(store.isInstalled("test-voice"))
        }
    }

    @Test
    fun meteredAndOfflineNetworksWaitByDefault() {
        TestServer().use { server ->
            var network = Connectivity.Metered
            val store = VoiceStore(tmp.newFolder("app"), manifest(server), { network })
            val metered = runCatching { runBlocking { store.install("test-voice").toList() } }.exceptionOrNull()
            assertTrue(metered is NetworkNotAllowedException)
            assertTrue(server.ranges.isEmpty())
            network = Connectivity.Offline
            assertTrue(runCatching { runBlocking { store.install("test-voice").toList() } }.exceptionOrNull() is NetworkNotAllowedException)
            network = Connectivity.Metered
            store.allowMetered = true
            runBlocking { store.install("test-voice").toList() }
            assertTrue(store.isInstalled("test-voice"))
        }
    }

    @Test
    fun aMissingArchiveEntryLeavesNothingInstalled() {
        TestServer().use { server ->
            val store = VoiceStore(tmp.newFolder("app"), manifest(server, FILES + ("MODEL_CARD" to "voice/MODEL_CARD")), { Connectivity.Unmetered })
            assertTrue(runCatching { runBlocking { store.install("test-voice").toList() } }.isFailure)
            assertFalse(store.isInstalled("test-voice"))
        }
    }

    private companion object {
        val FILES = mapOf("model.onnx" to "voice/en_US-test.onnx", "tokens.txt" to "voice/tokens.txt")
    }
}
