package metaticket.customer.user.route

import io.ktor.server.routing.*
import metaticket.customer.user.controller.UserCtrl
import metaticket.customer.user.model.SessionType

fun Routing.userRoutes(
    userCtrl: UserCtrl,
) {
    get("/public/customer-user/captcha-setting") {
        userCtrl.getCaptchaSetting(call)
    }
    get("/public/customer-user/email-verification-setting") {
        userCtrl.getEmailVerificationSetting(call)
    }
    post("/public/customer-user/register/init-session") {
        userCtrl.initSessionId(call, SessionType.REGISTERED_USER)
    }
    post("/public/customer-user/register/send-otp") {
        userCtrl.sendOTP(call, SessionType.REGISTERED_USER)
    }
    post("/public/customer-user/register/resend-otp") {
        userCtrl.resendOTP(call, SessionType.REGISTERED_USER)
    }
    post("/public/customer-user/register/verify-otp") {
        userCtrl.verifyOTP(call, SessionType.REGISTERED_USER)
    }
    post("/public/customer-user/register") {
        userCtrl.createUser(call)
    }

    post("/public/customer-user/reset-pass/init-session") {
        userCtrl.initSessionId(call, SessionType.RESET_PASSWORD)
    }
    post("/public/customer-user/reset-pass/send-otp") {
        userCtrl.sendOTP(call, SessionType.RESET_PASSWORD)
    }
    post("/public/customer-user/reset-pass/resend-otp") {
        userCtrl.resendOTP(call, SessionType.RESET_PASSWORD)
    }
    post("/public/customer-user/reset-pass/verify-otp") {
        userCtrl.verifyOTP(call, SessionType.RESET_PASSWORD)
    }
    post("/public/customer-user/reset-pass") {
        userCtrl.resetPasswordUser(call)
    }

    // Update Profile Routes (Authenticated)
    post("/customer-user/update-profile/init-session") {
        // Init session for update profile (change email)
        userCtrl.initSessionId(call, SessionType.UPDATE_PROFILE)
    }
    post("/customer-user/update-profile/send-otp") {
        userCtrl.sendOTP(call, SessionType.UPDATE_PROFILE)
    }
    post("/customer-user/update-profile/resend-otp") {
        userCtrl.resendOTP(call, SessionType.UPDATE_PROFILE)
    }
    post("/customer-user/update-profile/verify-otp") {
        userCtrl.verifyOTP(call, SessionType.UPDATE_PROFILE)
    }
    post("/customer-user/update-profile") {
        userCtrl.updateProfile(call)
    }

    get("/customer-user/profile") {
        userCtrl.getUserProfile(call)
    }
}
