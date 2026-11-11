package metaticket.customer.user.gateway

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.forms.*
import io.ktor.http.*
import io.ktor.server.config.*
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.jsonPrimitive
import metaticket.common.logger.Logging

class CaptchaGateway(private val config: ApplicationConfig, private val httpClient: HttpClient) : Logging {
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
            logger.error("Error verifying CAPTCHA: ", e)
            false
        }
    }
}