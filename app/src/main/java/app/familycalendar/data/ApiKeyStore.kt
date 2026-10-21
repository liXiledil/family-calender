package app.familycalendar.data

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import java.time.YearMonth
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Holds your Anthropic API key on this phone, encrypted with a key that never leaves the Android
 * Keystore. It is not uploaded, so each phone that should use Claude needs the key entered once.
 */
class ApiKeyStore(context: Context) {
    private val prefs = context.getSharedPreferences("claude_settings", Context.MODE_PRIVATE)

    fun hasKey() = prefs.contains(KEY_CIPHER)

    fun load(): String? = runCatching {
        val blob = Base64.decode(prefs.getString(KEY_CIPHER, null) ?: return null, Base64.NO_WRAP)
        val iv = blob.copyOfRange(0, IV_BYTES)
        val cipher = Cipher.getInstance(TRANSFORM).apply { init(Cipher.DECRYPT_MODE, secretKey(), GCMParameterSpec(128, iv)) }
        String(cipher.doFinal(blob.copyOfRange(IV_BYTES, blob.size)), Charsets.UTF_8)
    }.getOrNull()

    fun save(key: String) {
        val cipher = Cipher.getInstance(TRANSFORM).apply { init(Cipher.ENCRYPT_MODE, secretKey()) }
        val blob = cipher.iv + cipher.doFinal(key.trim().toByteArray(Charsets.UTF_8))
        prefs.edit().putString(KEY_CIPHER, Base64.encodeToString(blob, Base64.NO_WRAP)).apply()
    }

    fun clear() = prefs.edit().remove(KEY_CIPHER).apply()

    var model: String
        get() = prefs.getString("model", DEFAULT_MODEL) ?: DEFAULT_MODEL
        set(value) = prefs.edit().putString("model", value.trim().ifEmpty { DEFAULT_MODEL }).apply()

    /** Monthly spending limit in US dollars, enforced against this phone's own usage estimate. */
    var monthlyCapUsd: Double
        get() = prefs.getFloat("cap", 10f).toDouble()
        set(value) = prefs.edit().putFloat("cap", value.toFloat()).apply()

    fun usageThisMonth(): Usage {
        val month = YearMonth.now().toString()
        if (prefs.getString("usageMonth", null) != month) return Usage(0, 0)
        return Usage(prefs.getLong("inTokens", 0), prefs.getLong("outTokens", 0))
    }

    fun addUsage(inputTokens: Long, outputTokens: Long) {
        val current = usageThisMonth()
        prefs.edit().putString("usageMonth", YearMonth.now().toString())
            .putLong("inTokens", current.inputTokens + inputTokens)
            .putLong("outTokens", current.outputTokens + outputTokens).apply()
    }

    private fun secretKey(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey(ALIAS, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        generator.init(
            KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build()
        )
        return generator.generateKey()
    }

    data class Usage(val inputTokens: Long, val outputTokens: Long) {
        /** Rough cost at Claude Opus 5.5 list prices ($4 in / $20 out per million tokens). */
        val estimatedUsd: Double get() = inputTokens * 4.0 / 1_000_000 + outputTokens * 20.0 / 1_000_000
    }

    companion object {
        const val DEFAULT_MODEL = "claude-opus-5-5"
        private const val ALIAS = "firefly_claude_key"
        private const val KEY_CIPHER = "keyCipher"
        private const val TRANSFORM = "AES/GCM/NoPadding"
        private const val IV_BYTES = 12
    }
}
