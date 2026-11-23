package voice.piper

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import voice.piper.model.Download
import voice.piper.model.ModelDownloader
import java.io.File
import java.io.IOException
import kotlin.random.Random

class ModelDownloaderTest {
    @get:Rule
    val tmp = TemporaryFolder()

    private val bytes = Random(1).nextBytes(300_000)

    @Test
    fun downloadsAndVerifies() = runBlocking {
        TestServer().use { server ->
            val url = server.put("/model.bin", bytes)
            val target = File(tmp.root, "model.bin")
            var last = 0L
            ModelDownloader().fetch(Download(url, TestServer.sha256(bytes), bytes.size.toLong()), target) { b, _ -> last = b }
            assertArrayEquals(bytes, target.readBytes())
            assertEquals(bytes.size.toLong(), last)
            assertFalse(File(target.path + ".part").exists())
        }
    }

    @Test
    fun resumesAnInterruptedDownload() = runBlocking {
        TestServer().use { server ->
            val url = server.put("/model.bin", bytes)
            val target = File(tmp.root, "model.bin")
            File(target.path + ".part").writeBytes(bytes.copyOfRange(0, 120_000))
            File(target.path + ".etag").writeText("\"v1\"")
            ModelDownloader().fetch(Download(url, TestServer.sha256(bytes), bytes.size.toLong()), target)
            assertArrayEquals(bytes, target.readBytes())
            assertEquals(listOf("bytes=120000-"), server.ranges)
        }
    }

    @Test
    fun restartsWhenTheServerIgnoresRange() = runBlocking {
        TestServer(honourRange = false).use { server ->
            val url = server.put("/model.bin", bytes)
            val target = File(tmp.root, "model.bin")
            File(target.path + ".part").writeBytes(ByteArray(50_000))
            ModelDownloader().fetch(Download(url, TestServer.sha256(bytes), bytes.size.toLong()), target)
            assertArrayEquals(bytes, target.readBytes())
        }
    }

    @Test
    fun badChecksumDeletesTheFileAndFails() {
        TestServer().use { server ->
            val url = server.put("/model.bin", bytes)
            val target = File(tmp.root, "model.bin")
            val result = runCatching {
                runBlocking { ModelDownloader().fetch(Download(url, "0".repeat(64), bytes.size.toLong()), target) }
            }
            assertTrue(result.exceptionOrNull() is IOException)
            assertFalse(target.exists())
            assertFalse(File(target.path + ".part").exists())
        }
    }

    @Test
    fun aVerifiedFileIsNotDownloadedAgain() = runBlocking {
        TestServer().use { server ->
            val url = server.put("/model.bin", bytes)
            val target = File(tmp.root, "model.bin").apply { writeBytes(bytes) }
            ModelDownloader().fetch(Download(url, TestServer.sha256(bytes), bytes.size.toLong()), target)
            assertTrue(server.ranges.isEmpty())
        }
    }
}
