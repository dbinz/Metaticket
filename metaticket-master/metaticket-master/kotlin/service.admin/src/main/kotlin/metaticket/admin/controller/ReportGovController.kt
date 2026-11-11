package metaticket.admin.controller

import GetReportGovRequest
import com.fasterxml.jackson.databind.ObjectMapper
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import metaticket.admin.pojo.response.ReportGovResponse
import metaticket.common.logger.Logging
import java.io.File

class ReportGovController(
    private val env: ApplicationEnvironment,
    private val jsonMapper: ObjectMapper,
): Logging {
    private val username = env.config.propertyOrNull("reportGov.username")?.getString()
    private val password = env.config.propertyOrNull("reportGov.password")?.getString()
    private val filePath = env.config.property("reportGov.filePath").getString()

    suspend fun getReportGov(call: ApplicationCall) {
        val req = call.receive<GetReportGovRequest>()
        if (req.username != username || req.password != password) {
            call.respond(HttpStatusCode.Forbidden, "username or password is invalid")
        }
        val data = jsonMapper.readValue(File(filePath), ReportGovResponse::class.java)
        call.respond(data)
    }
}