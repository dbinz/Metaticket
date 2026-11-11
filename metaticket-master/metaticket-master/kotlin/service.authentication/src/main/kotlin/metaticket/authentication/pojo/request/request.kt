package metaticket.authentication.pojo.request

import com.fasterxml.jackson.annotation.JsonIgnoreProperties

@JsonIgnoreProperties(ignoreUnknown = true)
data class RegisterRequest(
    val email: String,
    val password: String,
)


@JsonIgnoreProperties(ignoreUnknown = true)
data class LoginRequest(
    val email: String,
    val password: String,
)

data class RefreshTokenRequest(
    val refreshToken: String
)