package metaticket.editor.policydesc.controller

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.response.*
import metaticket.common.logger.Logging
import metaticket.editor.policydesc.DatabaseService


/**
 *
 * @author dongcatchay
 */
class DocumentController(
    private val db: DatabaseService
): Logging {
    suspend fun loadDoc(call: ApplicationCall) {
        val id = call.parameters["globalId"] ?: return call.respond(HttpStatusCode.BadRequest)
        val d = db.loadDocument(id) ?: return call.respond(HttpStatusCode.BadRequest, "not found document")
        return call.respond(d)
    }

    suspend fun loadAllDocCategory(call: ApplicationCall) {
        val doc = db.loadAllDocCategory()
        return call.respond(HttpStatusCode.OK, doc)
    }

    suspend fun loadDocCategory(call: ApplicationCall) {
        val id: String = call.parameters["globalId"] ?: run {
            return call.respond(HttpStatusCode.BadRequest, "missing id")
        }
        val doc = db.loadDocCategoryById(id) ?: run {
            return call.respond(HttpStatusCode.NotFound, "not found document")
        }
        return call.respond(HttpStatusCode.OK, doc)
    }
}
