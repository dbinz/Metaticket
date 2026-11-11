package metaticket.admin.gateway

import metaticket.common.logger.Logging
import metaticket.customer.user.grpc.CustomerUserMessage
import metaticket.customer.user.grpc.CustomerUserServiceGrpcKt.CustomerUserServiceCoroutineStub
import org.bson.types.ObjectId

class CustomerUserGateway(
    private val userStub: CustomerUserServiceCoroutineStub,
): Logging {
    suspend fun findUserByIdOrPhone(idOrPhone: String): CustomerUserMessage.CustomerUserProto {
        return if (ObjectId.isValid(idOrPhone)) {
            userStub.getCustomerUserById(
                CustomerUserMessage.GetCustomerUserByIdRequest.newBuilder()
                    .setId(idOrPhone)
                    .build()
            )
        } else {
            userStub.getCustomerUserByPhone(
                CustomerUserMessage.GetCustomerUserByPhoneRequest.newBuilder()
                    .setPhone(idOrPhone)
                    .build()
            )
        }
    }
}