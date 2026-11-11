package metaticket.order.datacollector.controller

import com.fasterxml.jackson.databind.ObjectMapper
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import metaticket.common.checksum.Checksum
import metaticket.common.logger.Logging
import metaticket.order.datacollector.OrderDataCollectedStatus
import metaticket.order.datacollector.configuration.Environment
import metaticket.order.datacollector.database.service.OrderDataCollectedDBService
import metaticket.order.datacollector.database.service.OrderDataCollectorDBService
import metaticket.order.datacollector.database.service.OrderDataCollectorMappingDBService
import metaticket.order.datacollector.gateway.ReservationGateway
import metaticket.order.datacollector.pojo.OrderDataCollectedResponse
import metaticket.order.datacollector.pojo.OrderDataCollectorTemplateResponse
import metaticket.order.datacollector.pojo.SaveOrderDataCollectedRequest
import metaticket.order.datacollector.pojo.db.ListCustomFieldData
import metaticket.order.datacollector.pojo.db.OrderDataCollected
import metaticket.order.datacollector.pojo.db.OrderDataCollector
import metaticket.order.datacollector.pojo.db.TicketReservationDetail
import metaticket.order.datastructure.validation.ValidationCustomField
import metaticket.order.pojo.db.CustomFieldData
import metaticket.order.pojo.db.CustomFieldTmpl
import org.bson.Document
import org.bson.types.ObjectId


/**
 *
 * @author dongcatchay
 */
