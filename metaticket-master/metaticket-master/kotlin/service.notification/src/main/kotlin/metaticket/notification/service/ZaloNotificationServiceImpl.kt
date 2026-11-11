package metaticket.notification.service

import io.grpc.Status
import io.grpc.StatusException
import kotlinx.coroutines.CompletableDeferred
import metaticket.common.logger.Logging
import metaticket.notification.processor.ZaloMessageProcessor
import metaticket.notification.zns.grpc.ZaloNotificationServiceGrpcKt
import metaticket.notification.zns.grpc.ZnsMessage
import metaticket.plugin.zns.model.ZaloMessageMetadata
import metaticket.plugin.zns.model.ZaloMessageRequest


class ZaloNotificationServiceImpl(
    private val zaloMessageSender: ZaloMessageProcessor,
): ZaloNotificationServiceGrpcKt.ZaloNotificationServiceCoroutineImplBase(), Logging {
    override suspend fun sendZaloMessage(request: ZnsMessage.SendZaloMessageRequest): ZnsMessage.ZaloMessageResponse {
        val message = ZaloMessageRequest(
            metadata = ZaloMessageMetadata(
                ref_id = request.metadata.refId,
                plugin = request.metadata.plugin,
                template_id = request.metadata.templateId,
                phone = request.metadata.phone,
                failover = request.metadata.failover,
            ),
            data = request.templateDataMap,
            respond = CompletableDeferred()
        )

        return try {
            zaloMessageSender.sendMessage(message)
            val it = message.respond.await()
            ZnsMessage.ZaloMessageResponse.newBuilder()
                .setMessage(it.message)
                .setCode(it.code)
                .setData(it.data)
                .build()
        } catch (t: Throwable) {
            logger.error("Failed to send zalo message: ", t)
            throw StatusException(Status.INTERNAL.withDescription("Lỗi hệ thống, vui lòng thử lại sau"))
        }
    }
}