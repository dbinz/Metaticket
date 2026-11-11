package metaticket.payment.gateway.worker

import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import metaticket.common.logger.Logging
import metaticket.payment.NinePayPaymentGateway.*
import metaticket.payment.configuration.PaymentConfig
import metaticket.payment.datastructure.PaymentType
import metaticket.payment.pojo.db.NinePayPaymentDetail
import metaticket.payment.pojo.db.PGNinePayAccount
import metaticket.payment.utils.buildUrlWithChecksum
import java.net.URL

class NinePayPaymentGatewayWorker constructor(
    private val paymentConfig: PaymentConfig,
    private val client: HttpClient,
): Logging {

    fun createPaymentDetail(
        ninePayConfig: PGNinePayAccount, paymentId: String, amount: Long, paymentType: PaymentType,
        description: String
    ): NinePayPaymentDetail? {
        val method = when (paymentType) {
            PaymentType.InternationalCard -> "CREDIT_CARD"
            PaymentType.DomesticCard -> "ATM_CARD"
            PaymentType.QrCode -> "BANK_TRANSFER"
            else -> return null
        }

        val redirectUrl = createRedirectUrl(ninePayConfig, paymentId, amount, method, description)

        return NinePayPaymentDetail(
            buildUrlWithChecksum(ninePayConfig.returnUrl, paymentId, paymentConfig.hashSecret),
            redirectUrl,
        )
    }

    private fun createRedirectUrl(
        ninePayConfig: PGNinePayAccount, paymentId: String, amount: Long, method: String, description: String
    ): String {

        return createNinePayPaymentRedirectUrl(
            ninePayConfig.merchantKey,
            ninePayConfig.merchantSecretKey,
            ninePayConfig.baseUrl,
            paymentId,
            amount.toString(),
            description,
            method,
            buildUrlWithChecksum(ninePayConfig.returnUrl, paymentId, paymentConfig.hashSecret),
        )
    }

    fun getPaymentInfoFromResult(
        result: String, checksum: String, checksumKey: String
    ): String? {
        return decodePaymentResult(result, checksum, checksumKey) ?: return null
    }

    suspend fun callQueryStateInquiry(
        ninePayConfig: PGNinePayAccount, paymentId: String
    ): String {
        val time = (System.currentTimeMillis() / 1000).toString()
        val message = buildInquiryMessage(ninePayConfig.baseUrl, time, paymentId)
        val signature = signature(message, ninePayConfig.merchantSecretKey)
        return withContext(Dispatchers.IO) {
            val url = URL(ninePayConfig.baseUrl + "/v2/payments/" + paymentId + "/inquire")
            logger.debug("payment {} call 9Pay gateway request {}", paymentId, url)
            val response = client.get(url) {
                headers {
                    append(HttpHeaders.Date, time)
                    append(HttpHeaders.Authorization, buildAuthorization("HS256", ninePayConfig.merchantKey, "", signature))
                }
            }

            val result = response.bodyAsText()

            logger.debug("payment {} call 9Pay gateway response {}", paymentId, result)

            return@withContext result
        }
    }
    private fun buildAuthorization(
        algorithm: String, merchantKey: String, signedHeaders: String, signature: String?
    ): String {
        return "Signature Algorithm=$algorithm,Credential=$merchantKey,SignedHeaders=$signedHeaders,Signature=$signature"
    }

}
