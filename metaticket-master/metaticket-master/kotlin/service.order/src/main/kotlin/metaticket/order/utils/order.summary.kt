package metaticket.order.utils

import metaticket.common.checksum.Checksum
import metaticket.notification.mail.grpc.MailMessage
import metaticket.order.configuration.Environment
import metaticket.order.configuration.Environment.secretKey
import metaticket.order.datastructure.*
import metaticket.order.gateway.PaymentGateway
import metaticket.order.logger
import metaticket.order.pojo.db.OrderDocument
import metaticket.payment.datastructure.PaymentStatus
import metaticket.payment.datastructure.PaymentType
import metaticket.show.datastructure.CalendarRules
import metaticket.show.grpc.ShowMessage
import java.text.SimpleDateFormat
import java.util.*


/**
 *
 * @author dongcatchay
 */

/**
 * Thời gian hiển thị (email, trang đơn) của đơn vé theo lịch: ngày tham quan kèm giờ mở cửa, cùng định dạng
 * "HH:mm, dd/MM/yyyy" với suất thường để template email không phải đổi.
 */
internal fun formatVisitDate(visitDate: String, session: ShowMessage.GetSessionResponse): String {
    val date = CalendarRules.parseDate(visitDate) ?: return visitDate
    val openTime = if (session.hasCalendar()) session.calendar.openTime else null
    return listOfNotNull(openTime, CalendarRules.displayDate(date)).joinToString(", ")
}

suspend fun buildOrderSummary(
    paymentGateway: PaymentGateway,
    show: ShowMessage.GetShowResponse,
    session: ShowMessage.GetSessionResponse,
    order: OrderDocument,
): OrderSummary? {

    val dateTimeFormat: () -> SimpleDateFormat = {
        val isoFormat = SimpleDateFormat("HH:mm, dd/MM/yyyy")
        isoFormat.timeZone = TimeZone.getTimeZone("GMT+7")
        isoFormat
    }

    val prices = order.tickets.map { ticket ->
        OrderPrice(
            name =  if (!ticket.dname.isNullOrBlank()) ticket.dname!! else "${ticket.priceCat} | ${ticket.sid}",
            price =  ticket.basePrice * ticket.quantity,
            quantity =  ticket.quantity,
            priceCat =  ticket.priceCat,
            sid =  ticket.sid,
            ticketSlots = ticket.ticketSlots,
        )
    }

    val promotions = order.promotionDetails.map { Promotion(it.name, it.priceDiscount) }

    var payTime: String
    var paymentMethod: String? = null
    var paymentStatus: String
    var paymentInfo: PaymentInfo? = null
    var paymentAmount = 0L

    if(session.paymentOffline == true) {
        paymentMethod = "QR | Mobile Banking | Cash"
        paymentAmount = order.total
        paymentStatus = when(order.status) {
            OrderStatus.Created -> "Đã tạo"
            OrderStatus.PaymentPending -> "Đang chờ thanh toán"
            OrderStatus.PaymentProcessing -> "Đang thanh toán"
            OrderStatus.Canceled -> "Đơn hàng đã bị hủy"
            OrderStatus.Expired -> "Quá hạn thanh toán"
            OrderStatus.PaymentRefunded -> "Đã hoàn tiền"
            OrderStatus.PaymentSucceed -> "Thanh toán thành công"
            else -> order.status.name
        }
        payTime = dateTimeFormat().format(order.lastModified)
        paymentInfo = PaymentInfo(
            payTime,
            paymentMethod,
            paymentStatus
        )
    }

    order.paymentId?.let { paymentId ->
        val payment = try {
            paymentGateway.getPaymentById(paymentId)
        } catch (t: Throwable) {
            logger.error("order {}, fail to build summary, cannot get payment {} ... ", order.id, paymentId, t)
            return null
        }
        logger.info("payment {} found", payment)
        payTime = if (payment.hasPayTime()) dateTimeFormat().format(payment.payTime) else ""
        if (payment.hasPaymentType()) {
            paymentMethod = when (PaymentType.valueOf(payment.paymentType)) {
                PaymentType.DomesticCard -> "Thẻ ATM nội địa"
                PaymentType.InternationalCard -> "Thẻ quốc tế (Visa/Master Card/...)"
                PaymentType.QrCode -> "QR | Chuyển khoản | Ví điện tử"
                PaymentType.VietQr -> "Thanh toán với VietQr"
//            PaymentType.BNPL -> "Mua Trước Trả Sau"
            }
        }
        paymentStatus = when(PaymentStatus.valueOf(payment.status)) {
            PaymentStatus.Created,
            PaymentStatus.Starting -> "Đã tạo yêu cầu thanh toán"
            PaymentStatus.Processing -> "Đang thanh toán"
            PaymentStatus.Succeed -> "Thanh toán thành công"
            PaymentStatus.Failed -> "Thanh toán thất bại"
            PaymentStatus.Refunded -> "Đã hoàn tiền"
            PaymentStatus.Cancelled -> "Thanh toán đã bị hủy"
        }
        paymentAmount = payment.amount

        paymentInfo = PaymentInfo(
            payTime,
            paymentMethod,
            paymentStatus,
            Checksum.encode(paymentId, Environment.secretKey)
        )
    }

    val sessionStart = order.visitDate?.let { formatVisitDate(it, session) }
        ?: dateTimeFormat().format(Date(session.startTime))


    val orderDetail = OrderDetail(
        show.showName,
        sessionStart,
        session.venue,
        prices,
        promotions,
        paymentAmount,
        if (session.hasOrderNote()) session.orderNote else null,
    )
    val buyerInfo = BuyerInfo(
        order.customerName,
        order.customerPhone.replace("+84", "0"),
        order.customerEmail,
    )
    val recipientInfo = order.delivery?.let {
        RecipientInfo(
            it.deliveryName,
            it.deliveryPhone.replace("+84", "0"),
            "${it.address}, ${it.wardName}, ${it.districtName}, ${it.provinceName}"
        )
    }
    val customFields = order.customFields?.map { CustomField(it.name, it.value) }

    return OrderSummary(
        reservationId = Checksum.encode(order.reservationId, secretKey),
        orderId = Checksum.encode(order.id, secretKey),
        orderCode = order.code,
        orderStatus = order.status,
        email = order.customerEmail,
        paymentOffline = if (session.hasPaymentOffline()) session.paymentOffline else null,
        paymentNote = if (session.hasPaymentNote()) session.paymentNote else null,
        paymentInfo = paymentInfo,
        orderDetail = orderDetail,
        buyerInfo = buyerInfo,
        recipientInfo = recipientInfo,
        customFields = customFields,
        createdAt = order.createdAt.time
    )
}

