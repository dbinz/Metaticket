package metaticket.authentication

import io.ktor.server.auth.*
import io.ktor.server.routing.*
import metaticket.authentication.controller.AuthenticationController

fun Route.documentRoutes(c : AuthenticationController) {
    get("/internal/authentication/check-login") {
        c.checkLogin(call)
    }
    
    post("/public/authentication/refresh-token") {
        c.refreshToken(call)
    }

    post("/public/authentication/login") {
        c.login(call)
    }

    get("/public/authentication/logout") {
        c.logout(call)
    }

    // Public self-registration removed: operator accounts are full admins, so only a logged-in
    // operator may create one (nginx private frag enforces auth_request on /private/).
    post("/private/authentication/register") {
        c.createOperatorAccount(call)
    }
}
