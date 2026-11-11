package metaticket.accessory.controller

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.JsonNodeFactory
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import metaticket.accessory.database.CatalogDatabaseService
import metaticket.accessory.database.DiscountDatabaseService
import metaticket.accessory.database.AccessoryReportFilterDatabaseService
import metaticket.accessory.database.OrderDatabaseService
import metaticket.accessory.pojo.db.AccessoryOrderStatus
import metaticket.accessory.pojo.db.AccessoryReportFilterCriteria
import metaticket.accessory.pojo.db.AccessoryReportFilterDocument
import metaticket.accessory.pojo.db.AccessoryReportFilterStatus
import metaticket.accessory.pojo.db.AccessoryShowDocument
import metaticket.accessory.pojo.db.AccessoryShowStatus
import metaticket.accessory.pojo.db.AccessoryValidationException
import metaticket.accessory.pojo.db.validate
import metaticket.accessory.pojo.request.AdminAccessoryActiveRequest
import metaticket.accessory.pojo.request.AdminAccessoryCatalogUpdateRequest
import metaticket.accessory.pojo.request.AdminAccessoryStockAdjustRequest
import metaticket.accessory.pojo.request.AdminAccessoryShowPinRequest
import metaticket.accessory.pojo.request.AdminAccessoryShowStatusRequest
import metaticket.accessory.pojo.request.AdminAccessoryShowUpsertRequest
import metaticket.accessory.pojo.request.ConfirmAccessoryPinsRequest
import metaticket.accessory.pojo.request.AdminAccessoryUpsertRequest
import metaticket.accessory.pojo.request.AdminDiscountActiveRequest
import metaticket.accessory.pojo.request.AdminDiscountBatchCreateRequest
import metaticket.accessory.pojo.request.AdminDiscountUpsertRequest
import metaticket.accessory.pojo.request.AdminReportFilterCreateRequest
import metaticket.accessory.pojo.request.AdminReportFilterIdRequest
import metaticket.accessory.pojo.request.CheckoutInput
import metaticket.accessory.pojo.request.CheckoutValidationCode
import metaticket.accessory.pojo.request.CheckoutValidationError
import metaticket.accessory.pojo.request.CheckoutValidationException
import metaticket.accessory.pojo.request.CheckoutValidator
import metaticket.accessory.pojo.request.DiscountPreviewRequest
import metaticket.accessory.pojo.request.DiscountPreviewResponse
import metaticket.accessory.pojo.request.adminFieldErrors
import metaticket.accessory.pojo.request.catalogUpdateValidationErrors
import metaticket.accessory.pojo.request.productValidationErrors
import metaticket.accessory.pojo.request.stockAdjustValidationErrors
import metaticket.accessory.pojo.request.toNewAccessoryDocument
import metaticket.accessory.pojo.request.toNewDiscountDocument
import metaticket.accessory.pojo.request.toNewDocument
import metaticket.accessory.pojo.request.toUpdatedAccessoryDocument
import metaticket.accessory.pojo.request.toUpdatedDiscountDocument
import metaticket.accessory.pojo.request.toUpdatedDocument
import metaticket.accessory.pojo.request.validationErrors
import metaticket.accessory.pojo.request.variantKeyLockErrors
import metaticket.accessory.pojo.response.ACCESSORY_PIN_SLOT_MAX
import metaticket.accessory.pojo.response.AccessoryPinBoardResponse
import metaticket.accessory.pojo.response.AccessoryPinCandidatePage
import metaticket.accessory.pojo.response.AccessoryPinSlotResponse
import metaticket.accessory.pojo.response.AdminDiscountBatchCreateResponse
import metaticket.accessory.pojo.response.AdminDiscountBatchFailure
import metaticket.accessory.pojo.response.AdminOrderListResponse
import metaticket.accessory.pojo.response.AdminOrderExportResponse
import metaticket.accessory.pojo.response.PublicOrderExportResponse
import metaticket.accessory.pojo.response.PublicSalesReportResponse
import metaticket.accessory.pojo.response.computeAdminOrderStats
import metaticket.accessory.pojo.response.computeAdminSalesStatistic
import metaticket.accessory.pojo.response.toAdminExportItem
import metaticket.accessory.pojo.response.toAdminItem
import metaticket.accessory.pojo.response.toAdminListItem
import metaticket.accessory.pojo.response.toDetailResponse
import metaticket.accessory.pojo.response.toSummaryResponse
import metaticket.accessory.pojo.response.AccessoryShowListPage
import metaticket.accessory.pojo.response.toPinSummary
import metaticket.accessory.pojo.response.toExportProductColumn
import metaticket.accessory.pojo.response.toPublicExportItem
import metaticket.accessory.pojo.response.toResponse
import metaticket.accessory.security.PublicId
import metaticket.accessory.service.AccessoryOrderService
import metaticket.accessory.service.AccessoryShowOpenService
import metaticket.accessory.service.CheckoutException
import metaticket.accessory.service.DiscountCodeService
import metaticket.accessory.service.OrderAccessException
import metaticket.accessory.service.SlugNormalizer
import metaticket.common.logger.Logging
import com.mongodb.ErrorCategory
import com.mongodb.MongoCommandException
import com.mongodb.MongoException
import com.mongodb.MongoWriteException
import org.bson.types.ObjectId
import java.util.Date
import java.util.UUID

/**
 * `Cache-Control` for the public read endpoints that carry live stock (single-accessory fetch and
 * single-show fetch). A short shared max-age lets Cloudflare/browsers collapse the storefront's
 * frequent stock polls into far fewer origin hits; `stale-while-revalidate` serves the last value
 * instantly while a fresh one is fetched in the background. Staleness is safe here — the stock shown
 * is only a UX hint and overselling is prevented authoritatively at checkout (reserve-on-create).
 */
private const val PUBLIC_STOCK_CACHE_CONTROL = "public, max-age=5, stale-while-revalidate=10"
private const val PUBLIC_SHOW_LIST_DEFAULT_SIZE = 6
private const val PUBLIC_SHOW_LIST_MAX_SIZE = 50

/**
 * HTTP surface for the accessory storefront. Follows the platform route conventions
 * (/public unauthenticated, /private internal) and is reached by metaticket-home through the
 * nginx public/accessory path prefix.
 *
 * This controller hosts the public read endpoints (task 12.2) plus the checkout and discount-preview
 * endpoints (task 12.3). Order lookup and the private resend endpoint are wired in task 12.4.
 *
 * All identifiers exposed in responses are Checksum-encoded (Req 16.1); every public id input is
 * decoded and validated via [PublicId] before it reaches the database layer (Req 16.2).
 */
