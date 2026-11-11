package metaticket.admin.gateway

import com.google.protobuf.Empty
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import metaticket.common.logger.Logging
import metaticket.order.notification.grpc.OrderMessage
import metaticket.order.notification.grpc.OrderServiceGrpcKt
import metaticket.order.pojo.db.CustomFieldTmpl
import java.io.IOException

class OrderGateway constructor(
    private val stub: OrderServiceGrpcKt.OrderServiceCoroutineStub,
): Logging {
    /**
     * Creates an order.
     * @throws IOException for other network/IO related issues.
     */
    suspend fun createOrder(req: OrderMessage.CreateOrderRequest): String {
        return withContext(Dispatchers.IO) {
            stub.createOrder(req).value
        }
    }

    suspend fun cancelOrder(orderId: String) : Empty {
        return withContext(Dispatchers.IO) {
            stub.cancelOrder(OrderMessage.GetOrderByIdRequest.newBuilder().setOrderId(orderId).build())
        }
    }

    suspend fun getOrder(orderId: String) : OrderMessage.OrderProto {
        return withContext(Dispatchers.IO) {
            stub.getOrderById(OrderMessage.GetOrderByIdRequest.newBuilder().setOrderId(orderId).build())
        }
    }

    /**
     * Creates an order promotion. Assumes success is indicated by a 2xx status code.
     */
    suspend fun createOrderPromotion(
        showId: String,
        sessionId: String,
        data: String,
        pluginName: String,
    ) {
        val req = OrderMessage.CreateOrderPromotionRequest.newBuilder()
            .setShowId(showId)
            .setSessionId(sessionId)
            .setData(data)
            .setPluginName(pluginName)
            .build()
        stub.createOrderPromotion(req)
    }

    /**
     * Creates custom field mappings for an order.
     * @throws IOException for other network/IO related issues.
     */
    suspend fun createOrderCustomFieldsMapping(
        showId: String,
        sessionId: String,
        customFields: List<CustomFieldTmpl>,
    ): List<OrderMessage.CustomFieldTmplProto> { // Return non-nullable type
        val req = OrderMessage.CreateOrderCustomFieldsMappingRequest.newBuilder()
            .setShowId(showId)
            .setSessionId(sessionId)
            .addAllCustomFields(customFields.map {
                OrderMessage.CustomFieldTmplProto.newBuilder()
                    .setId(it.id)
                    .setType(it.type.name)
                    .setNote(it.note)
                    .setConstraints(it.constraints)
                    .build()
            })
            .build()
        return stub.createOrderCustomFieldsMapping(req).customFieldsList
    }
}