package metaticket.plugin.mailtemplate.pojo.db

import metaticket.notification.mail.grpc.MailMessage


data class PluginMailOrderData constructor(
    val showId: String,
    val sessionId: String,
    val orderSummary: MailMessage.OrderSummaryProto,
    val eventSquareImageUrl: String? = null,
    val eventNameImageUrl: String? = null,
    val timeoutToFullInS: Long? = null,
    val qrcodeEmail: Boolean? = null
)

/** One purchased accessory line for confirmation email templates. */
data class AccessoryMailLine(
    val name: String,
    val variantLabel: String? = null,
    val quantity: Int,
    val unitPrice: Long,
    val lineTotal: Long,
)

/** Payload passed to [metaticket.plugin.accessory.PluginMailTemplateAccessoryOrder]. */
data class AccessoryMailPayload(
    val orderCode: String,
    val customerName: String,
    val customerEmail: String,
    val customerPhone: String,
    val showName: String,
    val lines: List<AccessoryMailLine>,
    val subtotal: Long,
    val discountCode: String? = null,
    val discountAmount: Long = 0,
    val total: Long,
    val pickupNote: String? = null,
)