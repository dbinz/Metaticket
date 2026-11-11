package metaticket.payment.pojo.response

import metaticket.payment.datastructure.PaymentGateway
import metaticket.payment.pojo.db.*


/**
 *
 * @author dongcatchay
 */

data class PGConfigResponse constructor(
    val pgAccountDefaultCode: String,
    val pgAccount: List<PGAccountResponse>,
)

interface PGAccountResponse {
    val gateway: PaymentGateway
    val code: String
    val enabled: Boolean
    val name: String
    val description: String
    val returnUrl: String
    val ipnUrl: String
}

data class PGOnePayAccountResponse constructor(
    override val gateway: PaymentGateway,
    override val code: String,
    override val enabled: Boolean,
    override val name: String,
    override val description: String,
    override val returnUrl: String,
    override val ipnUrl: String,

    val baseUrl: String,
): PGAccountResponse

data class PGNinePayAccountResponse constructor(
    override val gateway: PaymentGateway,
    override val code: String,
    override val enabled: Boolean,
    override val name: String,
    override val description: String,
    override val returnUrl: String,
    override val ipnUrl: String,

    val baseUrl: String,
): PGAccountResponse

data class PGVnPayAccountResponse constructor(
    override val gateway: PaymentGateway,
    override val code: String,
    override val enabled: Boolean,
    override val name: String,
    override val description: String,
    override val returnUrl: String,
    override val ipnUrl: String,

    val queryUrl: String,
    val payUrl: String,
    val expireInMinute: Int,
): PGAccountResponse

fun PGAccount.toResponse(): PGAccountResponse {
    return when (gateway) {
        PaymentGateway.NinePay -> (this as PGNinePayAccount).toResponse()
        PaymentGateway.VNPay -> (this as PGVnPayAccount).toResponse()
        PaymentGateway.OnePay -> (this as PGOnePayAccount).toResponse()
    }
}

fun PGOnePayAccount.toResponse(): PGOnePayAccountResponse {
    return PGOnePayAccountResponse(
        gateway = gateway,
        code = code,
        enabled = enabled,
        name = name,
        description = description,
        returnUrl = returnUrl,
        ipnUrl = ipnUrl,
        baseUrl = baseUrl,
    )
}

fun PGNinePayAccount.toResponse(): PGNinePayAccountResponse {
    return PGNinePayAccountResponse(
        gateway = gateway,
        code = code,
        enabled = enabled,
        name = name,
        description = description,
        returnUrl = returnUrl,
        ipnUrl = ipnUrl,
        baseUrl = baseUrl,
    )
}

fun PGVnPayAccount.toResponse(): PGVnPayAccountResponse {
    return PGVnPayAccountResponse(
        gateway = gateway,
        code = code,
        enabled = enabled,
        name = name,
        description = description,
        queryUrl = queryUrl,
        payUrl = payUrl,
        returnUrl = returnUrl,
        ipnUrl = ipnUrl,
        expireInMinute = expireInMinute
    )
}

fun PaymentGatewayConfig.toResponse(): PGConfigResponse {
    return PGConfigResponse(
        pgAccountDefaultCode = pgAccountDefaultCode,
        pgAccount = pgAccount.values.map { it.toResponse() }
    )
}
