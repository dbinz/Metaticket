package metaticket.customer.user.service

import io.grpc.Status
import io.ktor.server.config.*
import metaticket.common.logger.Logging
import metaticket.common.utils.normalizePhoneNumber
import metaticket.customer.user.grpc.CustomerUserMessage
import metaticket.customer.user.grpc.CustomerUserServiceGrpcKt
import metaticket.customer.user.model.toGrpcUser
import metaticket.customer.user.repository.UserRepository
import metaticket.customer.user.utils.hashPassword

class CustomerUserServiceImpl constructor(
    private val config: ApplicationConfig,
    private val userRepository: UserRepository
) : CustomerUserServiceGrpcKt.CustomerUserServiceCoroutineImplBase(), Logging {
    private val salt = config.property("bcrypt.salt").getString()

    override suspend fun getCustomerUserById(request: CustomerUserMessage.GetCustomerUserByIdRequest): CustomerUserMessage.CustomerUserProto {
        val user = userRepository.findById(request.id)
        return user?.toGrpcUser()
            ?: throw Status.NOT_FOUND
                .withDescription("Không tìm thấy người dùng với ID: ${request.id}")
                .asException()
    }

    override suspend fun getCustomerUserByPhone(request: CustomerUserMessage.GetCustomerUserByPhoneRequest): CustomerUserMessage.CustomerUserProto {
        val phoneNumber = normalizePhoneNumber(request.phone) ?: run {
            throw Status.INVALID_ARGUMENT
                .withDescription("Số điện thoại không đúng, vui lòng kiểm tra lại")
                .asException()
        }
        val user = userRepository.findByPhone(phoneNumber)
        return user?.toGrpcUser()
            ?: throw Status.NOT_FOUND
                .withDescription("Không tìm thấy người dùng với số điện thoại: ${request.phone}")
                .asException()
    }

    override suspend fun verifyPassword(request: CustomerUserMessage.VerifyPasswordRequest): CustomerUserMessage.CustomerUserProto {
        val phoneNumber = normalizePhoneNumber(request.phone) ?: run {
            throw Status.UNAUTHENTICATED
                .withDescription("Số điện thoại không đúng, vui lòng kiểm tra lại")
                .asException()
        }
        val user = userRepository.findByPhone(phoneNumber)
        return if (user != null && user.password == hashPassword(request.password, salt)) {
            user.toGrpcUser()
        } else {
            throw Status.UNAUTHENTICATED
                .withDescription("Số điện thoại hoặc mật khẩu không đúng")
                .asException()
        }
    }
}