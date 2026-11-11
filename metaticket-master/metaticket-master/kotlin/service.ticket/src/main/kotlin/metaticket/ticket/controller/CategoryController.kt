package metaticket.ticket.controller

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import metaticket.common.logger.Logging
import metaticket.ticket.cache.ShowSessionCache
import metaticket.ticket.calendar.isCalendar
import metaticket.ticket.database.service.CategoryMappingDBService
import metaticket.ticket.database.service.TicketDocDBService
import metaticket.ticket.database.service.TicketReservationDBService
import metaticket.ticket.pojo.db.CategoryItemDocument
import metaticket.ticket.pojo.db.CategoryMappingDocument
import metaticket.ticket.pojo.request.AddOrUpdateCategoryMappingRequest
import metaticket.ticket.pojo.request.CreateCategoryMappingRequest
import metaticket.ticket.pojo.request.DeleteCategoryMappingRequest
import metaticket.ticket.pojo.response.toResponse
import metaticket.ticket.redis.RedisAsyncClient
import java.util.*


/**
 *
 * @author dongcatchay
 */
class CategoryController constructor(
    private val showSessionCache: ShowSessionCache,
    private val categoryMappingDB: CategoryMappingDBService,
    private val redisAsyncClient: RedisAsyncClient,
    private val ticketDocDBService: TicketDocDBService,
    private val reservationDBService: TicketReservationDBService,
): Logging {

    suspend fun createCategoryMapping(call: ApplicationCall) {
        val opUserId = call.request.headers.get("X-OP-User-Id") ?: kotlin.run {
            // No user IDs available yet for this log message
            logger.error("Get op user profile but missing user id in header, maybe user haven't logged in")
            return call.respond(HttpStatusCode.Unauthorized, "Không xác định người dùng")
        }

        val req = call.receive<CreateCategoryMappingRequest>()

        if (req.category.category.isBlank()) {
            return call.respond(HttpStatusCode.BadRequest, "Hãy chọn category")
        }

        val exist = categoryMappingDB.findByShowAndSession(req.showId, req.sessionId)
        val document = if (exist == null) {
            val doc = CategoryMappingDocument(
                showId = req.showId,
                sessionId = req.sessionId,
                mapId = req.mapId,
                kindMap = req.kindMap,
                catMapping = mapOf(
                    req.category.category to CategoryItemDocument(
                        category = req.category.category,
                        displayName = req.category.displayName,
                        price = req.category.price,
                        color = req.category.color,
                        openDate = req.category.openDate?.let { Date(it) },
                        closeDate = req.category.closeDate?.let { Date(it) },
                    )
                )
            )
            val id = categoryMappingDB.insert(doc).insertedId ?: run {
                logger.error("createCategoryMapping, user {}, cannot create category mapping {}", opUserId, req)
                return call.respond(HttpStatusCode.InternalServerError, "Lỗi hệ thống")
            }
            doc
        } else {
            if (exist.catMapping.get(req.category.category) != null) {
                logger.error("createCategoryMapping, user {}, category was created already {}", opUserId, req)
                return call.respond(HttpStatusCode.Conflict, "category đã được tạo rồi")
            }
            categoryMappingDB.addCategory(
                showId = req.showId,
                sessionId = req.sessionId,
                cat = CategoryItemDocument(
                    category = req.category.category,
                    displayName = req.category.displayName,
                    price = req.category.price,
                    color = req.category.color,
                    openDate = req.category.openDate?.let { Date(it) },
                    closeDate = req.category.closeDate?.let { Date(it) },
                )
            ) ?: run {
                logger.error("createCategoryMapping, user {}, show {} session {} cannot insert category mapping request {}", opUserId, req.showId, req.sessionId, req)
                return call.respond(HttpStatusCode.InternalServerError, "Lỗi hệ thống")
            }
        }
        logger.info("createCategoryMapping, user {}, category mapping {} is created", opUserId, document)
        return call.respond(HttpStatusCode.OK, document.toResponse())
    }

    suspend fun addOrUpdateCategoryMapping(call: ApplicationCall) {
        val opUserId = call.request.headers.get("X-OP-User-Id") ?: kotlin.run {
            // No user IDs available yet for this log message
            logger.error("Get op user profile but missing user id in header, maybe user haven't logged in")
            return call.respond(HttpStatusCode.Unauthorized, "Không xác định người dùng")
        }

        val req = call.receive<AddOrUpdateCategoryMappingRequest>()

        val checking = showSessionCache.checkShowSessionInOpen(req.showId, req.sessionId) ?: kotlin.run {
            logger.error("addOrUpdateCategoryMapping, user {}, show {} session {} not found", opUserId, req.showId, req.sessionId)
            return call.respond(HttpStatusCode.InternalServerError, "Không tải được suất diễn")
        }
        if (checking) {
            logger.error("addOrUpdateCategoryMapping, user {}, show {} session {} is open state, cannot update cat mapping {}", opUserId, req.showId, req.sessionId, req)
            return call.respond(HttpStatusCode.Forbidden, "Suất diễn đã được mở bán")
        }

        var updatedTicketInDb = 0L

        if (req.oldCat != null) {
            val ordering =
                reservationDBService.loadReservationOrderingByTicketCat(req.showId, req.sessionId, req.oldCat)
                    .map { it.id }
            if (ordering.isNotEmpty()) {
                logger.error(
                    "addOrUpdateCategoryMapping, user {}, show {} session {}, cannot update cat mapping {}, order is paying or pay success {}",
                    opUserId,
                    req.showId,
                    req.sessionId,
                    req.category.category,
                    ordering
                )
                return call.respond(HttpStatusCode.Forbidden, "Có đơn đang/đã thanh toán")
            }

            val tickets = ticketDocDBService.loadTicketDocByCat(req.showId, req.sessionId, req.oldCat)
            val ticketIds = tickets.map { it.id }

            if (ticketIds.isNotEmpty()) {
                val deletedOnRedis = redisAsyncClient.removeTicketRemCap(*ticketIds.toTypedArray())
                logger.info(
                    "addOrUpdateCategoryMapping, user {}, show {} session {} updating cat {}, deleted {} ticket on redis",
                    opUserId, req.showId, req.sessionId, req.category.category, deletedOnRedis
                )

                // Vé theo lịch có giá riêng từng ngày: giá của hạng chỉ là mặc định khi sinh lịch, không ghi đè xuống vé.
                val calendarSession = showSessionCache.get(req.showId, req.sessionId)?.isCalendar() == true
                updatedTicketInDb = if (calendarSession) {
                    ticketDocDBService.updateTicketPriceCat(ticketIds, req.category.displayName)
                } else {
                    ticketDocDBService.updateTicketPriceAndPriceCat(
                        ticketIds,
                        req.category.price,
                        req.category.displayName
                    )
                }
                logger.info(
                    "addOrUpdateCategoryMapping, user {}, show {} session {} updating cat {}, updated {} ticket in db",
                    opUserId, req.showId, req.sessionId, req.category.category, updatedTicketInDb
                )
            }

            val cat = categoryMappingDB.updateCategoryMapping(
                showId = req.showId,
                sessionId = req.sessionId,
                cat = CategoryItemDocument(
                    category = req.category.category,
                    displayName = req.category.displayName,
                    price = req.category.price,
                    color = req.category.color,
                    openDate = req.category.openDate?.let { Date(it) },
                    closeDate = req.category.closeDate?.let { Date(it) },
                )
            ) ?: run {
                logger.error("addOrUpdateCategoryMapping, user {}, show {} session {} cannot update category mapping request {}", opUserId, req.showId, req.sessionId, req)
                return call.respond(HttpStatusCode.InternalServerError, "Lỗi hệ thống")
            }
        } else {
            val cat = categoryMappingDB.addCategory(
                showId = req.showId,
                sessionId = req.sessionId,
                cat = CategoryItemDocument(
                    category = req.category.category,
                    displayName = req.category.displayName,
                    price = req.category.price,
                    color = req.category.color,
                    openDate = req.category.openDate?.let { Date(it) },
                    closeDate = req.category.closeDate?.let { Date(it) },
                )
            ) ?: run {
                logger.error("addOrUpdateCategoryMapping, user {}, show {} session {} cannot add category request {}", opUserId, req.showId, req.sessionId, req)
                return call.respond(HttpStatusCode.InternalServerError, "Lỗi hệ thống")
            }
        }

        logger.info("addOrUpdateCategoryMapping, user {}, show {} session {} updated category {}, updated {} ticket", opUserId, req.showId, req.sessionId, req, updatedTicketInDb)
        return call.respond(HttpStatusCode.OK, mapOf("updatedCat" to req.category.category, "noTicketUpdated" to updatedTicketInDb))
    }

    suspend fun deleteCategoryMapping(call: ApplicationCall) {
        val opUserId = call.request.headers.get("X-OP-User-Id") ?: kotlin.run {
            // No user IDs available yet for this log message
            logger.error("Get op user profile but missing user id in header, maybe user haven't logged in")
            return call.respond(HttpStatusCode.Unauthorized, "Không xác định người dùng")
        }

        val req = call.receive<DeleteCategoryMappingRequest>()

        val checking = showSessionCache.checkShowSessionInOpen(req.showId, req.sessionId) ?: kotlin.run {
            logger.error("deleteCategoryMapping, user {}, show {} session {} not found", opUserId, req.showId, req.sessionId)
            return call.respond(HttpStatusCode.InternalServerError, "Không tải được suất diễn")
        }
        if (checking) {
            logger.error("deleteCategoryMapping, user {}, show {} session {} is open state, cannot delete cat mapping {}", opUserId, req.showId, req.sessionId, req.category)
            return call.respond(HttpStatusCode.Forbidden, "Suất diễn đã được mở bán")
        }

        val catMapping = categoryMappingDB.findByShowAndSessionCat(req.showId, req.sessionId, req.category)?.catMapping?.get(req.category)
            ?: kotlin.run {
                logger.error("deleteCategoryMapping, user {}, show {} session {} cannot delete cat mapping {}, not found cat mapping", opUserId, req.showId, req.sessionId, req.category)
                return call.respond(HttpStatusCode.NotFound, "Không tìm thấy category")
            }

        val ordering = reservationDBService.loadReservationOrderingByTicketCat(req.showId, req.sessionId, catMapping.displayName)
            .map { it.id }
        if (ordering.isNotEmpty()) {
            logger.error("deleteCategoryMapping, user {}, show {} session {}, cannot delete cat mapping {}, order is paying or pay success {}", opUserId, req.showId, req.sessionId, req.category, ordering)
            return call.respond(HttpStatusCode.Forbidden, "Có đơn đang/đã thanh toán")
        }

        val tickets = ticketDocDBService.loadTicketDocByCat(req.showId, req.sessionId, catMapping.displayName)
        val ticketIds = tickets.map { it.id }

        var deletedInDb = 0L
        if (ticketIds.isNotEmpty()) {
            val deletedOnRedis = redisAsyncClient.removeTicketRemCap(*ticketIds.toTypedArray())
            logger.info("deleteCategoryMapping, user {}, show {} session {} deleting cat {}, deleted {} ticket on redis", opUserId, req.showId, req.sessionId, req.category, deletedOnRedis)

            deletedInDb = ticketDocDBService.deleteTickets(ticketIds)
            logger.info("deleteCategoryMapping, user {}, show {} session {} deleting cat {}, deleted {} ticket in db", opUserId, req.showId, req.sessionId, req.category, deletedInDb)
        }

        categoryMappingDB.deleteCategoryMapping(req.showId, req.sessionId, req.category) ?: run {
            logger.error("deleteCategoryMapping, user {}, show {} session {} cannot delete category mapping {} in db", opUserId, req.showId, req.sessionId, req.category)
            return call.respond(HttpStatusCode.InternalServerError, "Lỗi hệ thống")
        }

        logger.info("deleteCategoryMapping, user {}, show {} session {} deleted category {}, deleted {} ticket", opUserId, req.showId, req.sessionId, req.category, deletedInDb)
        return call.respond(HttpStatusCode.OK, mapOf("deletedCat" to req.category, "noTicketDeleted" to deletedInDb))
    }

    suspend fun loadCategoryMappingByShowAndSession(call: ApplicationCall) {
        val showId: String = call.parameters["showId"] ?: run {
            logger.error("missing showId")
            return call.respond(HttpStatusCode.BadRequest, "Thiếu show id")
        }
        val sessionId = call.parameters["sessionId"] ?: run {
            logger.error("missing sessionId")
            return call.respond(HttpStatusCode.BadRequest, "Thiếu session id")
        }

        val doc = categoryMappingDB.findByShowAndSession(showId, sessionId) ?: run {
            logger.debug("not found category mapping for show {} session {}", showId, sessionId)
            return call.respond(HttpStatusCode.OK)
        }

        return call.respond(HttpStatusCode.OK, doc)
    }
}
