package metaticket.order.gateway

import metaticket.common.logger.Logging
import metaticket.order.datacollector.OrderDataCollectedStatus
import metaticket.order.datacollector.grpc.OrderDataCollectorServiceGrpcKt
import metaticket.order.datacollector.grpc.OrderDatacollectorMessage

class OrderDataCollectorGateway(
    private val stub: OrderDataCollectorServiceGrpcKt.OrderDataCollectorServiceCoroutineStub
): Logging {
    suspend fun checkOrderDataCollectorIsValid(link: String): OrderDataCollectedStatus? {
        logger.info("checkOrderDataCollectorIsValid with link {}", link)
        return try {
            val response = stub.checkOrderDataCollectorIsValid(
                OrderDatacollectorMessage.CheckOrderDataCollectorIsValidRequest.newBuilder().setLink(link).build()
            )
            OrderDataCollectedStatus.valueOf(response.value)
        } catch (e: Exception) {
            logger.error("checkOrderDataCollectorIsValid with link {} failed with error {}", link, e.message)
            null
        }
    }
}