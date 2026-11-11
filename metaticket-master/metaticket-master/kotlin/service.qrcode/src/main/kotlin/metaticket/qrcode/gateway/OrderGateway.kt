package metaticket.qrcode.gateway

import metaticket.common.logger.Logging
import metaticket.order.datastructure.OrderStatus
import metaticket.order.notification.grpc.OrderMessage
import metaticket.order.notification.grpc.OrderServiceGrpcKt

class OrderGateway(
    private val stub: OrderServiceGrpcKt.OrderServiceCoroutineStub
) : Logging {
    suspend fun getOrderStatus(orderId: String): OrderStatus {
        return try {
            val response = stub.getOrderStatus(
                OrderMessage.GetOrderByIdRequest.newBuilder()
                    .setOrderId(orderId)
                    .build()
            )
            OrderStatus.valueOf(response.status)
        } catch (e: Exception) {
            logger.error("Failed to get order status for order {}", orderId, e)
            throw e
        }
    }
}
