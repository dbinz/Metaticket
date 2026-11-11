package metaticket.notification

import io.ktor.server.routing.*
import metaticket.notification.controller.MailController

fun Route.documentRoutes(c: MailController) {

    get("/private/notification/mail/templates/fetch")     {
        c.fetchAllTemplates(call)
    }

    get("/private/notification/mail/template/fetch")     {
        c.fetchMailTemplateById(call)
    }

    get("/private/notification/mail/plugins/fetch")     {
        c.fetchAllPlugins(call)
    }

    post("/private/notification/mail/template/create") { c.createMailTemplate(call) }
    post("/private/notification/mail/template/update") { c.updateMailTemplate(call) }
}
