package com.example.formeit

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
 * Stores the FormeIt login (email + password) encrypted with an AES-GCM key that never
 * leaves the Android Keystore. The official client keeps the same two values to log in
 * again at every start; the beta does the same, without keeping them in clear text.
 */
class SecureStore(context: Context) {
    private val prefs = context.getSharedPreferences("formeit_secure", Context.MODE_PRIVATE)

    fun saveCredentials(email: String, password: String) {
        prefs.edit()
            .putString(KEY_EMAIL, email)
            .putString(KEY_PASSWORD, encrypt(password))
            .apply()
    }

    fun email(): String? = prefs.getString(KEY_EMAIL, null)

    fun password(): String? = prefs.getString(KEY_PASSWORD, null)?.let { runCatching { decrypt(it) }.getOrNull() }

    fun hasCredentials(): Boolean = email() != null && password() != null

    fun clearPassword() {
        prefs.edit().remove(KEY_PASSWORD).apply()
    }

    private fun key(): SecretKey {
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (ks.getEntry(ALIAS, null) as? KeyStore.SecretKeyEntry)?.let { return it.secretKey }
        val gen = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        gen.init(
            KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build()
        )
        return gen.generateKey()
    }

    private fun encrypt(plain: String): String {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, key())
        val iv = cipher.iv
        val ct = cipher.doFinal(plain.toByteArray(Charsets.UTF_8))
        return Base64.encodeToString(iv, Base64.NO_WRAP) + ":" + Base64.encodeToString(ct, Base64.NO_WRAP)
    }

    private fun decrypt(stored: String): String {
        val (ivB64, ctB64) = stored.split(":", limit = 2)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, Base64.decode(ivB64, Base64.NO_WRAP)))
        return String(cipher.doFinal(Base64.decode(ctB64, Base64.NO_WRAP)), Charsets.UTF_8)
    }

    companion object {
        private const val ALIAS = "formeit_login_key"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val KEY_EMAIL = "email"
        private const val KEY_PASSWORD = "password_enc"
    }
}
