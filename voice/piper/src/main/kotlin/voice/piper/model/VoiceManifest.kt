package voice.piper.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

// voices.json, format 1: downloadable Piper voices and the shared files they need (the English lexicon).
// Model files never live in git; each entry points at its official source with a pinned sha256 and size.
@Serializable
public data class VoiceManifest(
    val format: Int,
    val assets: List<AssetEntry> = emptyList(),
    val voices: List<VoiceEntry>,
) {
    init {
        require(format == FORMAT) { "unsupported voice manifest format $format" }
        val assetIds = assets.map { it.id }.toSet()
        require(assetIds.size == assets.size) { "duplicate asset ids" }
        require(voices.map { it.id }.toSet().size == voices.size) { "duplicate voice ids" }
        for (v in voices) {
            for (r in v.requires) require(r in assetIds) { "voice ${v.id} requires unknown asset $r" }
            require(MODEL in v.archive.files && TOKENS in v.archive.files) { "voice ${v.id} must extract $MODEL and $TOKENS" }
        }
        require(voices.count { it.default } <= 1) { "more than one default voice" }
    }

    public val defaultVoice: VoiceEntry? get() = voices.firstOrNull { it.default } ?: voices.firstOrNull()

    public fun voice(id: String): VoiceEntry? = voices.firstOrNull { it.id == id }

    public fun asset(id: String): AssetEntry? = assets.firstOrNull { it.id == id }

    public companion object {
        public const val FORMAT: Int = 1
        public const val MODEL: String = "model.onnx"
        public const val TOKENS: String = "tokens.txt"
        private const val RESOURCE = "/voice/piper/voices.json"
        private val json = Json { ignoreUnknownKeys = false }

        public fun decode(text: String): VoiceManifest = json.decodeFromString(serializer(), text)

        public fun loadBundled(): VoiceManifest {
            val text = VoiceManifest::class.java.getResourceAsStream(RESOURCE)?.bufferedReader()?.use { it.readText() }
                ?: throw IllegalStateException("missing bundled $RESOURCE")
            return decode(text)
        }
    }
}

// Plain http is accepted only for a local test server.
@Serializable
public data class Download(val url: String, val sha256: String, val size: Long) {
    private companion object {
        val LOCAL = Regex("""^http://(127\.0\.0\.1|localhost)(:\d+)?/""")
    }

    init {
        require(url.startsWith("https://") || LOCAL.containsMatchIn(url)) { "downloads must use https: $url" }
        require(sha256.matches(Regex("[0-9a-f]{64}"))) { "sha256 must be 64 lowercase hex characters" }
        require(size > 0) { "size must be positive" }
    }
}

@Serializable
public data class License(val name: String, val url: String, val attribution: String = "")

// A single file (kind "file") stored as-is under its id.
@Serializable
public data class AssetEntry(val id: String, val file: String, val download: Download, val license: License)

// files maps the name we store (model.onnx, tokens.txt, MODEL_CARD) to the entry path inside the archive;
// everything else in the archive (for example eSpeak NG data) is skipped.
@Serializable
public data class Archive(val download: Download, val format: String = "tar.bz2", val files: Map<String, String>) {
    init {
        require(format == "tar.bz2") { "unsupported archive format $format" }
        for (name in files.keys) require(name.matches(Regex("[A-Za-z0-9_.-]+")) && name != "." && name != "..") { "bad file name $name" }
    }
}

@Serializable
public data class VoiceEntry(
    val id: String,
    val name: String,
    val language: String,
    val sampleRate: Int,
    val speakers: Int = 1,
    val quality: String = "medium",
    val license: License,
    val archive: Archive,
    val requires: List<String> = emptyList(),
    val default: Boolean = false,
)
