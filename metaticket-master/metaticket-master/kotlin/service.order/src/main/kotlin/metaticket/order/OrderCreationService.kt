package metaticket.order

import com.fasterxml.jackson.databind.ObjectMapper
import metaticket.common.coroutine.launch
import metaticket.common.logger.Logging
import metaticket.order.cache.CalculatePromotionClassesCache
import metaticket.order.cache.ShowCache
import metaticket.order.calculate.promotion.*
import metaticket.order.controller.OrderFailedHandler
import metaticket.order.database.service.*
import metaticket.order.datastructure.OrderStatus
import metaticket.order.gateway.*
import metaticket.order.kafka.OrderSucceedProducer
import metaticket.order.pocessing.isOrderProcessing
import metaticket.order.pocessing.releaseOrderProcessing
import metaticket.order.pojo.db.*
import metaticket.order.utils.ValidationUtil
import metaticket.order.validation.OrderValidationCustomField
import metaticket.show.datastructure.ShowSessionStatus
import metaticket.ticket.datastructure.SeatType
import metaticket.ticket.datastructure.TicketReservationStatus
import org.bson.types.ObjectId
import java.lang.reflect.Constructor
import java.util.*

// Define custom exceptions for better error handling separation
class OrderValidationException(message: String) : RuntimeException(message)
class ResourceNotFoundException(message: String) : RuntimeException(message)
class OrderCreationException(message: String, cause: Throwable? = null) : RuntimeException(message, cause)

// Define a common input data class
data class CreateOrderInput constructor(
    val reservationId: String,
    val customerUserId: String,
    val customerName: String,
    val customerPhone: String,
    val customerEmail: String,
    val deliveryInfo: DeliveryInfo?, // Use domain object
    val customFields: List<CustomFieldData>?, // Use domain object
    val promotionCode: String?
)

