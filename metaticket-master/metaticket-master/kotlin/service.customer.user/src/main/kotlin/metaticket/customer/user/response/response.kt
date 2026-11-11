package metaticket.customer.user.response

data class CaptchaSettingResponse constructor(
    val enable: Boolean,
    val captchaGateway: String, // hcaptcha | recaptcha
    val siteKey: String,
)

data class EmailVerificationSettingResponse constructor(
    val enable: Boolean,
)