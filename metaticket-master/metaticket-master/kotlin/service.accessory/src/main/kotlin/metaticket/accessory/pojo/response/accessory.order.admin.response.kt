package metaticket.accessory.pojo.response

import metaticket.accessory.pojo.db.AccessoryOrderDocument
import metaticket.accessory.pojo.db.AccessoryOrderStatus
import metaticket.accessory.security.PublicId

/**
 * Per-status counts plus paid-only revenue for one accessory show (admin orders page).
 * [revenue] / [paidOrders] only include [AccessoryOrderStatus.PaymentSucceed].
 */
data class AdminOrderStatsResponse(
    val totalOrders: Int,
    val paidOrders: Int,
    val revenue: Long,
    val byStatus: Map<String, Int>,
)

/**
 * Admin list row for one accessory order (order/show ids stay raw for portal filters).
 * [paymentId] is Checksum-encoded so portal can call `POST /private/payment/ids/fetch`
 * (same contract as ticket orders); null when the order has no payment (e.g. zero-pay).
 */
data class AdminOrderListItem(
    val id: String,
    val code: String,
    val status: String,
    val fulfillment: String,
    val paymentId: String?,
    /** Buyer user id (`X-Customer-User-Id` at checkout); null for legacy orders. */
    val customerUserId: String?,
    val showSessionId: String?,
    val showSessionLabel: String?,
    val pickupPointId: String?,
    val pickupPointName: String?,
    val pickupPointAddress: String?,
    val subtotal: Long,
    val discountAmount: Long,
    val discountCode: String?,
    val total: Long,
    val customerName: String,
    val customerPhone: String,
    val customerEmail: String,
    val createdAt: Long,
    val deliveryAddress: String?,
    val deliveryWard: String?,
    val deliveryCity: String?,
    val deliveryNote: String?,
    val lines: List<OrderExportLine>,
)

/** Bundle returned by GET `/private/accessory/order/fetch/all`. */
data class AdminOrderListResponse(
    val stats: AdminOrderStatsResponse,
    val orders: List<AdminOrderListItem>,
)

/** Maps an [AccessoryOrderDocument] to the admin list row (with lines for detail modal). */
fun AccessoryOrderDocument.toAdminListItem() = AdminOrderListItem(
    id = id,
    code = code,
    status = status.name,
    fulfillment = fulfillment.name,
    paymentId = paymentId?.let { PublicId.encode(it) },
    customerUserId = customerUserId,
    showSessionId = showSessionId,
    showSessionLabel = showSessionLabel,
    pickupPointId = pickupPointId,
    pickupPointName = pickupPointName,
    pickupPointAddress = pickupPointAddress,
    subtotal = subtotal,
    discountAmount = discount?.amount ?: 0L,
    discountCode = discount?.code,
    total = total,
    customerName = customerName,
    customerPhone = customerPhone,
    customerEmail = customerEmail,
    createdAt = createdAt.time,
    deliveryAddress = deliveryAddress?.address,
    deliveryWard = deliveryAddress?.ward,
    deliveryCity = deliveryAddress?.city,
    deliveryNote = deliveryAddress?.note,
    lines = lines.map { it.toExportLine() },
)

/** Builds [AdminOrderStatsResponse] from an in-memory order list for one show. */
fun computeAdminOrderStats(orders: List<AccessoryOrderDocument>): AdminOrderStatsResponse {
    val byStatus = mutableMapOf<String, Int>()
    var paidOrders = 0
    var revenue = 0L
    for (order in orders) {
        val key = order.status.name
        byStatus[key] = (byStatus[key] ?: 0) + 1
        if (order.status == AccessoryOrderStatus.PaymentSucceed) {
            paidOrders += 1
            revenue += order.total
        }
    }
    return AdminOrderStatsResponse(
        totalOrders = orders.size,
        paidOrders = paidOrders,
        revenue = revenue,
        byStatus = byStatus,
    )
}

/**
 * Rich statistics for the admin "Thống kê" page.
 * Paid metrics (units / gross / discount / revenue) only count [AccessoryOrderStatus.PaymentSucceed].
 */
data class AdminSalesSummaryResponse(
    val totalOrders: Int,
    val paidOrders: Int,
    val unitsSold: Int,
    val grossSales: Long,
    val discountTotal: Long,
    val revenue: Long,
    val byStatus: Map<String, Int>,
)

/** Per-product row aggregated from paid order lines. */
data class AdminProductSalesRow(
    val accessoryId: String,
    val sku: String,
    val name: String,
    val unitPrice: Long,
    val quantity: Int,
    val grossSales: Long,
)

/** GET `/private/accessory/order/stats` response. */
data class AdminSalesStatisticResponse(
    val summary: AdminSalesSummaryResponse,
    val byProduct: List<AdminProductSalesRow>,
)

/**
 * Aggregates sales stats from [orders] already filtered by show/date.
 * Product rows only include lines from PaymentSucceed orders.
 */
fun computeAdminSalesStatistic(orders: List<AccessoryOrderDocument>): AdminSalesStatisticResponse {
    val byStatus = mutableMapOf<String, Int>()
    var paidOrders = 0
    var unitsSold = 0
    var grossSales = 0L
    var discountTotal = 0L
    var revenue = 0L
    val productMap = linkedMapOf<String, AdminProductSalesRow>()

    for (order in orders) {
        val key = order.status.name
        byStatus[key] = (byStatus[key] ?: 0) + 1
        if (order.status != AccessoryOrderStatus.PaymentSucceed) continue
        paidOrders += 1
        revenue += order.total
        discountTotal += order.discount?.amount ?: 0L
        for (line in order.lines) {
            unitsSold += line.quantity
            val lineGross = line.unitPrice * line.quantity
            grossSales += lineGross
            val mapKey = "${line.accessoryId}|${line.sku}|${line.unitPrice}"
            val existing = productMap[mapKey]
            if (existing == null) {
                productMap[mapKey] = AdminProductSalesRow(
                    accessoryId = line.accessoryId,
                    sku = line.sku,
                    name = line.name,
                    unitPrice = line.unitPrice,
                    quantity = line.quantity,
                    grossSales = lineGross,
                )
            } else {
                productMap[mapKey] = existing.copy(
                    quantity = existing.quantity + line.quantity,
                    grossSales = existing.grossSales + lineGross,
                )
            }
        }
    }

    return AdminSalesStatisticResponse(
        summary = AdminSalesSummaryResponse(
            totalOrders = orders.size,
            paidOrders = paidOrders,
            unitsSold = unitsSold,
            grossSales = grossSales,
            discountTotal = discountTotal,
            revenue = revenue,
            byStatus = byStatus,
        ),
        byProduct = productMap.values.sortedByDescending { it.grossSales },
    )
}