fun OrderSummary.toOrderSummaryProto(): MailMessage.OrderSummaryProto {
    val orderSummaryBulder = MailMessage.OrderSummaryProto.newBuilder()
        .setOrderId(Checksum.decode(orderId, Environment.secretKey))
        .setOrderCode(orderCode)
        .setOrderStatus(orderStatus.name)
        .setEmail(email)
        .setReservationId(Checksum.decode(reservationId, Environment.secretKey))
        .setCreatedAt(createdAt)

    paymentNote?.let { orderSummaryBulder.setPaymentNote(it) }
    paymentOffline?.let { orderSummaryBulder.setPaymentOffline(it) }
    paymentInfo?.let { pInfo -> orderSummaryBulder.setPaymentInfo(
        MailMessage.PaymentInfoProto.newBuilder()
            .setPayTime(pInfo.payTime)
            .setPayStatus(pInfo.payStatus)
            .also { b ->
                pInfo.paymentId?.let { b.setPaymentId(Checksum.decode(it, Environment.secretKey)) }
                pInfo.payMethod?.let { b.setPayMethod(it) }
            }
    ) }
    buyerInfo.let { orderSummaryBulder.setBuyerInfo(
        MailMessage.BuyerInfoProto.newBuilder()
            .setEmail(it.email)
            .setName(it.name)
            .setPhone(it.phone)
    ) }
    orderDetail.let { detail ->
        val price = detail.prices.map { MailMessage.OrderPriceProto.newBuilder()
            .setName(it.name)
            .setPrice(it.price)
            .setPriceCat(it.priceCat)
            .setQuantity(it.quantity)
            .setSid(it.sid)
            .apply {
                it.ticketSlots?.let { ticketSlots -> this.setSlots(ticketSlots)}
            }
            .build()
        }
        val promotion = detail.promotions.map { MailMessage.PromotionProto.newBuilder()
            .setName(it.name)
            .setPrice(it.price)
            .build()
        }
        orderSummaryBulder.setOrderDetail(MailMessage.OrderDetailProto.newBuilder()
            .setTotal(detail.total)
            .setVenue(detail.venue)
            .setEventName(detail.eventName)
            .setEventTime(detail.eventTime)
            .addAllPrices(price)
            .addAllPromotions(promotion)
            .also { b ->
                detail.orderNote?.let { b.setOrderNote(it) }
            }
        )
    }
    return orderSummaryBulder.build()
}
