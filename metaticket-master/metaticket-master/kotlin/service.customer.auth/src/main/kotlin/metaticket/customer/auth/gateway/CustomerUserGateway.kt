package metaticket.customer.auth.gateway

import metaticket.common.logger.Logging
import metaticket.customer.user.grpc.CustomerUserMessage
import metaticket.customer.user.grpc.CustomerUserServiceGrpcKt.CustomerUserServiceCoroutineStub

class CustomerUserGateway(
    private val userStub: CustomerUserServiceCoroutineStub,
): Logging {

    suspend fun verifyPassword(phone: String, password: String): CustomerUserMessage.CustomerUserProto {
        return userStub.verifyPassword(
            CustomerUserMessage.VerifyPasswordRequest.newBuilder()
                .setPhone(phone)
                .setPassword(password)
                .build()
        )
    }
}