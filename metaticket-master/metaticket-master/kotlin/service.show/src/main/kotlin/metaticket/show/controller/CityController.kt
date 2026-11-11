package metaticket.show.controller

import com.fasterxml.jackson.databind.node.JsonNodeFactory
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.response.*
import metaticket.common.logger.Logging
import metaticket.show.database.service.CityDBService
import org.bson.types.ObjectId

class CityController(
    private val env : ApplicationEnvironment,
    val db : CityDBService,
    val jsonFactory : JsonNodeFactory,
) : Logging {
    suspend fun fetchAllCities(call: ApplicationCall) {
        val allCities = db.loadAllCity()
        return call.respond(allCities)
    }

    suspend fun fetchCity(call: ApplicationCall) {
        val id = call.parameters["id"] ?: return call.respond(
            HttpStatusCode.BadRequest,
            jsonFactory.objectNode().put("message", "Missing or malformed id"),
        )
        if (!ObjectId.isValid(id)) return call.respond(
            HttpStatusCode.BadRequest,
            jsonFactory.objectNode().put("message", "Invalid city id"),
        )

        val document = db.loadCity(id) ?: return call.respond(
            HttpStatusCode.BadRequest,
            jsonFactory.objectNode().put("message", "city isn't found"),
        )

        call.respond(document)
    }
}