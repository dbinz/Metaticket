package metaticket.order.service

import com.fasterxml.jackson.databind.ObjectMapper
import com.google.protobuf.Empty
import com.google.protobuf.StringValue
import io.grpc.Status
import io.grpc.StatusException
import metaticket.common.logger.Logging
import metaticket.order.CreateOrderInput
import metaticket.order.OrderCreationService
import metaticket.order.cache.CalculatePromotionClassesCache
import metaticket.order.calculate.promotion.CalculatePromotion
import metaticket.order.calculate.promotion.CreatePromotionInput
import metaticket.order.controller.OrderFailedHandler
import metaticket.order.database.service.*
import metaticket.order.datastructure.CustomFieldType
import metaticket.order.datastructure.OrderStatus
import metaticket.order.exception.OrderCreationException
import metaticket.order.exception.OrderValidationException
import metaticket.order.exception.ResourceNotFoundException
import metaticket.order.notification.grpc.OrderMessage
import metaticket.order.notification.grpc.OrderServiceGrpcKt
import metaticket.order.pojo.db.CustomFieldData
import metaticket.order.pojo.db.CustomFieldTmpl
import metaticket.order.pojo.db.DeliveryInfo
import metaticket.order.pojo.db.OrderCustomFieldMapping
import java.lang.reflect.Constructor

