package metaticket.order.pojo.response

import metaticket.common.checksum.Checksum
import metaticket.order.calculate.promotion.PromotionDetailData
import metaticket.order.configuration.Environment
import metaticket.order.datastructure.response.*
import metaticket.order.pojo.db.*


/**
 *
 * @author dongcatchay
 */

fun TicketInfo.toResponse(): TicketInfoResponse {
    return TicketInfoResponse(
        id = id,
        dName = dname,
        sid = sid,
        seatType = seatType,
        priceCat = priceCat,
        quantity = quantity,
        basePrice = basePrice
    )
}

fun OrderPromotionDetail.toResponse(): PromotionResponse {
    return PromotionResponse(name = name, priceDiscount = priceDiscount)
}

fun OrderDocument.toResponse(): OrderResponse {
    return OrderResponse(
        id = Checksum.encode(id, Environment.secretKey),
        reservationId = Checksum.encode(reservationId, Environment.secretKey),
        paymentId = paymentId?.let { Checksum.encode(it, Environment.secretKey) },
        showId = showId,
        sessionId = sessionId,
        code = code,
        status = status,
        customerName = customerName,
        customerPhone = customerPhone,
        customerEmail = customerEmail,
        delivery = delivery?.toResponse(),
        customFields = customFields?.map { it.toResponse() },
        promotionDetails = promotionDetails.map { it.toResponse() },
        tickets = tickets.map { it.toResponse() },
        total = total,
        createdAt = createdAt.time,
        visitDate = visitDate,
    )
}

fun DeliveryInfo.toResponse(): DeliveryInfoResponse {
    return DeliveryInfoResponse(
        deliveryName = deliveryName,
        deliveryPhone = deliveryPhone,
        provinceName = provinceName,
        districtName = districtName,
        wardName = wardName,
        address = address
    )
}

fun CustomFieldData.toResponse(): CustomFieldDataResponse {
    return CustomFieldDataResponse(
        id = id, name = name, value = value
    )
}

data class TentativePriceResponse constructor(
    val promotionName: String,
    val priceDiscount: Long, //price discount
    val priceOriginal: Long, //price before discount
    val priceTotal: Long, //price after discount
    val shipPrice: Long,
    val promotionDetails: List<PromotionDetailData>
)