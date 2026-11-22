package voice.api

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.File

// Also catches JVM-compatible androidx artifacts the JVM plugin alone would let through.
class NoAndroidImportsTest {
    @Test
    fun mainSourcesDoNotImportAndroid() {
        val root = File("src/main")
        assertTrue(root.isDirectory, "run from the module directory: ${root.absolutePath}")
        val forbidden = Regex("""^\s*import\s+(android|androidx)\.""", RegexOption.MULTILINE)
        val offenders = root.walkTopDown()
            .filter { it.isFile && (it.extension == "kt" || it.extension == "java") }
            .filter { forbidden.containsMatchIn(it.readText()) }
            .map { it.path }
            .toList()
        assertTrue(offenders.isEmpty(), "Android imports found in: $offenders")
    }
}
