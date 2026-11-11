package metaticket.ticketslots.gateway

import io.grpc.StatusException
import metaticket.common.logger.Logging
import metaticket.customer.user.grpc.CustomerUserMessage
import metaticket.customer.user.grpc.CustomerUserServiceGrpcKt.CustomerUserServiceCoroutineStub

class CustomerUserGateway constructor(
    private val userStub: CustomerUserServiceCoroutineStub,
): Logging {
    suspend fun findUserById(id: String): CustomerUserMessage.CustomerUserProto {
        return userStub.getCustomerUserById(
                CustomerUserMessage.GetCustomerUserByIdRequest.newBuilder()
                    .setId(id)
                    .build()
            )
    }

    suspend fun findUserByPhone(phone: String): CustomerUserMessage.CustomerUserProto? {
        return try {
            userStub.getCustomerUserByPhone(
                CustomerUserMessage.GetCustomerUserByPhoneRequest.newBuilder()
                    .setPhone(phone)
                    .build()
            )
        } catch (e: StatusException) {
            if (e.status.code == io.grpc.Status.Code.NOT_FOUND) {
                return null
            } else throw e
        }
    }
}