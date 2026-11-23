package voice.piper.model

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ProducerScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.buffer
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import voice.api.Connectivity
import java.io.File
import java.io.IOException

public sealed interface InstallState {
    public data class Downloading(val file: String, val bytes: Long, val total: Long) : InstallState
    public data object Extracting : InstallState
    public data object Installed : InstallState
}

public class NetworkNotAllowedException(message: String) : IOException(message)

// Voices live in app-private storage: <root>/voices/<id>/ and shared files in <root>/assets/.
// A voice counts as installed only once its marker file is written, after every file has been verified and extracted.
public class VoiceStore(
    root: File,
    public val manifest: VoiceManifest,
    private val connectivity: () -> Connectivity,
    private val downloader: ModelDownloader = ModelDownloader(),
) {
    private val voicesDir = File(root, "voices")
    private val assetsDir = File(root, "assets")
    private val downloadsDir = File(root, "downloads")

    // Wi-Fi (unmetered) only unless the user allows mobile data.
    @Volatile
    public var allowMetered: Boolean = false

    public fun isInstalled(voiceId: String): Boolean {
        val voice = manifest.voice(voiceId) ?: return false
        val dir = voiceDir(voiceId)
        if (!File(dir, MARKER).isFile) return false
        if (voice.archive.files.keys.any { !File(dir, it).isFile }) return false
        return voice.requires.all { assetFile(it)?.isFile == true }
    }

    public fun installedVoices(): List<VoiceEntry> = manifest.voices.filter { isInstalled(it.id) }

    public fun voiceDir(voiceId: String): File = File(voicesDir, voiceId)

    public fun assetFile(assetId: String): File? = manifest.asset(assetId)?.let { File(assetsDir, it.file) }

    // Progress is conflated: a slow collector sees the latest state, and always the final Installed.
    // One install at a time: concurrent calls would append to the same partial files.
    public fun install(voiceId: String): Flow<InstallState> = channelFlow {
        val voice = manifest.voice(voiceId) ?: throw IllegalArgumentException("unknown voice $voiceId")
        installLock.withLock { installLocked(voice) }
    }.buffer(Channel.CONFLATED).flowOn(Dispatchers.IO)

    private suspend fun ProducerScope<InstallState>.installLocked(voice: VoiceEntry) {
        val voiceId = voice.id
        if (isInstalled(voiceId)) {
            send(InstallState.Installed)
            return
        }
        checkNetwork()
        for (assetId in voice.requires) {
            val asset = manifest.asset(assetId) ?: continue
            downloader.fetch(asset.download, File(assetsDir, asset.file)) { bytes, total ->
                trySend(InstallState.Downloading(asset.file, bytes, total))
            }
        }
        val archive = File(downloadsDir, "$voiceId.tar.bz2")
        downloader.fetch(voice.archive.download, archive) { bytes, total -> trySend(InstallState.Downloading(voiceId, bytes, total)) }
        send(InstallState.Extracting)
        val staging = File(voicesDir, "$voiceId.tmp")
        staging.deleteRecursively()
        ArchiveExtractor.extract(archive, voice.archive.files, staging)
        File(staging, MARKER).writeText(voice.archive.download.sha256)
        val dir = voiceDir(voiceId)
        dir.deleteRecursively()
        if (!staging.renameTo(dir)) throw IOException("could not move voice $voiceId into place")
        archive.delete()
        send(InstallState.Installed)
    }

    public fun delete(voiceId: String) {
        voiceDir(voiceId).deleteRecursively()
        File(downloadsDir, "$voiceId.tar.bz2").delete()
    }

    private fun checkNetwork() {
        when (connectivity()) {
            Connectivity.Offline -> throw NetworkNotAllowedException("no network connection")
            Connectivity.Metered -> if (!allowMetered) throw NetworkNotAllowedException("voice downloads wait for Wi-Fi")
            Connectivity.Unmetered -> Unit
        }
    }

    private companion object {
        const val MARKER = ".installed"

        // Process-wide: the app and its settings screen may each hold a VoiceStore over the same files.
        val installLock = Mutex()
    }
}
