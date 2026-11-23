package voice.piper.model

import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

// Downloads into <target>.part and resumes it with a Range request (guarded by If-Range on the saved ETag),
// then checks size and sha256 before moving the file into place. A bad file is deleted, never kept.
public class ModelDownloader(private val open: (URL) -> HttpURLConnection = { it.openConnection() as HttpURLConnection }) {
    public suspend fun fetch(download: Download, target: File, progress: (Long, Long) -> Unit = { _, _ -> }) {
        if (target.isFile && target.length() == download.size && Sha256.of(target) == download.sha256) return
        target.parentFile?.mkdirs()
        val part = File(target.path + PART)
        val tag = File(target.path + ETAG)
        var have = if (part.isFile) part.length() else 0L
        if (have > download.size) {
            part.delete()
            have = 0
        }
        if (have < download.size) {
            val connection = open(URL(download.url))
            try {
                connection.connectTimeout = TIMEOUT_MILLIS
                connection.readTimeout = TIMEOUT_MILLIS
                connection.instanceFollowRedirects = true
                val etag = if (tag.isFile) tag.readText() else ""
                if (have > 0) {
                    connection.setRequestProperty("Range", "bytes=$have-")
                    if (etag.isNotEmpty()) connection.setRequestProperty("If-Range", etag)
                }
                val code = connection.responseCode
                val append = when (code) {
                    HttpURLConnection.HTTP_PARTIAL -> true
                    HttpURLConnection.HTTP_OK -> false
                    else -> throw IOException("download failed: HTTP $code for ${download.url}")
                }
                if (!append) have = 0
                connection.getHeaderField("ETag")?.let { tag.writeText(it) }
                connection.inputStream.use { input ->
                    FileOutputStream(part, append).use { output ->
                        val buffer = ByteArray(BUFFER)
                        progress(have, download.size)
                        while (true) {
                            currentCoroutineContext().ensureActive()
                            val n = input.read(buffer)
                            if (n < 0) break
                            output.write(buffer, 0, n)
                            have += n
                            if (have > download.size) throw IOException("more data than the manifest size for ${download.url}")
                            progress(have, download.size)
                        }
                    }
                }
            } finally {
                connection.disconnect()
            }
        }
        if (part.length() != download.size) throw IOException("incomplete download: ${part.length()} of ${download.size} bytes")
        val actual = Sha256.of(part)
        if (actual != download.sha256) {
            part.delete()
            tag.delete()
            throw IOException("checksum mismatch for ${download.url}: $actual")
        }
        target.delete()
        if (!part.renameTo(target)) throw IOException("could not move ${part.name} into place")
        tag.delete()
    }

    private companion object {
        const val PART = ".part"
        const val ETAG = ".etag"
        const val BUFFER = 1 shl 16
        const val TIMEOUT_MILLIS = 30_000
    }
}
