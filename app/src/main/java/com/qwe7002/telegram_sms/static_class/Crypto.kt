package com.qwe7002.telegram_sms.static_class

import android.util.Base64
import com.goterl.lazysodium.LazySodiumAndroid
import com.goterl.lazysodium.SodiumAndroid
import com.goterl.lazysodium.interfaces.SecretBox
import java.security.MessageDigest
import java.security.SecureRandom

object Crypto {
    private val lazySodium = LazySodiumAndroid(SodiumAndroid())
    private const val NONCE_LENGTH = SecretBox.NONCEBYTES // 24 bytes
    private const val KEY_LENGTH = SecretBox.KEYBYTES // 32 bytes

    fun encrypt(data: String, key: ByteArray): String {
        val nonce = ByteArray(NONCE_LENGTH)
        SecureRandom().nextBytes(nonce)

        val dataBytes = data.toByteArray(Charsets.UTF_8)
        val cipherText = ByteArray(dataBytes.size + SecretBox.MACBYTES)

        lazySodium.cryptoSecretBoxEasy(cipherText, dataBytes, dataBytes.size.toLong(), nonce, key)

        // 合併 nonce 和密文
        val result = ByteArray(nonce.size + cipherText.size)
        System.arraycopy(nonce, 0, result, 0, nonce.size)
        System.arraycopy(cipherText, 0, result, nonce.size, cipherText.size)

        return Base64.encodeToString(result, Base64.DEFAULT)
    }

    fun decrypt(encryptedData: String, key: ByteArray): String {
        val combined = Base64.decode(encryptedData, Base64.DEFAULT)

        if (combined.size < NONCE_LENGTH + SecretBox.MACBYTES) {
            throw IllegalArgumentException("Invalid encrypted data")
        }

        val nonce = ByteArray(NONCE_LENGTH)
        System.arraycopy(combined, 0, nonce, 0, NONCE_LENGTH)

        val cipherText = ByteArray(combined.size - NONCE_LENGTH)
        System.arraycopy(combined, NONCE_LENGTH, cipherText, 0, cipherText.size)

        val plainText = ByteArray(cipherText.size - SecretBox.MACBYTES)

        val success = lazySodium.cryptoSecretBoxOpenEasy(plainText, cipherText, cipherText.size.toLong(), nonce, key)

        if (!success) {
            throw IllegalArgumentException("Decryption failed")
        }

        return String(plainText, Charsets.UTF_8)
    }

    fun getKeyFromString(keyString: String): ByteArray {
        // 使用 SHA-256 哈希並擴展到 32 bytes 作為密鑰
        val sha256 = MessageDigest.getInstance("SHA-256")

        // 結合 keyString 和 SnowFlake 種子值來增加熵
        val snowFlakeSeed = SnowFlake.generate()
        val combinedInput = keyString + snowFlakeSeed.toString()

        // 第一次哈希
        val firstHash = sha256.digest(keyString.toByteArray(Charsets.UTF_8))

        // 使用 HKDF 風格的密鑰派生
        sha256.reset()
        sha256.update(firstHash)
        sha256.update(keyString.toByteArray(Charsets.UTF_8))

        val key = sha256.digest()

        // 確保密鑰長度為 32 bytes
        return if (key.size >= KEY_LENGTH) {
            key.copyOf(KEY_LENGTH)
        } else {
            val extendedKey = ByteArray(KEY_LENGTH)
            System.arraycopy(key, 0, extendedKey, 0, key.size)
            extendedKey
        }
    }
}
