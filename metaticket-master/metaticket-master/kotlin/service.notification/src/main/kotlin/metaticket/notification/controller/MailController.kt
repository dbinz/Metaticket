package metaticket.notification.controller

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import metaticket.common.logger.Logging
import metaticket.notification.cache.MailTemplateCache
import metaticket.notification.database.service.MailTemplateDBService
import metaticket.notification.plugin.template.service.PluginMailTemplateService
import metaticket.notification.pojo.request.CreateMailTemplateRequest
import metaticket.notification.pojo.request.UpdateMailTemplateRequest
import metaticket.plugin.mailtemplate.pojo.db.AttachmentDocument
import metaticket.plugin.mailtemplate.pojo.db.MailTemplateDocument
import metaticket.plugin.mailtemplate.pojo.db.PDFAttachmentDocument
import org.koin.core.component.KoinComponent


/**
 *
 * @author dongcatchay
 */
class MailController(
    private val pluginMailTemplateService: PluginMailTemplateService,
    private val mailTemplateCache: MailTemplateCache,
    private val db: MailTemplateDBService,
): KoinComponent, Logging {

    suspend fun fetchAllTemplates(call: ApplicationCall) {
        val templates = db.loadAllMailTemplate()
        return call.respond(templates)
    }

    suspend fun fetchMailTemplateById(call : ApplicationCall) {
        val id = call.parameters["id"] ?: return call.respond(HttpStatusCode.BadRequest,
            call.respond(HttpStatusCode.BadRequest, "Missing id")
        )

        val document = db.loadMailTemplate(id) ?: return call.respond(HttpStatusCode.BadRequest,
            call.respond(HttpStatusCode.BadRequest, "Template not found")
        )

        call.respond(document)
    }

    suspend fun fetchAllPlugins(call: ApplicationCall) {
        return call.respond(pluginMailTemplateService.getPlugins())
    }

    suspend fun createMailTemplate(call: ApplicationCall) {
        val reqData = call.receive<CreateMailTemplateRequest>()
        val templateMap = HashMap<String, String>()
        templateMap["root"] = reqData.root
        templateMap["priceItem"] = reqData.priceItem
        templateMap["promotionItem"] = reqData.promotionItem
        reqData.customFieldItem?.let { templateMap["customFieldItem"] = it }

        val mailTemplateDoc = MailTemplateDocument(
            reqData.id,
            templateMap,
            reqData.ticketPdfTemplate?.let { tpl ->
                AttachmentDocument(
                    PDFAttachmentDocument(
                        filename = reqData.ticketPdfFileName ?: "ve_su_kien",
                        template = tpl
                    )
                )
            }
        )

        db.createMailTemplate(mailTemplateDoc) ?: run {
            return call.respond(
                HttpStatusCode.InternalServerError,
                "Cannot create template",
            )
        }
        return call.respond(HttpStatusCode.OK, mailTemplateDoc)
    }

    suspend fun updateMailTemplate(call: ApplicationCall) {
        val reqData = call.receive<UpdateMailTemplateRequest>()
        val templateMap = HashMap<String, String>()
        templateMap["root"] = reqData.root
        templateMap["priceItem"] = reqData.priceItem
        templateMap["promotionItem"] = reqData.promotionItem
        reqData.customFieldItem?.let { templateMap["customFieldItem"] = it }

        val mailTemplateDoc = MailTemplateDocument(
            reqData.id,
            templateMap,
            reqData.ticketPdfTemplate?.let { tpl ->
                AttachmentDocument(
                    PDFAttachmentDocument(
                        filename = reqData.ticketPdfFileName ?: "ve_su_kien",
                        template = tpl
                    )
                )
            }
        )

        db.updateMailTemplate(
            reqData.id,
            mailTemplateDoc,
        ) ?: run {
            return call.respond(
                HttpStatusCode.InternalServerError,
                "Cannot update template",
            )
        }

        mailTemplateCache.remove(reqData.id)

        return call.respond(HttpStatusCode.OK, mailTemplateDoc)
    }
}
