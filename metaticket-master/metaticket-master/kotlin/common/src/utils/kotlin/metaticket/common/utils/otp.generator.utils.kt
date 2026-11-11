package metaticket.common.utils

import java.security.SecureRandom

/**
 * A utility class for generating One-Time Passwords (OTPs).
 *
 * This class provides methods to generate cryptographically secure OTPs.
 *
 * @author dongcatchay
 */
object OTPGenerator {
    private val random = SecureRandom()

    /**
     * Generates a new OTP.
     *
     * @param length The length of the OTP (default is 6).
     * @return An OTP object containing the generated OTP code and its expiry time.
     */
    fun generateOTP(length: Int = 6): String {
        val otp = StringBuilder(length)
        for (i in 0 until length) {
            otp.append(random.nextInt(10))
        }
        return otp.toString()
    }
}