package metaticket.plugin.mailtemplate

import metaticket.common.logger.Logging
import metaticket.notification.mail.grpc.MailMessage
import metaticket.plugin.mailtemplate.pojo.PluginMailMetaData
import metaticket.plugin.mailtemplate.pojo.db.MailTemplateDocument
import kotlin.text.replace

class PluginMailTemplateOTP : PluginMailTemplate, Logging {

    override suspend fun buildSubject(data: Any): String {
        return "META Ticket gửi mã xác thực"
    }

    override suspend fun buildBody(metaData: PluginMailMetaData, data: Any, document: MailTemplateDocument): String {
        data as MailMessage.SendMailOTPRequest
        val mailTemplate = document.templates["root"]!!
        var mailBody = mailTemplate
            .replace("{{otp}}", data.otp)
            .replace("{{name}}", data.name)
            .replace("{{phone}}", data.phone)
            .replace("{{supporter}}", metaData.supporter)
            .replace("{{hotline}}", metaData.hotline)
            .replace("{{end-point}}", metaData.endPoint)
        return mailBody
    }
}