package metaticket.authentication.utils

import org.mindrot.jbcrypt.BCrypt

class BcryptUtils {
    companion object {
        fun hashPassword(password: String, salt: String): String {
            return BCrypt.hashpw(password, salt)
        }

        fun verifyPassword(plainText: String, hashed: String): Boolean {
            return BCrypt.checkpw(plainText, hashed)
        }
    }
}