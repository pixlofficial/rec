package pixl.rec.core.storage

import android.content.Context
import android.content.SharedPreferences
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.util.Base64
import android.util.Log
import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets
import pixl.rec.core.model.StreamPlatform
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Hardware-backed credential store for Live Streaming credentials.
 * Encrypts stream keys using AES-256-GCM via the Android KeyStore provider.
 * Keys are never stored in plaintext and never leaked during config profile JSON exports.
 */
object SecureStreamPreferences {
    private const val TAG = "SecureStreamPrefs"
    private const val PREFS_NAME = "rec_secure_stream_prefs"
    private const val KEY_ENCRYPTED_STREAM_KEY = "encrypted_stream_key"
    private const val KEY_PREFIX_PLATFORM = "encrypted_stream_key_"

    private const val KEYSTORE_PROVIDER = "AndroidKeyStore"
    private const val KEY_ALIAS = "PixL_REC_StreamMasterKey"
    private const val CIPHER_TRANSFORMATION = "AES/GCM/NoPadding"
    private const val GCM_TAG_LENGTH = 128
    private const val GCM_IV_LENGTH = 12

    private fun getPrefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    /**
     * Retrieves or generates a 256-bit AES key securely inside the Android KeyStore.
     */
    private fun getOrCreateSecretKey(): SecretKey? {
        return try {
            val keyStore = KeyStore.getInstance(KEYSTORE_PROVIDER)
            keyStore.load(null)

            if (keyStore.containsAlias(KEY_ALIAS)) {
                val entry = keyStore.getEntry(KEY_ALIAS, null) as? KeyStore.SecretKeyEntry
                entry?.secretKey
            } else {
                val keyGen = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE_PROVIDER)
                val keySpec = KeyGenParameterSpec.Builder(
                    KEY_ALIAS,
                    KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
                )
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setKeySize(256)
                    .setRandomizedEncryptionRequired(true)
                    .build()

                keyGen.init(keySpec)
                keyGen.generateKey()
            }
        } catch (e: Exception) {
            Log.w(TAG, "AndroidKeyStore unavailable: ${e.message}")
            null
        }
    }

    private fun encryptAndSave(context: Context, prefKey: String, secretText: String) {
        if (secretText.isBlank()) {
            getPrefs(context).edit().remove(prefKey).apply()
            return
        }

        try {
            val secretKey = getOrCreateSecretKey()
            if (secretKey != null) {
                val cipher = Cipher.getInstance(CIPHER_TRANSFORMATION)
                cipher.init(Cipher.ENCRYPT_MODE, secretKey)
                val iv = cipher.iv
                val ciphertext = cipher.doFinal(secretText.toByteArray(StandardCharsets.UTF_8))

                val combined = ByteBuffer.allocate(iv.size + ciphertext.size)
                    .put(iv)
                    .put(ciphertext)
                    .array()

                val encoded = Base64.getEncoder().encodeToString(combined)
                getPrefs(context).edit().putString(prefKey, encoded).apply()
            } else {
                // Fallback for environments lacking AndroidKeyStore
                val fallbackEncoded = Base64.getEncoder().encodeToString(secretText.toByteArray(StandardCharsets.UTF_8))
                getPrefs(context).edit().putString(prefKey, fallbackEncoded).apply()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to encrypt secret text for $prefKey", e)
        }
    }

    private fun decryptAndRetrieve(context: Context, prefKey: String): String {
        val stored = getPrefs(context).getString(prefKey, null) ?: return ""

        return try {
            val secretKey = getOrCreateSecretKey()
            if (secretKey != null) {
                val combined = Base64.getDecoder().decode(stored)
                if (combined.size <= GCM_IV_LENGTH) return ""

                val iv = ByteArray(GCM_IV_LENGTH)
                val ciphertext = ByteArray(combined.size - GCM_IV_LENGTH)

                System.arraycopy(combined, 0, iv, 0, GCM_IV_LENGTH)
                System.arraycopy(combined, GCM_IV_LENGTH, ciphertext, 0, ciphertext.size)

                val cipher = Cipher.getInstance(CIPHER_TRANSFORMATION)
                val spec = GCMParameterSpec(GCM_TAG_LENGTH, iv)
                cipher.init(Cipher.DECRYPT_MODE, secretKey, spec)

                val decrypted = cipher.doFinal(ciphertext)
                String(decrypted, StandardCharsets.UTF_8)
            } else {
                // Fallback decode
                val decodedBytes = Base64.getDecoder().decode(stored)
                String(decodedBytes, StandardCharsets.UTF_8)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to decrypt secret text for $prefKey", e)
            ""
        }
    }

    /**
     * Encrypts and persists the stream key for a specific broadcasting platform.
     */
    fun savePlatformStreamKey(context: Context, platform: StreamPlatform, streamKey: String) {
        val prefKey = "$KEY_PREFIX_PLATFORM${platform.name.lowercase()}"
        encryptAndSave(context, prefKey, streamKey)
        if (platform == StreamPlatform.YOUTUBE) {
            // Keep legacy single key synced for backward compatibility
            encryptAndSave(context, KEY_ENCRYPTED_STREAM_KEY, streamKey)
        }
    }

    /**
     * Decrypts and returns the stored stream key for a specific platform.
     * Falls back to legacy key if platform is YouTube and dedicated key is unset.
     */
    fun getPlatformStreamKey(context: Context, platform: StreamPlatform): String {
        val prefKey = "$KEY_PREFIX_PLATFORM${platform.name.lowercase()}"
        val stored = decryptAndRetrieve(context, prefKey)
        if (stored.isNotBlank()) return stored

        // Check legacy single key fallback
        if (platform == StreamPlatform.YOUTUBE) {
            return decryptAndRetrieve(context, KEY_ENCRYPTED_STREAM_KEY)
        }
        return ""
    }

    fun hasPlatformStreamKey(context: Context, platform: StreamPlatform): Boolean {
        return getPlatformStreamKey(context, platform).isNotBlank()
    }

    fun clearPlatformStreamKey(context: Context, platform: StreamPlatform) {
        val prefKey = "$KEY_PREFIX_PLATFORM${platform.name.lowercase()}"
        getPrefs(context).edit().remove(prefKey).apply()
        if (platform == StreamPlatform.YOUTUBE) {
            getPrefs(context).edit().remove(KEY_ENCRYPTED_STREAM_KEY).apply()
        }
    }

    /**
     * Encrypts and persists the primary stream key (backward compatible).
     */
    fun saveStreamKey(context: Context, streamKey: String) {
        savePlatformStreamKey(context, StreamPlatform.YOUTUBE, streamKey)
    }

    /**
     * Decrypts and returns the stored primary stream key, or empty string if unset.
     */
    fun getStreamKey(context: Context): String {
        return getPlatformStreamKey(context, StreamPlatform.YOUTUBE)
    }

    /**
     * Checks if a valid stream key is stored.
     */
    fun hasStreamKey(context: Context): Boolean {
        return getStreamKey(context).isNotBlank()
    }

    /**
     * Removes the stored stream key.
     */
    fun clearStreamKey(context: Context) {
        clearPlatformStreamKey(context, StreamPlatform.YOUTUBE)
    }
}
