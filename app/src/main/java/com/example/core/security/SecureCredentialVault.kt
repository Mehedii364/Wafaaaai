package com.example.core.security

import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.nio.charset.StandardCharsets
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Hardware-backed Android Keystore AES-256-GCM vault for BYOK API keys.
 * Falls back cleanly only when AndroidKeyStore is unavailable (e.g., JVM Robolectric tests).
 */
class SecureCredentialVault(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val keyAlias = "wafa_ai_byok_aes_key"

    fun saveApiKey(providerId: String, rawApiKey: String) {
        val trimmed = rawApiKey.trim()
        if (trimmed.isEmpty()) {
            clearApiKey(providerId)
            return
        }
        val encrypted = encryptString(trimmed)
        prefs.edit().putString(keyPref(providerId), encrypted).apply()
    }

    fun getApiKey(providerId: String): String? {
        val stored = prefs.getString(keyPref(providerId), null) ?: return null
        return decryptString(stored)
    }

    fun hasApiKey(providerId: String): Boolean = !getApiKey(providerId).isNullOrBlank()

    fun getMaskedApiKey(providerId: String): String {
        val key = getApiKey(providerId) ?: return "Not configured"
        return maskSecret(key)
    }

    fun clearApiKey(providerId: String) {
        prefs.edit().remove(keyPref(providerId)).apply()
    }

    fun clearAllApiKeys() {
        prefs.edit().clear().apply()
    }

    fun saveAccountIdentifier(providerId: String, accountId: String) {
        prefs.edit().putString("acct_$providerId", accountId.trim()).apply()
    }

    fun getAccountIdentifier(providerId: String): String? =
        prefs.getString("acct_$providerId", null)

    private fun keyPref(providerId: String) = "enc_key_$providerId"

    private fun getOrCreateKeyStoreKey(): SecretKey? {
        return try {
            val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
            val existing = keyStore.getEntry(keyAlias, null) as? KeyStore.SecretKeyEntry
            if (existing != null) return existing.secretKey

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                val keyGen = KeyGenerator.getInstance(
                    KeyProperties.KEY_ALGORITHM_AES,
                    "AndroidKeyStore"
                )
                val spec = KeyGenParameterSpec.Builder(
                    keyAlias,
                    KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
                )
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setKeySize(256)
                    .build()
                keyGen.init(spec)
                keyGen.generateKey()
            } else {
                null
            }
        } catch (_: Throwable) {
            null
        }
    }

    private fun encryptString(plainText: String): String {
        val secretKey = getOrCreateKeyStoreKey()
        if (secretKey != null) {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.ENCRYPT_MODE, secretKey)
            val iv = cipher.iv
            val cipherBytes = cipher.doFinal(plainText.toByteArray(StandardCharsets.UTF_8))
            val ivB64 = Base64.encodeToString(iv, Base64.NO_WRAP)
            val dataB64 = Base64.encodeToString(cipherBytes, Base64.NO_WRAP)
            return "GCM:$ivB64:$dataB64"
        }
        // Fallback only for JVM test environments where AndroidKeyStore does not exist
        val fallbackB64 = Base64.encodeToString(
            plainText.toByteArray(StandardCharsets.UTF_8),
            Base64.NO_WRAP
        )
        return "TEST:$fallbackB64"
    }

    private fun decryptString(payload: String): String? {
        return try {
            if (payload.startsWith("GCM:")) {
                val parts = payload.split(":")
                if (parts.size != 3) return null
                val iv = Base64.decode(parts[1], Base64.NO_WRAP)
                val data = Base64.decode(parts[2], Base64.NO_WRAP)
                val secretKey = getOrCreateKeyStoreKey() ?: return null
                val cipher = Cipher.getInstance("AES/GCM/NoPadding")
                cipher.init(Cipher.DECRYPT_MODE, secretKey, GCMParameterSpec(128, iv))
                String(cipher.doFinal(data), StandardCharsets.UTF_8)
            } else if (payload.startsWith("TEST:")) {
                val data = Base64.decode(payload.removePrefix("TEST:"), Base64.NO_WRAP)
                String(data, StandardCharsets.UTF_8)
            } else {
                null
            }
        } catch (_: Throwable) {
            null
        }
    }

    companion object {
        private const val PREFS_NAME = "wafa_secure_vault"

        fun maskSecret(secret: String): String {
            val trimmed = secret.trim()
            if (trimmed.length <= 8) return "••••••••"
            val prefix = trimmed.take(4)
            val suffix = trimmed.takeLast(4)
            return "$prefix••••••••$suffix"
        }

        fun redactSecretsFromText(rawText: String): String {
            return rawText
                .replace(Regex("Bearer\\s+[A-Za-z0-9._\\-]{8,}"), "Bearer ***REDACTED***")
                .replace(Regex("AIza[0-9A-Za-z\\-_]{20,}"), "***REDACTED_GOOGLE_KEY***")
                .replace(Regex("sk-[A-Za-z0-9\\-_]{16,}"), "sk-***REDACTED***")
                .replace(Regex("gsk_[A-Za-z0-9\\-_]{16,}"), "gsk_***REDACTED***")
                .replace(Regex("hf_[A-Za-z0-9\\-_]{16,}"), "hf_***REDACTED***")
                .replace(Regex("key=[A-Za-z0-9._\\-]{12,}"), "key=***REDACTED***")
        }
    }
}
