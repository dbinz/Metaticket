package metaticket.ticketslots.gateway

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.forms.submitForm
import io.ktor.http.Parameters
import io.ktor.server.config.ApplicationConfig
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.jsonPrimitive
import metaticket.common.logger.Logging

class CaptchaGateway constructor(private val config: ApplicationConfig, private val httpClient: HttpClient) : Logging {
    private val captchaGateway = config.property("captcha.captchaGateway").getString()
    private val secretKey = config.property("captcha.${captchaGateway}.secretKey").getString()
    private val url = config.property("captcha.${captchaGateway}.url").getString()

    suspend fun isCaptchaSuccess(token: String): Boolean {
        return try {
            val response: JsonObject = httpClient.submitForm(
                url = url,
                formParameters = Parameters.build {
                    append("secret", secretKey)
                    append("response", token)
                }
            ).body()

            response["success"]?.jsonPrimitive?.boolean ?: false
        } catch (e: Throwable) {
            logger.error("Error verifying reCAPTCHA: ", e)
            false
        }
    }
}