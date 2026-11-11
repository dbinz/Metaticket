package metaticket.order.controller

import com.fasterxml.jackson.databind.ObjectMapper
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.plugins.requestvalidation.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import metaticket.common.checksum.Checksum
import metaticket.common.coroutine.launch
import metaticket.common.logger.Logging
import metaticket.order.CreateOrderInput
import metaticket.order.OrderCreationService
import metaticket.order.cache.CalculatePromotionClassesCache
import metaticket.order.cache.ShowCache
import metaticket.order.calculate.promotion.*
import metaticket.order.configuration.Environment
import metaticket.order.database.service.*
import metaticket.order.datastructure.OrderStatus
import metaticket.order.exception.OrderCreationException
import metaticket.order.exception.OrderValidationException
import metaticket.order.exception.ResourceNotFoundException
import metaticket.order.gateway.*
import metaticket.order.pocessing.isOrderProcessing
import metaticket.order.pocessing.releaseOrderProcessing
import metaticket.order.pojo.db.CustomFieldTmpl
import metaticket.order.pojo.db.OrderCustomFieldMapping
import metaticket.order.pojo.db.OrderDocument
import metaticket.order.pojo.request.*
import metaticket.order.pojo.response.TentativePriceResponse
import metaticket.order.pojo.response.toResponse
import metaticket.order.request.*
import metaticket.order.utils.ValidationUtil
import metaticket.order.utils.buildOrderSummary
import metaticket.order.validation.OrderValidationCustomField
import metaticket.payment.datastructure.PaymentType
import metaticket.show.datastructure.ShowSessionStatus
import metaticket.ticket.datastructure.TicketReservationStatus
import java.lang.reflect.Constructor
import java.util.*


