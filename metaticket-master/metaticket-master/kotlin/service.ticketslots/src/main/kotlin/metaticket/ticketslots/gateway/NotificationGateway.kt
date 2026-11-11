package metaticket.ticketslots.gateway

import metaticket.common.logger.Logging
import metaticket.notification.zns.grpc.ZaloNotificationServiceGrpcKt
import metaticket.notification.zns.grpc.ZnsMessage
import metaticket.notification.zns.grpc.ZnsMessage.ZaloMessageMetadataProto
import metaticket.ticketslots.model.ZNSConfig

class NotificationGateway constructor(
    private val znsStub: ZaloNotificationServiceGrpcKt.ZaloNotificationServiceCoroutineStub,
) : Logging {

    suspend fun sendZaloOTP(
        znsConfig: ZNSConfig,
        refId: String,
        phone: String,
        otp: String,
        ticket: String,
        event: String,
    ): ZnsMessage.ZaloMessageResponse? {
        return sendZaloNotification(znsConfig, refId, phone, mapOf("otp" to otp, "ma_ve" to ticket, "su_kien" to event))
    }

    suspend fun sendZaloNotification(
        znsConfig: ZNSConfig,
        refId: String,
        phone: String,
        templateData: Map<String, String>,
    ): ZnsMessage.ZaloMessageResponse? {
        return runCatching {
            znsStub.sendZaloMessage(
                ZnsMessage.SendZaloMessageRequest.newBuilder()
                    .setMetadata(
                        ZaloMessageMetadataProto.newBuilder()
                            .setRefId(refId)
                            .setPhone(phone)
                            .setPlugin(znsConfig.plugin)
                            .setTemplateId(znsConfig.templateId)
                            .setLang("vi")
                            .setFailover(znsConfig.failover)
                            .build()
                    )
                    .putAllTemplateData(templateData)
                    .build()
            )
        }.onFailure {
            logger.error("Error while sending OTP: {}", it.message)
        }.getOrNull()
    }
}