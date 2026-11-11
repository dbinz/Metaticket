package metaticket.admin.gateway

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import metaticket.admin.configuration.Environment
import metaticket.common.checksum.Checksum
import metaticket.common.logger.Logging
import metaticket.payment.datastructure.PaymentGateway
import metaticket.payment.datastructure.PaymentStatus
import metaticket.payment.datastructure.PaymentType
import metaticket.payment.grpc.PaymentMessage
import metaticket.payment.grpc.PaymentServiceGrpcKt
import metaticket.payment.response.PaymentResponse

class PaymentGateway(
    private val stub: PaymentServiceGrpcKt.PaymentServiceCoroutineStub,
): Logging {
    suspend fun loadPaymentByIds(paymentIds: List<String>): List<PaymentResponse> {
        val requestBuilder = PaymentMessage.GetPaymentByIdsRequest.newBuilder()
            .addAllPaymentIds(paymentIds)
            .build()
        return withContext(Dispatchers.IO) { stub.getPaymentByIds(requestBuilder) }.paymentsList.map {
            PaymentResponse(
                id = Checksum.encode(it.id, Environment.secretKey),
                paymentNo = it.paymentNo,
                payTime = it.payTime,
                paymentType = PaymentType.valueOf(it.paymentType),
                status = PaymentStatus.valueOf(it.status),
                amount = it.amount,
                pgAccountCode = it.pgAccountCode,
                pgAccountName = it.pgAccountName,
                gateway = PaymentGateway.valueOf(it.gateway),
            )
        }
    }
}