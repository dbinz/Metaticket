package metaticket.notification.gateway

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.client.statement.bodyAsText
import io.ktor.http.*
import io.ktor.server.application.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import metaticket.common.logger.Logging
import metaticket.plugin.zns.model.ZaloMessageMetadata

class ZaloNotificationServiceGateway(
    private val env: ApplicationEnvironment,
    private val httpClient: HttpClient,
): Logging {
    private val sendMessageUrl = env.config.property("zns.sendMessageUrl").getString()
    private val appId = env.config.property("zns.appId").getString()
    private val secretKey = env.config.property("zns.secretKey").getString()

    suspend fun sendMessage(metadata: ZaloMessageMetadata, data: Map<String, String>, smsMsg: String): ZNSMessageResponse {
        val resp = withContext(Dispatchers.IO) {
            httpClient.post {
                url(sendMessageUrl)
                header(HttpHeaders.ContentType, "application/json")
                header("app-id", appId)
                header("secret-key", secretKey)
                setBody(
                    mapOf(
                        "phone" to metadata.phone,
                        "template_id" to metadata.template_id,
                        "ref_id" to metadata.ref_id,
                        "template_data" to data,
                        "failover" to metadata.failover,
                        "sms_msg" to smsMsg,
                    )
                )
            }
        }
        logger.debug("request {} message response {}", metadata, resp.bodyAsText())
        return resp.body()
    }
}