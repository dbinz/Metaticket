package metaticket.order.datacollector.service

import com.google.protobuf.StringValue
import io.grpc.StatusException
import io.ktor.server.application.*
import metaticket.common.checksum.Checksum
import metaticket.common.logger.Logging
import metaticket.order.datacollector.configuration.Environment
import metaticket.order.datacollector.database.service.OrderDataCollectedDBService
import metaticket.order.datacollector.grpc.OrderDataCollectorServiceGrpcKt
import metaticket.order.datacollector.grpc.OrderDatacollectorMessage
import org.bson.types.ObjectId

class OrderDataCollectorServiceImpl(
    private val env: ApplicationEnvironment,
    private val odCollectedDBS: OrderDataCollectedDBService,
): OrderDataCollectorServiceGrpcKt.OrderDataCollectorServiceCoroutineImplBase(), Logging {
    private val linkBaseUrl = env.config.property("odc.linkBaseUrl").getString()

    override suspend fun checkOrderDataCollectorIsValid(request: OrderDatacollectorMessage.CheckOrderDataCollectorIsValidRequest): StringValue {
        val link = request.link
        val id = link.removePrefix("$linkBaseUrl/").trim().let { Checksum.decode(it, Environment.secretKey) }
        if (!ObjectId.isValid(id)) {
            logger.error("cannot parse id from link")
            throw StatusException(io.grpc.Status.INVALID_ARGUMENT.withDescription("missing id"))
        }
        val data = odCollectedDBS.getDataById(id) ?: run {
            logger.error("data collected {} not found", id)
            throw StatusException(io.grpc.Status.NOT_FOUND.withDescription("not found data collected"))
        }
        return StringValue.of(data.status.name)
    }
}