package metaticket.common.checksum

import java.math.BigInteger
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.security.NoSuchAlgorithmException
import java.util.*
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec


/**
 *
 * @author dongcatchay
 */
object Checksum {

    fun check(data: String, checksum: String, secretKey: String): Boolean {
        return generate(data, secretKey) == checksum
    }

    fun generate(data: String, secretKey: String): String {
        return toHexString(getSHA("$data$secretKey"))
    }

    fun encode(data: String): String {
        return Base64.getUrlEncoder().encode(data.toByteArray()).toString(Charsets.UTF_8)
    }

    @Throws(IllegalArgumentException::class)
    fun decode(entity: String): String {
        return Base64.getUrlDecoder().decode(entity).decodeToString()
    }

    fun encode(data: String, encryptionKey : String): String {
        val iv = IvParameterSpec(encryptionKey.toByteArray())
        val keySpec = SecretKeySpec(encryptionKey.toByteArray(), "AES")
        val cipher = Cipher.getInstance("AES/CBC/PKCS5PADDING")
        cipher.init(Cipher.ENCRYPT_MODE, keySpec, iv)
        val crypted = cipher.doFinal(data.toByteArray())
        val encodedByte = Base64.getUrlEncoder().encode(crypted)
        return String(encodedByte)
    }

    @Throws(IllegalArgumentException::class)
    fun decode(entity: String, encryptionKey: String): String {
        val decodedByte: ByteArray = Base64.getUrlDecoder().decode(entity)
        val iv = IvParameterSpec(encryptionKey.toByteArray())
        val keySpec = SecretKeySpec(encryptionKey.toByteArray(), "AES")
        val cipher = Cipher.getInstance("AES/CBC/PKCS5PADDING")
        cipher.init(Cipher.DECRYPT_MODE, keySpec, iv)
        val output = cipher.doFinal(decodedByte)
        return String(output)
    }

    @Throws(NoSuchAlgorithmException::class)
    private fun getSHA(input: String): ByteArray {
        // Static getInstance method is called with hashing SHA
        val md = MessageDigest.getInstance("SHA-256")

        // digest() method called
        // to calculate message digest of an input
        // and return array of byte
        return md.digest(input.toByteArray(StandardCharsets.UTF_8))
    }

    private fun toHexString(hash: ByteArray): String {
        // Convert byte array into signum representation
        val number = BigInteger(1, hash)

        // Convert message digest into hex value
        val hexString = StringBuilder(number.toString(16))

        // Pad with leading zeros
        while (hexString.length < 64) {
            hexString.insert(0, '0')
        }
        return hexString.toString().uppercase(Locale.getDefault())
    }
}