class OrderDataCollectorController(
    private val env: ApplicationEnvironment,
    private val jsonMapper: ObjectMapper,
    private val odcMappingDBS: OrderDataCollectorMappingDBService,
    private val odCollectorDBS: OrderDataCollectorDBService,
    private val odCollectedDBS: OrderDataCollectedDBService,
    private val ticketClient: ReservationGateway,

    ): Logging {

    private val linkBaseUrl = env.config.property("odc.linkBaseUrl").getString()

    suspend fun generateLink(call: ApplicationCall) {
        val req = call.receive<Map<String, String>>()
        val resId: String = req["resId"]?.let {
            Checksum.decode(it, Environment.secretKey)
        } ?: run {
            logger.error("missing resId")
            return call.respond(HttpStatusCode.BadRequest, "missing resId")
        }

        if (odCollectorDBS.getDataById(resId) != null) return call.respond("$linkBaseUrl/${resId}")

        val reservation = ticketClient.getReservation(resId) ?: run {
            logger.error("reservation {} not found", resId)
            return call.respond(HttpStatusCode.NotFound, "not found reservation")
        }

        val data = OrderDataCollector(
            id = resId,
            showId = reservation.showId,
            sessionId = reservation.sessionId,
            resId = resId,
            tickets = reservation.detailsList.map { TicketReservationDetail(it.ticketId, it.quantity, it.priceCat, it.sid, it.capacityId) }
        )

        odCollectorDBS.saveCollector(data) ?: run {
            logger.error("data collector {} cannot save to db", data)
            return call.respond(HttpStatusCode.InternalServerError, "unknown")
        }

        return call.respond("$linkBaseUrl/${Checksum.encode(resId, Environment.secretKey)}")
    }

    suspend fun loadOrderDataCollectorTemplate(call: ApplicationCall) {
        val id: String = call.parameters["id"]?.let {
            Checksum.decode(it, Environment.secretKey)
        } ?: run {
            logger.error("missing id")
            return call.respond(HttpStatusCode.BadRequest, "missing id")
        }
        val odc = odCollectorDBS.getDataById(id) ?: run {
            logger.error("data collector {} not found", id)
            return call.respond(HttpStatusCode.NotFound, "not found order data collector")
        }

        val mapping = odcMappingDBS.getFieldMappingByShowAndSession(odc.showId, odc.sessionId) ?: run {
            logger.error("data collector {} not found mapping show {}, session {}", id, odc.showId, odc.sessionId)
            return call.respond(HttpStatusCode.NotFound, "not found mapping")
        }

        val priceCategory: HashMap<String, List<CustomFieldTmpl>> = HashMap()
        val individualTicket: HashMap<String, ArrayList<List<CustomFieldTmpl>>> = HashMap()

        odc.tickets.forEach { tk ->
            mapping.priceCategories.get(tk.priceCat)?.let { tmpls -> priceCategory.set(tk.priceCat, tmpls) }
            mapping.individualTickets.get(tk.priceCat)?.let { tmpls ->
                val ls = individualTicket.getOrPut(tk.priceCat) { ArrayList() }
                (1..tk.quantity).forEach { _ -> ls.add(tmpls) }
            }
        }

        val tmpl = OrderDataCollectorTemplateResponse(
            id = Checksum.encode(id, Environment.secretKey),
            showId = odc.showId,
            sessionId = odc.sessionId,
            resId = Checksum.encode(odc.resId, Environment.secretKey),
            eventName = mapping.eventName,
            faviconUrl = mapping.faviconUrl,
            priceCategories = priceCategory,
            individualTickets = individualTicket,
            dataCollectedId = odc.dataCollectedId?.let { Checksum.encode(it, Environment.secretKey) },
        )

        return call.respond(HttpStatusCode.OK, tmpl)
    }

    suspend fun saveOrderDataCollected(call: ApplicationCall) {
        val req = call.receive<SaveOrderDataCollectedRequest>().apply {
            this.id = Checksum.decode(this.id, Environment.secretKey)
        }
        val odCollector = odCollectorDBS.getDataById(req.id) ?: run {
            logger.error("data collector {} not found", req.id)
            return call.respond(HttpStatusCode.NotFound, "not found order data collector")
        }

        val mapping = odcMappingDBS.getFieldMappingByShowAndSession(odCollector.showId, odCollector.sessionId) ?: run {
            logger.error("data collector {} not found mapping show {}, session {}", odCollector.id, odCollector.showId, odCollector.sessionId)
            return call.respond(HttpStatusCode.NotFound, "not found mapping")
        }

        val priceCategoriesTmpl: HashMap<String, List<CustomFieldTmpl>> = HashMap()
        val individualTicketsTmpl: HashMap<String, ArrayList<List<CustomFieldTmpl>>> = HashMap()

        odCollector.tickets.forEach { tk ->
            mapping.priceCategories.get(tk.priceCat)?.let { tmpls -> priceCategoriesTmpl.set(tk.priceCat, tmpls) }
            mapping.individualTickets.get(tk.priceCat)?.let { tmpls ->
                val ls = individualTicketsTmpl.getOrPut(tk.priceCat) { ArrayList() }
                (1..tk.quantity).forEach { _ -> ls.add(tmpls) }
            }
        }

        //validate data collector
        var status: OrderDataCollectedStatus = OrderDataCollectedStatus.INVALID
        var isValidAll = true
        val validationCustomField = ValidationCustomField()
        priceCategoriesTmpl.map { priceCategoryTmpl ->
            req.priceCategories[priceCategoryTmpl.key]?.let { priceCategories ->
                if(isValidAll) {
                    isValidAll = validationCustomField.isCustomFieldsValid(priceCategoryTmpl.value, priceCategories, jsonMapper)
                }
            }
        }
        if(isValidAll) {
            individualTicketsTmpl.map { listIndividualTicketsTmpl ->
                req.individualTickets[listIndividualTicketsTmpl.key]?.let { individualTickets ->
                    for(listCustomFieldsData in individualTickets) {
                        if(isValidAll) {
                            isValidAll = validationCustomField.isCustomFieldsValid(listIndividualTicketsTmpl.value[0], listCustomFieldsData, jsonMapper)
                        }
                    }
                }
            }
        }
        if(!isValidAll) {
            logger.error("invalid data collected {}, template {}", req, mapping)
            return call.respond(HttpStatusCode.BadRequest, OrderDataCollectedStatus.INVALID)
        }

        status = OrderDataCollectedStatus.VALID

        val orderDataCollected = OrderDataCollected(
            id = req.id,
            showId = odCollector.showId,
            sessionId = odCollector.sessionId,
            resId = odCollector.resId,
            individualTickets = req.individualTickets,
            priceCategories = req.priceCategories,
            status = status,
        )

        val saved = odCollectedDBS.saveData(orderDataCollected);
        if (!saved) {
            logger.error("data collected {} cannot save", orderDataCollected)
            return call.respond(HttpStatusCode.InternalServerError, "cannot save data")
        }

        logger.info("data collected {} saved success", orderDataCollected)

        odCollectorDBS.assignCollectedIdAndStatus(req.id, status) ?: run {
            logger.error("data collector {} cannot assigned collected id", req.id)
            return call.respond(HttpStatusCode.InternalServerError, "cannot assign collected id")
        }

        return call.respond(HttpStatusCode.OK, status)
    }

    suspend fun loadOrderDataCollected(call: ApplicationCall) {
        val id: String = call.parameters["id"]?.let {
            Checksum.decode(it, Environment.secretKey)
        } ?: run {
            logger.error("missing id")
            return call.respond(HttpStatusCode.BadRequest, "missing id")
        }
        val data = odCollectedDBS.getDataById(id) ?: run {
            logger.error("data collected {} not found", id)
            return call.respond(HttpStatusCode.NotFound, "not found data collected")
        }
        val b = data.individualTickets.map { (k: String, v: List<ListCustomFieldData>) ->
            k to v.map { ls: ListCustomFieldData ->
                (ls as List<Document>).map {
                    jsonMapper.readValue(it.toJson(), CustomFieldData::class.java)
                }
            }
        }.associate { it }
        val response = OrderDataCollectedResponse(
            id = Checksum.encode(data.id, Environment.secretKey),
            showId = data.showId,
            sessionId = data.sessionId,
            resId = Checksum.encode(data.resId, Environment.secretKey),
            individualTickets = b,
            priceCategories = data.priceCategories,
            status = data.status,
        )

        return call.respond(HttpStatusCode.OK, response)
    }

    suspend fun checkOrderDataCollectedIsValid(call: ApplicationCall) {
        val link = call.receive<String>()
        if (link.isBlank()) {
            logger.error("missing link")
            return call.respond(HttpStatusCode.BadRequest, "missing link")
        }
        val id = link.removePrefix("$linkBaseUrl/").trim().let { Checksum.decode(it, Environment.secretKey) }
        if (!ObjectId.isValid(id)) {
            logger.error("cannot parse id from link")
            return call.respond(HttpStatusCode.BadRequest, "missing id")
        }
        val data = odCollectedDBS.getDataById(id) ?: run {
            logger.error("data collected {} not found", id)
            return call.respond(HttpStatusCode.OK, OrderDataCollectedStatus.EMPTY)
        }
        return call.respond(HttpStatusCode.OK, data.status)
    }
}
