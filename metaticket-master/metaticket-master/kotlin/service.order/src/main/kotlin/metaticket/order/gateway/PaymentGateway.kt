package metaticket.order.gateway

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import metaticket.common.logger.Logging
import metaticket.payment.datastructure.PaymentType
import metaticket.payment.grpc.PaymentMessage
import metaticket.payment.grpc.PaymentServiceGrpcKt

class PaymentGateway(
    private val stub: PaymentServiceGrpcKt.PaymentServiceCoroutineStub,
): Logging {
    suspend fun getPaymentById(paymentId: String): PaymentMessage.PaymentResponse {
        return withContext(Dispatchers.IO) {
            stub.getPaymentById(
                PaymentMessage.GetPaymentByIdRequest.newBuilder()
                .setPaymentId(paymentId)
                .build()
            )
        }
    }
    suspend fun createPayment(orderId: String, orderCode: String, amount: Long, paymentMethods: List<PaymentType>, pgAccountCode: String?): String {
        return withContext(Dispatchers.IO) {
            stub.createPayment(PaymentMessage.CreatePaymentRequest.newBuilder().also {
                    it.orderId = orderId
                    it.orderCode = orderCode
                    it.amount = amount
                    if (!pgAccountCode.isNullOrBlank()) {
                        it.pgAccountCode = pgAccountCode
                    }
                    it.addAllPaymentMethods(paymentMethods.map { it.name })
                }.build()
            ).value
        }
    }

}