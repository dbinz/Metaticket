package metaticket.customer.user.utils

import java.security.MessageDigest

@OptIn(ExperimentalStdlibApi::class)
fun hashPassword(password: String, salt: String): String {
    val saltedPassword = password + salt
    val digest = MessageDigest.getInstance("SHA-256")
    val hash: ByteArray = digest.digest(saltedPassword.toByteArray())
    return hash.toHexString()
}