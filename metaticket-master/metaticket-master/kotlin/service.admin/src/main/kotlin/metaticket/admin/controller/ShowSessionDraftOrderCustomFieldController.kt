package metaticket.admin.controller

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.JsonNodeFactory
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import metaticket.admin.database.service.ShowSessionDraftOrderCustomFieldDBService
import metaticket.admin.database.service.ShowSessionDraftPromotionDBService
import metaticket.admin.pojo.db.ShowSessionDraftOrderCustomFieldDocument
import metaticket.admin.pojo.request.CreateShowSessionDraftOrderCustomFieldRequest
import metaticket.admin.pojo.request.CreateShowSessionDraftPromotionRequest
import metaticket.admin.pojo.request.UpdateShowSessionDraftOrderCustomFieldRequest
import metaticket.common.logger.Logging
import org.bson.types.ObjectId

class ShowSessionDraftOrderCustomFieldController (
    val showSessionDraftOrderCustomFieldDBService : ShowSessionDraftOrderCustomFieldDBService,
    val jsonFactory : JsonNodeFactory,
    val jsonMapper: ObjectMapper,
) : Logging {

    suspend fun loadSessionDraftOrderCustomFields(call : ApplicationCall) {
        val sessionDraftId = call.parameters["sessionDraftId"]
            ?: return call.respond(HttpStatusCode.BadRequest,
                jsonFactory.objectNode().put("message", "Missing or malformed session draft id")
            )

        val doc = showSessionDraftOrderCustomFieldDBService.loadSessionsDraftOrderCustomFields(sessionDraftId) ?: return call.respond(
            HttpStatusCode.NotFound,
            jsonFactory.objectNode().put("message", "Invalid session draft id"),
        )


        call.respond(HttpStatusCode.OK, doc)
    }

    suspend fun createSessionDraftOrderCustomField(call : ApplicationCall) {
        val input = call.receive<CreateShowSessionDraftOrderCustomFieldRequest>()
        if (!ObjectId.isValid(input.sessionDraftId)) return call.respond(
            HttpStatusCode.BadRequest,
            jsonFactory.objectNode().put("message", "Invalid session draft id"),
        )

        val doc = ShowSessionDraftOrderCustomFieldDocument(
            sessionDraftId = input.sessionDraftId,
            customFields = input.customFields,
        )

        showSessionDraftOrderCustomFieldDBService.createShowSessionDraftOrderCustomFields(doc)

        logger.info("show session draft order custom field {} created", doc)

        call.respond(HttpStatusCode.OK, doc)
    }

    suspend fun updateSessionDraftOrderCustomField(call : ApplicationCall) {
        val input = call.receive<UpdateShowSessionDraftOrderCustomFieldRequest>()
        if (!ObjectId.isValid(input.id)) return call.respond(
            HttpStatusCode.BadRequest,
            jsonFactory.objectNode().put("message", "Invalid session draft order custom field id"),
        )

        val doc = showSessionDraftOrderCustomFieldDBService.updateShowSessionDraftOrderCustomFields(input.id, input.customFields) ?: return call.respond(
            HttpStatusCode.BadRequest,
            jsonFactory.objectNode().put("message", "Cannot update session draft")
        )

        logger.info("show session draft order custom field {} updated", doc)

        call.respond(HttpStatusCode.OK, doc)
    }
}
