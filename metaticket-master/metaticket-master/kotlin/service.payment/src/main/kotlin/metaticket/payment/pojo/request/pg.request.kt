package metaticket.payment.pojo.request


/**
 *
 * @author dongcatchay
 */

data class AddOnePayAccountRequest constructor(
    val code: String,
    val enabled: Boolean,
    val name: String,
    val description: String,

    val baseUrl: String,

    val merchantId: String,
    val merchantAccessCode: String,
    val merchantHashCode: String,

    val user: String,
    val password: String,
)

data class UpdateOnePayAccountRequest constructor(
    val code: String,
    val name: String? = null,
    val description: String? = null,

    val baseUrl: String? = null,
)

data class AddNinePayAccountRequest constructor(
    val code: String,
    val enabled: Boolean,
    val name: String,
    val description: String,

    val baseUrl: String,

    val merchantKey: String,
    val merchantSecretKey: String,
    val checksumKey: String
)

data class UpdateNinePayAccountRequest constructor(
    val code: String,
    val name: String? = null,
    val description: String? = null,

    val baseUrl: String? = null,
)

data class AddVnPayAccountRequest constructor(
    val code: String,
    val enabled: Boolean,
    val name: String,
    val description: String,

    val payUrl: String,
    val queryUrl: String,
    val expireInMinute: Int,

    val tmnCode: String,
    val hashSecret: String
)

data class UpdateVnPayAccountRequest constructor(
    val code: String,
    val name: String? = null,
    val description: String? = null,

    val payUrl: String? = null,
    val queryUrl: String? = null,
    val expireInMinute: Int? = null,
)

data class CodeOnlyRequest constructor(
    val code: String
)
