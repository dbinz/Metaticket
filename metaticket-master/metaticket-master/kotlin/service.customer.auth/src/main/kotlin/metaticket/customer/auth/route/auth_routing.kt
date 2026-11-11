package metaticket.customer.auth.route

import io.ktor.server.auth.*
import io.ktor.server.routing.*
import metaticket.customer.auth.controller.AuthCtrl

fun Route.authRoutes(
    authCtrl: AuthCtrl
) {
    // Protected route requiring JWT authentication
    authenticate("auth-jwt") {
        get("/internal/customer-auth/authenticate") {
            authCtrl.authenticate(call)
        }
    }

    get("/public/customer-auth/captcha-setting") {
        authCtrl.getCaptchaSetting(call)
    }

    // Login endpoint
    post("/public/customer-auth/login") {
        authCtrl.login(call)
    }

    // Refresh token endpoint
    post("/public/customer-auth/refresh-token") {
        authCtrl.refreshToken(call)
    }

    // Logout endpoint
    post("/public/customer-auth/logout") {
        authCtrl.logout(call)
    }
}