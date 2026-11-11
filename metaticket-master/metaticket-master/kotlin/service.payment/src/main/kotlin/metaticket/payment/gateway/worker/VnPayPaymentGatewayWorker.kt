package metaticket.payment.gateway.worker

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import io.ktor.util.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import metaticket.common.logger.Logging
import metaticket.payment.VnPayPaymentGateway
import metaticket.payment.VnPayPaymentGateway.createVnPayPaymentRedirectUrl
import metaticket.payment.VnPayPaymentGateway.hashAllFields
import metaticket.payment.configuration.PaymentConfig
import metaticket.payment.datastructure.PaymentType
import metaticket.payment.gateway.worker.VnPayConstants.Companion.vnpayDateFormat
import metaticket.payment.pojo.db.PGVnPayAccount
import metaticket.payment.pojo.db.VNPayPaymentDetail
import metaticket.payment.utils.buildUrlWithChecksum
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.util.*
import kotlin.collections.component1
import kotlin.collections.component2
import kotlin.collections.set


class VnPayPaymentGatewayWorker constructor(
    private val paymentConfig: PaymentConfig,
): Logging {

    private fun createVnPayRedirectUrl(
        vnPayConfig: PGVnPayAccount, paymentId: String, amount: Long, ipAddress: String, bankCode: String,
        orderInfo: String, vnpCreateDate: String, vnpExpireDate: String
    ): String {
        return createVnPayPaymentRedirectUrl(
            vnPayConfig.tmnCode,
            buildUrlWithChecksum(vnPayConfig.returnUrl, paymentId, paymentConfig.hashSecret),
            vnPayConfig.hashSecret,
            vnPayConfig.payUrl,
            paymentId,
            amount.toString(),
            ipAddress,
            vnpCreateDate,
            vnpExpireDate,
            bankCode,
            orderInfo
        )
    }

    fun createPaymentDetails(
        vnPayConfig: PGVnPayAccount, paymentId: String, amount: Long, ipAddress: String,
        paymentType: PaymentType, orderInfo: String
    ): VNPayPaymentDetail? {
        val cld = Calendar.getInstance(TimeZone.getTimeZone("Etc/GMT+7"))
        val vnpCreateDate = vnpayDateFormat.format(cld.time)
        cld.add(Calendar.MINUTE, vnPayConfig.expireInMinute)
        val vnpExpireDate = vnpayDateFormat.format(cld.time)

        val bankCode = when (paymentType) {
            PaymentType.InternationalCard -> "INTCARD"
            PaymentType.DomesticCard -> "VNBANK"
            PaymentType.QrCode -> "VNPAYQR"
            else -> return null
        }

        val redirectUrl = createVnPayRedirectUrl(vnPayConfig, paymentId, amount, ipAddress, bankCode, orderInfo, vnpCreateDate, vnpExpireDate)

        return VNPayPaymentDetail(
            redirectUrl = redirectUrl,
            returnUrl = buildUrlWithChecksum(vnPayConfig.returnUrl, paymentId, paymentConfig.hashSecret),
            vnpCreateDate = vnpCreateDate
        )
    }

    fun validateHashSecure(hashSecret: String, params: StringValues): Boolean {

        val fields = mutableMapOf<String, String>()
        for ((key, value) in params.toMap()) {
            val fieldName = URLEncoder.encode(key, StandardCharsets.US_ASCII.toString())
            val fieldValue = if(value.isNotEmpty()) {
                URLEncoder.encode(value[0], StandardCharsets.US_ASCII.toString())
            } else {
                ""
            }
            fields[fieldName] = fieldValue
        }
        val vnpSecureHash: String = params["vnp_SecureHash"] ?: ""
        if (fields.containsKey("vnp_SecureHashType")) {
            fields.remove("vnp_SecureHashType")
        }
        if (fields.containsKey("vnp_SecureHash")) {
            fields.remove("vnp_SecureHash")
        }

        val signValue = hashAllFields(hashSecret, fields)
        return signValue.equals(vnpSecureHash)
    }

    suspend fun callQueryStateInquiry(
        vnPayConfig: PGVnPayAccount, ipAddress: String, paymentId: String, transDate: String
    ): String {
        return withContext(Dispatchers.IO) {
            logger.debug("payment {} call VnPay gateway request", paymentId)
            val result = VnPayPaymentGateway.queryVNPayPayment(
                    vnPayConfig.tmnCode,
                    vnPayConfig.queryUrl,
                    vnPayConfig.hashSecret,
                    paymentId,
                    ipAddress,
                    transDate
            )

            logger.debug("payment {} call VnPay gateway response {}", paymentId, result)

            result
        }
    }
}
