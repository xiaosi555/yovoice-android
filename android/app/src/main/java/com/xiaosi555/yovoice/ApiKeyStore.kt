package com.xiaosi555.yovoice

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Stores provider API credentials encrypted with an AES-256 key held by Android Keystore.
 * The encryption key is non-exportable; only the IV and ciphertext are kept in app preferences.
 */
object ApiKeyStore {
    private const val PREFS = "yovoice-assistant"
    private const val KEY_ALIAS = "yovoice-assistant-api-key-v1"
    private const val CIPHER_TEXT = "api_key_ciphertext"
    private const val CIPHER_IV = "api_key_iv"
    private const val LEGACY_PLAIN_TEXT = "api_key"
    private const val TRANSFORMATION = "AES/GCM/NoPadding"
    private const val TAG_BITS = 128

    @Synchronized
    fun get(context: Context): String {
        val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val ciphertext = prefs.getString(CIPHER_TEXT, null)
        if (!ciphertext.isNullOrBlank()) {
            val iv = prefs.getString(CIPHER_IV, null)
                ?: throw IllegalStateException("API Key 加密数据不完整，请重新保存密钥")
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, getOrCreateKey(), GCMParameterSpec(TAG_BITS, Base64.decode(iv, Base64.NO_WRAP)))
            return String(cipher.doFinal(Base64.decode(ciphertext, Base64.NO_WRAP)), Charsets.UTF_8)
        }

        // One-time migration from the earlier plaintext preference entry.
        val legacy = prefs.getString(LEGACY_PLAIN_TEXT, null).orEmpty()
        if (legacy.isNotBlank()) {
            save(context, legacy)
            prefs.edit().remove(LEGACY_PLAIN_TEXT).apply()
            return legacy
        }
        return ""
    }

    @Synchronized
    fun save(context: Context, apiKey: String) {
        require(apiKey.isNotBlank()) { "API Key 不能为空" }
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
        val encrypted = cipher.doFinal(apiKey.toByteArray(Charsets.UTF_8))
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString(CIPHER_IV, Base64.encodeToString(cipher.iv, Base64.NO_WRAP))
            .putString(CIPHER_TEXT, Base64.encodeToString(encrypted, Base64.NO_WRAP))
            .remove(LEGACY_PLAIN_TEXT)
            .apply()
    }

    @Synchronized
    fun clear(context: Context) {
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .remove(CIPHER_TEXT)
            .remove(CIPHER_IV)
            .remove(LEGACY_PLAIN_TEXT)
            .apply()
    }

    private fun getOrCreateKey(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        generator.init(
            KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .setRandomizedEncryptionRequired(true)
                .build()
        )
        return generator.generateKey()
    }
}
