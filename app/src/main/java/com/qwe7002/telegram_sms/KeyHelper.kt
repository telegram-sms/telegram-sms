package com.qwe7002.telegram_sms

import android.content.Context
import android.content.SharedPreferences
import java.security.MessageDigest
import java.security.SecureRandom

// Modified at 11:30 AM UTC, October 26, 2023 - Helper for key hashing and SharedPreferences
object KeyHelper {

    private const val PREFS_NAME = "admin_prefs"
    private const val KEY_HASH = "key_hash"
    private const val KEY_SALT = "key_salt"
    private const val KEY_ADMIN_DISABLE_AUTHORIZED = "admin_disable_authorized"

    private fun getPrefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    fun saveKeyHash(context: Context, secretKey: String) {
        val salt = ByteArray(16)
        SecureRandom().nextBytes(salt)
        val hash = hashKey(secretKey, salt)
        getPrefs(context).edit()
            .putString(KEY_HASH, bytesToHex(hash))
            .putString(KEY_SALT, bytesToHex(salt))
            .apply()
    }

    fun verifyKey(context: Context, secretKey: String): Boolean {
        val prefs = getPrefs(context)
        val storedHashHex = prefs.getString(KEY_HASH, null)
        val storedSaltHex = prefs.getString(KEY_SALT, null)

        if (storedHashHex == null || storedSaltHex == null) {
            return false // No key set
        }
        val salt = hexToBytes(storedSaltHex)
        val comparisonHash = hashKey(secretKey, salt)
        return bytesToHex(comparisonHash) == storedHashHex
    }

    private fun hashKey(key: String, salt: ByteArray): ByteArray {
        val digest = MessageDigest.getInstance("SHA-256")
        digest.update(salt)
        return digest.digest(key.toByteArray(Charsets.UTF_8))
    }

    fun isKeySet(context: Context): Boolean {
        return getPrefs(context).getString(KEY_HASH, null) != null
    }

    fun setAdminDisableAuthorized(context: Context, authorized: Boolean) {
        getPrefs(context).edit().putBoolean(KEY_ADMIN_DISABLE_AUTHORIZED, authorized).apply()
    }

    fun isAdminDisableAuthorized(context: Context): Boolean {
        return getPrefs(context).getBoolean(KEY_ADMIN_DISABLE_AUTHORIZED, false)
    }

    private fun bytesToHex(bytes: ByteArray): String {
        val hexChars = CharArray(bytes.size * 2)
        for (j in bytes.indices) {
            val v = bytes[j].toInt() and 0xFF
            hexChars[j * 2] = "0123456789abcdef"[v ushr 4]
            hexChars[j * 2 + 1] = "0123456789abcdef"[v and 0x0F]
        }
        return String(hexChars)
    }

    private fun hexToBytes(hex: String): ByteArray {
        val len = hex.length
        val data = ByteArray(len / 2)
        var i = 0
        while (i < len) {
            data[i / 2] = ((Character.digit(hex[i], 16) shl 4) + Character.digit(hex[i + 1], 16)).toByte()
            i += 2
        }
        return data
    }
}
