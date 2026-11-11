package metaticket.editor.seatplanner.controller
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.JsonNodeFactory
import metaticket.common.logger.Logging
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import metaticket.editor.seatplanner.CmdMeta.CmdMetaProto
import metaticket.editor.seatplanner.CmdType
import metaticket.editor.seatplanner.DatabaseService
import metaticket.editor.seatplanner.exception.DocumentNotFoundException
import metaticket.editor.seatplanner.pojo.SaveItemsCmdState
import metaticket.editor.seatplanner.pojo.SeatPlanDocument
import java.io.File
import java.util.UUID
import io.ktor.http.content.*

class DocumentController(
    val db : DatabaseService,
    val jsonFactory : JsonNodeFactory,
    val jsonMapper : ObjectMapper,
    val env: ApplicationEnvironment
) : Logging {

    suspend fun createDoc(call : ApplicationCall) {
        val emptyDoc = SeatPlanDocument()
        db.create(emptyDoc)

        call.respond(emptyDoc)
    }

    suspend fun deleteDoc(call: ApplicationCall) {
        val id = call.parameters["globalId"] ?: return call.respond(
            HttpStatusCode.BadRequest,
            jsonFactory.objectNode().put("message", "Missing or malformed globalId"),
        )

        try {
            val deletedCount = db.delete(id)
            if (deletedCount > 0) {
                call.respond(HttpStatusCode.OK)
            } else {
                call.respond(HttpStatusCode.NotFound, jsonFactory.objectNode().put("message", "Document not found"))
            }
        } catch (t: Throwable) {
            logger.error("Failed to delete document {}: ", id, t)
            call.respond(
                HttpStatusCode.InternalServerError,
                jsonFactory.objectNode().put("message", "failed to delete document $id: ${t.message}"),
            )
        }
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

    suspend fun loadDoc(call : ApplicationCall) {
        val id = call.parameters["globalId"] ?: return call.respond(
            HttpStatusCode.BadRequest,
            jsonFactory.objectNode().put("message", "Missing or malformed globalId"),
        )

        logger.info("Getting document {}",id)

        try {
            val document = db.loadDocument(id)

            document?.let {
                call.respond(document)
                return
            }

            call.respond(HttpStatusCode.NotFound,jsonFactory.objectNode().put("message", "Cannot find document $id"),)

        } catch (t: Throwable) {
            logger.error("Failed to get document {}: ", id, t)
            call.respond(
                HttpStatusCode.InternalServerError,
                jsonFactory.objectNode().put("message", "failed to get document $id: ${t.message}"),
            )
        }
    }

    suspend fun processCmd(call : ApplicationCall) {
        val data = call.receive<ByteArray>()

        val metaLength = data[0].toInt()
        val cmdMetaArrBuf = data.copyOfRange(1, metaLength + 1)
        val cmdDataArrBuf = data.copyOfRange(metaLength + 1, data.size)

        val cmdMeta = try {
            CmdMetaProto.parseFrom(cmdMetaArrBuf)
        } catch (t: Throwable) {
            logger.error("failed to parse CmdMeta: ", t)
            call.respond(
                HttpStatusCode.InternalServerError,
                jsonFactory.objectNode().put("message", "failed to parse CmdMeta: ${t.message}")
            )
            return
        }

        if (cmdMeta.channelCode != 1001) {
            call.respond(
                HttpStatusCode.BadRequest,
                jsonFactory.objectNode().put("message", "invalid channel code")
            )
            return
        }

        val globalId = call.parameters["globalId"] ?: return call.respond(
            HttpStatusCode.BadRequest,
            jsonFactory.objectNode().put("message", "missing global id"),
        )

        when(cmdMeta.cmdType) {
            CmdType.INSERT_DOC.ordinal -> {
                logger.debug("Receive ${CmdType.INSERT_DOC.name} command -- {}", cmdDataArrBuf.decodeToString())
                call.respond(HttpStatusCode.OK)
            }
            CmdType.INSERT_LAYER.ordinal -> {
                logger.debug("Receive ${CmdType.INSERT_LAYER.name} command -- {}", cmdDataArrBuf.decodeToString())
                call.respond(HttpStatusCode.OK)
            }
            CmdType.SAVE_ITEMS.ordinal -> {
                val cmdData = cmdDataArrBuf.decodeToString()
                logger.debug("Receive ${CmdType.SAVE_ITEMS.name} command -- {}", cmdData)
                processSaveItems(call, cmdData, globalId)
            }
            else -> {
                logger.error("Not accept cmd type {} from cmd meta {}", cmdMeta.cmdType, cmdMeta)
                call.respond(
                    HttpStatusCode.BadRequest,
                    jsonFactory.objectNode().put("message", "not accept cmd type ${cmdMeta.cmdType}")
                )
                return
            }
        }
    }

    private suspend fun processSaveItems(call : ApplicationCall, data : String, id : String) {
        try {
            val cmdState = jsonMapper.readValue(data, SaveItemsCmdState::class.java)
            db.saveItems(id, cmdState.added, cmdState.updated, cmdState.removed)
            call.respond(HttpStatusCode.OK)
        } catch (notFound : DocumentNotFoundException) {
            call.respond(HttpStatusCode.NotFound, jsonFactory.objectNode().put("message", notFound.message))
        }
    }

    suspend fun saveDocStyle(call : ApplicationCall) {
        val style = call.receiveText()
        val globalId = call.parameters["globalId"] ?: return call.respond(
            HttpStatusCode.BadRequest,
            jsonFactory.objectNode().put("message", "missing global id"),
        )
        db.updateStyle(globalId, style)

        call.respond(HttpStatusCode.OK)
    }

    suspend fun saveDocDef(call : ApplicationCall) {
        val def = call.receiveText()
        val globalId = call.parameters["globalId"] ?: return call.respond(
            HttpStatusCode.BadRequest,
            jsonFactory.objectNode().put("message", "missing global id"),
        )
        db.updateDef(globalId, def)

        call.respond(HttpStatusCode.OK)
    }

    suspend fun saveDocName(call : ApplicationCall) {
        val name = call.receiveText()
        val globalId = call.parameters["globalId"] ?: return call.respond(
            HttpStatusCode.BadRequest,
            jsonFactory.objectNode().put("message", "missing global id"),
        )
        db.updateName(globalId, name)

        call.respond(HttpStatusCode.OK)
    }

    suspend fun saveCategories(call: ApplicationCall) {
        val category: List<String> = call.receive()
        val globalId = call.parameters["globalId"] ?: return call.respond(
            HttpStatusCode.BadRequest,
            jsonFactory.objectNode().put("message", "missing global id"),
        )
        db.updateCategory(globalId, category)

        call.respond(HttpStatusCode.OK)
    }

    suspend fun uploadImage(call: ApplicationCall) {
        val docId = call.parameters["globalId"] ?: return call.respond(HttpStatusCode.BadRequest, "Missing globalId")

        val multipart = call.receiveMultipart()
        var fileUrl = ""

        multipart.forEachPart { part ->
            if (part is PartData.FileItem) {
                val fileName = part.originalFileName as String
                val fileBytes = part.streamProvider().readBytes()

                val uploadDir = env.config.property("storage.uploadDir").getString()
                val baseUrl = env.config.property("storage.baseUrl").getString()

                val dir = File(uploadDir)
                if (!dir.exists()) dir.mkdirs()

                // Use a unique name to avoid collisions
                val ext = fileName.substringAfterLast(".", "")
                val savedFileName = "${UUID.randomUUID()}.$ext"
                val file = File(dir, savedFileName)
                file.writeBytes(fileBytes)

                fileUrl = "$baseUrl/$savedFileName"
            }
            part.dispose()
        }

        if (fileUrl.isNotEmpty()) {
            db.updateBaseImgUrl(docId, fileUrl)
            call.respond(jsonFactory.objectNode().put("url", fileUrl))
        } else {
            call.respond(HttpStatusCode.BadRequest, "No file uploaded")
        }
    }

    suspend fun updateBaseImgUrl(call: ApplicationCall) {
        val docId = call.parameters["globalId"] ?: return call.respond(HttpStatusCode.BadRequest, "Missing globalId")
        val params = call.receive<Map<String, String>>()
        val url = params["url"] ?: return call.respond(HttpStatusCode.BadRequest, "Missing url")

        db.updateBaseImgUrl(docId, url)
        call.respond(HttpStatusCode.OK)
    }

}