class OrderController(
    private val showCache: ShowCache,
    private val orderDB: OrderDBService,
    private val customFieldDB: OrderCustomFieldMappingDBService,
    private val promotionDb: PromotionDBService,
    private val promotionCodeQuantityDb: PromotionCodeQuantityDBService,
    private val promotionComboQuantityDb: PromotionComboQuantityDBService,
    private val jsonMapper: ObjectMapper,
    private val paymentGateway: PaymentGateway,
    private val orderDataCollectorGateway: OrderDataCollectorGateway,
    private val orderFailedHandler: OrderFailedHandler,
    private val notificationGateway: NotificationGateway,
    private val jobrunrGateway: JobrunrGateway,
    private val ticketGateway: TicketGateway,
    private val reservationGateway: ReservationGateway,
    private val calculatePromotionClasses: CalculatePromotionClassesCache,
    private val orderCreationService: OrderCreationService,
) : Logging {

    suspend fun updateCustomFieldMapping(call: ApplicationCall) {
        val req = call.receive<UpdateOrderCustomFieldsRequest>()
        val mapping = customFieldDB.getCustomFieldMappingByShowAndSession(req.showId, req.sessionId)
        val result: OrderCustomFieldMapping?
        if(mapping == null) {
            result = OrderCustomFieldMapping(showId = req.showId, sessionId = req.sessionId, customFields = req.customFields)
            customFieldDB.createCustomFieldMapping(result)
                ?: return call.respond(HttpStatusCode.BadRequest, "Cập nhật thông tin bổ sung thất bại")
        } else {
            result = customFieldDB.updateCustomFieldMapping(req.showId, req.sessionId, req.customFields)
                ?: return call.respond(HttpStatusCode.BadRequest, "Cập nhật thông tin bổ sung thất bại")
        }

        return call.respond(HttpStatusCode.OK, result.customFields)
    }

    suspend fun updatePromotion(call: ApplicationCall) {
        val reqData = call.receive<UpdateOrderPromotionRequest>()
        var classInstancePromotion: CalculatePromotion? = calculatePromotionClasses.get(reqData.pluginName)

        if(classInstancePromotion == null) {
            try {
                val clazz = Class.forName(reqData.pluginName)
                val ctor: Constructor<*> = clazz.getConstructor()

                classInstancePromotion = ctor.newInstance() as CalculatePromotion

                calculatePromotionClasses.add(classInstancePromotion)
            } catch (ex: ClassNotFoundException) {
                logger.error("failed to create promotion cause not found promotion class... ", ex)
                return call.respond(HttpStatusCode.BadRequest, "Không tìm thấy khuyến mãi")
            }

        }

        val result = classInstancePromotion.createPromotion(CreatePromotionInput(
            data = reqData.data,
            showId = reqData.showId,
            sessionId = reqData.sessionId,
            promotionDBService = promotionDb,
            promotionComboQuantityDBService = promotionComboQuantityDb,
            promotionCodeQuantityDBService = promotionCodeQuantityDb,
            jsonMapper = jsonMapper,
        ))
        if(reqData.promotionId != null) {
            classInstancePromotion.deletePromotion(
                DeletePromotionInput(
                    promotionId = reqData.promotionId!!,
                    promotionDBService = promotionDb
                )
            )
        }




        if(result) {
            return call.respond(HttpStatusCode.OK,)
        }
        return call.respond(HttpStatusCode.BadRequest, "Cập nhật khuyến mãi thất bại")

    }

    suspend fun fetchCustomFieldTemplateByShowAndSession(call: ApplicationCall) {
        val req = call.receive<LoadCustomFieldRequest>()
        val mapping = customFieldDB.getCustomFieldMappingByShowAndSession(req.showId, req.sessionId) ?: run {
            return call.respond(HttpStatusCode.OK, emptyList<CustomFieldTmpl>())
        }
        call.respond(HttpStatusCode.OK, mapping.customFields)
    }

    suspend fun cancelOrder(call: ApplicationCall) {
        val opUserId = call.request.headers.get("X-OP-User-Id") ?: kotlin.run {
            // No user IDs available yet for this log message
            logger.error("Get op user profile but missing user id in header, maybe user haven't logged in")
            return call.respond(HttpStatusCode.Unauthorized, "Không xác định người dùng")
        }

        val reqData = call.receive<OrderIdOnlyRequest>()

        try {
            orderCreationService.executeCancelOrder(reqData.orderId)
            call.respond(HttpStatusCode.OK)
        } catch (e: OrderValidationException) {
            call.respond(HttpStatusCode.BadRequest, e.message ?: "Yêu cầu không hợp lệ")
        } catch (e: ResourceNotFoundException) {
            call.respond(HttpStatusCode.NotFound, e.message ?: "Không tìm thấy tài nguyên")
        } catch (e: OrderCreationException) {
            call.respond(HttpStatusCode.InternalServerError, e.message ?: "Lỗi hệ thống")
        } catch (e: Exception) { // Catch forbidden (ShowSessionStatus.Terminated check logic in service might throw ValidationException or we need specific handling if strict status mapping needed, but ValidationException covers 'Bad Request' semantic which fits 'Event Terminated' context for client action)
            // If strictly mapping Forbidden:
             if (e.message == "Sự kiện đã kết thúc") {
                 call.respond(HttpStatusCode.Forbidden, e.message!!)
             } else {
                 call.respond(HttpStatusCode.InternalServerError, "Lỗi hệ thống")
             }
        }
    }

    suspend fun createOrder(call: ApplicationCall) {
        val userId = call.request.headers["X-Customer-User-Id"] ?: run {
            logger.error("Missing X-Customer-User-Id header")
            return call.respond(HttpStatusCode.Unauthorized, "Không xác định người dùng")
        }

        val reqData = call.receive<CreateOrderRequest>() // Receive the HTTP request DTO

        // Map HTTP Request DTO to the common Input DTO
        val createOrderInput = CreateOrderInput(
            reservationId = reqData.reservationId,
            customerUserId = userId,
            customerName = reqData.name,
            customerPhone = reqData.phone,
            customerEmail = reqData.email,
            deliveryInfo = reqData.delivery, // Assuming CreateOrderRequest has DeliveryInfo?
            customFields = reqData.customFields, // Assuming CreateOrderRequest has List<CustomFieldData>?
            promotionCode = reqData.promotionCode
        )

        try {
            val orderDoc = orderCreationService.executeCreateOrder(createOrderInput)

            // Respond based on the result (status is handled inside executeCreateOrder for 0/offline)
            logger.info("Order {} created via HTTP request.", orderDoc.id)
            call.respond(orderDoc.toResponse()) // Convert OrderDocument to HTTP response DTO

        } catch (e: ResourceNotFoundException) {
            logger.warn("Resource not found during order creation: {}", e.message)
            call.respond(HttpStatusCode.NotFound, e.message ?: "Yêu cầu đặt vé không tồn tại")
        } catch (e: OrderValidationException) {
            logger.warn("Validation failed during order creation: {}", e.message)
            call.respond(HttpStatusCode.BadRequest, e.message ?: "Dữ liệu không hợp lệ")
        } catch (e: OrderCreationException) {
            logger.error("Order creation failed: {}", e.message, e.cause)
            call.respond(HttpStatusCode.InternalServerError, e.message ?: "Tạo đơn hàng thất bại")
        } catch (e: Throwable) {
            // Catch unexpected errors
            logger.error("Unexpected error during order creation: {}", e.message, e)
            // Ensure compensation if possible (though OrderCreationService should handle its own)
            orderFailedHandler.onCreateOrderFailed(createOrderInput.reservationId)
            call.respond(HttpStatusCode.InternalServerError, "Lỗi hệ thống không mong muốn khi tạo đơn hàng.")
        }
    }

    suspend fun updateOrderInfo(call: ApplicationCall) {
        val reqData = call.receive<UpdateOrderInfoRequest>()
        val orderId = Checksum.decode(reqData.orderId, Environment.secretKey)
        val order = orderDB.loadOrderById(orderId) ?: run {
            logger.error("order {} không tồn tại", orderId)
            return call.respond(HttpStatusCode.NotFound, "Không tìm thấy đơn hàng")
        }

        val session = showCache.getShowSession(order.showId, order.sessionId) ?: run {
            logger.error("order {} failed to update info cause not found show session {}", order.id, order.sessionId)
            return call.respond(HttpStatusCode.NotFound, "Sự kiện không tồn tại")
        }

        if (session.requireDelivery) {
            val validDelivery = if (reqData.delivery == null) ValidationResult.Invalid("Thiếu dữ liệu người nhận vé")
            else reqData.delivery!!.let {
                if (it.deliveryName.isBlank()) ValidationResult.Invalid("Tên người nhận không được để trống")
                else if (!ValidationUtil.isPhoneValid(it.deliveryPhone)) ValidationResult.Invalid("Số điện thoại người nhận không chính xác")
                else if (it.provinceName.isBlank()) ValidationResult.Invalid("Tên tỉnh/thành không được để trống")
                else if (it.districtName.isBlank()) ValidationResult.Invalid("Tên quận không được để trống")
                else if (it.wardName.isBlank()) ValidationResult.Invalid("Tên phường không được để trống")
                else if (it.address.isBlank()) ValidationResult.Invalid("Địa chỉ không được để trống")
                else ValidationResult.Valid
            }

            if (validDelivery !is ValidationResult.Valid) {
                logger.error("order {} failed to update info cause invalid delivery info {}", order.id, reqData)
                return call.respond(HttpStatusCode.BadRequest, validDelivery)
            }
        }

        val mapping = customFieldDB.getCustomFieldMappingByShowAndSession(session.showId, session.sessionId)
        if (mapping != null) {
            if (mapping.customFields.isNotEmpty()) {

                if (reqData.customFields.isNullOrEmpty()) {
                    logger.error("order {} failed to update info cause invalid size custom field {}", order.id, reqData)
                    return call.respond(HttpStatusCode.BadRequest, "Thiếu dữ liệu điền thông tin bổ sung")
                }
                val orderValidationCustomField = OrderValidationCustomField()
                mapping.customFields.forEach { tmpl ->
                    val data = reqData.customFields!!.firstOrNull { it.id == tmpl.id }

                    if (!orderValidationCustomField.isCustomFieldValid(tmpl, data?.value, jsonMapper, orderDataCollectorGateway)) {
                        logger.error("order {} failed to update info cause custom field {} invalid data {}", order.id, tmpl.id, data)
                        return call.respond(HttpStatusCode.BadRequest, "Thông tin bổ sung không đúng")
                    }
                }
            }
        }

        val orderUpdated = orderDB.updateOrderInfo(reqData.orderId, reqData) ?: run {
            logger.error("order {}, failed to update to db of request {}", reqData.orderId, reqData)
            return call.respond(HttpStatusCode.BadRequest,"Cập nhật thông tin đơn hàng thất bại")
        }

        logger.info("order updated {}", orderUpdated)
        call.respond(HttpStatusCode.OK, orderUpdated.toResponse())
    }

    suspend fun getOrderById(call: ApplicationCall) {
        val id = call.parameters["id"]?.let {
            Checksum.decode(it, Environment.secretKey)
        } ?: return call.respond(HttpStatusCode.BadRequest, "Missing or malformed id")

        val order = orderDB.loadOrderById(id)
            ?: return call.respond(HttpStatusCode.NotFound, "Không tìm thấy đơn hàng")

        call.respond(order.toResponse())
    }

    suspend fun getOrderByShowAndSession(call: ApplicationCall) {
        val req = call.receive<LoadOrderByShowAndSessionRequest>()

        val show = showCache.getShow(req.showId) ?: run {
            logger.error("fail to fetch offline payment order, cannot get show {}", req.showId)
            return call.respond(HttpStatusCode.NotFound, "Không tìm thấy sự kiện")
        }

        val orderSummaries = orderDB.loadOrderByShowAndSession(req.showId, req.sessionId)
            .mapNotNull {order: OrderDocument ->
                val session = show.sessionsList.firstOrNull { req.sessionId == null || it.sessionId == order.sessionId } ?: run {
                    logger.error("fail to fetch order, cannot get show session {}", req.sessionId)
                    return call.respond(HttpStatusCode.NotFound,"Không tìm thấy phiên bản sự kiện")
                }
                buildOrderSummary(paymentGateway, show, session, order) ?: run {
                    logger.error("order {}, cannot build summary", order.id)
                    null
                }
            }

        call.respond(HttpStatusCode.OK, orderSummaries)
    }

    suspend fun getOfflinePaymentOrder(call: ApplicationCall) {
        val req = call.receive<LoadOrderByShowAndSessionRequest>()

        val show = showCache.getShow(req.showId) ?: run {
            logger.error("fail to fetch offline payment order, cannot get show {}", req.showId)
            return call.respond(HttpStatusCode.NotFound,"Không tìm thấy sự kiện")
        }

        val orderSummaries = orderDB.loadOrderByShowAndOrderStatus(req.showId, OrderStatus.PaymentPending)
            .mapNotNull {order: OrderDocument ->
                val session = show.sessionsList.firstOrNull { req.sessionId == null || it.sessionId == order.sessionId } ?: run {
                    logger.error("fail to fetch offline payment, cannot get show session {}", req.sessionId)
                    return call.respond(HttpStatusCode.NotFound,"Không tìm thấy phiên bản sự kiện")
                }
                buildOrderSummary(paymentGateway, show, session, order) ?: run {
                    logger.error("order {}, cannot build summary", order.id)
                    null
                }
            }

        call.respond(HttpStatusCode.OK, orderSummaries)
    }

    suspend fun getSummaryOrderById(call: ApplicationCall) {
        val id = call.parameters["id"]?.let {
            Checksum.decode(it, Environment.secretKey)
        } ?: return call.respond(HttpStatusCode.BadRequest, "Thiếu thông tin request")

        val order = orderDB.loadOrderById(id)
            ?: return call.respond(HttpStatusCode.NotFound, "Không tìm thấy đơn hàng")

        val show = showCache.getShow(order.showId) ?: run {
            logger.error("order {}, fail to build summary, cannot get show", order.id)
            return call.respond(HttpStatusCode.NotFound, "Không tìm thấy sự kiện")
        }
        val session = show.sessionsList.firstOrNull { it.sessionId == order.sessionId } ?: run {
            logger.error("order {}, fail to build summary, cannot get show session", order.sessionId)
            return call.respond(HttpStatusCode.NotFound, "Không tìm thấy sự kiện")
        }

        val orderSummary = buildOrderSummary(paymentGateway, show, session, order) ?: run {
            logger.error("order {}, cannot build summary", order.id)
            return call.respond(HttpStatusCode.InternalServerError,  "Lấy thông tin đơn hàng thất bại")
        }

        call.respond(HttpStatusCode.OK, orderSummary)
    }

    suspend fun createPayment(call: ApplicationCall) {
        val input = call.receive<OrderIdOnlyRequest>()

        val order = orderDB.loadOrderById(input.orderId)?: run {
            logger.error("order {}, failed create payment cause not found order", input.orderId)
            return call.respond(HttpStatusCode.NotFound,"Không tìm thấy đơn hàng, vui lòng đặt vé lại")
        }

        if (order.status != OrderStatus.Created) {
            logger.error("order {}, failed create payment cause order is {}", input.orderId, order.status)
            return call.respond(HttpStatusCode.PreconditionFailed,"Đơn hàng bị lỗi, vui lòng đặt vé lại")
        }

        val reservation = reservationGateway.getReservation(order.reservationId) ?: run {
            logger.error("order {}, failed create payment cause not found reservation {}", input.orderId, order.reservationId)
            return call.respond(HttpStatusCode.NotFound,"Không tìm thấy thông tin đặt vé, vui lòng đặt lại")
        }

        val ticketIds = reservation.detailsList.map { it.ticketId }
        val tickets = ticketGateway.getTicketsByIds(ticketIds)

        if (tickets.size < ticketIds.size) {
            logger.error("order {}, failed to create payment cause not enough ticket {} {}", input.orderId, ticketIds, tickets)
            return call.respond(HttpStatusCode.InternalServerError,"Đặt vé bị lỗi, vui lòng đặt vé lại")
        }

        val session = showCache.getShowSession(order.showId, order.sessionId) ?: run {
            logger.error("order {}, fail to build summary, cannot get show session", order.sessionId)
            return call.respond(HttpStatusCode.ServiceUnavailable,"Lỗi hệ thống")
        }

        if (session.status != ShowSessionStatus.Open.name) {
            logger.error(
                "failed create payment cause show {} or session {} is not open",
                reservation.showId, reservation.sessionId
            )
            orderFailedHandler.onCreateOrderFailed(reservation.id)
            return call.respond(HttpStatusCode.Forbidden,"Sự kiện đang đóng")
        }

        val paymentMethods = session.paymentMethodsList.map { PaymentType.valueOf(it) }
        val paymentId = try {
            val pgAccountCode = if (session.hasPgAccountCode()) session.pgAccountCode else null
            paymentGateway.createPayment(order.id, order.code, order.total, paymentMethods, pgAccountCode)
        } catch (t: Throwable) {
            logger.error("order {}, failed to call payment service ... ", order.id, t)
            orderFailedHandler.onOrderFailed(reservation.id, order.id)
            return call.respond(HttpStatusCode.InternalServerError,"Đặt vé bị lỗi, vui lòng đặt vé lại")
        }
        orderDB.setPaymentId(order.id, paymentId) ?: run {
            logger.error("order {}, cannot set payment id {} to db", paymentId, order.id)
            orderFailedHandler.onOrderFailed(reservation.id, order.id)
            return call.respond(HttpStatusCode.InternalServerError,"Đặt vé bị lỗi, vui lòng đặt vé lại")
        }

        logger.info("order {}, created payment {}", order.id, paymentId)
        call.respond(HttpStatusCode.OK, Checksum.encode(paymentId, Environment.secretKey))
    }

    suspend fun loadOrderByReservation(call: ApplicationCall) {
        val rq = call.receive<LoadOrderByReservationRequest>()
        val show = showCache.getShow(rq.showId) ?: run {
            logger.error("fail to build summary, cannot get show for request {}", rq)
            return call.respond(HttpStatusCode.NotFound,"Không tìm thấy sự kiện")
        }
        val session = show.sessionsList.firstOrNull { it.sessionId == rq.sessionId } ?: run {
            logger.error("fail to build summary, cannot get show session for request {}", rq.sessionId)
            return call.respond(HttpStatusCode.InternalServerError,"Không tìm thấy phiên bản sự kiện")
        }
        val orders = orderDB.loadOrderByReservationIds(rq.reservationIds)
        val summaries = orders.map { order ->
            buildOrderSummary(paymentGateway, show, session, order) ?: run {
                logger.error("order {}, cannot build summary", order.id)
                return call.respond(HttpStatusCode.InternalServerError,"Lấy thông tin đơn hàng thất bại")
            }
        }

        return call.respond(HttpStatusCode.OK, summaries)
    }

    suspend fun calculateTentativePrice(call: ApplicationCall) {
        val request = call.receive<CalculateTentativePriceRequest>().apply {
            this.reservationId?.let { Checksum.encode(it, Environment.secretKey) }
        }
        val ticketDetails = request.tickets

        if (ticketDetails.isEmpty()) return call.respond(HttpStatusCode.BadRequest, "Thiếu thông tin vé")

        val ticketIds = ticketDetails.map { it.ticketId }
        val tickets = ticketGateway.getTicketsByIds(ticketIds)

        if (tickets.isEmpty()) return call.respond(HttpStatusCode.BadRequest, "Thiếu thông tin vé")

        val show = showCache.getShow(tickets[0].showId) ?: run {
            return call.respond(HttpStatusCode.NotFound, "Không tìm thấy sự kiện")
        }

        val listPromotions = promotionDb.loadPromotions(request.showId, request.sessionId)
        val promoCalculatedList = mutableListOf<PromotionData>()
        loopPromotion@for(promotion in listPromotions) {
            var classInstancePromotion: CalculatePromotion? = calculatePromotionClasses.get(promotion.pluginName)

            if(classInstancePromotion == null) {
                try {
                    val clazz = Class.forName(promotion.pluginName)
                    val ctor: Constructor<*> = clazz.getConstructor()

                    classInstancePromotion = ctor.newInstance() as CalculatePromotion

                    calculatePromotionClasses.add(classInstancePromotion)
                } catch (ex: ClassNotFoundException) {
                    logger.error("not found plugin class... ", ex)
                    return call.respond(HttpStatusCode.BadRequest, "Không tìm thấy plugin")
                }
            }
            val result = classInstancePromotion.calculatePromotionDiscount(
                CalculatePromotionInput(
                    show.showId,
                    request.sessionId,
                    configuration = promotion.configuration,
                    tickets = tickets,
                    jsonMapper = jsonMapper,
                    tentativeTickets = request.tickets,
                    promotionCode = request.promotionCode,
                    promotionCodeQuantityDBService = promotionCodeQuantityDb,
                    promotionComboQuantityDBService = promotionComboQuantityDb
                )
            )
            result?.let {
                promoCalculatedList.add(it)
            }
        }

        if(promoCalculatedList.isEmpty()) {
            var priceVision = 0L

            for(ticketRes in request.tickets) {
                for(ticket in tickets) {
                    if(ticket.id == ticketRes.ticketId) {
                        priceVision += ticket.basePrice * ticketRes.quantity
                    }
                }
            }

            call.respond(HttpStatusCode.OK, TentativePriceResponse(
                    promotionName = "",
                    priceDiscount = 0,
                    shipPrice = show.shipPrice,
                    priceTotal = priceVision + show.shipPrice,
                    priceOriginal = priceVision + show.shipPrice,
                    promotionDetails = listOf()
            )
            )
        } else {
            var promoChosen = promoCalculatedList[0]
            for(item in promoCalculatedList) {
                if(promoChosen.priceDiscount < item.priceDiscount) {
                    promoChosen = item
                }
            }

            call.respond(
                HttpStatusCode.OK, TentativePriceResponse(
                    promotionName = "",
                    priceDiscount = promoChosen.priceDiscount,
                    shipPrice = show.shipPrice,
                    priceTotal = promoChosen.priceTotal + show.shipPrice,
                    priceOriginal = promoChosen.priceOriginal + show.shipPrice,
                    promotionDetails = promoChosen.details
                )
            )
        }
    }

    suspend fun reloadPlugin(call: ApplicationCall) {
        val className = call.parameters["className"]
            ?: return call.respond(HttpStatusCode.BadRequest, "Thiếu thông tin plugin")

        val clazz = Class.forName(className)
        val ctor: Constructor<*> = clazz.getConstructor()

        val instance = ctor.newInstance() as CalculatePromotion
        calculatePromotionClasses.add(instance)

        return call.respond(HttpStatusCode.OK)
    }
}
