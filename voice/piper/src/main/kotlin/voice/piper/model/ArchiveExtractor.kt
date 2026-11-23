package voice.piper.model

import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import org.apache.commons.compress.compressors.bzip2.BZip2CompressorInputStream
import java.io.File
import java.io.IOException

// Pulls only the listed entries out of a tar.bz2, writing each to its fixed name in dir. Entry paths are never used
// as output paths, so a hostile archive can't write outside dir.
internal object ArchiveExtractor {
    fun extract(archive: File, files: Map<String, String>, dir: File) {
        val wanted = files.entries.associate { (name, entry) -> entry.trimStart('/') to name }
        val found = HashSet<String>()
        dir.mkdirs()
        TarArchiveInputStream(BZip2CompressorInputStream(archive.inputStream().buffered())).use { tar ->
            while (true) {
                val entry = tar.nextEntry ?: break
                val name = wanted[entry.name.trimStart('/').removePrefix("./")] ?: continue
                if (!entry.isFile) continue
                File(dir, name).outputStream().use { tar.copyTo(it) }
                found.add(name)
            }
        }
        val missing = files.keys - found
        if (missing.isNotEmpty()) throw IOException("archive ${archive.name} is missing $missing")
    }
}
