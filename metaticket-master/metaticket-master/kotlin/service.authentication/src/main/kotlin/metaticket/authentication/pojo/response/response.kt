package metaticket.authentication.pojo.response

data class LoginResponse constructor(
    val token: String,
    val refreshToken: String
)

data class OperatorAccountResponse constructor(
    val userId: String,
    val email: String,
)

data class RefreshTokenResponse constructor(
    val token: String,
    val refreshToken: String
)