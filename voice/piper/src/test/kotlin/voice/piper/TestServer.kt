package voice.piper

import com.sun.net.httpserver.HttpServer
import java.net.InetSocketAddress
import java.security.MessageDigest

// Serves byte arrays on 127.0.0.1; honours Range requests unless told not to. Records each request's Range header.
internal class TestServer(private val honourRange: Boolean = true) : AutoCloseable {
    private val files = HashMap<String, ByteArray>()
    val ranges = ArrayList<String?>()
    private val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0).apply {
        createContext("/") { exchange ->
            val body = files[exchange.requestURI.path]
            val range = exchange.requestHeaders.getFirst("Range")
            synchronized(ranges) { ranges.add(range) }
            if (body == null) {
                exchange.sendResponseHeaders(404, -1)
                exchange.close()
                return@createContext
            }
            exchange.responseHeaders.add("ETag", "\"v1\"")
            val from = if (honourRange && range != null) range.removePrefix("bytes=").substringBefore('-').toInt() else 0
            val part = body.copyOfRange(from, body.size)
            exchange.sendResponseHeaders(if (from > 0) 206 else 200, part.size.toLong())
            exchange.responseBody.use { it.write(part) }
        }
        start()
    }

    fun put(path: String, bytes: ByteArray): String {
        files[path] = bytes
        return "http://127.0.0.1:${server.address.port}$path"
    }

    override fun close() = server.stop(0)

    companion object {
        fun sha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
    }
}
