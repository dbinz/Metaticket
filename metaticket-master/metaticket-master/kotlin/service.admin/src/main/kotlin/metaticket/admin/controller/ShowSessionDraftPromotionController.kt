package metaticket.admin.controller

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.JsonNodeFactory
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import metaticket.admin.database.service.ShowSessionDraftPromotionDBService
import metaticket.admin.pojo.db.ShowSessionDraftPromotionDocument
import metaticket.admin.pojo.request.CreateShowSessionDraftPromotionRequest
import metaticket.admin.pojo.request.UpdateShowSessionDraftPromotionRequest
import metaticket.common.logger.Logging
import org.bson.types.ObjectId

class ShowSessionDraftPromotionController(
    val showSessionDraftPromotionDBService : ShowSessionDraftPromotionDBService,
    val jsonFactory : JsonNodeFactory,
    val jsonMapper: ObjectMapper,
) : Logging {

    suspend fun loadSessionDraftPromotions(call : ApplicationCall) {
        val sessionDraftId = call.parameters["sessionDraftId"]
            ?: return call.respond(HttpStatusCode.NotFound,
                jsonFactory.objectNode().put("message", "Missing or malformed session draft id")
            )

        val docs = showSessionDraftPromotionDBService.loadSessionsDraftPromotions(sessionDraftId)

        call.respond(HttpStatusCode.OK, docs)
    }

    suspend fun createSessionDraftPromotion(call : ApplicationCall) {
        val input = call.receive<CreateShowSessionDraftPromotionRequest>()

        if (!ObjectId.isValid(input.sessionDraftId)) return call.respond(
            HttpStatusCode.BadRequest,
            jsonFactory.objectNode().put("message", "Invalid session draft id"),
        )

        if (input.data.isEmpty()) return call.respond(
            HttpStatusCode.BadRequest,
            jsonFactory.objectNode().put("message", "Invalid data"),
        )

        val emptyDoc = ShowSessionDraftPromotionDocument(
            sessionDraftId = input.sessionDraftId,
            data = input.data,
            name = input.name
        )

        showSessionDraftPromotionDBService.createShowSessionDraftPromotion(emptyDoc)

        logger.info("show session draft promotion {} created", emptyDoc)

        call.respond(HttpStatusCode.OK, emptyDoc)
    }

    suspend fun updateSessionDraftPromotion(call : ApplicationCall) {
        val input = call.receive<UpdateShowSessionDraftPromotionRequest>()

        if (!ObjectId.isValid(input.id)) return call.respond(
            HttpStatusCode.BadRequest,
            jsonFactory.objectNode().put("message", "Invalid show id"),
        )
        val sessionDraftPromotion = showSessionDraftPromotionDBService.updateShowSessionDraftPromotion(input.id, input.name, input.data) ?: return call.respond(
            HttpStatusCode.BadRequest,
            jsonFactory.objectNode().put("message", "Cannot update session draft")
        )

        logger.info("show session draft promotion {} updated", sessionDraftPromotion)

        call.respond(HttpStatusCode.OK, sessionDraftPromotion)
    }
}
