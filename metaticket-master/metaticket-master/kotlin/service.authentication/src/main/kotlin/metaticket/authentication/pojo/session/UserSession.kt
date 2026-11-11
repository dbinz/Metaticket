package metaticket.authentication.pojo.session

import io.ktor.server.auth.*

data class UserSession(val email: String, val token: String) : Principal
