package com.catetai.app.data.local.security

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import dagger.hilt.android.qualifiers.ApplicationContext
import java.security.SecureRandom
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DatabaseKeyManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        private const val PREFS_FILE_NAME = "catet_secure_storage"
        private const val KEY_DB_PASSPHRASE = "catet_db_passphrase_bytes"
        private const val PASSPHRASE_LENGTH_BYTES = 32 // 256-bit AES key
    }

    private val masterKey: MasterKey by lazy {
        MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
    }

    private val encryptedPrefs by lazy {
        EncryptedSharedPreferences.create(
            context,
            PREFS_FILE_NAME,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    /**
     * Retrieves or generates a 256-bit passphrase for SQLCipher database encryption.
     * Guaranteed to persist securely across app restarts in Android Keystore.
     */
    fun getOrCreatePassphrase(): ByteArray {
        val existingHex = encryptedPrefs.getString(KEY_DB_PASSPHRASE, null)
        if (!existingHex.isNullOrEmpty()) {
            return hexToBytes(existingHex)
        }

        // Generate a new cryptographically secure random 256-bit key
        val randomBytes = ByteArray(PASSPHRASE_LENGTH_BYTES)
        SecureRandom().nextBytes(randomBytes)
        val hexString = bytesToHex(randomBytes)

        encryptedPrefs.edit()
            .putString(KEY_DB_PASSPHRASE, hexString)
            .apply()

        return randomBytes
    }

    private fun bytesToHex(bytes: ByteArray): String {
        return bytes.joinToString("") { "%02x".format(it) }
    }

    private fun hexToBytes(hex: String): ByteArray {
        val len = hex.length
        val data = ByteArray(len / 2)
        for (i in 0 until len step 2) {
            data[i / 2] = ((Character.digit(hex[i], 16) shl 4) +
                    Character.digit(hex[i + 1], 16)).toByte()
        }
        return data
    }
}
