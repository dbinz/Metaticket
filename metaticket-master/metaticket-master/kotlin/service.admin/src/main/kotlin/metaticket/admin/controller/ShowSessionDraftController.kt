package metaticket.admin.controller

import com.fasterxml.jackson.databind.node.JsonNodeFactory
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import metaticket.admin.database.service.ShowSessionDraftDBService
import metaticket.admin.database.service.ShowSessionDraftOrderCustomFieldDBService
import metaticket.admin.database.service.ShowSessionDraftPromotionDBService
import metaticket.admin.gateway.OrderGateway
import metaticket.admin.gateway.ShowGateway
import metaticket.admin.pojo.db.CityDocument
import metaticket.admin.pojo.db.ShowSessionDraftDocument
import metaticket.admin.pojo.db.ShowSessionDraftStatus
import metaticket.admin.pojo.request.*
import metaticket.admin.pojo.db.SessionCalendarDraft
import metaticket.common.logger.Logging
import metaticket.show.datastructure.CalendarRules
import metaticket.show.datastructure.SessionType
import metaticket.show.grpc.ShowMessage
import org.bson.types.ObjectId

/**
 * Kiểm tra sớm cấu hình vé theo lịch trên nháp (service.show kiểm lại khi tạo session thật).
 * @return thông báo lỗi tiếng Việt, null khi hợp lệ.
 */
private fun validateCalendarDraft(sessionType: SessionType?, calendar: SessionCalendarDraft?, manageTicket: Boolean?): String? {
    if (sessionType != SessionType.CALENDAR) return null
    val config = calendar ?: return "Vé theo lịch cần cấu hình giờ mở cửa và số ngày bán trước"
    CalendarRules.validateConfig(config.openTime, config.closeTime, config.bookingWindowDays, config.sameDayCutoffMinutes)
        ?.let { return it }
    if (manageTicket != true) return "Vé theo lịch bắt buộc bật quản lý vé"
    return null
}

