package metaticket.order.datastructure


/**
 * Represents the various statuses an order can be in throughout its lifecycle.
 * @author dongcatchay
 */
enum class OrderStatus {
    /**
     * The order has been successfully created.
     */
    Created,
    /**
     * The order is awaiting payment; a payment request has been successfully created with the payment service.
     */
    WaitingForPayment,
    /**
     * The customer has initiated the payment process.
     */
    PaymentProcessing,
    /**
     * The order is for a zero-amount or offline payment, not requiring interaction with the payment service.
     * It awaits customer payment and manual confirmation by operations to be marked as successful.
     */
    PaymentPending,
    /**
     * The payment for the order has been successfully processed.
     */
    PaymentSucceed,
    /**
     * The payment for the order has failed.
     */
    PaymentFailed,
    /**
     * The payment for the order has been cancelled.
     */
    PaymentCancelled,
    /**
     * The payment for the order has been refunded.
     */
    PaymentRefunded,
    /**
     * The order has expired, typically due to non-payment within a specified timeframe.
     */
    Expired,
    /**
     * The order processing has failed for reasons other than payment.
     */
    Failed,
    /**
     * The order has been cancelled.
     */
    Canceled,
}

enum class CustomFieldType {
    String, Boolean, Date, List, DataCollector, Number
}

data class OrderSummary constructor(
    var reservationId: String,
    var orderId: String,
    val orderCode: String,
    val orderStatus: OrderStatus,
    val email: String,
    var paymentOffline: Boolean?,
    var paymentNote: String?,
    val paymentInfo: PaymentInfo?,
    val orderDetail: OrderDetail,
    val buyerInfo: BuyerInfo,
    val createdAt: Long,
    val recipientInfo: RecipientInfo? = null,
    val customFields: List<CustomField>? = null,
)

data class PaymentInfo constructor(
    val payTime: String,
    val payMethod: String?,
    val payStatus: String,
    var paymentId: String? = null,
)

data class OrderDetail constructor(
    val eventName: String,
    val eventTime: String,
    val venue: String,
    val prices: List<OrderPrice>,
    val promotions: List<Promotion>,
    val total: Long,
    val orderNote: String? = null,
)

data class OrderPrice constructor(
    val name: String,
    val price: Long,
    val quantity: Int,
    var priceCat : String,      // price category
    var sid: String,
    var ticketSlots: Int? = null,
)

data class Promotion constructor(
    val name: String,
    val price: Long,
)

data class BuyerInfo constructor(
    val name: String,
    val phone: String,
    val email: String,
)

data class RecipientInfo constructor(
    val name: String,
    val phone: String,
    val address: String,
)

data class CustomField constructor(
    val name: String,
    val value: String?,
)
