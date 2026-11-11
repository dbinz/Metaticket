package metaticket.customer.auth.pojo

data class LoginRequest(val phone: String, val password: String, val captchaToken: String? = null)

data class LoginResponse constructor(
    val message: String? = null,
    val token: String? = null
)

data class VoteRequest(val optionIds: List<String>)