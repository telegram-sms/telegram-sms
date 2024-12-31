package com.qwe7002.telegram_sms.static_class

import android.util.Base64
import java.security.Key
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

object AES {
    private const val ALGORITHM = "AES"
    private const val TRANSFORMATION = "AES/GCM/NoPadding"
    private const val TAG_LENGTH_BIT = 128
    private const val IV_LENGTH_BYTE = 12

    fun generateKey(): Key {
        val keyGen = KeyGenerator.getInstance(ALGORITHM)
        keyGen.init(256)
        return keyGen.generateKey()
    }

    fun encrypt(data: String, key: Key): String {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        val iv = ByteArray(IV_LENGTH_BYTE)
        SecureRandom().nextBytes(iv)
        val gcmParameterSpec = GCMParameterSpec(TAG_LENGTH_BIT, iv)
        cipher.init(Cipher.ENCRYPT_MODE, key, gcmParameterSpec)
        val encryptedBytes = cipher.doFinal(data.toByteArray())
        val encryptedIvAndText = ByteArray(iv.size + encryptedBytes.size)
        System.arraycopy(iv, 0, encryptedIvAndText, 0, iv.size)
        System.arraycopy(encryptedBytes, 0, encryptedIvAndText, iv.size, encryptedBytes.size)
        return Base64.encodeToString(encryptedIvAndText, Base64.DEFAULT)
    }

    fun decrypt(encryptedData: String, key: Key): String {
        val encryptedIvAndText = Base64.decode(encryptedData, Base64.DEFAULT)
        val iv = ByteArray(IV_LENGTH_BYTE)
        System.arraycopy(encryptedIvAndText, 0, iv, 0, iv.size)
        val gcmParameterSpec = GCMParameterSpec(TAG_LENGTH_BIT, iv)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, key, gcmParameterSpec)
        val encryptedBytes = ByteArray(encryptedIvAndText.size - iv.size)
        System.arraycopy(encryptedIvAndText, iv.size, encryptedBytes, 0, encryptedBytes.size)
        val decryptedBytes = cipher.doFinal(encryptedBytes)
        return String(decryptedBytes)
    }

    fun getKeyFromString(keyString: String): Key {
        val decodedKey = Base64.decode(keyString, Base64.DEFAULT)
        return SecretKeySpec(decodedKey, 0, decodedKey.size, ALGORITHM)
    }

    fun keyToString(key: Key): String {
        return Base64.encodeToString(key.encoded, Base64.DEFAULT)
    }
}
