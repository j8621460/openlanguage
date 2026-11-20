package com.palabrasfuncionales.app.security

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
 * Stores the user's Google Cloud API key encrypted with an AES-256-GCM key that
 * lives in the Android Keystore (hardware-backed where available) and never
 * leaves it. Backups are disabled in the manifest, so the ciphertext stays on
 * this device too.
 */
class SecureStore(context: Context) {
    private val prefs = context.applicationContext
        .getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun getApiKey(): String? {
        val iv = prefs.getString(K_IV, null) ?: return null
        val ct = prefs.getString(K_CT, null) ?: return null
        return try {
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, secretKey(), GCMParameterSpec(128, Base64.decode(iv, Base64.NO_WRAP)))
            String(cipher.doFinal(Base64.decode(ct, Base64.NO_WRAP)), Charsets.UTF_8)
        } catch (e: Exception) {
            // Key invalidated (e.g. lock-screen reset) or data corrupted: start clean.
            clear()
            null
        }
    }

    fun hasApiKey(): Boolean = prefs.contains(K_CT)

    fun putApiKey(value: String) {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, secretKey())
        val ct = cipher.doFinal(value.toByteArray(Charsets.UTF_8))
        prefs.edit()
            .putString(K_IV, Base64.encodeToString(cipher.iv, Base64.NO_WRAP))
            .putString(K_CT, Base64.encodeToString(ct, Base64.NO_WRAP))
            .apply()
    }

    fun clear() {
        prefs.edit().remove(K_IV).remove(K_CT).apply()
    }

    private fun secretKey(): SecretKey {
        val ks = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        (ks.getEntry(ALIAS, null) as? KeyStore.SecretKeyEntry)?.let { return it.secretKey }
        val gen = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        gen.init(
            KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .setRandomizedEncryptionRequired(true)
                .build()
        )
        return gen.generateKey()
    }

    private companion object {
        const val PREFS = "secure_store"
        const val K_IV = "api_key_iv"
        const val K_CT = "api_key_ct"
        const val ALIAS = "palabras_api_key_v1"
        const val ANDROID_KEYSTORE = "AndroidKeyStore"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
    }
}
