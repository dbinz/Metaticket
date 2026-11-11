package metaticket.authentication.utils

import java.io.IOException
import java.security.*
import java.security.spec.PKCS8EncodedKeySpec
import java.security.spec.X509EncodedKeySpec
import java.util.*
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

class RSAEncryption {

    companion object {
        private const val RSA_ALGORITHM = "RSA"
        private const val CIPHER_TYPE_FOR_RSA = "RSA/ECB/PKCS1Padding"
        private val keyFactory = KeyFactory.getInstance(RSA_ALGORITHM)
        private val cipher = Cipher.getInstance(CIPHER_TYPE_FOR_RSA)

        fun getPublicKeyFromString(publicKeyString: String): PublicKey? =
                try {
                    val keySpec = X509EncodedKeySpec(Base64.getDecoder().decode(publicKeyString.toByteArray()))
                    keyFactory.generatePublic(keySpec)
                } catch (exception: Exception) {
                    exception.printStackTrace()
                    null
                }

        fun getPrivateKeyFromString(privateKeyString: String): PrivateKey? =
                try {
                    val keySpec =
                            PKCS8EncodedKeySpec(Base64.getDecoder().decode(privateKeyString.toByteArray()))
                    keyFactory.generatePrivate(keySpec)
                } catch (exception: Exception) {
                    exception.printStackTrace()
                    null
                }


        fun encrypt(publicKey: PublicKey, strToEncrypt: String): String? {
            return try {
                cipher.init(Cipher.ENCRYPT_MODE, publicKey)
                Base64.getEncoder().encodeToString(cipher.doFinal(strToEncrypt.toByteArray()))
            } catch (exception: Exception) {
                exception.printStackTrace()
                null
            }
        }

        fun decrypt(privateKey: PrivateKey, strToDecrypt : String): String? {
            return try {
                cipher.init(Cipher.DECRYPT_MODE, privateKey)
                String(cipher.doFinal(Base64.getDecoder().decode(strToDecrypt)))
            } catch (exception: Exception) {
                exception.printStackTrace()
                null
            }
        }
    }



}