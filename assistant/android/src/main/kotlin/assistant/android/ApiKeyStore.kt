package assistant.android

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.security.keystore.StrongBoxUnavailableException
import android.util.Base64
import assistant.core.BrainProvider
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

// An API key (one store per provider), encrypted at rest with AES-256-GCM under a key that lives in the Android Keystore
// (StrongBox where the phone has it) and can never be exported. Only the IV and ciphertext are stored, in an
// app-private preferences file that is excluded from backups and device transfer (a restored copy couldn't be
// decrypted anyway: Keystore keys don't leave the device). The key is never logged or shown in full.
public class ApiKeyStore(context: Context, public val provider: BrainProvider = BrainProvider.Claude) {
    private val prefs = context.applicationContext.getSharedPreferences(file(provider), Context.MODE_PRIVATE)
    private val alias = ALIAS_PREFIX + provider.id + ALIAS_SUFFIX

    public val hasKey: Boolean get() = prefs.contains(CIPHERTEXT)

    // A masked hint for the UI, e.g. "sk-ant-…x7Qa" or "…x7Qa".
    public val hint: String? get() = prefs.getString(HINT, null)

    public fun save(raw: String) {
        val key = raw.trim()
        require(isPlausible(provider, key)) { "That doesn't look like a ${provider.label} API key." }
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, secret())
        val sealed = cipher.doFinal(key.toByteArray(Charsets.UTF_8))
        prefs.edit()
            .putString(IV, Base64.encodeToString(cipher.iv, Base64.NO_WRAP))
            .putString(CIPHERTEXT, Base64.encodeToString(sealed, Base64.NO_WRAP))
            .putString(HINT, (if (provider == BrainProvider.Claude) PREFIX else "") + "…" + key.takeLast(HINT_CHARS))
            .apply()
    }

    // Null when no key is saved, or when it can no longer be decrypted (then it's removed and must be entered again).
    public fun load(): String? {
        val iv = prefs.getString(IV, null) ?: return null
        val sealed = prefs.getString(CIPHERTEXT, null) ?: return null
        return try {
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, secret(), GCMParameterSpec(TAG_BITS, Base64.decode(iv, Base64.NO_WRAP)))
            String(cipher.doFinal(Base64.decode(sealed, Base64.NO_WRAP)), Charsets.UTF_8)
        } catch (e: Exception) {
            clear()
            null
        }
    }

    public fun clear() {
        prefs.edit().clear().apply()
    }

    private fun secret(): SecretKey {
        val store = KeyStore.getInstance(KEYSTORE).apply { load(null) }
        (store.getEntry(alias, null) as? KeyStore.SecretKeyEntry)?.let { return it.secretKey }
        return try {
            generate(strongBox = true)
        } catch (e: StrongBoxUnavailableException) {
            generate(strongBox = false)
        }
    }

    private fun generate(strongBox: Boolean): SecretKey {
        val spec = KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(KEY_BITS)
            .setRandomizedEncryptionRequired(true)
            .setIsStrongBoxBacked(strongBox)
            .build()
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE)
        generator.init(spec)
        return generator.generateKey()
    }

    public companion object {
        private const val FILE = "assistant_secrets"
        private const val KEYSTORE = "AndroidKeyStore"
        private const val ALIAS_PREFIX = "dexter."
        private const val ALIAS_SUFFIX = ".api-key"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val KEY_BITS = 256
        private const val TAG_BITS = 128
        private const val IV = "iv"
        private const val CIPHERTEXT = "ciphertext"
        private const val HINT = "hint"
        private const val PREFIX = "sk-ant-"
        private const val HINT_CHARS = 4
        private val ANTHROPIC = Regex("^sk-ant-[A-Za-z0-9_-]{20,}$")
        // Google has changed its key format over time: accept any printable key without spaces.
        private val GOOGLE = Regex("^[\\x21-\\x7E]{20,256}$")

        // Claude keeps the original file name, so keys saved before Gemini existed still load.
        private fun file(provider: BrainProvider): String = if (provider == BrainProvider.Claude) FILE else FILE + "_" + provider.id

        public fun isPlausible(provider: BrainProvider, key: String): Boolean = when (provider) {
            BrainProvider.Claude -> ANTHROPIC.matches(key.trim())
            BrainProvider.Gemini -> GOOGLE.matches(key.trim())
        }
    }
}
