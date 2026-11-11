package metaticket.payment.gateway.worker

import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.client.request.forms.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import metaticket.common.logger.Logging
import metaticket.payment.OnePayUtil
import metaticket.payment.configuration.PaymentConfig
import metaticket.payment.datastructure.PaymentType
import metaticket.payment.pojo.db.OnePayPaymentDetail
import metaticket.payment.pojo.db.PGOnePayAccount
import metaticket.payment.utils.buildUrlWithChecksum
import java.net.URLDecoder
import java.net.URLEncoder
import java.util.*
import kotlin.collections.component1
import kotlin.collections.component2
import kotlin.collections.set


class OnePayPaymentGatewayWorker constructor(
    private val paymentConfig: PaymentConfig,
    private val httpClient: HttpClient,
): Logging {

    private fun createInvoice(
        merchantId: String,
        merchantAccessCode: String,
        merchantHashCode: String,
        paymentId: String,
        paymentMethod: String,
        returnURL: String,
        orderInfo: String,
        amount: Long,
        ipAddress: String,
        referenceUrl: String,
        baseUrl: String,
        title: String? = null,
        customerPhone: String? = null,
        customerEmail: String? = null,
        customerId: String? = null,
    ): String {
        val param: MutableMap<String, String> = HashMap()
        param["vpc_Version"] = "2"
        param["vpc_Currency"] = "VND"
        param["vpc_Command"] = "pay"
        param["vpc_AccessCode"] = merchantAccessCode
        param["vpc_Merchant"] = merchantId
        param["vpc_Locale"] = "vn"
        param["vpc_ReturnURL"] = returnURL
        param["vpc_MerchTxnRef"] = paymentId
        param["vpc_OrderInfo"] = orderInfo
        param["vpc_Amount"] = "${amount}00"
        param["vpc_TicketNo"] = ipAddress
        param["vpc_CardList"] = paymentMethod
        param["AgainLink"] = referenceUrl
        param["Title"] = title ?: orderInfo
        customerPhone?.let { param["vpc_Customer_Phone"] = it }
        customerEmail?.let { param["vpc_Customer_Email"] = it }
        customerId?.let { param["vpc_Customer_Id"] = it }

        val queryParamSorted: TreeMap<String, String> = OnePayUtil.sortMap(param)
        val stringToHash: String = OnePayUtil.generateStringToHash(queryParamSorted)

        logger.debug("merchant's string to hash{}", stringToHash)

        val merchantSecureHash: String = OnePayUtil.generateSecureHash(stringToHash, merchantHashCode)
        param["vpc_SecureHash"] = merchantSecureHash

        var requestParam = ""
        for ((key, value1) in param) {
            val value = value1.toString()
            requestParam += key + "=" + URLEncoder.encode(value, "UTF-8") + "&"
        }
        val requestUrl: String = "$baseUrl/paygate/vpcpay.op?$requestParam"

        return requestUrl
    }

    fun createPaymentDetails(
        onepayConfig: PGOnePayAccount, paymentId: String, amount: Long, ipAddress: String,
        paymentType: PaymentType, orderInfo: String, referenceUrl: String,
    ): OnePayPaymentDetail? {
        val bankCode = when (paymentType) {
            PaymentType.InternationalCard -> "INTERNATIONAL"
            PaymentType.DomesticCard -> "DOMESTIC"
            PaymentType.QrCode -> "QR"
            PaymentType.VietQr -> "VIETQR"
            //PaymentType.BNPL -> "BNPL"
            else -> return null
        }

        val returnURL = buildUrlWithChecksum(onepayConfig.returnUrl, paymentId, paymentConfig.hashSecret)
        logger.info(
            "[pay-flow] onepay createPaymentDetails payment {} configuredReturnUrl='{}' -> vpc_ReturnURL='{}' (OnePay will redirect the browser here after pay/cancel) referenceUrl(AgainLink)='{}'",
            paymentId, onepayConfig.returnUrl, returnURL, referenceUrl
        )

        val redirectUrl = createInvoice(
            merchantId = onepayConfig.merchantId,
            merchantAccessCode = onepayConfig.merchantAccessCode,
            merchantHashCode = onepayConfig.merchantHashCode,
            paymentId = paymentId,
            amount = amount,
            paymentMethod = bankCode,
            orderInfo = orderInfo,
            baseUrl = onepayConfig.baseUrl,
            ipAddress = ipAddress,
            returnURL = returnURL,
            referenceUrl = referenceUrl
        )

        return OnePayPaymentDetail(
            redirectUrl = redirectUrl,
            returnUrl = returnURL,
        )
    }

    suspend fun callQueryStateInquiry(
        onepayConfig: PGOnePayAccount, paymentId: String,
    ): Map<String, String> {
        return withContext(Dispatchers.IO) {
            val param: MutableMap<String, String> = HashMap()
            param["vpc_Version"] = "2"
            param["vpc_Command"] = "queryDR"
            param["vpc_AccessCode"] = onepayConfig.merchantAccessCode
            param["vpc_Merchant"] = onepayConfig.merchantId
            param["vpc_Password"] = onepayConfig.password
            param["vpc_User"] = onepayConfig.user
            param["vpc_MerchTxnRef"] = paymentId
            val queryParamSorted: TreeMap<String, String> = OnePayUtil.sortMap(param)
            val stringToHash: String = OnePayUtil.generateStringToHash(queryParamSorted)
//                println("merchant's string to hash: $stringToHash")
            val merchantSecureHash: String = OnePayUtil.generateSecureHash(stringToHash, onepayConfig.merchantHashCode)
            param["vpc_SecureHash"] = merchantSecureHash
            val url = onepayConfig.baseUrl + "/msp/api/v1/vpc/invoices/queries"
            logger.debug("payment {} call onepay gateway request {} {}", paymentId, url, param)
            val response = httpClient.post {
                url(url)
                headers {
                    append("Accept", "*/*")
                }
                setBody(FormDataContent(parametersOf(param.mapValues { listOf(it.value) })))
            }
            val result = response.bodyAsText()
            logger.debug("payment {} call onepay gateway response {}", paymentId, result)
            val params: MutableMap<String, String> = HashMap()
            if (result.isNotEmpty()) {
                for (kv in result.split("&".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()) {
                    if (kv.contains("=")) {
                        val aryKV = kv.split("=".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
                        if (aryKV.size == 2) {
                            params[URLDecoder.decode(aryKV.get(0), "UTF-8")] = URLDecoder.decode(aryKV.get(1), "UTF-8")
                        } else {
                            params[URLDecoder.decode(aryKV.get(0), "UTF-8")] = ""
                        }
                    }
                }
            }
            params
        }
    }

    fun onepayVerifySecureHash(queryParam: Map<String, String>, merchantHashCode: String): Boolean {
        try {
            val paramSorted = OnePayUtil.sortMap(queryParam)
            val stringToHash = OnePayUtil.generateStringToHash(paramSorted)
            val onePayVpcSecureHash = OnePayUtil.generateSecureHash(stringToHash, merchantHashCode)
            val merchantVpcSecureHash = queryParam["vpc_SecureHash"]
            return onePayVpcSecureHash == merchantVpcSecureHash
        } catch (t: Throwable) {
            logger.error("exception when verify secure hash... ", t)
            return false
        }
    }
}