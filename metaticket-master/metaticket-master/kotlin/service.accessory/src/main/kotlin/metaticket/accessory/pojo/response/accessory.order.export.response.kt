package metaticket.accessory.pojo.response

import metaticket.accessory.pojo.db.AccessoryDocument
import metaticket.accessory.pojo.db.AccessoryOrderDocument
import metaticket.accessory.pojo.db.AccessoryOrderLine
import metaticket.accessory.security.PublicId

/** One snapshotted line for Excel export. */
data class OrderExportLine(
    val accessoryId: String,
    val sku: String,
    val name: String,
    val variantKey: String?,
    val variantLabel: String?,
    val unitPrice: Long,
    val quantity: Int,
)

/** Catalog product column for Excel (full show list, not derived from orders). */
data class OrderExportProductColumn(
    val accessoryId: String,
    val sku: String,
    val name: String,
)

/**
 * Admin Excel row source — all statuses in the filter window.
 * [paymentId] is the raw payment-service id for a follow-up payment lookup in portal.admin.
 */
data class AdminOrderExportItem(
    val id: String,
    val code: String,
    val status: String,
    val fulfillment: String,
    val paymentId: String?,
    val showSessionId: String? = null,
    val showSessionLabel: String? = null,
    val pickupPointId: String? = null,
    val pickupPointName: String? = null,
    val pickupPointAddress: String? = null,
    val customerName: String,
    val customerPhone: String,
    val customerEmail: String,
    val createdAt: Long,
    val discountCode: String?,
    val discountAmount: Long,
    val subtotal: Long,
    val total: Long,
    val deliveryAddress: String?,
    val deliveryWard: String?,
    val deliveryCity: String?,
    val deliveryNote: String?,
    val lines: List<OrderExportLine>,
)

/** Partner Excel row — PaymentSucceed only; no phone/email/payment internals. */
data class PublicOrderExportItem(
    val code: String,
    val customerName: String,
    val createdAt: Long,
    val discountCode: String?,
    val discountAmount: Long,
    val total: Long,
    val lines: List<OrderExportLine>,
)

/** Admin Excel payload: catalog products + orders. */
data class AdminOrderExportResponse(
    val products: List<OrderExportProductColumn>,
    val orders: List<AdminOrderExportItem>,
)

/** Public Excel payload: catalog products + paid orders. */
data class PublicOrderExportResponse(
    val products: List<OrderExportProductColumn>,
    val orders: List<PublicOrderExportItem>,
)

/** Maps one order line for export. */
fun AccessoryOrderLine.toExportLine() = OrderExportLine(
    accessoryId = accessoryId,
    sku = sku,
    name = name,
    variantKey = variantKey,
    variantLabel = variantLabel,
    unitPrice = unitPrice,
    quantity = quantity,
)

/** Maps catalog accessory → Excel column. */
fun AccessoryDocument.toExportProductColumn() = OrderExportProductColumn(
    accessoryId = id,
    sku = sku,
    name = name,
)

/** Maps an order document → admin export row. */
fun AccessoryOrderDocument.toAdminExportItem() = AdminOrderExportItem(
    id = id,
    code = code,
    status = status.name,
    fulfillment = fulfillment.name,
    paymentId = paymentId?.let { PublicId.encode(it) },
    showSessionId = showSessionId,
    showSessionLabel = showSessionLabel,
    pickupPointId = pickupPointId,
    pickupPointName = pickupPointName,
    pickupPointAddress = pickupPointAddress,
    customerName = customerName,
    customerPhone = customerPhone,
    customerEmail = customerEmail,
    createdAt = createdAt.time,
    discountCode = discount?.code,
    discountAmount = discount?.amount ?: 0L,
    subtotal = subtotal,
    total = total,
    deliveryAddress = deliveryAddress?.address,
    deliveryWard = deliveryAddress?.ward,
    deliveryCity = deliveryAddress?.city,
    deliveryNote = deliveryAddress?.note,
    lines = lines.map { it.toExportLine() },
)

/** Maps an order document → public partner export row. */
fun AccessoryOrderDocument.toPublicExportItem() = PublicOrderExportItem(
    code = code,
    customerName = customerName,
    createdAt = createdAt.time,
    discountCode = discount?.code,
    discountAmount = discount?.amount ?: 0L,
    total = total,
    lines = lines.map { it.toExportLine() },
)
