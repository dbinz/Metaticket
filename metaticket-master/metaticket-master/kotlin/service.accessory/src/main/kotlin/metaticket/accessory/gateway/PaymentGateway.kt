package metaticket.accessory.gateway

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import metaticket.common.logger.Logging
import metaticket.payment.grpc.PaymentMessage
import metaticket.payment.grpc.PaymentServiceGrpcKt

/**
 * Accessory-side gRPC client to `service.payment.CreatePayment` (design "Reused: PaymentGateway").
 *
 * This reuses the existing, order-type-agnostic payment contract. The only accessory-specific input
 * is the additive `orderType = ACCESSORY` discriminator on [PaymentMessage.CreatePaymentRequest]
 * (proto field `order_type`, added in task 9.1). Passing it lets `service.payment` route the eventual
 * status callback back to the accessory flow instead of `service.order`. Every existing ticket
 * payment-request field is left untouched — this is a strictly additive change (Req 7.1, 18.4).
 *
 * The returned value is the opaque payment id string produced by `service.payment`; the caller
 * (`AccessoryOrderService.checkout`, task 6.3) stores it on the order and returns an encoded form.
 */
class PaymentGateway(
    private val stub: PaymentServiceGrpcKt.PaymentServiceCoroutineStub,
) : Logging {

    companion object {
        /** Additive discriminator value telling `service.payment` this payment belongs to the accessory flow. */
        const val ORDER_TYPE_ACCESSORY = "ACCESSORY"

        /**
         * Default payment methods used when a show does not pin its own [allowedPaymentMethods].
         * Values are `metaticket.payment.datastructure.PaymentType` enum names; `service.payment`
         * resolves them with `PaymentType.valueOf`. Keeps shows created before the field existed
         * working with the full method set.
         */
        val DEFAULT_PAYMENT_METHODS: List<String> =
            listOf("VietQr", "QrCode", "InternationalCard", "DomesticCard")
    }

    /**
     * Create a payment for an accessory order.
     *
     * @param orderId  opaque accessory order id (payment treats it as an opaque string)
     * @param orderCode human-readable accessory order code (e.g. "TF2026-0148")
     * @param amount   order total in whole VND (caller only invokes when amount > 0; zero-total orders skip payment)
     * @param pgAccountCode which PG account to bill through; `null`/blank => payment service default
     * @param paymentMethods allowed payment methods for this order as `PaymentType` enum names;
     *        forwarded so `service.payment` gates `payment/start` to these methods (mirrors the
     *        ticket flow's `session.paymentMethods`). Blank/absent entries are ignored; an empty
     *        list falls back to [DEFAULT_PAYMENT_METHODS].
     * @param orderType additive routing discriminator; defaults to [ORDER_TYPE_ACCESSORY]
     * @return the opaque payment id produced by `service.payment`
     */
    suspend fun createPayment(
        orderId: String,
        orderCode: String,
        amount: Long,
        pgAccountCode: String?,
        paymentMethods: List<String>,
        orderType: String = ORDER_TYPE_ACCESSORY,
    ): String {
        val methods = paymentMethods.map { it.trim() }.filter { it.isNotBlank() }
            .ifEmpty { DEFAULT_PAYMENT_METHODS }
        return withContext(Dispatchers.IO) {
            stub.createPayment(
                PaymentMessage.CreatePaymentRequest.newBuilder().also {
                    it.orderId = orderId
                    it.orderCode = orderCode
                    it.amount = amount
                    it.orderType = orderType
                    if (!pgAccountCode.isNullOrBlank()) {
                        it.pgAccountCode = pgAccountCode
                    }
                    // Forward the show's allowed payment methods (VietQR / bank transfer / intl card
                    // for The Flob). service.payment stores them and rejects any other method at start.
                    it.addAllPaymentMethods(methods)
                }.build()
            ).value
        }
    }
}
