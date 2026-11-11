package metaticket.accessory.gateway

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import metaticket.accessory.pojo.db.AccessoryOrderDocument
import metaticket.accessory.pojo.db.AccessoryShowDocument
import metaticket.accessory.pojo.response.fulfillmentSummary
import metaticket.common.logger.Logging
import metaticket.notification.mail.grpc.MailMessage
import metaticket.notification.mail.grpc.MailNotificationServiceGrpcKt

/**
 * Accessory-side gRPC client to `service.notification` (design "Component: AccessoryMailGateway").
 *
 * It calls the **new, dedicated** `SendMailAccessoryOrder` RPC with a
 * [MailMessage.SendMailAccessoryOrderRequest] built **natively** from the accessory order + show.
 * It deliberately does **not** touch the ticket `SendMailOrder` method or the
 * `SendMailOrderRequest`/`OrderSummary` messages, keeping the accessory email path fully separate
 * from the ticket email path (Req 9.4, 18.2). One order = one email.
 */
class AccessoryMailGateway(
    private val stub: MailNotificationServiceGrpcKt.MailNotificationServiceCoroutineStub,
) : Logging {

    companion object {
        /** Fallback notification template id when the show does not pin one (matches the seeded row, task 11.4). */
        const val DEFAULT_MAIL_TEMPLATE = "accessory-order-confirm-vi"
    }

    /**
     * Send the confirmation email for a single accessory order through the dedicated
     * `SendMailAccessoryOrder` RPC.
     *
     * All request fields are carried natively on [MailMessage.SendMailAccessoryOrderRequest]; the
     * ticket-shaped `OrderSummary` is never populated or referenced. `req_id` is set to the order
     * code so the whole path is traceable/de-dupable per order.
     *
     * @param order the accessory order (source of code, lines, totals, discount, recipient)
     * @param show  the owning accessory show (source of show name, pickup note, mail plugin/template)
     */
    suspend fun sendMailAccessoryOrder(order: AccessoryOrderDocument, show: AccessoryShowDocument) {
        val request = buildSendMailAccessoryOrderRequest(order, show)

        withContext(Dispatchers.IO) {
            stub.sendMailAccessoryOrder(request)
        }
    }

    /**
     * Build the native [MailMessage.SendMailAccessoryOrderRequest] for [order] + [show].
     *
     * Shared by the direct send above and the JobRunr fallback ([metaticket.accessory.gateway.JobrunrGateway])
     * so both paths carry an identical request. `req_id` is the order code so the whole path is
     * traceable/de-dupable per order.
     */
    fun buildSendMailAccessoryOrderRequest(
        order: AccessoryOrderDocument,
        show: AccessoryShowDocument,
    ): MailMessage.SendMailAccessoryOrderRequest {
        val mailTemplate = show.mailTemplateId?.takeIf { it.isNotBlank() } ?: DEFAULT_MAIL_TEMPLATE

        return MailMessage.SendMailAccessoryOrderRequest.newBuilder().also { req ->
            req.reqId = order.code
            req.mailPlugin = show.mailPlugin
            req.mailTemplate = mailTemplate
            req.recipientEmail = order.customerEmail
            req.orderCode = order.code
            req.showName = show.name
            req.addAllLines(
                order.lines.map { line ->
                    MailMessage.AccessoryMailLineItem.newBuilder().also { item ->
                        item.productName = line.name
                        item.variantLabel = line.variantLabel ?: ""
                        item.quantity = line.quantity
                        item.unitPrice = line.unitPrice
                        item.lineTotal = line.unitPrice * line.quantity
                    }.build()
                }
            )
            req.subtotal = order.subtotal
            val discount = order.discount
            req.hasDiscount = discount != null
            if (discount != null) {
                req.discountCode = discount.code
                req.discountAmount = discount.amount
            }
            req.total = order.total
            req.pickupNote = order.fulfillmentSummary(show.pickupNote)
            req.customerName = order.customerName
            req.customerPhone = order.customerPhone
        }.build()
    }
}
