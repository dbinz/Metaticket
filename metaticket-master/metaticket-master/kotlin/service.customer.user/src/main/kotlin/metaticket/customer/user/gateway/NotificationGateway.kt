package metaticket.customer.user.gateway

import metaticket.common.logger.Logging
import metaticket.customer.user.model.MailOTPConfig
import metaticket.customer.user.model.ZNSConfig
import metaticket.notification.mail.grpc.MailMessage
import metaticket.notification.mail.grpc.MailNotificationServiceGrpcKt
import metaticket.notification.zns.grpc.ZaloNotificationServiceGrpcKt
import metaticket.notification.zns.grpc.ZnsMessage
import metaticket.notification.zns.grpc.ZnsMessage.ZaloMessageMetadataProto

class NotificationGateway constructor(
    private val znsStub: ZaloNotificationServiceGrpcKt.ZaloNotificationServiceCoroutineStub,
    private val mailStub: MailNotificationServiceGrpcKt.MailNotificationServiceCoroutineStub,
) : Logging {

    suspend fun sendZaloOTP(
        otpConfig: ZNSConfig,
        refId: String,
        phone: String,
        otp: String,
    ): ZnsMessage.ZaloMessageResponse? {
        return kotlin.runCatching {
            znsStub.sendZaloMessage(
                ZnsMessage.SendZaloMessageRequest.newBuilder()
                    .setMetadata(
                        ZaloMessageMetadataProto.newBuilder()
                            .setRefId(refId)
                            .setPhone(phone)
                            .setPlugin(otpConfig.plugin)
                            .setTemplateId(otpConfig.templateId)
                            .setLang("vi")
                            .setFailover(otpConfig.failover)
                            .build()
                    )
                    .putTemplateData("otp", otp)
                    .build()
            )
        }.onFailure {
            logger.error("Error while sending OTP: {}", it.message)
        }.getOrNull()
    }

    suspend fun sendEmailOTP(
        mailConfig: MailOTPConfig,
        reqId: String,
        email: String,
        otp: String
    ): Boolean {
        return runCatching {
            logger.info("[{}] Sending Email OTP to [{}]: {}", reqId, email, otp)
            mailStub.sendMailOTP(
                MailMessage.SendMailOTPRequest.newBuilder()
                    .setReqId(reqId)
                    .setMailPlugin(mailConfig.plugin)
                    .setMailTemplate(mailConfig.templateId)
                    .setEmail(email)
                    .setOtp(otp)
                    .build()
            )
        }.onFailure {
         logger.error("[{}] Error while sending Email OTP: {}", reqId, it.message)
        }.isSuccess
    }
}