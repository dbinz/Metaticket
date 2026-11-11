package metaticket.admin.pojo.response

import metaticket.admin.configuration.Environment
import metaticket.admin.pojo.db.TicketBookingDocument
import metaticket.common.checksum.Checksum
import metaticket.order.pojo.db.CustomFieldData

data class TicketBookingResponse(
    val id: String,

    // ticket info
    val priceCat : String? = null,   // price category
    val sid: String,
    val quantity : Int,     // requested capacity

    // customer info
    val customerName: String,
    val customerPhone: String,
    val customerEmail: String,
    // delivery ìnfo
    val deliveryName: String? = null,
    val deliveryPhone: String? = null,
    val provinceName: String? = null,
    val districtName: String? = null,
    val wardName: String? = null,
    val address: String? = null,
    // custom fields
    val customFields: List<CustomFieldData>? = null,

    // order info
    val promotionCode: String? = null,
    var reservationId: String? = null,
    var orderId: String? = null,
    var paymentId: String? = null,

    val version: Int = 1,
    val lastUpdated: Long,
    val createdAt: Long,
)

fun TicketBookingDocument.toResponse() = TicketBookingResponse(
    id = id,

    sid = sid,
    priceCat = priceCat,
    quantity = quantity,
    promotionCode = promotionCode,

    customerName = customerName,
    customerPhone = customerPhone,
    customerEmail = customerEmail,

    deliveryName = deliveryName,
    deliveryPhone = deliveryPhone,
    provinceName = provinceName,
    districtName = districtName,
    wardName = wardName,
    address = address,

    customFields = customFields,

    reservationId = reservationId?. let { Checksum.encode(it, Environment.secretKey) },
    orderId = orderId?. let { Checksum.encode(it, Environment.secretKey) },
    paymentId = paymentId?. let { Checksum.encode(it, Environment.secretKey) },

    version = version,
    createdAt = createdAt.time,
    lastUpdated = lastUpdated.time,
)