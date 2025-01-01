
import android.util.Base64
import java.security.Key
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

object AES {
    private const val ALGORITHM = "AES"
    private const val TRANSFORMATION = "AES/GCM/NoPadding"
    private const val TAG_LENGTH_BIT = 128
    private const val IV_LENGTH_BYTE = 12
    private const val ITERATION_COUNT = 65536
    private const val KEY_LENGTH_BIT = 256
    

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
        if (encryptedIvAndText.size < IV_LENGTH_BYTE) {
            throw IllegalArgumentException("Invalid encrypted data")
        }
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
        val md = MessageDigest.getInstance("SHA-256")
        val salt = md.digest(keyString.toByteArray())
        val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        val spec = PBEKeySpec(keyString.toCharArray(), salt, ITERATION_COUNT, KEY_LENGTH_BIT)
        val tmp = factory.generateSecret(spec)
        val decodedKey = tmp.encoded
        return SecretKeySpec(decodedKey, ALGORITHM)
    }
}
