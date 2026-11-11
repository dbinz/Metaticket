package metaticket.customer.user.model

enum class SessionType { REGISTERED_USER, RESET_PASSWORD, UPDATE_PROFILE }

enum class SessionStatus { CREATED, OTP_SENT, OTP_VERIFY_FAILED, OTP_VERIFIED, COMPLETED }

enum class OtpTarget { PHONE, EMAIL }

data class SessionData(
    val status: SessionStatus,
    val phone: String? = null,
    val email: String? = null,
    val currentTarget: OtpTarget? = null,
    val otp: String? = null,
    val failedCount: Int = 0,
    val expiredTime: Long = 0,
    val isPhoneVerified: Boolean = false,
    val isEmailVerified: Boolean = false
)

data class SessionConfig(
    val expiredTimeInS: Long,
    val maxOtpAttempts: Int
)