class ShowSessionDraftController constructor(
    val showSessionDraftDBService: ShowSessionDraftDBService,
    val showSessionDraftOrderCustomFieldDBService: ShowSessionDraftOrderCustomFieldDBService,
    private val showSessionDraftPromotionDBService: ShowSessionDraftPromotionDBService,
    val jsonFactory: JsonNodeFactory,
    private val showClient: ShowGateway,
    private val orderClient: OrderGateway,
) : Logging {
    suspend fun createSessionDraft(call: ApplicationCall) {
        val input = call.receive<CreateShowSessionDraftBasicInfoRequest>()

        if (!ObjectId.isValid(input.showId)) return call.respond(
            HttpStatusCode.BadRequest,
            jsonFactory.objectNode().put("message", "Invalid show id"),
        )

        if (input.sessionName.isNullOrBlank()) {
            return call.respond(
                HttpStatusCode.BadRequest,
                jsonFactory.objectNode().put("message", "Invalid session name"),
            )
        }

        if (input.code.isBlank()) {
            return call.respond(
                HttpStatusCode.BadRequest,
                jsonFactory.objectNode().put("message", "Invalid code"),
            )
        }

        if (input.address.isEmpty()) {
            return call.respond(
                HttpStatusCode.BadRequest,
                jsonFactory.objectNode().put("message", "Invalid address"),
            )
        }

        if (input.startTime == 0L) {
            return call.respond(
                HttpStatusCode.BadRequest,
                jsonFactory.objectNode().put("message", "Invalid start time"),
            )
        }


        if (input.endTime == 0L) {
            return call.respond(
                HttpStatusCode.BadRequest,
                jsonFactory.objectNode().put("message", "Invalid end time"),
            )
        }

        if (input.qrcodeEmail == true && input.manageTicket != true) {
            return call.respond(
                HttpStatusCode.BadRequest,
                jsonFactory.objectNode().put("message", "qrcodeEmail requires manageTicket"),
            )
        }

        validateCalendarDraft(input.sessionType, input.calendar, input.manageTicket)?.let {
            return call.respond(HttpStatusCode.BadRequest, jsonFactory.objectNode().put("message", it))
        }

        val cityDoc = showClient.getCity(input.cityId) ?: return call.respond(
            HttpStatusCode.BadRequest,
            jsonFactory.objectNode().put("message", "Invalid city"),
        )

        val emptyShowSessionDoc = ShowSessionDraftDocument(
            showId = input.showId,
            sessionName = input.sessionName,
            code = input.code,
            address = input.address,
            startTime = input.startTime,
            endTime = input.endTime,
            ledgendImageUrl = input.ledgendImageUrl ?: "",
            ledgendImage = input.ledgendImage ?: "",
            eventSquareImageUrl = input.eventSquareImageUrl,
            eventSquareImage = input.eventSquareImage,
            eventNameImageUrl = input.eventNameImageUrl,
            eventNameImage = input.eventNameImage,
            requireDelivery = input.requireDelivery,
            orderNote = input.orderNote,
            status = ShowSessionDraftStatus.None,
            city = CityDocument(cityDoc.cityId, cityDoc.cityName, cityDoc.cityCode),
            isFree = input.isFree,
            showInHome = input.showInHome,
            manageTicket = input.manageTicket,
            qrcodeEmail = input.qrcodeEmail,
            slug = input.slug,
            sessionType = input.sessionType,
            calendar = input.calendar.takeIf { input.sessionType == SessionType.CALENDAR },
            // Vé theo lịch chỉ bán vé số lượng, không có bước chọn sơ đồ ghế.
            edType = CalendarRules.PROMOTABLE_ED_TYPE.takeIf { input.sessionType == SessionType.CALENDAR },
        )

        showSessionDraftDBService.createShowSessionDraft(emptyShowSessionDoc)

        logger.info("show session draft {} created", emptyShowSessionDoc)

        call.respond(HttpStatusCode.OK, emptyShowSessionDoc)
    }

    suspend fun updateSessionDraft(call: ApplicationCall) {
        val input = call.receive<UpdateShowSessionDraftRequest>()

        if (!ObjectId.isValid(input.id)) return call.respond(
            HttpStatusCode.BadRequest,
            jsonFactory.objectNode().put("message", "Invalid show id"),
        )

        if (input.sessionName.isNullOrBlank()) {
            return call.respond(
                HttpStatusCode.BadRequest,
                jsonFactory.objectNode().put("message", "Invalid session name"),
            )
        }

        if (input.code.isEmpty()) {
            return call.respond(
                HttpStatusCode.BadRequest,
                jsonFactory.objectNode().put("message", "Invalid code"),
            )
        }

        if (input.address.isEmpty()) {
            return call.respond(
                HttpStatusCode.BadRequest,
                jsonFactory.objectNode().put("message", "Invalid address"),
            )
        }

        if (input.startTime == 0L) {
            return call.respond(
                HttpStatusCode.BadRequest,
                jsonFactory.objectNode().put("message", "Invalid start date"),
            )
        }

        if (input.endTime == 0L) {
            return call.respond(
                HttpStatusCode.BadRequest,
                jsonFactory.objectNode().put("message", "Invalid end date"),
            )
        }

        if (input.qrcodeEmail == true && input.manageTicket != true) {
            return call.respond(
                HttpStatusCode.BadRequest,
                jsonFactory.objectNode().put("message", "qrcodeEmail requires manageTicket"),
            )
        }

        val cityDoc = showClient.getCity(input.cityId) ?: return call.respond(
            HttpStatusCode.BadRequest,
            jsonFactory.objectNode().put("message", "Invalid city"),
        )

        validateCalendarDraft(input.sessionType, input.calendar, input.manageTicket)?.let {
            return call.respond(HttpStatusCode.BadRequest, jsonFactory.objectNode().put("message", it))
        }

        val existing = showSessionDraftDBService.loadSessionDraft(input.id) ?: return call.respond(
            HttpStatusCode.BadRequest,
            jsonFactory.objectNode().put("message", "Cannot update session draft"),
        )
        if (input.sessionType == SessionType.CALENDAR &&
            (existing.seatPlanId != null || (existing.edType != null && existing.edType != CalendarRules.PROMOTABLE_ED_TYPE))) {
            return call.respond(
                HttpStatusCode.BadRequest,
                jsonFactory.objectNode().put("message", "Nháp đang dùng sơ đồ ghế, không chuyển sang vé theo lịch được"),
            )
        }
        if (existing.status != ShowSessionDraftStatus.None) {
            return call.respond(
                HttpStatusCode.BadRequest,
                jsonFactory.objectNode().put("message", "Session draft is not editable"),
            )
        }

        val supersededTemps = supersededTempImagePaths(
            oldLedgend = existing.ledgendImage,
            newLedgend = input.ledgendImage,
            oldSquare = existing.eventSquareImage,
            newSquare = input.eventSquareImage,
            oldName = existing.eventNameImage,
            newName = input.eventNameImage,
        )

        val sessionDraft = showSessionDraftDBService.updateShowSessionDraft(
            sessionDraftId = input.id,
            sessionName = input.sessionName,
            code = input.code,
            address = input.address,
            startTime = input.startTime,
            endTime = input.endTime,
            requireDelivery = input.requireDelivery,
            ledgendImage = input.ledgendImage,
            ledgendImageUrl = input.ledgendImageUrl,
            eventSquareImage = input.eventSquareImage,
            eventSquareImageUrl = input.eventSquareImageUrl,
            eventNameImage = input.eventNameImage,
            eventNameImageUrl = input.eventNameImageUrl,
            orderNote = input.orderNote,
            city = CityDocument(cityDoc.cityId, cityDoc.cityName, cityDoc.cityCode),
            isFree = input.isFree ?: false,
            showInHome = input.showInHome ?: false,
            manageTicket = input.manageTicket,
            qrcodeEmail = input.qrcodeEmail,
            slug = input.slug,
            sessionType = input.sessionType,
            calendar = input.calendar.takeIf { input.sessionType == SessionType.CALENDAR },
        ) ?: return call.respond(
            HttpStatusCode.BadRequest,
            jsonFactory.objectNode().put("message", "Cannot update session draft")
        )

        if (supersededTemps.isNotEmpty()) {
            val deleted = showClient.deleteTempImages(supersededTemps)
            logger.info(
                "show session draft {} update removed {} superseded temp image(s) (requested {})",
                sessionDraft.id,
                deleted,
                supersededTemps.size,
            )
        }

        logger.info("show session draft {} updated", sessionDraft)

        call.respond(HttpStatusCode.OK, sessionDraft)
    }

    suspend fun updateSessionDraftPayment(call: ApplicationCall) {
        val input = call.receive<UpdateShowSessionDraftPaymentRequest>()

        if (!ObjectId.isValid(input.id)) return call.respond(
            HttpStatusCode.BadRequest,
            jsonFactory.objectNode().put("message", "Invalid show id"),
        )

        val sessionDraft = showSessionDraftDBService.updateShowSessionDraftPayment(
            input.id, input.paymentOffline, input.paymentNote, input.pgAccountCode, input.paymentMethods
        ) ?: return call.respond(
            HttpStatusCode.BadRequest,
            jsonFactory.objectNode().put("message", "Cannot update session draft")
        )
        logger.info("show session draft {} updated", sessionDraft)

        call.respond(HttpStatusCode.OK, sessionDraft)
    }

    suspend fun updateSessionDraftSeatPlan(call: ApplicationCall) {
        val input = call.receive<UpdateShowSessionDraftSeatPlanRequest>()

        if (!ObjectId.isValid(input.id)) return call.respond(
            HttpStatusCode.BadRequest,
            jsonFactory.objectNode().put("message", "Invalid show id"),
        )

        val draft = showSessionDraftDBService.loadSessionDraft(input.id) ?: return call.respond(
            HttpStatusCode.BadRequest,
            jsonFactory.objectNode().put("message", "Cannot update session draft")
        )
        if (draft.sessionType == SessionType.CALENDAR &&
            (input.seatPlanId != null || input.edType != CalendarRules.PROMOTABLE_ED_TYPE)) {
            return call.respond(
                HttpStatusCode.BadRequest,
                jsonFactory.objectNode().put("message", "Vé theo lịch chỉ dùng cấu hình vé ${CalendarRules.PROMOTABLE_ED_TYPE}, không dùng sơ đồ ghế"),
            )
        }

        // Blank / whitespace-only → clear; never invent <br> from newlines.
        val normalizedExtraInfo = normalizeExtraInfo(input.extraInfo)

        val sessionDraft =
            showSessionDraftDBService.updateShowSessionDraftSeatPlan(
                input.id,
                input.seatPlanId,
                input.edType,
                normalizedExtraInfo,
                clearExtraInfo = input.extraInfo != null && normalizedExtraInfo == null,
            )
                ?: return call.respond(
                    HttpStatusCode.BadRequest,
                    jsonFactory.objectNode().put("message", "Cannot update session draft")
                )
        logger.info("show session draft {} updated", sessionDraft)

        call.respond(HttpStatusCode.OK, sessionDraft)
    }

    suspend fun updateSessionDraftMailTemplates(call: ApplicationCall) {
        val input = call.receive<UpdateShowSessionDraftMailTemplatesRequest>()

        if (!ObjectId.isValid(input.id)) return call.respond(
            HttpStatusCode.BadRequest,
            jsonFactory.objectNode().put("message", "Invalid show id"),
        )

        val sessionDraft = showSessionDraftDBService.updateShowSessionDraftMailTemplates(input.id, input.mailTemplates)
            ?: return call.respond(
                HttpStatusCode.BadRequest,
                jsonFactory.objectNode().put("message", "Cannot update session draft")
            )
        logger.info("show session draft {} updated", sessionDraft)

        call.respond(HttpStatusCode.OK, sessionDraft)
    }

    suspend fun loadShowSessionsDraft(call: ApplicationCall) {
        val showId = call.parameters["showId"]
            ?: return call.respond(
                HttpStatusCode.BadRequest,
                jsonFactory.objectNode().put("message", "Missing or malformed show id")
            )

        val showSessionsDraftDocument = showSessionDraftDBService.loadSessionsDraft(showId)

        call.respond(HttpStatusCode.OK, showSessionsDraftDocument)
    }

    suspend fun createShowSessionFromSessionDraft(call: ApplicationCall) {
        val input = call.receive<CreateShowSessionRequestDraft>()
        var sessionDraft: ShowSessionDraftDocument? =
            showSessionDraftDBService.loadSessionDraft(input.id) ?: return call.respond(
                HttpStatusCode.BadRequest,
                jsonFactory.objectNode().put("message", "Session draft invalid")
            )

        if (sessionDraft!!.sessionId != null && ObjectId.isValid(sessionDraft.sessionId)) {
            return call.respond(
                HttpStatusCode.BadRequest,
                jsonFactory.objectNode().put("message", "Session already created")
            )
        }

        val mailPluginTemplate = sessionDraft.mailPluginTemplate?.map {
            ShowMessage.MailPluginTemplateProto.newBuilder()
                .setMailTemplate(it.templateId)
                .setMailPlugin(it.plugin)
                .setOrderStatus(it.orderStatus)
                .build()
        }
        val sessionId = showClient.createSession(
            ShowMessage.CreateSessionRequest.newBuilder().also { builder ->
                builder.showId = sessionDraft.showId
                sessionDraft.city?.let { builder.cityId = it.id }
                builder.venue = sessionDraft.address
                sessionDraft.seatPlanId?.let { builder.seatPlanId = it }
                sessionDraft.edType?.let { builder.edType = it }
                builder.requireDelivery = sessionDraft.requireDelivery
                sessionDraft.paymentOffline?.let { builder.paymentOffline = it }
                mailPluginTemplate?.let { builder.addAllMailPluginTemplate(it) }
                sessionDraft.paymentNote?.let { builder.paymentNote = it }
                sessionDraft.orderNote?.let { builder.orderNote = it }
                sessionDraft.code?.let { builder.code = it }
                sessionDraft.sessionName?.let { builder.sessionName = it }
                builder.startTime = sessionDraft.startTime
                builder.endTime = sessionDraft.endTime
                sessionDraft.ledgendImage?.let { builder.ledgendImage = it }
                sessionDraft.eventSquareImage?.let { builder.eventSquareImage = it }
                sessionDraft.eventNameImage?.let { builder.eventNameImage = it }
                sessionDraft.extraInfo?.let { builder.extraInfo = it }
                sessionDraft.pgAccountCode?.let { builder.pgAccountCode = it }
                sessionDraft.paymentMethods?.let { builder.addAllPaymentMethods(it) }
                sessionDraft.manageTicket?.let { builder.manageTicket = it }
                sessionDraft.qrcodeEmail?.let { builder.qrcodeEmail = it }
                sessionDraft.slug?.takeIf { it.isNotBlank() }?.let { builder.slug = it }
                sessionDraft.sessionType?.let { builder.sessionType = it.name }
                sessionDraft.calendar?.let { calendar ->
                    builder.calendar = ShowMessage.SessionCalendarProto.newBuilder()
                        .setOpenTime(calendar.openTime)
                        .setCloseTime(calendar.closeTime)
                        .setBookingWindowDays(calendar.bookingWindowDays)
                        .also { c -> calendar.sameDayCutoffMinutes?.let { c.setSameDayCutoffMinutes(it) } }
                        .build()
                }
            }.build()
        ) ?: return call.respond(
            HttpStatusCode.BadRequest,
            jsonFactory.objectNode().put("message", "Create Session failed")
        )
        sessionDraft =
            showSessionDraftDBService.updateShowSessionDraftSessionId(sessionDraft.id, sessionId) ?: return call.respond(
                HttpStatusCode.BadRequest,
                jsonFactory.objectNode().put("message", "Session draft invalid")
            )

        val orderCustomFields =
            showSessionDraftOrderCustomFieldDBService.loadSessionsDraftOrderCustomFields(sessionDraft.id)

        if (orderCustomFields != null) {
            orderClient.createOrderCustomFieldsMapping(
                sessionDraft.showId,
                sessionDraft.sessionId!!,
                orderCustomFields.customFields
            )
        }

        val promotions = showSessionDraftPromotionDBService.loadSessionsDraftPromotions(sessionDraft.id)

        if (promotions.isNotEmpty()) {
            promotions.map {
                orderClient.createOrderPromotion(
                    sessionDraft.showId,
                    sessionDraft.sessionId!!,
                    it.data,
                    "metaticket.order.calculate.promotion.meta_promo.MetaPromoPlugin"
                )
            }
        }


        call.respond(HttpStatusCode.OK, sessionDraft)
    }

    suspend fun deleteSessionDraft(call: ApplicationCall) {
        val input = call.receive<DeleteShowSessionDraftRequest>()

        if (!ObjectId.isValid(input.id)) {
            return call.respond(
                HttpStatusCode.BadRequest,
                jsonFactory.objectNode().put("message", "Invalid draft id"),
            )
        }

        val existing = showSessionDraftDBService.loadSessionDraft(input.id) ?: return call.respond(
            HttpStatusCode.NotFound,
            jsonFactory.objectNode().put("message", "Session draft not found"),
        )
        if (existing.status != ShowSessionDraftStatus.None) {
            return call.respond(
                HttpStatusCode.BadRequest,
                jsonFactory.objectNode().put("message", "Session draft cannot be deleted"),
            )
        }
        if (existing.sessionId != null && ObjectId.isValid(existing.sessionId)) {
            return call.respond(
                HttpStatusCode.BadRequest,
                jsonFactory.objectNode().put("message", "Session already created from this draft"),
            )
        }

        val deleted = showSessionDraftDBService.softDeleteShowSessionDraft(input.id) ?: return call.respond(
            HttpStatusCode.BadRequest,
            jsonFactory.objectNode().put("message", "Cannot delete session draft"),
        )

        showSessionDraftPromotionDBService.deleteBySessionDraftId(deleted.id)
        showSessionDraftOrderCustomFieldDBService.deleteBySessionDraftId(deleted.id)

        val temps = draftTempImagePaths(deleted)
        if (temps.isNotEmpty()) {
            val n = showClient.deleteTempImages(temps)
            logger.info("show session draft {} deleted; removed {} temp image(s)", deleted.id, n)
        }

        logger.info("show session draft {} soft-deleted", deleted.id)
        call.respond(HttpStatusCode.OK, jsonFactory.objectNode().put("id", deleted.id).put("deleted", true))
    }

    /**
     * Keeps operator HTML as typed (including explicit `&lt;br&gt;`); blank / whitespace-only → null.
     * Does not convert newlines into `&lt;br&gt;`.
     */
    private fun normalizeExtraInfo(raw: String?): String? {
        if (raw == null) return null
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) return null
        val collapsed = trimmed
            .replace("\u00a0", " ")
            .replace("&nbsp;", " ", ignoreCase = true)
            .trim()
        if (collapsed.isEmpty()) return null
        return trimmed
    }

    /** Collects local temp paths still referenced by a draft document. */
    private fun draftTempImagePaths(draft: ShowSessionDraftDocument): List<String> =
        listOfNotNull(draft.ledgendImage, draft.eventSquareImage, draft.eventNameImage)
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .distinct()

    /** Paths that were replaced or cleared on draft update and can be deleted. */
    private fun supersededTempImagePaths(
        oldLedgend: String?,
        newLedgend: String?,
        oldSquare: String?,
        newSquare: String?,
        oldName: String?,
        newName: String?,
    ): List<String> {
        fun superseded(old: String?, new: String?): String? {
            val o = old?.trim()?.takeIf { it.isNotEmpty() } ?: return null
            val n = new?.trim()?.takeIf { it.isNotEmpty() }
            return if (o != n) o else null
        }
        return listOfNotNull(
            superseded(oldLedgend, newLedgend),
            superseded(oldSquare, newSquare),
            superseded(oldName, newName),
        ).distinct()
    }
}