class OrderCreationService constructor(
    private val orderSucceedProducer: OrderSucceedProducer,
    private val orderDB: OrderDBService,
    private val reservationGateway: ReservationGateway,
    private val showGateway: ShowGateway,
    private val ticketGateway: TicketGateway,
    private val jobrunrGateway: JobrunrGateway,
    private val notificationGateway: NotificationGateway,
    private val showCache: ShowCache,
    private val orderFailedHandler: OrderFailedHandler,
    private val customFieldDB: OrderCustomFieldMappingDBService,
    private val promotionDb: PromotionDBService,
    private val calculatePromotionClasses: CalculatePromotionClassesCache, // Use the cache
    private val orderDataCollectorGateway: OrderDataCollectorGateway,
    private val promotionCodeQuantityDb: PromotionCodeQuantityDBService,
    private val promotionComboQuantityDb: PromotionComboQuantityDBService,
    private val jsonMapper: ObjectMapper,
) : Logging {

    suspend fun executeCreateOrder(input: CreateOrderInput): OrderDocument {
        val reservation = reservationGateway.getReservation(input.reservationId)
            ?: throw ResourceNotFoundException("Không tìm thấy yêu cầu đặt vé (Reservation ID: ${input.reservationId})")

        if (reservation.customerUserId != input.customerUserId) {
            logger.error("User ID mismatch: Request User={}, Reservation User={}", input.customerUserId, reservation.customerUserId)
            throw OrderValidationException("Không có quyền tạo đơn hàng với vé này")
        }

        if (reservation.status != TicketReservationStatus.Reserved.name) {
            logger.error("Invalid reservation status: ID={}, Status={}, Input={}", reservation.id, reservation.status, input)
            throw OrderValidationException("Trạng thái yêu cầu đặt vé không hợp lệ: ${reservation.status}")
        }

        val show = showCache.getShow(reservation.showId) ?: run {
            logger.error("Show not found: {}", reservation.showId)
            orderFailedHandler.onCreateOrderFailed(reservation.id) // Compensate
            throw ResourceNotFoundException("Sự kiện không tồn tại (Show ID: ${reservation.showId})")
        }

        val session = show.sessionsList.firstOrNull { it.sessionId == reservation.sessionId } ?: run {
            logger.error("Session not found: Show={}, Session={}", reservation.showId, reservation.sessionId)
            orderFailedHandler.onCreateOrderFailed(reservation.id) // Compensate
            throw ResourceNotFoundException("Phiên sự kiện không tồn tại (Session ID: ${reservation.sessionId})")
        }

        if (session.status != ShowSessionStatus.Open.name) {
            logger.error("Session not open: Show={}, Session={}, Status={}", reservation.showId, reservation.sessionId, session.status)
            orderFailedHandler.onCreateOrderFailed(reservation.id) // Compensate
            throw OrderValidationException("Sự kiện đang đóng")
        }

        // --- Delivery Validation ---
        if (session.requireDelivery) {
            val delivery = input.deliveryInfo
            val validationError = when {
                delivery == null -> "Thiếu dữ liệu người nhận vé"
                delivery.deliveryName.isBlank() -> "Tên người nhận không được để trống"
                !ValidationUtil.isPhoneValid(delivery.deliveryPhone) -> "Số điện thoại người nhận không chính xác"
                delivery.provinceName.isBlank() -> "Tên tỉnh/thành không được để trống"
                delivery.districtName.isBlank() -> "Tên quận không được để trống"
                delivery.wardName.isBlank() -> "Tên phường không được để trống"
                delivery.address.isBlank() -> "Địa chỉ không được để trống"
                else -> null
            }
            if (validationError != null) {
                logger.error("Invalid delivery info: {}, Input: {}", validationError, input)
                orderFailedHandler.onCreateOrderFailed(reservation.id) // Compensate
                throw OrderValidationException(validationError)
            }
        }

        // --- Custom Field Validation ---
        val mapping = customFieldDB.getCustomFieldMappingByShowAndSession(session.showId, session.sessionId)
        if (mapping != null && mapping.customFields.isNotEmpty()) {
            val inputCustomFields = input.customFields ?: emptyList()
            if (inputCustomFields.isEmpty()) { // Check if required fields are missing entirely
                logger.error("Missing required custom fields. Input: {}", input)
                orderFailedHandler.onCreateOrderFailed(reservation.id) // Compensate
                throw OrderValidationException("Thiếu dữ liệu điền thông tin bổ sung bắt buộc")
            }

            val orderValidationCustomField = OrderValidationCustomField()
            for (tmpl in mapping.customFields) {
                val data = inputCustomFields.firstOrNull { it.id == tmpl.id }
                if (!orderValidationCustomField.isCustomFieldValid(tmpl, data?.value, jsonMapper, orderDataCollectorGateway)) {
                    logger.error("Invalid custom field data: Template ID={}, Data={}, Input={}", tmpl.id, data, input)
                    orderFailedHandler.onCreateOrderFailed(reservation.id) // Compensate
                    throw OrderValidationException("Thông tin bổ sung '${tmpl.id}' không đúng định dạng hoặc không hợp lệ")
                }
            }
        }

        // --- Generate Order Code & Fetch Tickets ---
        val orderCode = showGateway.generateUniqueId(reservation.showId, reservation.sessionId)
        val ticketIds = reservation.detailsList.map { it.ticketId }
        val ticketsResp = ticketGateway.getTicketsByIds(ticketIds)
        if (ticketsResp.size != ticketIds.size) {
            logger.error("Mismatch fetching tickets. Expected: {}, Got: {}, Reservation: {}", ticketIds, ticketsResp.map { it.id }, reservation.id)
            orderFailedHandler.onCreateOrderFailed(reservation.id) // Compensate
            throw OrderCreationException("Không thể lấy đủ thông tin vé đã đặt.")
        }
        if (ticketsResp.any { it.showId != reservation.showId || it.sessionId != reservation.sessionId }) {
            logger.error("Reservation {} tickets belong to different show/session. Expected: {}/{}, Got: {}",
                reservation.id, reservation.showId, reservation.sessionId, ticketsResp.map { "${it.showId}/${it.sessionId}" })
            orderFailedHandler.onCreateOrderFailed(reservation.id) // Compensate
            throw OrderValidationException("Vé không thuộc suất diễn của đơn hàng")
        }
        // Vé theo lịch: service.ticket đã chặn đơn nhiều ngày lúc giữ chỗ, ở đây chỉ chốt ngày tham quan vào đơn.
        val visitDates = ticketsResp.map { if (it.hasCalendarDate()) it.calendarDate else null }.toSet()
        if (visitDates.size > 1) {
            logger.error("Reservation {} mixes visit dates {}", reservation.id, visitDates)
            orderFailedHandler.onCreateOrderFailed(reservation.id) // Compensate
            throw OrderValidationException("Mỗi đơn chỉ được chọn vé của một ngày")
        }
        val visitDate = visitDates.singleOrNull()
        val tickets = ticketsResp.map { res ->
            TicketInfo(
                id = res.id,
                sid = res.sid,
                seatType = SeatType.valueOf(res.seatType),
                priceCat = res.priceCat,
                quantity = reservation.detailsList.first { it.ticketId == res.id }.quantity, // Ensure this doesn't fail
                basePrice = res.basePrice,
                dname = if (res.hasDname()) res.dname else null,
                ticketSlots = if (res.hasTicketSlots()) res.ticketSlots else null,
            )
        }

        // --- Promotion Calculation ---
        val listPromotions = promotionDb.loadPromotions(session.showId, session.sessionId)
        val promoCalculatedList = mutableListOf<CalculatedPromotion>()
        var promotionChosen: CalculatedPromotion? = null

        for (promotion in listPromotions) {
            // Use the cache to get/create promotion instances
            var classInstancePromotion: CalculatePromotion? = calculatePromotionClasses.get(promotion.pluginName)
            if (classInstancePromotion == null) {
                try {
                    val clazz = Class.forName(promotion.pluginName)
                    val ctor: Constructor<*> = clazz.getConstructor()
                    classInstancePromotion = ctor.newInstance() as CalculatePromotion
                    calculatePromotionClasses.add(classInstancePromotion) // Add to cache
                } catch (ex: ClassNotFoundException) {
                    logger.error("Promotion class not found: {}", promotion.pluginName, ex)
                    orderFailedHandler.onCreateOrderFailed(reservation.id) // Compensate
                    throw OrderCreationException("Lỗi hệ thống: Không tìm thấy cấu hình khuyến mãi.")
                } catch (ex: Throwable) {
                    logger.error("Failed to instantiate promotion class: {}", promotion.pluginName, ex)
                    orderFailedHandler.onCreateOrderFailed(reservation.id) // Compensate
                    throw OrderCreationException("Lỗi hệ thống: Không thể khởi tạo cấu hình khuyến mãi.")
                }
            }

            val result = classInstancePromotion.calculatePromotionDiscount(
                CalculatePromotionInput(
                    session.showId,
                    session.sessionId,
                    configuration = promotion.configuration,
                    tickets = ticketsResp,
                    ticketsFromReservation = reservation.detailsList,
                    jsonMapper = jsonMapper,
                    promotionCode = input.promotionCode,
                    promotionCodeQuantityDBService = promotionCodeQuantityDb,
                    promotionComboQuantityDBService = promotionComboQuantityDb
                )
            )
            result?.let {
                promoCalculatedList.add(CalculatedPromotion(promotion.id, classInstancePromotion, it))
            }
        }

        // --- Apply Best Promotion ---
        var priceTotal = 0L
        val orderPromotionDetails = mutableListOf<OrderPromotionDetail>()
        if (promoCalculatedList.isEmpty()) {
            priceTotal = tickets.sumOf { it.basePrice * it.quantity } + show.shipPrice
        } else {
            // Find the best valid promotion
            promotionChosen = promoCalculatedList
                .filter { !it.promotionData.applyPromotionFailed } // Consider only valid ones
                .maxByOrNull { it.promotionData.priceDiscount } // Find max discount

            if (promotionChosen != null) {
                promotionChosen.plugin.applyPromotionDiscount(
                    ApplyPromotionInput(
                        promotionChosen.promotionData,
                        promotionCodeQuantityDb,
                        promotionComboQuantityDb
                    )
                )

                if (promotionChosen.promotionData.applyPromotionFailed) {
                    val errorMessage = promotionChosen.promotionData.details
                        .firstOrNull { it.error && it.errorMessage != null }?.errorMessage
                    logger.error("Failed to apply chosen promotion: ID={}, Error={}, Input={}", promotionChosen.promotionPluginId, errorMessage, input)
                    // Don't compensate here yet, let the order creation fail naturally below if DB save fails
                    // Or decide if this is a hard failure stopping order creation
                    // For now, let's treat it as a potential issue but proceed to save without applied promo effects if needed
                    // Revert if necessary in the final catch block
                    orderFailedHandler.onCreateOrderFailed(reservation.id) // Compensate immediately if applying fails
                    throw OrderCreationException(errorMessage ?: "Không thể áp dụng khuyến mãi đã chọn.")
                }

                priceTotal = promotionChosen.promotionData.priceTotal + show.shipPrice
                orderPromotionDetails.addAll(
                    promotionChosen.promotionData.details.map {
                        OrderPromotionDetail(
                            it.name,
                            it.priceDiscount,
                            it.promotionCodeQuantityId,
                            it.promotionComboQuantityId,
                            it.promotionComboQuantity
                        )
                    }
                )
            } else {
                // No valid promotion could be applied (e.g., all failed calculation/validation)
                logger.warn("No valid promotion could be applied for input: {}", input)
                priceTotal = tickets.sumOf { it.basePrice * it.quantity } + show.shipPrice
                promotionChosen = null // Ensure it's null if no valid one was chosen/applied
            }
        }


        // --- Create and Save Order Document ---
        val orderId = ObjectId().toHexString()
        isOrderProcessing(orderId) // Lock

        try {
            val orderDoc = OrderDocument(
                id = orderId,
                code = orderCode,
                customerUserId = input.customerUserId,
                reservationId = input.reservationId,
                showId = reservation.showId,
                sessionId = reservation.sessionId,
                tickets = tickets,
                customerName = input.customerName,
                customerPhone = input.customerPhone,
                customerEmail = input.customerEmail,
                customFields = input.customFields ?: emptyList(),
                promotionDetails = orderPromotionDetails,
                total = priceTotal,
                promotionPluginConfigId = promotionChosen?.promotionPluginId, // Use the chosen one
                visitDate = visitDate,
                status = if (session.paymentOffline || priceTotal == 0L) OrderStatus.PaymentPending else OrderStatus.Created
            ).apply {
                if (session.requireDelivery) {
                    // Already validated above, assign directly
                    delivery = input.deliveryInfo
                }
            }

            val createdOrder = orderDB.createOrder(orderDoc) ?: run {
                logger.error("Failed to save order to DB. Input: {}", input)
                // Revert promotion if one was applied
                promotionChosen?.plugin?.revertPromotionDiscount(
                    RevertPromotionInput(orderPromotionDetails, promotionCodeQuantityDb, promotionComboQuantityDb)
                )
                orderFailedHandler.onCreateOrderFailed(reservation.id) // Compensate reservation
                throw OrderCreationException("Không thể lưu đơn hàng vào hệ thống.")
            }

            // --- Post-Creation Steps (Notifications, Status Updates) ---
            handlePostOrderCreation(orderDoc, session.paymentOffline)

            logger.info("Order {} created successfully.", createdOrder)
            return orderDoc

        } catch (e: Throwable) {
            logger.error("Error during final order creation steps for Order ID (potential) {}: {}", orderId, e.message, e)
            // Revert promotion if one was applied and potentially saved
            promotionChosen?.plugin?.revertPromotionDiscount(
                RevertPromotionInput(orderPromotionDetails, promotionCodeQuantityDb, promotionComboQuantityDb)
            )
            // Ensure reservation status is handled
            orderFailedHandler.onCreateOrderFailed(reservation.id) // Use handler for consistency
            // Rethrow a specific exception
            throw OrderCreationException("Lỗi xảy ra trong quá trình tạo đơn hàng: ${e.message}", e)
        } finally {
            releaseOrderProcessing(orderId) // Unlock
        }
    }

    private suspend fun handlePostOrderCreation(orderDoc: OrderDocument, paymentOffline: Boolean) {
        if (paymentOffline) {
            logger.info("Order {} is offline payment, notifying reservation {}.", orderDoc.id, orderDoc.reservationId)
            try {
                reservationGateway.notifyOrderStatusChanged(orderDoc.reservationId, OrderStatus.PaymentPending)
                scheduleOrSendEmail(orderDoc)
            } catch (t: Throwable) {
                logger.error("Failed to notify reservation {} for offline payment order {}.", orderDoc.reservationId, orderDoc.id, t)
                // Decide if this failure should revert the order or just log/retry notification
                throw OrderCreationException("Không thể cập nhật trạng thái đặt vé cho đơn hàng offline: ${t.message}", t) // Make it critical
            }
        } else if (orderDoc.total == 0L) {
            logger.info("Order {} has zero total, processing as succeeded.", orderDoc.id)
            try {
                // Notify pending first (as per original logic flow)
                reservationGateway.notifyOrderStatusChanged(orderDoc.reservationId, OrderStatus.PaymentPending)

                // Update order status to Succeeded
                val updatedOrder = orderDB.updateStatus(orderDoc.id, OrderStatus.PaymentSucceed)
                    ?: run {
                        logger.error("Failed to update order {} status to PaymentSucceed for zero total.", orderDoc.id)
                        // Attempt to revert reservation status? Or mark order as failed?
                        reservationGateway.notifyOrderStatusChanged(orderDoc.reservationId, OrderStatus.Failed) // Try to revert
                        throw OrderCreationException("Không thể cập nhật trạng thái đơn hàng 0đ sang trạng thái thành công.")
                    }

                // Notify Succeeded
                reservationGateway.notifyOrderStatusChanged(updatedOrder.reservationId, OrderStatus.PaymentSucceed)
                logger.info("Order {} (zero total) processed successfully.", updatedOrder.id)

                orderSucceedProducer.scheduleOrSendOrderSucceedEvent(orderDoc)
                scheduleOrSendEmail(updatedOrder) // Send email for the succeeded order

            } catch (t: Throwable) {
                logger.error("Failed during zero total order processing for order {}.", orderDoc.id, t)
                // Attempt to mark order as failed?
                orderDB.updateStatus(orderDoc.id, OrderStatus.Failed)
                reservationGateway.notifyOrderStatusChanged(orderDoc.reservationId, OrderStatus.Failed) // Ensure reservation is failed
                throw OrderCreationException("Lỗi xử lý đơn hàng 0đ: ${t.message}", t)
            }
        }
        // For non-offline, non-zero total orders, the status is 'Created', handled by the caller (e.g., proceed to payment)
    }

    suspend fun executeCancelOrder(orderId: String) {
        if (isOrderProcessing(orderId)) {
            logger.info("order {} update to {} but another one is processing", orderId, OrderStatus.Canceled)
            throw OrderValidationException("Đơn hàng đang được xử lý")
        }

        try {
            val order = orderDB.loadOrderById(orderId)
                ?: throw ResourceNotFoundException("Không tìm thấy đơn hàng")

            val session = showCache.getShowSession(order.showId, order.sessionId) ?: run {
                logger.info("order {} update to {} but show session is not found", orderId, OrderStatus.Canceled)
                throw ResourceNotFoundException("Không tìm thấy sự kiện")
            }

            if (session.status == ShowSessionStatus.Terminated.name) {
                logger.info("order {} update to {} but show session is terminated", orderId, OrderStatus.Canceled)
                throw OrderValidationException("Sự kiện đã kết thúc")
            }

            try {
                reservationGateway.notifyOrderStatusChanged(order.reservationId, OrderStatus.Canceled)
            } catch (t: Throwable) {
                logger.error("reservationId {} failed to update to {} on ticket service... ", order.reservationId, TicketReservationStatus.OrderCancelled, t)
                throw OrderCreationException("Cập nhật trạng thái đơn hàng thất bại", t)
            }

            if(order.promotionPluginConfigId != null) {
                orderFailedHandler.revertPromotion(order.promotionPluginConfigId!!, order.promotionDetails)
            }

            orderDB.updateStatus(order.id, OrderStatus.Canceled) ?.let {
                launch { // eun async
                    val reqId = UUID.randomUUID()
                    val sendMail = notificationGateway.sendMailOrder(it, reqId)
                    if (!sendMail) {
                        logger.error("order {} failed to send mail", it.id)
                        val job = jobrunrGateway.resendEmailOrder(it, reqId)
                        if (!job) {
                            logger.error("order {} failed to schedule job send mail", it.id)
                            // TODO retry or customer will missing email
                        }
                    }
                }
            }

            logger.info("order {} canceled", order.id)
        } finally {
            releaseOrderProcessing(orderId)
        }
    }

    private suspend fun scheduleOrSendEmail(orderDoc: OrderDocument) {
        launch { // Run async
            val reqId = UUID.randomUUID()
            val jobScheduled = jobrunrGateway.resendEmailOrder(orderDoc, reqId)
            if (!jobScheduled) {
                logger.warn("Failed to schedule email job for order {}, attempting direct send.", orderDoc.id)
                val sent = notificationGateway.sendMailOrder(orderDoc, reqId)
                if (!sent) {
                    logger.error("CRITICAL: Failed to schedule AND send email for order {}. Req ID: {}", orderDoc.id, reqId)
                    // TODO: Implement more robust retry/alerting mechanism
                }
            } else {
                logger.info("Scheduled email job for order {}. Req ID: {}", orderDoc.id, reqId)
            }
        }
    }
}