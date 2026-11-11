package metaticket.payment.pojo.db

import com.fasterxml.jackson.annotation.JsonSubTypes
import com.fasterxml.jackson.annotation.JsonTypeInfo
import metaticket.payment.datastructure.PaymentGateway
import org.bson.BsonType
import org.bson.codecs.pojo.annotations.*
import org.bson.types.ObjectId


/**
 *
 * @author dongcatchay
 */

@JsonTypeInfo(include = JsonTypeInfo.As.WRAPPER_OBJECT, use = JsonTypeInfo.Id.NAME)
@JsonSubTypes(
    JsonSubTypes.Type(value = PGNinePayAccount::class),
    JsonSubTypes.Type(value = PGVnPayAccount::class),
    JsonSubTypes.Type(value = PGOnePayAccount::class)
)
@BsonDiscriminator
abstract class PGAccount constructor() {
    abstract val gateway: PaymentGateway
    abstract val code: String
    abstract val name: String
    abstract val description: String
    abstract val enabled: Boolean
    abstract val returnUrl: String
    abstract val ipnUrl: String
}

@BsonDiscriminator
class PGOnePayAccount @BsonCreator constructor(

    @BsonProperty("gateway")
    override val gateway: PaymentGateway = PaymentGateway.OnePay,

    @BsonProperty("code")
    override val code: String,

    @BsonProperty("name")
    override val name: String,
    @BsonProperty("description")
    override val description: String,
    @BsonProperty("enabled")
    override val enabled: Boolean,

    @BsonProperty("returnUrl")
    override val returnUrl: String,
    @BsonProperty("ipnUrl")
    override val ipnUrl: String,

    @BsonProperty("baseUrl")
    val baseUrl: String,
    @BsonProperty("merchantId")
    val merchantId: String,
    @BsonProperty("merchantAccessCode")
    val merchantAccessCode: String,
    @BsonProperty("merchantHashCode")
    val merchantHashCode: String,
    @BsonProperty("user")
    val user: String,
    @BsonProperty("password")
    val password: String,

): PGAccount()

@BsonDiscriminator
class PGNinePayAccount @BsonCreator constructor(

    @BsonProperty("gateway")
    override val gateway: PaymentGateway = PaymentGateway.NinePay,

    @BsonProperty("code")
    override val code: String,

    @BsonProperty("name")
    override val name: String,
    @BsonProperty("description")
    override val description: String,
    @BsonProperty("enabled")
    override val enabled: Boolean,

    @BsonProperty("returnUrl")
    override val returnUrl: String,
    @BsonProperty("ipnUrl")
    override val ipnUrl: String,

    @BsonProperty("baseUrl")
    val baseUrl: String,
    @BsonProperty("merchantKey")
    val merchantKey: String,
    @BsonProperty("merchantSecretKey")
    val merchantSecretKey: String,
    @BsonProperty("checksumKey")
    val checksumKey: String
): PGAccount()

@BsonDiscriminator
class PGVnPayAccount @BsonCreator constructor(

    @BsonProperty("gateway")
    override val gateway: PaymentGateway = PaymentGateway.NinePay,

    @BsonProperty("code")
    override val code: String,

    @BsonProperty("name")
    override val name: String,
    @BsonProperty("description")
    override val description: String,
    @BsonProperty("enabled")
    override val enabled: Boolean,

    @BsonProperty("returnUrl")
    override val returnUrl: String,
    @BsonProperty("ipnUrl")
    override val ipnUrl: String,

    @BsonProperty("payUrl")
    val payUrl: String,
    @BsonProperty("queryUrl")
    val queryUrl: String,
    @BsonProperty("expireInMinute")
    val expireInMinute: Int,

    @BsonProperty("tmnCode")
    val tmnCode: String,
    @BsonProperty("hashSecret")
    val hashSecret: String
): PGAccount()

data class PaymentGatewayConfig constructor(
    @BsonId @BsonRepresentation(BsonType.OBJECT_ID)
    val id: String = ObjectId().toHexString(),
    val pgAccountDefaultCode: String,
    val pgAccount: Map<String, PGAccount>, // Map<Code,PGAccount>
)