class AccessoryController(
    private val catalogDb: CatalogDatabaseService,
    private val orderService: AccessoryOrderService,
    private val discountService: DiscountCodeService,
    private val discountDb: DiscountDatabaseService,
    private val orderDb: OrderDatabaseService,
    private val reportFilterDb: AccessoryReportFilterDatabaseService,
    private val objectMapper: ObjectMapper,
    private val jsonFactory: JsonNodeFactory,
    private val showOpenService: AccessoryShowOpenService,
) : Logging {

    /**
     * GET /private/accessory/show/fetch/all — admin catalog list (Open + Closed).
     *
     * Returns raw Mongo ids and admin-facing fields (payment/mail/fulfillment defaults, product
     * counts). Local hybrid currently has no JWT gate on `/private/accessory`; do not expose this
     * route unauthenticated outside trusted networks.
     */
    suspend fun fetchAllShowsForAdmin(call: ApplicationCall) {
        val shows = catalogDb.loadAllShows()
        val counts = catalogDb.countAccessoriesByShowIds(shows.map { it.id })
        val items = shows.map { show -> show.toAdminListItem(counts[show.id] ?: 0) }
        call.respond(HttpStatusCode.OK, items)
    }

    /**
     * GET /private/accessory/show/fetch?id= — one show for the admin edit form (any status).
     * Missing/malformed id → 400; unknown id → 404.
     */
    suspend fun fetchShowForAdmin(call: ApplicationCall) {
        val rawId = call.parameters["id"]
        if (rawId.isNullOrBlank() || !ObjectId.isValid(rawId)) {
            return call.respond(
                HttpStatusCode.BadRequest,
                jsonFactory.objectNode().put("message", "Missing or malformed id"),
            )
        }
        val show = catalogDb.loadShowById(rawId) ?: return call.respond(
            HttpStatusCode.NotFound,
            jsonFactory.objectNode().put("message", "Accessory show not found"),
        )
        val count = catalogDb.countAccessoriesByShowIds(listOf(show.id))[show.id] ?: 0
        call.respond(HttpStatusCode.OK, show.toAdminListItem(count))
    }

    /**
     * POST /private/accessory/show/create — insert a new accessory show from the admin form.
     * Duplicate `code` → 409; validation failures → 400.
     */
    suspend fun createShowForAdmin(call: ApplicationCall) {
        val body = receiveAdminUpsert(call) ?: return
        val errors = body.validationErrors(requireCode = true)
        if (errors.isNotEmpty()) {
            return call.respond(
                HttpStatusCode.BadRequest,
                jsonFactory.objectNode().put("message", errors.joinToString("; ")),
            )
        }
        val code = body.code!!.trim()
        if (catalogDb.loadShowByCode(code) != null) {
            return call.respond(
                HttpStatusCode.Conflict,
                jsonFactory.objectNode().put("message", "Accessory show code already exists: $code"),
            )
        }
        val saved = try {
            catalogDb.insertShow(body.toNewDocument().validate())
        } catch (e: AccessoryValidationException) {
            return call.respond(
                HttpStatusCode.BadRequest,
                jsonFactory.objectNode().put("message", e.errors.joinToString("; ")),
            )
        } catch (e: Exception) {
            if (isDuplicateSlugError(e)) {
                val slug = body.slug?.trim().orEmpty()
                return call.respond(
                    HttpStatusCode.Conflict,
                    jsonFactory.objectNode().put("message", "Duplicate slug").put("slug", slug),
                )
            }
            throw e
        }
        logger.info("admin created accessory show id={} code={}", saved.id, saved.code)
        showOpenService.afterShowPersisted(previousStatus = null, previousSaleOpenAt = null, saved = saved)
        call.respond(HttpStatusCode.OK, saved.toAdminListItem(0))
    }

    /**
     * POST /private/accessory/show/update?id= — replace mutable fields on an existing show.
     * `code` is immutable (taken from the existing document). Unknown id → 404.
     */
    suspend fun updateShowForAdmin(call: ApplicationCall) {
        val rawId = call.parameters["id"]
        if (rawId.isNullOrBlank() || !ObjectId.isValid(rawId)) {
            return call.respond(
                HttpStatusCode.BadRequest,
                jsonFactory.objectNode().put("message", "Missing or malformed id"),
            )
        }
        val body = receiveAdminUpsert(call) ?: return
        val errors = body.validationErrors(requireCode = false)
        if (errors.isNotEmpty()) {
            return call.respond(
                HttpStatusCode.BadRequest,
                jsonFactory.objectNode().put("message", errors.joinToString("; ")),
            )
        }
        val existing = catalogDb.loadShowById(rawId) ?: return call.respond(
            HttpStatusCode.NotFound,
            jsonFactory.objectNode().put("message", "Accessory show not found"),
        )
        val updated = try {
            body.toUpdatedDocument(existing).validate()
        } catch (e: AccessoryValidationException) {
            return call.respond(
                HttpStatusCode.BadRequest,
                jsonFactory.objectNode().put("message", e.errors.joinToString("; ")),
            )
        }
        val saved = try {
            catalogDb.replaceShow(updated) ?: return call.respond(
                HttpStatusCode.NotFound,
                jsonFactory.objectNode().put("message", "Accessory show not found"),
            )
        } catch (e: Exception) {
            if (isDuplicateSlugError(e)) {
                val slug = body.slug?.trim().orEmpty()
                return call.respond(
                    HttpStatusCode.Conflict,
                    jsonFactory.objectNode().put("message", "Duplicate slug").put("slug", slug),
                )
            }
            throw e
        }
        val count = catalogDb.countAccessoriesByShowIds(listOf(saved.id))[saved.id] ?: 0
        logger.info("admin updated accessory show id={} code={}", saved.id, saved.code)
        showOpenService.afterShowPersisted(existing.status, existing.saleOpenAt, saved)
        call.respond(HttpStatusCode.OK, saved.toAdminListItem(count))
    }

    /**
     * POST /private/accessory/show/status?id= — set Open / Waiting / Closed for the admin list toggle.
     * Body: `{ "status": "Open" | "Waiting" | "Closed" }`.
     */
    suspend fun setShowStatusForAdmin(call: ApplicationCall) {
        val rawId = call.parameters["id"]
        if (rawId.isNullOrBlank() || !ObjectId.isValid(rawId)) {
            return call.respond(
                HttpStatusCode.BadRequest,
                jsonFactory.objectNode().put("message", "Missing or malformed id"),
            )
        }
        val body: AdminAccessoryShowStatusRequest = try {
            call.receive()
        } catch (t: Throwable) {
            return call.respond(
                HttpStatusCode.BadRequest,
                jsonFactory.objectNode().put("message", "Malformed status payload"),
            )
        }
        val statusName = body.status?.trim().orEmpty()
        val status = runCatching { AccessoryShowStatus.valueOf(statusName) }.getOrNull()
            ?: return call.respond(
                HttpStatusCode.BadRequest,
                jsonFactory.objectNode().put("message", "`status` must be Open, Waiting, or Closed"),
            )
        val existing = catalogDb.loadShowById(rawId) ?: return call.respond(
            HttpStatusCode.NotFound,
            jsonFactory.objectNode().put("message", "Accessory show not found"),
        )
        val saved = catalogDb.updateShowStatus(rawId, status) ?: return call.respond(
            HttpStatusCode.NotFound,
            jsonFactory.objectNode().put("message", "Accessory show not found"),
        )
        val count = catalogDb.countAccessoriesByShowIds(listOf(saved.id))[saved.id] ?: 0
        logger.info("admin set accessory show id={} status={}", saved.id, saved.status)
        showOpenService.afterShowPersisted(existing.status, existing.saleOpenAt, saved)
        call.respond(HttpStatusCode.OK, saved.toAdminListItem(count))
    }

    /**
     * POST /private/accessory/show/pin?id= — set or clear admin pin order.
     * Body: `{ "pinOrder": <positive int> | null }`. Empty/null clears; `<= 0` → 400.
     */
    suspend fun setShowPinForAdmin(call: ApplicationCall) {
        val rawId = call.parameters["id"]
        if (rawId.isNullOrBlank() || !ObjectId.isValid(rawId)) {
            return call.respond(
                HttpStatusCode.BadRequest,
                jsonFactory.objectNode().put("message", "Missing or malformed id"),
            )
        }
        val body: AdminAccessoryShowPinRequest = try {
            call.receive()
        } catch (t: Throwable) {
            return call.respond(
                HttpStatusCode.BadRequest,
                jsonFactory.objectNode().put("message", "Malformed pin payload"),
            )
        }
        val pinOrder = body.pinOrder
        if (pinOrder != null && (pinOrder <= 0 || pinOrder > ACCESSORY_PIN_SLOT_MAX)) {
            return call.respond(
                HttpStatusCode.BadRequest,
                jsonFactory.objectNode().put(
                    "message",
                    "`pinOrder` must be 1..$ACCESSORY_PIN_SLOT_MAX or null",
                ),
            )
        }
        val saved = catalogDb.updateShowPinOrder(rawId, pinOrder) ?: return call.respond(
            HttpStatusCode.NotFound,
            jsonFactory.objectNode().put("message", "Accessory show not found"),
        )
        val count = catalogDb.countAccessoriesByShowIds(listOf(saved.id))[saved.id] ?: 0
        logger.info("admin set accessory show id={} pinOrder={}", saved.id, saved.pinOrder)
        call.respond(HttpStatusCode.OK, saved.toAdminListItem(count))
    }

    /** Occupied ranks 1..max, omitting vacant slots so the board length stays flexible. */
    private suspend fun accessoryPinBoard(): AccessoryPinBoardResponse {
        val slots = catalogDb.loadPinnedShows(ACCESSORY_PIN_SLOT_MAX).mapNotNull { show ->
            val order = show.pinOrder ?: return@mapNotNull null
            AccessoryPinSlotResponse(pinOrder = order, show = show.toPinSummary())
        }
        return AccessoryPinBoardResponse(slots = slots)
    }

    /** Persist filled ids as pinOrder 1..n (n ≤ max) and return the board. */
    private suspend fun applyAccessoryPins(orderedIds: List<String>): AccessoryPinBoardResponse {
        catalogDb.replaceAccessoryPins(orderedIds)
        return accessoryPinBoard()
    }

    /** GET /private/accessory/show/pin/board — occupied accessory pin slots (max 5). */
    suspend fun getAccessoryPinBoard(call: ApplicationCall) {
        call.respond(HttpStatusCode.OK, accessoryPinBoard())
    }

    /** POST /private/accessory/show/pin/refresh — pin the newest-edited shows, up to max 5. */
    suspend fun refreshAccessoryPins(call: ApplicationCall) {
        val newest = catalogDb.loadMostRecentlyModifiedShows(ACCESSORY_PIN_SLOT_MAX)
        call.respond(HttpStatusCode.OK, applyAccessoryPins(newest.map { it.id }))
    }

    /** POST /private/accessory/show/pin/confirm — apply FE order; empty slots dropped; max 5 filled. */
    suspend fun confirmAccessoryPins(call: ApplicationCall) {
        val req: ConfirmAccessoryPinsRequest = try {
            call.receive()
        } catch (t: Throwable) {
            return call.respond(
                HttpStatusCode.BadRequest,
                jsonFactory.objectNode().put("message", "Malformed pin board payload"),
            )
        }
        if (req.slots.size > ACCESSORY_PIN_SLOT_MAX) {
            return call.respond(
                HttpStatusCode.BadRequest,
                jsonFactory.objectNode().put("message", "At most $ACCESSORY_PIN_SLOT_MAX pinned accessory shows"),
            )
        }
        val ordered = req.slots.sortedBy { it.pinOrder }
        val ids = mutableListOf<String>()
        val seen = mutableSetOf<String>()
        for (slot in ordered) {
            val id = slot.showId?.trim().orEmpty()
            if (id.isEmpty()) continue
            if (!ObjectId.isValid(id)) {
                return call.respond(
                    HttpStatusCode.BadRequest,
                    jsonFactory.objectNode().put("message", "Malformed showId").put("showId", id),
                )
            }
            if (!seen.add(id)) {
                return call.respond(
                    HttpStatusCode.BadRequest,
                    jsonFactory.objectNode().put("message", "Duplicate accessory show on the pin board"),
                )
            }
            ids += id
        }
        if (ids.size > ACCESSORY_PIN_SLOT_MAX) {
            return call.respond(
                HttpStatusCode.BadRequest,
                jsonFactory.objectNode().put("message", "At most $ACCESSORY_PIN_SLOT_MAX pinned accessory shows"),
            )
        }
        call.respond(HttpStatusCode.OK, applyAccessoryPins(ids))
    }

    /** GET /private/accessory/show/pin/candidates — paginated picker (page is 1-based). */
    suspend fun searchAccessoryPinCandidates(call: ApplicationCall) {
        val page = call.request.queryParameters["page"]?.toIntOrNull() ?: 1
        val size = call.request.queryParameters["size"]?.toIntOrNull() ?: ACCESSORY_PIN_SLOT_MAX
        val keyword = call.request.queryParameters["keyword"]
        val safeSize = size.coerceIn(1, 50)
        val result = catalogDb.searchShowsForPinPicker(page, safeSize, keyword)
        val totalPages = if (result.total == 0L) 0 else ((result.total + safeSize - 1) / safeSize).toInt()
        call.respond(
            HttpStatusCode.OK,
            AccessoryPinCandidatePage(
                content = result.items.map { it.toPinSummary() },
                page = page.coerceAtLeast(1),
                size = safeSize,
                totalElements = result.total,
                totalPages = totalPages,
            ),
        )
    }

    /**
     * GET /private/accessory/fetch/all?showId= — admin product list for one show (active + inactive).
     * Missing/malformed showId → 400; unknown show → 404.
     */
    suspend fun fetchAccessoriesForAdmin(call: ApplicationCall) {
        val showId = call.parameters["showId"]
        if (showId.isNullOrBlank() || !ObjectId.isValid(showId)) {
            return call.respond(
                HttpStatusCode.BadRequest,
                jsonFactory.objectNode().put("message", "Missing or malformed showId"),
            )
        }
        catalogDb.loadShowById(showId) ?: return call.respond(
            HttpStatusCode.NotFound,
            jsonFactory.objectNode().put("message", "Accessory show not found"),
        )
        val items = catalogDb.loadAccessoriesByShowId(showId).map { it.toAdminListItem() }
        call.respond(HttpStatusCode.OK, items)
    }

    /**
     * GET /private/accessory/fetch?id= — one product for the admin edit form (any active flag).
     */
    suspend fun fetchAccessoryForAdmin(call: ApplicationCall) {
        val rawId = call.parameters["id"]
        if (rawId.isNullOrBlank() || !ObjectId.isValid(rawId)) {
            return call.respond(
                HttpStatusCode.BadRequest,
                jsonFactory.objectNode().put("message", "Missing or malformed id"),
            )
        }
        val accessory = catalogDb.loadAccessoryById(rawId) ?: return call.respond(
            HttpStatusCode.NotFound,
            jsonFactory.objectNode().put("message", "Accessory not found"),
        )
        call.respond(HttpStatusCode.OK, accessory.toAdminListItem())
    }

    /**
     * POST /private/accessory/create — insert a product under an accessory show.
     * Duplicate (showId, sku) → 409.
     */
    suspend fun createAccessoryForAdmin(call: ApplicationCall) {
        val body = receiveAdminProductUpsert(call) ?: return
        val errors = body.productValidationErrors(requireShowId = true)
        if (errors.isNotEmpty()) {
            return call.respond(
                HttpStatusCode.BadRequest,
                jsonFactory.objectNode().put("message", errors.joinToString("; ")),
            )
        }
        val showId = body.accessoryShowId!!.trim()
        catalogDb.loadShowById(showId) ?: return call.respond(
            HttpStatusCode.NotFound,
            jsonFactory.objectNode().put("message", "Accessory show not found"),
        )
        val sku = body.sku!!.trim()
        if (catalogDb.loadAccessoryByShowIdAndSku(showId, sku) != null) {
            return call.respond(
                HttpStatusCode.Conflict,
                jsonFactory.objectNode().put("message", "SKU already exists in this show: $sku"),
            )
        }
        val saved = try {
            catalogDb.insertAccessory(body.toNewAccessoryDocument().validate())
        } catch (e: AccessoryValidationException) {
            return call.respond(
                HttpStatusCode.BadRequest,
                jsonFactory.objectNode().put("message", e.errors.joinToString("; ")),
            )
        }
        logger.info("admin created accessory id={} sku={} showId={}", saved.id, saved.sku, saved.accessoryShowId)
        call.respond(HttpStatusCode.OK, saved.toAdminListItem())
    }

    /**
     * POST /private/accessory/update?id= — update mutable catalog fields only (not stock).
     * Variant keys are immutable; `accessoryShowId` is kept from existing.
     * Inventory changes go through [adjustAccessoryStockForAdmin].
     * Body: [AdminAccessoryCatalogUpdateRequest] (no stock fields).
     */
    suspend fun updateAccessoryForAdmin(call: ApplicationCall) {
        val rawId = call.parameters["id"]
        if (rawId.isNullOrBlank() || !ObjectId.isValid(rawId)) {
            return call.respond(
                HttpStatusCode.BadRequest,
                jsonFactory.objectNode().put("message", "Missing or malformed id"),
            )
        }
        val body: AdminAccessoryCatalogUpdateRequest = try {
            call.receive()
        } catch (t: Throwable) {
            return call.respond(
                HttpStatusCode.BadRequest,
                jsonFactory.objectNode().put("message", "Malformed catalog update payload"),
            )
        }
        val errors = body.catalogUpdateValidationErrors()
        if (errors.isNotEmpty()) {
            return call.respond(
                HttpStatusCode.BadRequest,
                jsonFactory.objectNode().put("message", errors.joinToString("; ")),
            )
        }
        val existing = catalogDb.loadAccessoryById(rawId) ?: return call.respond(
            HttpStatusCode.NotFound,
            jsonFactory.objectNode().put("message", "Accessory not found"),
        )
        val keyErrors = body.variantKeyLockErrors(existing)
        if (keyErrors.isNotEmpty()) {
            return call.respond(
                HttpStatusCode.Conflict,
                jsonFactory.objectNode().put("message", keyErrors.joinToString("; ")),
            )
        }
        val sku = body.sku!!.trim()
        if (catalogDb.loadAccessoryByShowIdAndSku(existing.accessoryShowId, sku, excludeId = rawId) != null) {
            return call.respond(
                HttpStatusCode.Conflict,
                jsonFactory.objectNode().put("message", "SKU already exists in this show: $sku"),
            )
        }
        val updated = try {
            body.toUpdatedAccessoryDocument(existing).validate()
        } catch (e: AccessoryValidationException) {
            return call.respond(
                HttpStatusCode.BadRequest,
                jsonFactory.objectNode().put("message", e.errors.joinToString("; ")),
            )
        }
        // Never replaceOne the whole doc — that would clobber live reserved stock.
        // Pass [existing] so newly added variants are $push'd without rewriting existing stock cells.
        val saved = catalogDb.updateAccessoryCatalog(updated, existing) ?: return call.respond(
            HttpStatusCode.NotFound,
            jsonFactory.objectNode().put("message", "Accessory not found"),
        )
        logger.info("admin updated accessory catalog id={} sku={}", saved.id, saved.sku)
        call.respond(HttpStatusCode.OK, saved.toAdminListItem())
    }

    /**
     * POST /private/accessory/stock/adjust?id= — atomic sellable adjust (delta / setTo).
     * Does not replace other catalog fields. Cannot set unlimited (-1).
     */
    suspend fun adjustAccessoryStockForAdmin(call: ApplicationCall) {
        val rawId = call.parameters["id"]
        if (rawId.isNullOrBlank() || !ObjectId.isValid(rawId)) {
            return call.respond(
                HttpStatusCode.BadRequest,
                jsonFactory.objectNode().put("message", "Missing or malformed id"),
            )
        }
        val body: AdminAccessoryStockAdjustRequest = try {
            call.receive()
        } catch (t: Throwable) {
            return call.respond(
                HttpStatusCode.BadRequest,
                jsonFactory.objectNode().put("message", "Malformed stock adjust payload"),
            )
        }
        val shapeErrors = body.stockAdjustValidationErrors()
        if (shapeErrors.isNotEmpty()) {
            return call.respond(
                HttpStatusCode.BadRequest,
                jsonFactory.objectNode().put("message", shapeErrors.joinToString("; ")),
            )
        }
        val (outcome, saved) = catalogDb.adjustAccessoryStock(
            rawId = rawId,
            variantKey = body.variantKey,
            delta = body.delta,
            setTo = body.setTo,
        )
        when (outcome) {
            CatalogDatabaseService.StockAdjustOutcome.OK -> {
                logger.info(
                    "admin adjusted stock id={} variantKey={} delta={} setTo={}",
                    rawId, body.variantKey, body.delta, body.setTo,
                )
                call.respond(HttpStatusCode.OK, saved!!.toAdminListItem())
            }
            CatalogDatabaseService.StockAdjustOutcome.NOT_FOUND -> call.respond(
                HttpStatusCode.NotFound,
                jsonFactory.objectNode().put("message", "Accessory not found"),
            )
            CatalogDatabaseService.StockAdjustOutcome.CELL_NOT_FOUND -> call.respond(
                HttpStatusCode.NotFound,
                jsonFactory.objectNode().put("message", "Stock cell / variant not found"),
            )
            CatalogDatabaseService.StockAdjustOutcome.UNLIMITED_BLOCKED -> call.respond(
                HttpStatusCode.Conflict,
                jsonFactory.objectNode().put(
                    "message",
                    "Cell is unlimited (-1); use setTo to set a finite sellable",
                ),
            )
            CatalogDatabaseService.StockAdjustOutcome.INSUFFICIENT -> call.respond(
                HttpStatusCode.Conflict,
                jsonFactory.objectNode().put(
                    "message",
                    "Không đủ tồn để giảm (có thể đang bị giữ bởi đơn chờ thanh toán)",
                ),
            )
            CatalogDatabaseService.StockAdjustOutcome.INVALID -> call.respond(
                HttpStatusCode.BadRequest,
                jsonFactory.objectNode().put("message", "Invalid stock adjust for this product shape"),
            )
        }
    }

    /**
     * POST /private/accessory/active?id= — set active true/false for the admin list toggle.
     * Body: `{ "active": true | false }`.
     */
    suspend fun setAccessoryActiveForAdmin(call: ApplicationCall) {
        val rawId = call.parameters["id"]
        if (rawId.isNullOrBlank() || !ObjectId.isValid(rawId)) {
            return call.respond(
                HttpStatusCode.BadRequest,
                jsonFactory.objectNode().put("message", "Missing or malformed id"),
            )
        }
        val body: AdminAccessoryActiveRequest = try {
            call.receive()
        } catch (t: Throwable) {
            return call.respond(
                HttpStatusCode.BadRequest,
                jsonFactory.objectNode().put("message", "Malformed active payload"),
            )
        }
        val active = body.active
            ?: return call.respond(
                HttpStatusCode.BadRequest,
                jsonFactory.objectNode().put("message", "`active` is required"),
            )
        val saved = catalogDb.updateAccessoryActive(rawId, active) ?: return call.respond(
            HttpStatusCode.NotFound,
            jsonFactory.objectNode().put("message", "Accessory not found"),
        )
        logger.info("admin set accessory id={} active={}", saved.id, saved.active)
        call.respond(HttpStatusCode.OK, saved.toAdminListItem())
    }

    /**
     * GET /private/accessory/discount/fetch/all?showId= — list promotions for one show.
     */
    suspend fun fetchDiscountsForAdmin(call: ApplicationCall) {
        val showId = call.parameters["showId"]
        if (showId.isNullOrBlank() || !ObjectId.isValid(showId)) {
            return call.respond(
                HttpStatusCode.BadRequest,
                jsonFactory.objectNode().put("message", "Missing or malformed showId"),
            )
        }
        if (catalogDb.loadShowById(showId) == null) {
            return call.respond(
                HttpStatusCode.NotFound,
                jsonFactory.objectNode().put("message", "Accessory show not found"),
            )
        }
        val items = discountDb.listByShowId(showId).map { it.toAdminListItem() }
        call.respond(HttpStatusCode.OK, items)
    }

    /**
     * GET /private/accessory/discount/fetch?id= — one promotion for the admin edit form.
     */
    suspend fun fetchDiscountForAdmin(call: ApplicationCall) {
        val rawId = call.parameters["id"]
        if (rawId.isNullOrBlank() || !ObjectId.isValid(rawId)) {
            return call.respond(
                HttpStatusCode.BadRequest,
                jsonFactory.objectNode().put("message", "Missing or malformed id"),
            )
        }
        val doc = discountDb.findById(rawId) ?: return call.respond(
            HttpStatusCode.NotFound,
            jsonFactory.objectNode().put("message", "Discount not found"),
        )
        call.respond(HttpStatusCode.OK, doc.toAdminListItem())
    }

    /**
     * POST /private/accessory/discount/create — insert a promotion (coupon / Tiered / EarlyBird).
     * Duplicate code → 409.
     */
    suspend fun createDiscountForAdmin(call: ApplicationCall) {
        val body = receiveAdminDiscountUpsert(call) ?: return
        val fieldErrors = body.adminFieldErrors(requireCode = true)
        if (fieldErrors.isNotEmpty()) {
            return call.respond(
                HttpStatusCode.BadRequest,
                jsonFactory.objectNode().put("message", fieldErrors.joinToString("; ")),
            )
        }
        val showId = body.accessoryShowId!!.trim()
        if (catalogDb.loadShowById(showId) == null) {
            return call.respond(
                HttpStatusCode.NotFound,
                jsonFactory.objectNode().put("message", "Accessory show not found"),
            )
        }
        val canonical = body.code!!.trim().uppercase()
        if (discountDb.findByCode(canonical) != null) {
            return call.respond(
                HttpStatusCode.Conflict,
                jsonFactory.objectNode().put("message", "Discount code already exists: $canonical"),
            )
        }
        val saved = try {
            discountDb.insert(body.toNewDiscountDocument().validate())
        } catch (e: AccessoryValidationException) {
            return call.respond(
                HttpStatusCode.BadRequest,
                jsonFactory.objectNode().put("message", e.errors.joinToString("; ")),
            )
        }
        logger.info("admin created discount id={} code={} type={}", saved.id, saved.code, saved.type)
        call.respond(HttpStatusCode.OK, saved.toAdminListItem())
    }

    /**
     * POST /private/accessory/discount/create/batch — create many promotions in one request.
     * Partial success: valid unique codes are inserted; invalid / duplicate codes land in [failures].
     */
    suspend fun createDiscountsBatchForAdmin(call: ApplicationCall) {
        val body: AdminDiscountBatchCreateRequest = try {
            call.receive()
        } catch (t: Throwable) {
            return call.respond(
                HttpStatusCode.BadRequest,
                jsonFactory.objectNode().put("message", "Malformed batch payload: ${t.message}"),
            )
        }
        val items = body.items
        if (items.isEmpty()) {
            return call.respond(
                HttpStatusCode.BadRequest,
                jsonFactory.objectNode().put("message", "`items` must not be empty"),
            )
        }
        if (items.size > AdminDiscountBatchCreateRequest.MAX_BATCH_SIZE) {
            return call.respond(
                HttpStatusCode.BadRequest,
                jsonFactory.objectNode().put(
                    "message",
                    "`items` size exceeds max ${AdminDiscountBatchCreateRequest.MAX_BATCH_SIZE}",
                ),
            )
        }

        val showIds = items.mapNotNull { it.accessoryShowId?.trim()?.takeIf { id -> id.isNotEmpty() } }.toSet()
        if (showIds.size != 1) {
            return call.respond(
                HttpStatusCode.BadRequest,
                jsonFactory.objectNode().put("message", "all items must share the same accessoryShowId"),
            )
        }
        val showId = showIds.first()
        if (catalogDb.loadShowById(showId) == null) {
            return call.respond(
                HttpStatusCode.NotFound,
                jsonFactory.objectNode().put("message", "Accessory show not found"),
            )
        }

        data class Pending(val index: Int, val code: String, val request: AdminDiscountUpsertRequest)

        val failures = mutableListOf<AdminDiscountBatchFailure>()
        val pending = mutableListOf<Pending>()
        val seenInBatch = mutableSetOf<String>()

        items.forEachIndexed { index, item ->
            val fieldErrors = item.adminFieldErrors(requireCode = true)
            if (fieldErrors.isNotEmpty()) {
                failures += AdminDiscountBatchFailure(
                    code = item.code?.trim(),
                    message = fieldErrors.joinToString("; "),
                )
                return@forEachIndexed
            }
            val canonical = item.code!!.trim().uppercase()
            if (!seenInBatch.add(canonical)) {
                failures += AdminDiscountBatchFailure(code = canonical, message = "Duplicate code in batch")
                return@forEachIndexed
            }
            pending += Pending(index, canonical, item)
        }

        val existing = discountDb.findByCodes(pending.map { it.code }).map { it.canonicalCode }.toSet()
        val creatable = mutableListOf<Pending>()
        for (p in pending) {
            if (p.code in existing) {
                failures += AdminDiscountBatchFailure(code = p.code, message = "Discount code already exists")
            } else {
                creatable += p
            }
        }

        val created = mutableListOf<metaticket.accessory.pojo.response.AdminDiscountListItem>()
        for (p in creatable) {
            try {
                val saved = discountDb.insert(p.request.toNewDiscountDocument().validate())
                created += saved.toAdminListItem()
            } catch (e: AccessoryValidationException) {
                failures += AdminDiscountBatchFailure(code = p.code, message = e.errors.joinToString("; "))
            } catch (t: Throwable) {
                failures += AdminDiscountBatchFailure(
                    code = p.code,
                    message = t.message?.takeIf { it.isNotBlank() } ?: "Insert failed",
                )
            }
        }

        logger.info(
            "admin batch created discounts showId={} created={} failed={}",
            showId,
            created.size,
            failures.size,
        )
        call.respond(HttpStatusCode.OK, AdminDiscountBatchCreateResponse(created = created, failures = failures))
    }

    /**
     * POST /private/accessory/discount/update?id= — replace mutable fields; `code` and `usedCount` stay.
     */
    suspend fun updateDiscountForAdmin(call: ApplicationCall) {
        val rawId = call.parameters["id"]
        if (rawId.isNullOrBlank() || !ObjectId.isValid(rawId)) {
            return call.respond(
                HttpStatusCode.BadRequest,
                jsonFactory.objectNode().put("message", "Missing or malformed id"),
            )
        }
        val body = receiveAdminDiscountUpsert(call) ?: return
        val fieldErrors = body.adminFieldErrors(requireCode = false)
        if (fieldErrors.isNotEmpty()) {
            return call.respond(
                HttpStatusCode.BadRequest,
                jsonFactory.objectNode().put("message", fieldErrors.joinToString("; ")),
            )
        }
        val existing = discountDb.findById(rawId) ?: return call.respond(
            HttpStatusCode.NotFound,
            jsonFactory.objectNode().put("message", "Discount not found"),
        )
        val showId = body.accessoryShowId!!.trim()
        if (catalogDb.loadShowById(showId) == null) {
            return call.respond(
                HttpStatusCode.NotFound,
                jsonFactory.objectNode().put("message", "Accessory show not found"),
            )
        }
        val updated = try {
            body.toUpdatedDiscountDocument(existing).validate()
        } catch (e: AccessoryValidationException) {
            return call.respond(
                HttpStatusCode.BadRequest,
                jsonFactory.objectNode().put("message", e.errors.joinToString("; ")),
            )
        }
        val saved = discountDb.replace(updated) ?: return call.respond(
            HttpStatusCode.NotFound,
            jsonFactory.objectNode().put("message", "Discount not found"),
        )
        logger.info("admin updated discount id={} code={}", saved.id, saved.code)
        call.respond(HttpStatusCode.OK, saved.toAdminListItem())
    }

    /**
     * POST /private/accessory/discount/active?id= — set active true/false.
     */
    suspend fun setDiscountActiveForAdmin(call: ApplicationCall) {
        val rawId = call.parameters["id"]
        if (rawId.isNullOrBlank() || !ObjectId.isValid(rawId)) {
            return call.respond(
                HttpStatusCode.BadRequest,
                jsonFactory.objectNode().put("message", "Missing or malformed id"),
            )
        }
        val body: AdminDiscountActiveRequest = try {
            call.receive()
        } catch (t: Throwable) {
            return call.respond(
                HttpStatusCode.BadRequest,
                jsonFactory.objectNode().put("message", "Malformed active payload"),
            )
        }
        val active = body.active
            ?: return call.respond(
                HttpStatusCode.BadRequest,
                jsonFactory.objectNode().put("message", "`active` is required"),
            )
        val saved = discountDb.setActive(rawId, active) ?: return call.respond(
            HttpStatusCode.NotFound,
            jsonFactory.objectNode().put("message", "Discount not found"),
        )
        logger.info("admin set discount id={} active={}", saved.id, saved.active)
        call.respond(HttpStatusCode.OK, saved.toAdminListItem())
    }

    /**
     * POST /private/accessory/discount/delete?id= — hard-delete only when never used.
     * Codes with `usedCount > 0` must be deactivated instead (protects unpaid-order revert).
     */
    suspend fun deleteDiscountForAdmin(call: ApplicationCall) {
        val rawId = call.parameters["id"]
        if (rawId.isNullOrBlank() || !ObjectId.isValid(rawId)) {
            return call.respond(
                HttpStatusCode.BadRequest,
                jsonFactory.objectNode().put("message", "Missing or malformed id"),
            )
        }
        val existing = discountDb.findById(rawId) ?: return call.respond(
            HttpStatusCode.NotFound,
            jsonFactory.objectNode().put("message", "Discount not found"),
        )
        if (existing.usedCount > 0) {
            return call.respond(
                HttpStatusCode.Conflict,
                jsonFactory.objectNode().put(
                    "message",
                    "Không xóa được mã đã sử dụng (usedCount=${existing.usedCount}). Hãy tắt Active thay vì xóa.",
                ),
            )
        }
        if (!discountDb.deleteById(rawId)) {
            return call.respond(
                HttpStatusCode.NotFound,
                jsonFactory.objectNode().put("message", "Discount not found"),
            )
        }
        logger.info("admin deleted discount id={}", rawId)
        call.respond(HttpStatusCode.OK, jsonFactory.objectNode().put("ok", true))
    }

    /** Parses the admin discount upsert JSON body or responds 400 and returns null. */
    private suspend fun receiveAdminDiscountUpsert(call: ApplicationCall): AdminDiscountUpsertRequest? {
        return try {
            call.receive<AdminDiscountUpsertRequest>()
        } catch (t: Throwable) {
            call.respond(
                HttpStatusCode.BadRequest,
                jsonFactory.objectNode().put("message", "Malformed discount payload: ${t.message}"),
            )
            null
        }
    }

    /** Parses the admin product upsert JSON body or responds 400 and returns null. */
    private suspend fun receiveAdminProductUpsert(call: ApplicationCall): AdminAccessoryUpsertRequest? {
        return try {
            call.receive<AdminAccessoryUpsertRequest>()
        } catch (t: Throwable) {
            call.respond(
                HttpStatusCode.BadRequest,
                jsonFactory.objectNode().put("message", "Malformed accessory product payload: ${t.message}"),
            )
            null
        }
    }

    /** Parses the admin upsert JSON body or responds 400 and returns null. */
    private suspend fun receiveAdminUpsert(call: ApplicationCall): AdminAccessoryShowUpsertRequest? {
        return try {
            call.receive<AdminAccessoryShowUpsertRequest>()
        } catch (t: Throwable) {
            call.respond(
                HttpStatusCode.BadRequest,
                jsonFactory.objectNode().put("message", "Malformed accessory show payload: ${t.message}"),
            )
            null
        }
    }

    /**
     * GET /public/accessory/show/pin/banner — pinned shows in pinOrder for `/san-pham` slider.
     */
    suspend fun fetchPinnedBanner(call: ApplicationCall) {
        val shows = catalogDb.loadPinnedShows(ACCESSORY_PIN_SLOT_MAX)
        val minPrices = catalogDb.minPriceByShowIds(shows.map { it.id })
        call.respond(HttpStatusCode.OK, shows.map { it.toSummaryResponse(minPrices[it.id]) })
    }

    /**
     * GET /public/accessory/show/fetch/all?page=&size= — storefront listing page (0-based).
     * Virtual order: pins 1..5, then Waiting, Open, Closed (see [CatalogDatabaseService.rankShowsForStorefront]).
     * Default size 6; FE passes size so page size is caller-controlled.
     */
    suspend fun fetchAllShowsForStorefront(call: ApplicationCall) {
        val page = (call.request.queryParameters["page"]?.toIntOrNull() ?: 0).coerceAtLeast(0)
        val size = (call.request.queryParameters["size"]?.toIntOrNull() ?: PUBLIC_SHOW_LIST_DEFAULT_SIZE)
            .coerceIn(1, PUBLIC_SHOW_LIST_MAX_SIZE)
        val ranked = catalogDb.rankShowsForStorefront(
            catalogDb.loadAllShows(),
            orderDb.sumPaidQtyByShowId(),
            ACCESSORY_PIN_SLOT_MAX,
        )
        val total = ranked.size
        val slice = ranked.drop(page * size).take(size)
        val totalPages = if (total == 0) 0 else (total + size - 1) / size
        val minPrices = catalogDb.minPriceByShowIds(slice.map { it.id })
        call.respond(
            HttpStatusCode.OK,
            AccessoryShowListPage(
                content = slice.map { it.toSummaryResponse(minPrices[it.id]) },
                number = page,
                size = size,
                totalElements = total.toLong(),
                totalPages = totalPages,
                first = page == 0,
                last = totalPages == 0 || page >= totalPages - 1,
                empty = slice.isEmpty(),
                numberOfElements = slice.size,
            ),
        )
    }

    /**
     * GET /public/accessory/show/search?keyword=&page=&size= — storefront keyword search for the
     * "Sản phẩm" column of unified search (0-based page). Ranked by match quality
     * (see [CatalogDatabaseService.searchShowsForStorefront]), not the browse/pin order used by
     * [fetchAllShowsForStorefront]. Blank/missing keyword returns an empty page rather than an error,
     * so the FE doesn't need a separate "no keyword" branch.
     */
    suspend fun searchShowsForStorefront(call: ApplicationCall) {
        val keyword = (call.request.queryParameters["keyword"] ?: "").trim()
        if (keyword.length > 100) {
            return call.respond(
                HttpStatusCode.BadRequest,
                jsonFactory.objectNode().put("message", "`keyword` must be at most 100 characters"),
            )
        }
        val page = (call.request.queryParameters["page"]?.toIntOrNull() ?: 0).coerceAtLeast(0)
        val size = (call.request.queryParameters["size"]?.toIntOrNull() ?: PUBLIC_SHOW_LIST_DEFAULT_SIZE)
            .coerceIn(1, PUBLIC_SHOW_LIST_MAX_SIZE)
        val result = catalogDb.searchShowsForStorefront(keyword, page, size)
        val total = result.total
        val totalPages = if (total == 0L) 0 else ((total + size - 1) / size).toInt()
        val minPrices = catalogDb.minPriceByShowIds(result.items.map { it.id })
        call.respond(
            HttpStatusCode.OK,
            AccessoryShowListPage(
                content = result.items.map { it.toSummaryResponse(minPrices[it.id]) },
                number = page,
                size = size,
                totalElements = total,
                totalPages = totalPages,
                first = page == 0,
                last = totalPages == 0 || page >= totalPages - 1,
                empty = result.items.isEmpty(),
                numberOfElements = result.items.size,
            ),
        )
    }

    /**
     * GET /public/accessory/show/fetch?id= - return one accessory show together with its
     * active accessories, including each accessory's variants (Req 1.3, 1.4).
     * Waiting / Closed shows are listed on `/san-pham` so detail is allowed; checkout still requires Open.
     *
     * Error handling:
     *  - missing or malformed id: 400 validation error, no show data (Req 1.7).
     *  - well-formed id that does not correspond to a show: 404
     *    not-found, no show data (Req 1.6).
     */
    suspend fun fetchShowById(call: ApplicationCall) {
        val encodedId = call.parameters["id"]
        if (encodedId.isNullOrBlank()) {
            return call.respond(
                HttpStatusCode.BadRequest,
                jsonFactory.objectNode().put("message", "Missing or malformed id"),
            )
        }

        // Decode + validate the public id. A decode/validation failure is a malformed identifier
        // (Req 1.7): never a raw-id leak or a 500.
        val rawId = PublicId.decode(encodedId) ?: return call.respond(
            HttpStatusCode.BadRequest,
            jsonFactory.objectNode().put("message", "Invalid accessory show identifier"),
        )

        val show = catalogDb.loadShowById(rawId) ?: return call.respond(
            HttpStatusCode.NotFound,
            jsonFactory.objectNode().put("message", "Accessory show unavailable"),
        )

        val docs = catalogDb.loadActiveAccessories(show.id)
        val customerUserId = call.request.headers["X-Customer-User-Id"]?.trim()?.takeIf { it.isNotEmpty() }
        val purchasedById = if (customerUserId != null) {
            orderDb.sumCountedQtyByAccessoryForCustomer(customerUserId, docs.map { it.id })
        } else {
            emptyMap()
        }
        val accessories = docs.map { it.toResponse(myPurchasedQty = purchasedById[it.id]) }
        // Personalized (per-user purchased qty) responses must not be shared across browsers.
        if (customerUserId != null) {
            call.response.header(HttpHeaders.CacheControl, "private, max-age=0, no-cache")
        } else {
            call.response.header(HttpHeaders.CacheControl, PUBLIC_STOCK_CACHE_CONTROL)
        }
        call.respond(HttpStatusCode.OK, show.toDetailResponse(accessories))
    }

    /**
     * GET /public/accessory/show/fetch/slug?slug= — resolve one show by Canonical_Slug
     * (normalized server-side), returning the same detail payload as [fetchShowById].
     * Missing slug → 400; unknown → 404. Waiting / Closed are allowed (same as fetch-by-id).
     */
    suspend fun fetchShowBySlug(call: ApplicationCall) {
        val rawSlug = call.parameters["slug"]
        if (rawSlug.isNullOrBlank()) {
            return call.respond(
                HttpStatusCode.BadRequest,
                jsonFactory.objectNode().put("message", "Missing or malformed slug"),
            )
        }
        val canonicalSlug = SlugNormalizer.normalize(rawSlug)
        if (canonicalSlug.isBlank()) {
            return call.respond(
                HttpStatusCode.BadRequest,
                jsonFactory.objectNode().put("message", "Missing or malformed slug"),
            )
        }
        val show = catalogDb.loadShowBySlug(canonicalSlug) ?: return call.respond(
            HttpStatusCode.NotFound,
            jsonFactory.objectNode().put("message", "Accessory show unavailable"),
        )
        val docs = catalogDb.loadActiveAccessories(show.id)
        val customerUserId = call.request.headers["X-Customer-User-Id"]?.trim()?.takeIf { it.isNotEmpty() }
        val purchasedById = if (customerUserId != null) {
            orderDb.sumCountedQtyByAccessoryForCustomer(customerUserId, docs.map { it.id })
        } else {
            emptyMap()
        }
        val accessories = docs.map { it.toResponse(myPurchasedQty = purchasedById[it.id]) }
        if (customerUserId != null) {
            call.response.header(HttpHeaders.CacheControl, "private, max-age=0, no-cache")
        } else {
            call.response.header(HttpHeaders.CacheControl, PUBLIC_STOCK_CACHE_CONTROL)
        }
        call.respond(HttpStatusCode.OK, show.toDetailResponse(accessories))
    }

    /**
     * GET /public/accessory/fetch?id= - return one active accessory with its current stock (top-level
     * for single-SKU products, per-variant for variant products), including its variants.
     *
     * Backs the storefront product-detail popup poll: when a customer opens a product the popup
     * refreshes this endpoint (and polls it while open) so the displayed availability — and the
     * "sold out ⇒ cannot buy" gating — reflects the live catalog rather than the snapshot taken when
     * the show list was first loaded.
     *
     * Error handling mirrors [fetchShowById]:
     *  - missing/malformed id: 400, no accessory data;
     *  - well-formed id that matches no active accessory (absent or inactive): 404, no accessory data.
     */
    suspend fun fetchAccessoryById(call: ApplicationCall) {
        val encodedId = call.parameters["id"]
        if (encodedId.isNullOrBlank()) {
            return call.respond(
                HttpStatusCode.BadRequest,
                jsonFactory.objectNode().put("message", "Missing or malformed id"),
            )
        }

        val rawId = PublicId.decode(encodedId) ?: return call.respond(
            HttpStatusCode.BadRequest,
            jsonFactory.objectNode().put("message", "Invalid accessory identifier"),
        )

        val accessory = catalogDb.loadActiveAccessoryById(rawId) ?: return call.respond(
            HttpStatusCode.NotFound,
            jsonFactory.objectNode().put("message", "Accessory unavailable"),
        )
        val customerUserId = call.request.headers["X-Customer-User-Id"]?.trim()?.takeIf { it.isNotEmpty() }
        val myPurchased = if (customerUserId != null && accessory.maxPerUserQty != null) {
            orderDb.sumCountedQtyByAccessoryForCustomer(customerUserId, listOf(accessory.id))[accessory.id]
        } else {
            null
        }
        if (customerUserId != null) {
            call.response.header(HttpHeaders.CacheControl, "private, max-age=0, no-cache")
        } else {
            // Short-lived shared cache: stock shown here is only a UX hint; checkout is authoritative.
            call.response.header(HttpHeaders.CacheControl, PUBLIC_STOCK_CACHE_CONTROL)
        }
        call.respond(HttpStatusCode.OK, accessory.toResponse(myPurchasedQty = myPurchased))
    }

    /**
     * POST /accessory/checkout - validate the submitted cart, create exactly one order, and
     * create its payment, returning the encoded payment/order ids plus the created order code and
     * total (Req 6.1, 6.15, 7.3).
     *
     * Flow:
     * 1. Parse the raw JSON body first so the top-level field names and any `fulfillment` value the
     *    client sent are available to the payload gate, which rejects any shipping field (Req 14.3)
     *    or non-`PickupAtShow` fulfillment (Req 14.4). A malformed/non-object body is a `400`.
     * 2. Run [CheckoutValidator] with that raw-body context (Req 16.3). On failure, reject WITHOUT
     *    creating an order and name the failed constraint(s), mapping to `400`/`422` (Req 16.4).
     * 3. Delegate to [AccessoryOrderService.checkout]; map each [CheckoutException] subtype to its
     *    HTTP status (`404`/`422`/`400`/`409`/`502`).
     *
     * Checkout requires an authenticated **customer**: the gateway injects `X-Customer-User-Id`
     * after authentication, so its absence means the caller is not logged in and the request is
     * rejected with `401` before any work (mirrors `service.order`'s `createOrder`).
     */
    suspend fun checkout(call: ApplicationCall) {
        // 0. Require an authenticated customer (X-Customer-User-Id injected by the gateway).
        val customerUserId = call.request.headers["X-Customer-User-Id"]
        if (customerUserId.isNullOrBlank()) {
            logger.error("accessory checkout rejected: missing X-Customer-User-Id header (customer not logged in)")
            return call.respond(
                HttpStatusCode.Unauthorized,
                jsonFactory.objectNode().put("message", "Không xác định người dùng"),
            )
        }

        // 1. Parse the raw body to capture the exact top-level fields + fulfillment value (Req 14.3, 14.4).
        val bodyNode: JsonNode = try {
            call.receive<JsonNode>()
        } catch (t: Throwable) {
            return call.respond(
                HttpStatusCode.BadRequest,
                jsonFactory.objectNode().put("message", "Malformed checkout payload"),
            )
        }
        if (!bodyNode.isObject) {
            return call.respond(
                HttpStatusCode.BadRequest,
                jsonFactory.objectNode().put("message", "Checkout payload must be a JSON object"),
            )
        }

        val presentTopLevelFields: Set<String> =
            bodyNode.fieldNames().asSequence().map { it.lowercase() }.toSet()
        val fulfillment: String? = bodyNode.get("fulfillment")?.takeIf { !it.isNull }?.asText()

        val input: CheckoutInput = try {
            objectMapper.treeToValue(bodyNode, CheckoutInput::class.java)
        } catch (t: Throwable) {
            return call.respond(
                HttpStatusCode.BadRequest,
                jsonFactory.objectNode().put("message", "Invalid checkout payload: ${t.message}"),
            )
        }

        // 2. Pure payload gate WITH the raw-body context — before any DB work (Req 16.3, 16.4, 14.3, 14.4).
        val validationErrors = CheckoutValidator.validate(
            input,
            presentTopLevelFields = presentTopLevelFields,
            fulfillment = fulfillment,
        )
        if (validationErrors.isNotEmpty()) {
            return respondValidationErrors(call, validationErrors)
        }

        // 3. Create the order + payment; map catalog/payment failures to their mandated status.
        try {
            val result = orderService.checkout(input, customerUserId)
            call.respond(HttpStatusCode.OK, result) // encoded ids (Req 7.3) + order code & total (Req 6.15)
        } catch (e: CheckoutValidationException) {
            // Defensive: the service re-runs the pure gate; surface the same structured 400/422.
            respondValidationErrors(call, e.errors)
        } catch (e: CheckoutException) {
            call.respond(
                HttpStatusCode.fromValue(e.status),
                jsonFactory.objectNode().put("message", e.message),
            )
        }
    }

    /**
     * POST /accessory/discount/validate - preview a **typed** discount code against the submitted
     * cart lines (Req 4). No silent Auto campaigns: blank code → 422. Re-derives the subtotal from
     * the stored catalog (Req 6.9), evaluates without reserving usage (Req 4.7).
     *
     * Like checkout, this requires an authenticated **customer**: a missing `X-Customer-User-Id`
     * header is rejected with `401` before any catalog/discount work.
     */
    suspend fun validateDiscount(call: ApplicationCall) {
        // Require an authenticated customer (X-Customer-User-Id injected by the gateway).
        val customerUserId = call.request.headers["X-Customer-User-Id"]
        if (customerUserId.isNullOrBlank()) {
            logger.error("accessory discount preview rejected: missing X-Customer-User-Id header (customer not logged in)")
            return call.respond(
                HttpStatusCode.Unauthorized,
                jsonFactory.objectNode().put("message", "Không xác định người dùng"),
            )
        }

        val request: DiscountPreviewRequest = try {
            call.receive<DiscountPreviewRequest>()
        } catch (t: Throwable) {
            return call.respond(
                HttpStatusCode.BadRequest,
                jsonFactory.objectNode().put("message", "Malformed discount preview payload"),
            )
        }

        // Req 4.3: a preview with zero cart lines is rejected with 422.
        if (request.lines.isEmpty()) {
            return respondDiscountRejected(call, "cart must contain at least one line")
        }

        // Server-derived subtotal + owning show (Req 4.1, 6.9). Catalog errors keep their status.
        val context = try {
            orderService.deriveCartContext(request.lines)
        } catch (e: CheckoutException) {
            return call.respond(
                HttpStatusCode.fromValue(e.status),
                jsonFactory.objectNode().put("message", e.message),
            )
        }

        // Req 4.3: a non-positive subtotal cannot yield a discount.
        if (context.subtotal <= 0L) {
            return respondDiscountRejected(call, "cart subtotal must be greater than zero")
        }

        // Typed code only — blank is "does not exist".
        val code = request.discountCode
        if (code.isNullOrBlank()) {
            return respondDiscountRejected(call, "discount code does not exist")
        }

        val preview = discountService.evaluatePreview(code, context.showId, context.subtotal)
            ?: return respondDiscountRejected(call, "discount code is not applicable to this cart")

        // Clamp like checkout computeTotal so a 100% (or larger) discount previews as total 0
        // and can complete as a zero-payment order.
        val total = maxOf(0L, context.subtotal - preview.amount)
        call.respond(
            HttpStatusCode.OK,
            DiscountPreviewResponse(
                code = preview.code,
                type = preview.type.name,
                value = preview.value,
                amount = preview.amount,
                subtotal = context.subtotal,
                total = total,
            ),
        )
    }

    /**
     * GET /public/accessory/order/fetch?id= - return the customer-facing review of a single order
     * (Req 13.3), powering the "Hoàn tất" review link. The response carries the order code, status,
     * customer, subtotal/discount/total, the always-`PickupAtShow` fulfillment (no shipping fields —
     * Req 14.5), and each line's composed display string (product name + variant label + quantity,
     * with the variant label omitted for single-SKU lines — Req 13.5).
     *
     * A missing/malformed id, or a well-formed id that matches no order, is a `404` not-found with no
     * order data (Req 13.4); the id is only ever consumed `Checksum`-encoded (Req 16.1).
     */
    suspend fun fetchOrder(call: ApplicationCall) {
        val encodedId = call.parameters["id"].orEmpty()
        try {
            // getOrder decodes + validates the id; a blank/malformed id decodes to null → 404 (Req 13.4).
            call.respond(HttpStatusCode.OK, orderService.getOrder(encodedId))
        } catch (e: OrderAccessException) {
            call.respond(
                HttpStatusCode.fromValue(e.status),
                jsonFactory.objectNode().put("message", e.message),
            )
        }
    }

    /**
     * GET /public/accessory/report/{reportKey} — partner-facing sales report for an Active filter.
     * No auth. Inactive / missing key → 404. Bundles show label + paid sales statistic.
     */
    suspend fun fetchPublicSalesReport(call: ApplicationCall) {
        val reportKey = call.parameters["reportKey"]?.trim().orEmpty()
        if (reportKey.isEmpty()) {
            return call.respond(
                HttpStatusCode.BadRequest,
                jsonFactory.objectNode().put("message", "Missing reportKey"),
            )
        }
        val doc = reportFilterDb.findByReportKey(reportKey) ?: return call.respond(
            HttpStatusCode.NotFound,
            jsonFactory.objectNode().put("message", "Report filter not found"),
        )
        if (doc.status != AccessoryReportFilterStatus.Active) {
            return call.respond(
                HttpStatusCode.NotFound,
                jsonFactory.objectNode().put("message", "Report filter is inactive"),
            )
        }
        val showId = doc.filter.showId ?: doc.showId
        if (showId.isNullOrBlank() || !ObjectId.isValid(showId)) {
            return call.respond(
                HttpStatusCode.BadRequest,
                jsonFactory.objectNode().put("message", "Filter is missing a valid showId"),
            )
        }
        val show = catalogDb.loadShowById(showId)
        val startAt = doc.filter.startAt?.let { Date(it) }
        val endAt = doc.filter.endAt?.let { Date(it) }
        val stats = computeAdminSalesStatistic(
            orderDb.findOrdersByShowId(showId, startAt = startAt, endAt = endAt),
        )
        call.respond(
            HttpStatusCode.OK,
            PublicSalesReportResponse(
                reportKey = doc.reportKey,
                reportName = doc.reportName,
                showId = showId,
                showCode = show?.code,
                showName = show?.name,
                startAt = doc.filter.startAt,
                endAt = doc.filter.endAt,
                summary = stats.summary,
                byProduct = stats.byProduct,
            ),
        )
    }

    /**
     * GET /public/accessory/report/{reportKey}/orders — partner Excel source.
     * Active filter only; catalog products (all) + PaymentSucceed orders with lines.
     */
    suspend fun fetchPublicReportOrders(call: ApplicationCall) {
        val resolved = resolveActiveReportFilter(call) ?: return
        val (showId, startAt, endAt) = resolved
        val products = catalogDb.loadAccessoriesByShowId(showId)
            .sortedWith(compareBy({ it.name.lowercase() }, { it.sku.lowercase() }))
            .map { it.toExportProductColumn() }
        val orders = orderDb.findOrdersByShowId(showId, startAt = startAt, endAt = endAt)
            .filter { it.status == AccessoryOrderStatus.PaymentSucceed }
            .map { it.toPublicExportItem() }
        call.respond(HttpStatusCode.OK, PublicOrderExportResponse(products = products, orders = orders))
    }

    /**
     * POST /accessory/order/cancel?id= - cancel the customer's own pre-payment order (the "Hủy đơn"
     * action on the payment step). Decodes the id (malformed/none or unknown → `404`); an order past
     * the cancellable window (payment in-flight or already terminal) → `409`; otherwise it performs
     * the guarded transition to `PaymentCancelled`, releases the reserved stock + discount, and
     * returns `200`.
     *
     * Like checkout, this requires an authenticated **customer**: a missing `X-Customer-User-Id`
     * header (caller not logged in) is rejected with `401` before any work.
     */
    suspend fun cancelOrder(call: ApplicationCall) {
        // Require an authenticated customer (X-Customer-User-Id injected by the gateway).
        val customerUserId = call.request.headers["X-Customer-User-Id"]
        if (customerUserId.isNullOrBlank()) {
            logger.error("accessory cancel rejected: missing X-Customer-User-Id header (customer not logged in)")
            return call.respond(
                HttpStatusCode.Unauthorized,
                jsonFactory.objectNode().put("message", "Không xác định người dùng"),
            )
        }

        val encodedId = call.parameters["id"].orEmpty()
        try {
            logger.info("customer {} requested cancel for accessory order id={}", customerUserId, encodedId)
            orderService.cancelOrder(encodedId, customerUserId)
            call.respond(
                HttpStatusCode.OK,
                jsonFactory.objectNode().put("message", "order cancelled"),
            )
        } catch (e: OrderAccessException) {
            call.respond(
                HttpStatusCode.fromValue(e.status),
                jsonFactory.objectNode().put("message", e.message),
            )
        }
    }

    /**
     * GET /accessory/order/fetch/mine — list PaymentSucceed accessory orders for the logged-in
     * customer (storefront "Quản lý đơn hàng"). Requires `X-Customer-User-Id` (401 when missing).
     */
    suspend fun fetchMyPaidOrders(call: ApplicationCall) {
        val customerUserId = call.request.headers["X-Customer-User-Id"]
        if (customerUserId.isNullOrBlank()) {
            logger.error("accessory my-orders rejected: missing X-Customer-User-Id header")
            return call.respond(
                HttpStatusCode.Unauthorized,
                jsonFactory.objectNode().put("message", "Không xác định người dùng"),
            )
        }
        val orders = orderService.listMyPaidOrders(customerUserId)
        call.respond(HttpStatusCode.OK, orders)
    }

    /**
     * GET /accessory/order/pending — newest `Created` / `WaitingForPayment` order for the logged-in
     * customer (product-page resume). Requires `X-Customer-User-Id` (401 when missing).
     * Always HTTP 200 when authenticated; body `{ "order": ... | null }`.
     */
    suspend fun fetchPendingOrder(call: ApplicationCall) {
        val customerUserId = call.request.headers["X-Customer-User-Id"]
        if (customerUserId.isNullOrBlank()) {
            logger.error("accessory pending-order rejected: missing X-Customer-User-Id header")
            return call.respond(
                HttpStatusCode.Unauthorized,
                jsonFactory.objectNode().put("message", "Không xác định người dùng"),
            )
        }
        val pending = orderService.findPendingOrderForCustomer(customerUserId)
        call.respond(
            HttpStatusCode.OK,
            metaticket.accessory.pojo.response.PendingAccessoryOrderLookupResponse(order = pending),
        )
    }

    /**
     * GET /private/accessory/order/fetch/all?showId= — admin order list + stats for one show.
     * Revenue / paidOrders count only PaymentSucceed. Missing/malformed showId → 400; unknown → 404.
     */
    suspend fun fetchOrdersForAdmin(call: ApplicationCall) {
        val showId = call.parameters["showId"]
        if (showId.isNullOrBlank() || !ObjectId.isValid(showId)) {
            return call.respond(
                HttpStatusCode.BadRequest,
                jsonFactory.objectNode().put("message", "Missing or malformed showId"),
            )
        }
        if (catalogDb.loadShowById(showId) == null) {
            return call.respond(
                HttpStatusCode.NotFound,
                jsonFactory.objectNode().put("message", "Accessory show not found"),
            )
        }
        val docs = orderDb.findOrdersByShowId(showId)
        call.respond(
            HttpStatusCode.OK,
            AdminOrderListResponse(
                stats = computeAdminOrderStats(docs),
                orders = docs.map { it.toAdminListItem() },
            ),
        )
    }

    /**
     * GET /private/accessory/order/stats?showId=&startAt=&endAt= — sales statistic for one show.
     * Dates are epoch millis; endAt is exclusive. Paid metrics use PaymentSucceed only.
     */
    suspend fun fetchOrderStatsForAdmin(call: ApplicationCall) {
        val showId = call.parameters["showId"]
        if (showId.isNullOrBlank() || !ObjectId.isValid(showId)) {
            return call.respond(
                HttpStatusCode.BadRequest,
                jsonFactory.objectNode().put("message", "Missing or malformed showId"),
            )
        }
        if (catalogDb.loadShowById(showId) == null) {
            return call.respond(
                HttpStatusCode.NotFound,
                jsonFactory.objectNode().put("message", "Accessory show not found"),
            )
        }
        val startAt = call.parameters["startAt"]?.toLongOrNull()?.let { Date(it) }
        val endAt = call.parameters["endAt"]?.toLongOrNull()?.let { Date(it) }
        if (startAt != null && endAt != null && !endAt.after(startAt)) {
            return call.respond(
                HttpStatusCode.BadRequest,
                jsonFactory.objectNode().put("message", "`endAt` must be after `startAt`"),
            )
        }
        val docs = orderDb.findOrdersByShowId(showId, startAt = startAt, endAt = endAt)
        call.respond(HttpStatusCode.OK, computeAdminSalesStatistic(docs))
    }

    /**
     * GET /private/accessory/order/export?showId=&startAt=&endAt= — admin Excel source.
     * Catalog products (all statuses) + orders in the optional date window (lines + paymentId).
     */
    suspend fun exportOrdersForAdmin(call: ApplicationCall) {
        val showId = call.parameters["showId"]
        if (showId.isNullOrBlank() || !ObjectId.isValid(showId)) {
            return call.respond(
                HttpStatusCode.BadRequest,
                jsonFactory.objectNode().put("message", "Missing or malformed showId"),
            )
        }
        if (catalogDb.loadShowById(showId) == null) {
            return call.respond(
                HttpStatusCode.NotFound,
                jsonFactory.objectNode().put("message", "Accessory show not found"),
            )
        }
        val startAt = call.parameters["startAt"]?.toLongOrNull()?.let { Date(it) }
        val endAt = call.parameters["endAt"]?.toLongOrNull()?.let { Date(it) }
        if (startAt != null && endAt != null && !endAt.after(startAt)) {
            return call.respond(
                HttpStatusCode.BadRequest,
                jsonFactory.objectNode().put("message", "`endAt` must be after `startAt`"),
            )
        }
        val products = catalogDb.loadAccessoriesByShowId(showId)
            .sortedWith(compareBy({ it.name.lowercase() }, { it.sku.lowercase() }))
            .map { it.toExportProductColumn() }
        val orders = orderDb.findOrdersByShowId(showId, startAt = startAt, endAt = endAt)
            .filter { it.status == AccessoryOrderStatus.PaymentSucceed }
            .map { it.toAdminExportItem() }
        call.respond(HttpStatusCode.OK, AdminOrderExportResponse(products = products, orders = orders))
    }

    /**
     * Resolves an Active report filter from path `reportKey`; responds 4xx and returns null on failure.
     * Triple: showId, startAt?, endAt?.
     */
    private suspend fun resolveActiveReportFilter(call: ApplicationCall): Triple<String, Date?, Date?>? {
        val reportKey = call.parameters["reportKey"]?.trim().orEmpty()
        if (reportKey.isEmpty()) {
            call.respond(
                HttpStatusCode.BadRequest,
                jsonFactory.objectNode().put("message", "Missing reportKey"),
            )
            return null
        }
        val doc = reportFilterDb.findByReportKey(reportKey)
        if (doc == null) {
            call.respond(
                HttpStatusCode.NotFound,
                jsonFactory.objectNode().put("message", "Report filter not found"),
            )
            return null
        }
        if (doc.status != AccessoryReportFilterStatus.Active) {
            call.respond(
                HttpStatusCode.NotFound,
                jsonFactory.objectNode().put("message", "Report filter is inactive"),
            )
            return null
        }
        val showId = doc.filter.showId ?: doc.showId
        if (showId.isNullOrBlank() || !ObjectId.isValid(showId)) {
            call.respond(
                HttpStatusCode.BadRequest,
                jsonFactory.objectNode().put("message", "Filter is missing a valid showId"),
            )
            return null
        }
        val startAt = doc.filter.startAt?.let { Date(it) }
        val endAt = doc.filter.endAt?.let { Date(it) }
        return Triple(showId, startAt, endAt)
    }

    /**
     * POST /private/accessory/report-filter/create — save a shareable statistic filter preset.
     * Returns `{ "reportKey": "..." }` for copy-link.
     */
    suspend fun createReportFilterForAdmin(call: ApplicationCall) {
        val body: AdminReportFilterCreateRequest = try {
            call.receive()
        } catch (t: Throwable) {
            return call.respond(
                HttpStatusCode.BadRequest,
                jsonFactory.objectNode().put("message", "Malformed report-filter payload"),
            )
        }
        val name = body.reportName?.trim().orEmpty()
        if (name.isEmpty()) {
            return call.respond(
                HttpStatusCode.BadRequest,
                jsonFactory.objectNode().put("message", "`reportName` is required"),
            )
        }
        val showId = body.showId?.trim()
        if (showId.isNullOrBlank() || !ObjectId.isValid(showId)) {
            return call.respond(
                HttpStatusCode.BadRequest,
                jsonFactory.objectNode().put("message", "Missing or malformed showId"),
            )
        }
        if (catalogDb.loadShowById(showId) == null) {
            return call.respond(
                HttpStatusCode.NotFound,
                jsonFactory.objectNode().put("message", "Accessory show not found"),
            )
        }
        val startAt = body.startAt
        val endAt = body.endAt
        if (startAt != null && endAt != null && endAt <= startAt) {
            return call.respond(
                HttpStatusCode.BadRequest,
                jsonFactory.objectNode().put("message", "`endAt` must be after `startAt`"),
            )
        }
        val reportKey = UUID.randomUUID().toString()
        val doc = AccessoryReportFilterDocument(
            reportKey = reportKey,
            reportName = name,
            username = body.username?.trim().orEmpty(),
            status = AccessoryReportFilterStatus.Active,
            showId = showId,
            filter = AccessoryReportFilterCriteria(
                showId = showId,
                startAt = startAt,
                endAt = endAt,
            ),
        )
        reportFilterDb.insert(doc) ?: return call.respond(
            HttpStatusCode.InternalServerError,
            jsonFactory.objectNode().put("message", "Không lưu được filter"),
        )
        call.respond(HttpStatusCode.OK, jsonFactory.objectNode().put("reportKey", reportKey))
    }

    /** GET /private/accessory/report-filter/fetch/all?showId= — list saved presets (optional show). */
    suspend fun fetchReportFiltersForAdmin(call: ApplicationCall) {
        val showId = call.parameters["showId"]?.trim()
        if (!showId.isNullOrBlank() && !ObjectId.isValid(showId)) {
            return call.respond(
                HttpStatusCode.BadRequest,
                jsonFactory.objectNode().put("message", "Malformed showId"),
            )
        }
        val items = reportFilterDb.list(showId?.takeIf { it.isNotBlank() }).map { it.toAdminItem() }
        call.respond(HttpStatusCode.OK, items)
    }

    /**
     * GET /private/accessory/report-filter/fetch?reportKey= — load one Active preset for Thống kê.
     * Inactive / missing → 404.
     */
    suspend fun fetchReportFilterByKeyForAdmin(call: ApplicationCall) {
        val reportKey = call.parameters["reportKey"]?.trim().orEmpty()
        if (reportKey.isEmpty()) {
            return call.respond(
                HttpStatusCode.BadRequest,
                jsonFactory.objectNode().put("message", "Missing reportKey"),
            )
        }
        val doc = reportFilterDb.findByReportKey(reportKey) ?: return call.respond(
            HttpStatusCode.NotFound,
            jsonFactory.objectNode().put("message", "Report filter not found"),
        )
        if (doc.status != AccessoryReportFilterStatus.Active) {
            return call.respond(
                HttpStatusCode.NotFound,
                jsonFactory.objectNode().put("message", "Report filter is inactive"),
            )
        }
        call.respond(HttpStatusCode.OK, doc.toAdminItem())
    }

    /** POST /private/accessory/report-filter/enable — set Active. */
    suspend fun enableReportFilterForAdmin(call: ApplicationCall) {
        setReportFilterStatus(call, AccessoryReportFilterStatus.Active)
    }

    /** POST /private/accessory/report-filter/disable — set Inactive. */
    suspend fun disableReportFilterForAdmin(call: ApplicationCall) {
        setReportFilterStatus(call, AccessoryReportFilterStatus.Inactive)
    }

    /** POST /private/accessory/report-filter/delete — hard-delete preset. */
    suspend fun deleteReportFilterForAdmin(call: ApplicationCall) {
        val body: AdminReportFilterIdRequest = try {
            call.receive()
        } catch (t: Throwable) {
            return call.respond(
                HttpStatusCode.BadRequest,
                jsonFactory.objectNode().put("message", "Malformed payload"),
            )
        }
        val id = body.reportId?.trim().orEmpty()
        if (id.isEmpty() || !ObjectId.isValid(id)) {
            return call.respond(
                HttpStatusCode.BadRequest,
                jsonFactory.objectNode().put("message", "Missing or malformed reportId"),
            )
        }
        if (!reportFilterDb.delete(id)) {
            return call.respond(
                HttpStatusCode.NotFound,
                jsonFactory.objectNode().put("message", "Report filter not found"),
            )
        }
        call.respond(HttpStatusCode.OK, jsonFactory.objectNode().put("ok", true))
    }

    /** Shared Active/Inactive update for report filters. */
    private suspend fun setReportFilterStatus(call: ApplicationCall, status: AccessoryReportFilterStatus) {
        val body: AdminReportFilterIdRequest = try {
            call.receive()
        } catch (t: Throwable) {
            return call.respond(
                HttpStatusCode.BadRequest,
                jsonFactory.objectNode().put("message", "Malformed payload"),
            )
        }
        val id = body.reportId?.trim().orEmpty()
        if (id.isEmpty() || !ObjectId.isValid(id)) {
            return call.respond(
                HttpStatusCode.BadRequest,
                jsonFactory.objectNode().put("message", "Missing or malformed reportId"),
            )
        }
        val updated = reportFilterDb.updateStatus(id, status) ?: return call.respond(
            HttpStatusCode.NotFound,
            jsonFactory.objectNode().put("message", "Report filter not found"),
        )
        call.respond(HttpStatusCode.OK, updated.toAdminItem())
    }

    /**
     * POST /private/accessory/order/notify/resend?id= - internal operational recovery that resends a
     * single order's confirmation email (Req 15).
     *
     * Decodes the id (malformed/none → `404`, Req 15.3); an order not in `PaymentSucceed` → `409`
     * (Req 15.4); otherwise it forces exactly one further send through the dedicated
     * `SendMailAccessoryOrder` path and returns `200` (Req 15.1).
     *
     * Being an internal operational action, it requires an authenticated **operator**: the gateway
     * injects `X-OP-User-Id` after authentication, so its absence means the operator is not logged
     * in and the request is rejected with `401` before any work (mirrors `service.order`'s
     * `cancelOrder`).
     */
    suspend fun resendConfirmation(call: ApplicationCall) {
        // Require an authenticated operator (X-OP-User-Id injected by the gateway).
        val opUserId = call.request.headers["X-OP-User-Id"]
        if (opUserId.isNullOrBlank()) {
            logger.error("accessory resend rejected: missing X-OP-User-Id header (operator not logged in)")
            return call.respond(
                HttpStatusCode.Unauthorized,
                jsonFactory.objectNode().put("message", "Không xác định người dùng"),
            )
        }

        val encodedId = call.parameters["id"].orEmpty()
        try {
            logger.info("operator {} requested confirmation-email resend for accessory order id={}", opUserId, encodedId)
            orderService.resendConfirmation(encodedId)
            call.respond(
                HttpStatusCode.OK,
                jsonFactory.objectNode().put("message", "confirmation email resend dispatched"),
            )
        } catch (e: OrderAccessException) {
            call.respond(
                HttpStatusCode.fromValue(e.status),
                jsonFactory.objectNode().put("message", e.message),
            )
        }
    }

    /**
     * Emit a structured checkout-payload validation failure (Req 16.4). Each failed constraint is
     * reported with its machine-readable code, offending field, and message. The overall status is
     * `400` when any failure is a malformed/forbidden-payload error, otherwise `422` for
     * well-formed-but-semantically-invalid values (customer name/email/phone, fulfillment).
     */
    private suspend fun respondValidationErrors(call: ApplicationCall, errors: List<CheckoutValidationError>) {
        val status =
            if (errors.any { statusFor(it.code) == HttpStatusCode.BadRequest }) HttpStatusCode.BadRequest
            else HttpStatusCode.UnprocessableEntity

        val body = jsonFactory.objectNode()
        body.put("message", "checkout payload validation failed")
        val arr = body.putArray("errors")
        errors.forEach { e ->
            arr.addObject().also {
                it.put("code", e.code.name)
                it.put("field", e.field)
                it.put("message", e.message)
            }
        }
        call.respond(status, body)
    }

    /** Map a payload-validation code to the HTTP status the checkout response should carry (Req 16.4). */
    private fun statusFor(code: CheckoutValidationCode): HttpStatusCode = when (code) {
        // Well-formed but semantically invalid values → 422 Unprocessable Entity.
        CheckoutValidationCode.INVALID_CUSTOMER_NAME,
        CheckoutValidationCode.INVALID_EMAIL,
        CheckoutValidationCode.INVALID_PHONE,
        CheckoutValidationCode.INVALID_FULFILLMENT -> HttpStatusCode.UnprocessableEntity
        // Malformed / missing / forbidden payload structure → 400 Bad Request.
        else -> HttpStatusCode.BadRequest
    }

    /** Reject a discount preview with `422` and a human-readable reason (Req 4.2, 4.3). */
    private suspend fun respondDiscountRejected(call: ApplicationCall, reason: String) {
        call.respond(
            HttpStatusCode.UnprocessableEntity,
            jsonFactory.objectNode().put("message", reason),
        )
    }

    /** True when [e] is a duplicate-key on the partial unique index for [AccessoryShowDocument.slug]. */
    private fun isDuplicateSlugError(e: Throwable): Boolean {
        val mongo = e as? MongoException ?: e.cause as? MongoException ?: return false
        return when (mongo) {
            is MongoWriteException ->
                mongo.error.category == ErrorCategory.DUPLICATE_KEY &&
                    mongo.error.message.contains(AccessoryShowDocument::slug.name)
            is MongoCommandException ->
                mongo.errorCode == 11000 &&
                    mongo.errorMessage.contains(AccessoryShowDocument::slug.name)
            else -> false
        }
    }
}
