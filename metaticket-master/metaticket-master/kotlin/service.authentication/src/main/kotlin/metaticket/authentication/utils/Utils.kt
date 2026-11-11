package metaticket.authentication.utils

import java.util.*
import kotlin.random.Random

object Utils {
    private fun getRandomString(length: Int) : String {
        val charset = "ABCDEFGHIJKLMNOPQRSTUVWXTZabcdefghiklmnopqrstuvwxyz0123456789"
        return (1..length)
                .map { charset.random() }
                .joinToString("")
    }

    fun buildStringHashToEncrypt(userId: String): String {
        return userId + "|" + Date().time + "|" + getRandomString(4)


    }
}