class OrderServiceImpl(
    private val orderDB: OrderDBService,
    private val orderFailedHandler: OrderFailedHandler,
    private val orderCreationService: OrderCreationService,
    private val customFieldDB: OrderCustomFieldMappingDBService,
    private val calculatePromotionClasses: CalculatePromotionClassesCache,
    private val promotionDb: PromotionDBService,
    private val promotionComboQuantityDb: PromotionComboQuantityDBService,
    private val promotionCodeQuantityDb: PromotionCodeQuantityDBService,
    private val jsonMapper: ObjectMapper,
): OrderServiceGrpcKt.OrderServiceCoroutineImplBase(), Logging {
    override suspend fun getOrderById(request: OrderMessage.GetOrderByIdRequest): OrderMessage.OrderProto {
        return try {
            orderDB.loadOrderById(request.orderId)?.let { doc ->
                OrderMessage.OrderProto.newBuilder()
                    .setId(doc.id)
                    .setReservationId(doc.reservationId)
                    .setCode(doc.code)
                    .setStatus(doc.status.name)
                    .setShowId(doc.showId)
                    .setSessionId(doc.sessionId)
                    .setTotal(doc.total)
                    .setCustomerPhone(doc.customerPhone)
                    .setCustomerName(doc.customerName)
                    .setCustomerEmail(doc.customerEmail)
                    .addAllPromotionDetails(doc.promotionDetails.map {
                        OrderMessage.PromotionProto.newBuilder()
                            .setName(it.name)
                            .setPriceDiscount(it.priceDiscount)
                            .build()
                    })
                    .apply {
                        doc.paymentId?.let { paymentId ->
                            this.setPaymentId(paymentId)
                        }
                        doc.delivery?.let {
                            this.setDelivery(OrderMessage.DeliveryProto.newBuilder()
                                .setDeliveryName(it.deliveryName)
                                .setDeliveryPhone(it.deliveryPhone)
                                .setProvinceName(it.provinceName)
                                .setDistrictName(it.districtName)
                                .setWardName(it.wardName)
                                .setAddress(it.address)
                                .build())
                        }
                    }
                    .build()
            } ?: run {
                logger.warn("not found order {}", request.orderId)
                throw StatusException(Status.NOT_FOUND.withDescription("not found order"))
            }
        } catch (t: Throwable) {
            logger.error("get order by id failed", t)
            throw StatusException(Status.INTERNAL.withDescription("get order by id failed"))
        }
    }

    override suspend fun getOrderStatus(request: OrderMessage.GetOrderByIdRequest): OrderMessage.GetOrderStatusResponse {
        return try {
            orderDB.loadOrderById(request.orderId)?.let { doc ->
                OrderMessage.GetOrderStatusResponse.newBuilder()
                    .setStatus(doc.status.name)
                    .build()
            } ?: run {
                logger.warn("not found order {}", request.orderId)
                throw StatusException(Status.NOT_FOUND.withDescription("not found order"))
            }
        } catch (t: Throwable) {
            logger.error("get order status failed", t)
            throw StatusException(Status.INTERNAL.withDescription("get order status failed"))
        }
    }

    override suspend fun createOrder(request: OrderMessage.CreateOrderRequest): StringValue {
        // Map gRPC Request DTO to the common Input DTO
        val deliveryInfo = if (request.hasDelivery()) {
            DeliveryInfo(
                deliveryName = request.delivery.deliveryName,
                deliveryPhone = request.delivery.deliveryPhone,
                provinceName = request.delivery.provinceName,
                districtName = request.delivery.districtName,
                wardName = request.delivery.wardName,
                address = request.delivery.address
            )
        } else null

        val customFieldsData = request.customFieldsList?.map {
            CustomFieldData(id = it.id, name = it.name, value = it.value)
        }

        val createOrderInput = CreateOrderInput(
            reservationId = request.reservationId,
            customerUserId = request.customerUserId, // Use customerUserId from gRPC request
            customerName = request.name,
            customerPhone = request.phone,
            customerEmail = request.email,
            deliveryInfo = deliveryInfo,
            customFields = customFieldsData,
            promotionCode = if (request.hasPromotionCode()) request.promotionCode else null
        )

        try {
            val orderDoc = orderCreationService.executeCreateOrder(createOrderInput)

            // Respond with the order ID
            logger.info("Order {} created via gRPC request.", orderDoc.id)
            return StringValue.of(orderDoc.id)

        } catch (e: ResourceNotFoundException) {
            logger.warn("[gRPC] Resource not found during order creation: {}", e.message)
            throw StatusException(Status.NOT_FOUND.withDescription(e.message ?: "Tài nguyên không tồn tại"))
        } catch (e: OrderValidationException) {
            logger.warn("[gRPC] Validation failed during order creation: {}", e.message)
            // Use FAILED_PRECONDITION or INVALID_ARGUMENT based on context
            throw StatusException(Status.FAILED_PRECONDITION.withDescription(e.message ?: "Dữ liệu không hợp lệ"))
        } catch (e: OrderCreationException) {
            logger.error("[gRPC] Order creation failed: {}", e.message, e.cause)
            throw StatusException(Status.INTERNAL.withDescription(e.message ?: "Tạo đơn hàng thất bại"))
        } catch (e: Throwable) {
            // Catch unexpected errors
            logger.error("[gRPC] Unexpected error during order creation: {}", e.message, e)
            // Ensure compensation if possible
            orderFailedHandler.onCreateOrderFailed(createOrderInput.reservationId)
            throw StatusException(Status.INTERNAL.withDescription("Lỗi hệ thống không mong muốn khi tạo đơn hàng."))
        }
    }

    override suspend fun createOrderCustomFieldsMapping(request: OrderMessage.CreateOrderCustomFieldsMappingRequest): OrderMessage.CreateOrderCustomFieldsMappingResponse {
        val mapping = customFieldDB.getCustomFieldMappingByShowAndSession(request.showId, request.sessionId)

        if(mapping == null) {
            val entity = OrderCustomFieldMapping(
                showId = request.showId,
                sessionId = request.sessionId,
                customFields = request.customFieldsList.map { CustomFieldTmpl(id = it.id, type = CustomFieldType.valueOf(it.type), displayName = it.displayName, constraints = it.constraints, note = it.note) }
            )

            customFieldDB.createCustomFieldMapping(entity) ?: run {
                logger.error("Create custom field mapping failed {}", entity)
                throw StatusException(Status.INTERNAL.withDescription("Tạo thông tin bổ sung thất bại"))
            }

            logger.info("Create custom field mapping success {}", entity)
            return OrderMessage.CreateOrderCustomFieldsMappingResponse.newBuilder().addAllCustomFields(request.customFieldsList).build()
        } else {
            val result = customFieldDB.updateCustomFieldMapping(request.showId, request.sessionId, request.customFieldsList.map { CustomFieldTmpl(id = it.id, type = CustomFieldType.valueOf(it.type), displayName = it.displayName, constraints = it.constraints, note = it.note) })
                ?: run {
                    logger.error("Create custom field mapping failed {}", request)
                    throw StatusException(Status.INTERNAL.withDescription("Tạo thông tin bổ sung thất bại"))
                }
            logger.info("Create custom field mapping success {}", result)
            return OrderMessage.CreateOrderCustomFieldsMappingResponse.newBuilder().addAllCustomFields(request.customFieldsList).build()
        }
    }

    override suspend fun createOrderPromotion(request: OrderMessage.CreateOrderPromotionRequest): Empty {
        var classInstancePromotion: CalculatePromotion? = calculatePromotionClasses.get(request.pluginName)

        if(classInstancePromotion == null) {
            try {
                val clazz = Class.forName(request.pluginName)
                val ctor: Constructor<*> = clazz.getConstructor()

                classInstancePromotion = ctor.newInstance() as CalculatePromotion

                calculatePromotionClasses.add(classInstancePromotion)
            } catch (ex: ClassNotFoundException) {
                logger.error("failed to create promotion cause not found promotion class... ", ex)
                throw StatusException(Status.NOT_FOUND.withDescription("Không tìm thấy khuyến mãi"))
            }

        }

        val result = classInstancePromotion.createPromotion(CreatePromotionInput(
            data = request.data,
            showId = request.showId,
            sessionId = request.sessionId,
            promotionDBService = promotionDb,
            promotionComboQuantityDBService = promotionComboQuantityDb,
            promotionCodeQuantityDBService = promotionCodeQuantityDb,
            jsonMapper = jsonMapper,
        ))

        if(!result) {
            throw StatusException(Status.INTERNAL.withDescription("Tạo khuyến mãi thất bại"))
        }

        return Empty.getDefaultInstance()
    }

    override suspend fun getReport(request: OrderMessage.GetReportRequest): OrderMessage.GetReportResponse {
        val status = request.statusList?.map { OrderStatus.valueOf(it) }
        val orders = orderDB.loadOrder(request.startDate, request.endDate, status, request.showId, request.sessionId)
        return OrderMessage.GetReportResponse.newBuilder().addAllOrders(orders.map {
            OrderMessage.OrderProto.newBuilder().apply {
                setId(it.id)
                setReservationId(it.reservationId)
                setCode(it.code)
                setStatus(it.status.name)
                setShowId(it.showId)
                setSessionId(it.sessionId)
                setPaymentId(it.paymentId)
                setCustomerName(it.customerName)
                setCustomerPhone(it.customerPhone)
                setCustomerEmail(it.customerEmail)
                delivery = OrderMessage.DeliveryProto.newBuilder().apply {
                    setDeliveryName(it.delivery?.deliveryName ?: "")
                    setDeliveryPhone(it.delivery?.deliveryPhone ?: "")
                    setProvinceName(it.delivery?.provinceName ?: "")
                    setDistrictName(it.delivery?.districtName ?: "")
                    setWardName(it.delivery?.wardName ?: "")
                    setAddress(it.delivery?.address ?: "")
                }.build()
                it.customFields?.map { field ->
                    addCustomFields(OrderMessage.CustomFieldProto.newBuilder().apply {
                        setId(field.id)
                        setName(field.name)
                        field.value?.let { setValue(field.value) }
                    }.build()
                    )
                }
                it.promotionDetails.map { promotion ->
                    addPromotionDetails(OrderMessage.PromotionProto.newBuilder().apply {
                        setName(promotion.name)
                        setPriceDiscount(promotion.priceDiscount)
                    }.build())
                }
                setTotal(it.total)
                setCreatedAt(it.createdAt.time)
            }.build()
        }).build()
    }

    override suspend fun cancelOrder(request: OrderMessage.GetOrderByIdRequest): Empty {
        try {
            orderCreationService.executeCancelOrder(request.orderId)
            return Empty.getDefaultInstance()
        } catch (e: ResourceNotFoundException) {
            throw StatusException(Status.NOT_FOUND.withDescription(e.message))
        } catch (e: OrderValidationException) {
            throw StatusException(Status.FAILED_PRECONDITION.withDescription(e.message))
        } catch (e: OrderCreationException) {
            throw StatusException(Status.INTERNAL.withDescription(e.message))
        } catch (e: Throwable) {
            logger.error("Failed to cancel order via gRPC: {}", e.message, e)
            throw StatusException(Status.INTERNAL.withDescription("Lỗi hệ thống khi hủy đơn hàng"))
        }
    }
}