package metaticket.customer.user.request

data class SendOTPRequest(val phone: String? = null, val email: String? = null)
data class VerifyOTPRequest(val phone: String? = null, val otp: String, val email: String? = null)
data class CreateUserRequest(
    val phone: String,
    val password: String,
    val otp: String,
    val name: String,
    val email: String,
    val avatar: String?
)

data class ResetPasswordRequest constructor(
    val phone: String,
    val password: String,
    val otp: String,
)

// Xác minh Captcha ngay tại bước khởi tạo session
data class InitSessionRequest(val captchaToken: String?)

data class UpdateProfileRequest(val name: String, val email: String, val otp: String?)
