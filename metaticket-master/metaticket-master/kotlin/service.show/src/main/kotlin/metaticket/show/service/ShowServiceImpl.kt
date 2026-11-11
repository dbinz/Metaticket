package metaticket.show.service

import com.google.protobuf.Empty
import com.google.protobuf.StringValue
import io.grpc.Status
import io.grpc.StatusException
import io.ktor.server.application.*
import metaticket.common.coroutine.launch
import metaticket.common.logger.Logging
import metaticket.show.database.service.CityDBService
import metaticket.show.database.service.DatabaseService
import metaticket.show.datastructure.MailPluginTemplate
import metaticket.show.datastructure.SessionType
import metaticket.show.gateway.JobrunrGateway
import metaticket.show.grpc.ShowMessage
import metaticket.show.grpc.ShowServiceGrpcKt
import metaticket.show.metasearch.MetaSearchClient
import metaticket.show.metasearch.MetaSearchUpdateSoldRequest
import metaticket.show.pojo.db.ShowSessionDocument
import org.bson.types.ObjectId
import java.io.File
import java.util.*

class ShowServiceImpl(
    env : ApplicationEnvironment,
    private val db : DatabaseService,
    private val cityDBService: CityDBService,
    private val jobrunrGateway: JobrunrGateway,
    private val metaSearchClient: MetaSearchClient,
): ShowServiceGrpcKt.ShowServiceCoroutineImplBase(), Logging {
    private val imagePath = env.config.property("storage_path.image_path").getString()
    private val imagePathTemp = env.config.property("storage_path.image_path_temp").getString()
    private val imagePrefix = env.config.property("storage_path.image_prefix").getString()

    override suspend fun createSession(request: ShowMessage.CreateSessionRequest): StringValue {
        if (request.hasSeatPlanId() && !ObjectId.isValid(request.seatPlanId))
            throw StatusException(Status.INVALID_ARGUMENT.withDescription("Invalid seat plan id"))

        val cityDoc = cityDBService.loadCity(request.cityId)
            ?: throw StatusException(Status.NOT_FOUND.withDescription("City not found"))

        // Normalize + validate the optional Session_Slug and enforce within-show uniqueness BEFORE
        // building the session or touching the filesystem/DB, so a rejected promotion leaves the
        // draft and the stored Sessions unchanged. This block sits OUTSIDE the try/catch below so the
        // invalid/duplicate StatusExceptions are not masked as INTERNAL.
        //   - invalid format/length → INVALID_ARGUMENT "Invalid slug"
        //   - duplicate within the parent Show → ALREADY_EXISTS "Duplicate slug"
        //   - absent (unset/empty/whitespace) → stored as null, validation skipped
        val canonicalSlug: String? = if (request.hasSlug() && request.slug.isNotBlank()) {
            when (SlugValidator.checkFormat(request.slug)) {
                is SlugCheck.Invalid ->
                    throw StatusException(Status.INVALID_ARGUMENT.withDescription("Invalid slug"))
                SlugCheck.Valid -> SlugNormalizer.normalize(request.slug)
            }
        } else {
            null
        }
        if (canonicalSlug != null) {
            val show = db.loadShow(request.showId)
                ?: throw StatusException(Status.NOT_FOUND.withDescription("Show not found"))
            if (show.sessions.any { it.slug == canonicalSlug }) {
                throw StatusException(Status.ALREADY_EXISTS.withDescription("Duplicate slug"))
            }
        }

        val sessionType = if (request.hasSessionType()) {
            runCatching { SessionType.valueOf(request.sessionType) }.getOrNull()
                ?: throw StatusException(Status.INVALID_ARGUMENT.withDescription("Invalid session type"))
        } else null
        val calendar = if (sessionType == SessionType.CALENDAR && request.hasCalendar()) request.calendar.toDocument() else null
        val calendarError = CalendarSessionValidator.validate(
            sessionType, calendar, if (request.hasManageTicket()) request.manageTicket else null,
            Date(request.startTime), Date(request.endTime),
        ) ?: CalendarSessionValidator.validateSeatPlan(
            sessionType, if (request.hasEdType()) request.edType else null,
            if (request.hasSeatPlanId()) request.seatPlanId else null,
        )
        if (calendarError != null) throw StatusException(Status.INVALID_ARGUMENT.withDescription(calendarError))

        try {
            val tmpShowSessionDoc = ShowSessionDocument(
                sessionName = request.sessionName,
                code = request.code,
                seatPlanId = if (request.hasSeatPlanId()) request.seatPlanId else null,
                edType = request.edType,
                venue = request.venue,
                requireDelivery = request.requireDelivery,
                paymentOffline = if (request.hasPaymentOffline()) request.paymentOffline else false,
                mailPluginTemplate = request.mailPluginTemplateList.map {
                    MailPluginTemplate(
                        it.orderStatus,
                        it.mailPlugin,
                        it.mailTemplate
                    )
                },
                orderNote = request.orderNote,
                paymentNote = request.paymentNote,
                startTime = Date(request.startTime),
                endTime = Date(request.endTime),
                city = cityDoc,
                manageTicket = if (request.hasManageTicket()) request.manageTicket else null,
                qrcodeEmail = if (request.hasQrcodeEmail()) request.qrcodeEmail else null,
                sessionType = sessionType,
                calendar = calendar,
                )

            // Persist the validated Canonical_Session_Slug (or null when absent). Stored value is
            // always canonical.
            tmpShowSessionDoc.slug = canonicalSlug

            if (request.hasLedgendImage()) {
                val target =
                    File("${imagePath}/${request.showId}-${tmpShowSessionDoc.id}-session-ledgend-image.png")
                metaticket.show.util.ShowImageFiles.moveTempToPermanent(
                    File(request.ledgendImage),
                    target,
                    logger,
                )
                tmpShowSessionDoc.ledgendImageUrl =
                    "${imagePrefix}${imagePath}/${request.showId}-${tmpShowSessionDoc.id}-session-ledgend-image.png"
            }

            if (request.hasEventSquareImage()) {
                val target =
                    File("${imagePath}/${request.showId}-${tmpShowSessionDoc.id}-session-event-square-image.png")
                metaticket.show.util.ShowImageFiles.moveTempToPermanent(
                    File(request.eventSquareImage),
                    target,
                    logger,
                )
                tmpShowSessionDoc.eventSquareImageUrl =
                    "${imagePrefix}${imagePath}/${request.showId}-${tmpShowSessionDoc.id}-session-event-square-image.png"
            }

            if (request.hasEventNameImage()) {
                val target =
                    File("${imagePath}/${request.showId}-${tmpShowSessionDoc.id}-session-event-name-image.png")
                metaticket.show.util.ShowImageFiles.moveTempToPermanent(
                    File(request.eventNameImage),
                    target,
                    logger,
                )
                tmpShowSessionDoc.eventNameImageUrl =
                    "${imagePrefix}${imagePath}/${request.showId}-${tmpShowSessionDoc.id}-session-event-name-image.png"
            }

            if (request.hasExtraInfo()) {
                tmpShowSessionDoc.extraInfo =
                    metaticket.show.util.HtmlContentNormalizer.normalize(request.extraInfo)
            }

            db.createShowSession(tmpShowSessionDoc, showId = request.showId)

            logger.info("show session {} created", tmpShowSessionDoc)

            launch { jobrunrGateway.onShowChange(request.showId) }

            return StringValue.of(tmpShowSessionDoc.id)
        } catch (t: Throwable) {
            logger.error("cannot create show session", t)
            throw StatusException(Status.INTERNAL.withDescription(t.message ?: "Cannot create show session"))
        }
    }

    /**
     * generating order id in format <show code>|<session code>|<unique increment>
     */
    override suspend fun generateUniqueId(request: ShowMessage.GenerateUniqueIdRequest): StringValue {
        val showDocument = db.increaseUniqueIncrement(request.showId, request.sessionId)
            ?: throw StatusException(Status.NOT_FOUND.withDescription("Show not found"))

        val session = showDocument.sessions.firstOrNull { it.id == request.sessionId }
            ?: throw StatusException(Status.NOT_FOUND.withDescription("Session not found"))

        val unique = String.format("%06d", session.uniqueIncrement)
        val id = "${showDocument.code}${session.code?:""}${unique}"

        return StringValue.of(id)
    }

    override suspend fun updateSoldTicket(request: ShowMessage.UpdateSoldTicketRequest): Empty {
        val show = db.updateSoldTicket(request.showId, request.soldTicket) ?: run {
            logger.error("show {} failed to update sold ticket", request.showId)
            throw StatusException(Status.NOT_FOUND.withDescription("fail to update sold ticket"))
        }

        logger.info("show {} update sold ticket {} success", request.showId, show.soldTicket)

        launch {
            val sentToMetaSearch = metaSearchClient.executeSendUpdateSoldTicket(
                MetaSearchUpdateSoldRequest(
                    id = request.showId,
                    soldTicket = show.soldTicket.toLong()
                )
            )
            if (!sentToMetaSearch) {
                logger.info("show {} failed send update sold ticket {} to metasearch", request.showId, show.soldTicket)
                jobrunrGateway.onShowChange(request.showId)
            }
        }

        return Empty.getDefaultInstance()
    }

    override suspend fun getSessionById(request: ShowMessage.GetSessionByIdRequest): ShowMessage.GetSessionResponse {
        val sessionDocument = try {
            db.loadShow(request.showId)?.sessions?.firstOrNull { it.id == request.sessionId }
                ?: throw StatusException(Status.NOT_FOUND.withDescription("Session not found"))
        } catch (t: Throwable) {
            logger.error("load show {} session {} exception... ", request.showId, request.sessionId, t)
            throw StatusException(Status.INTERNAL.withDescription("Lõi server"))
        }

        return try {
            ShowMessage.GetSessionResponse.newBuilder()
                .setShowId(request.showId)
                .setSessionId(sessionDocument.id)
                .setCode(sessionDocument.code)
                .setStatus(sessionDocument.status.name)
                .setVenue(sessionDocument.venue)
                .setRequireDelivery(sessionDocument.requireDelivery)
                .setStartTime(sessionDocument.startTime.time)
                .addAllMailPluginTemplate(sessionDocument.mailPluginTemplate.map { ShowMessage.MailPluginTemplateProto.newBuilder().setMailTemplate(it.templateId).setMailPlugin(it.plugin).setOrderStatus(it.orderStatus).build() })
                .apply {
                    sessionDocument.paymentMethods?.let { m -> addAllPaymentMethods(m.map { it.name }) }
                    sessionDocument.orderNote?.let { setOrderNote(it) }
                    sessionDocument.paymentNote?.let { setPaymentNote(it) }
                    sessionDocument.pgAccountCode?.let { setPgAccountCode(it) }
                    sessionDocument.paymentOffline?.let { setPaymentOffline(it) }
                    sessionDocument.eventSquareImageUrl?.let { setEventSquareImageUrl(it) }
                    sessionDocument.eventNameImageUrl?.let { setEventNameImageUrl(it) }
                    sessionDocument.ledgendImageUrl?.let { setLedgendImageUrl(it) }
                    sessionDocument.extraInfo?.let { setExtraInfo(it) }
                    sessionDocument.manageTicket?.let { setManageTicket(it) }
                    sessionDocument.qrcodeEmail?.let { setQrcodeEmail(it) }
                }
                .applyCalendarFields(sessionDocument)
                .build()
        } catch (t: Throwable) {
            logger.error("load show {} session {} exception... ", request.showId, request.sessionId, t)
            throw StatusException(Status.INTERNAL.withDescription("Lõi server"))
        }
    }

    override suspend fun getShowById(request: ShowMessage.GetShowByIdRequest): ShowMessage.GetShowResponse {
        val showDoc = db.loadShow(request.showId)
            ?: throw StatusException(Status.NOT_FOUND.withDescription("Show not found"))

        return ShowMessage.GetShowResponse.newBuilder().apply {
            setShowId(showDoc.id)
            setShowName(showDoc.showName)
            setCode(showDoc.code)
            setStatus(showDoc.status.name)
            setShipPrice(showDoc.shipPrice)
            setCreatedAt(showDoc.createdAt.time)
            addAllSessions(showDoc.sessions.map { sessionDocument ->
                ShowMessage.GetSessionResponse.newBuilder()
                    .setShowId(request.showId)
                    .setSessionId(sessionDocument.id)
                    .setCode(sessionDocument.code)
                    .setStatus(sessionDocument.status.name)
                    .setVenue(sessionDocument.venue)
                    .setRequireDelivery(sessionDocument.requireDelivery)
                    .setStartTime(sessionDocument.startTime.time)
                    .addAllMailPluginTemplate(sessionDocument.mailPluginTemplate.map { ShowMessage.MailPluginTemplateProto.newBuilder().setMailTemplate(it.templateId).setMailPlugin(it.plugin).setOrderStatus(it.orderStatus).build() })
                    .apply {
                        sessionDocument.paymentMethods?.let { m -> addAllPaymentMethods(m.map { it.name }) }
                        sessionDocument.orderNote?.let { setOrderNote(it) }
                        sessionDocument.paymentNote?.let { setPaymentNote(it) }
                        sessionDocument.pgAccountCode?.let { setPgAccountCode(it) }
                        sessionDocument.paymentOffline?.let { setPaymentOffline(it) }
                        sessionDocument.eventSquareImageUrl?.let { setEventSquareImageUrl(it) }
                        sessionDocument.eventNameImageUrl?.let { setEventNameImageUrl(it) }
                        sessionDocument.ledgendImageUrl?.let { setLedgendImageUrl(it) }
                        sessionDocument.extraInfo?.let { setExtraInfo(it) }
                        sessionDocument.manageTicket?.let { setManageTicket(it) }
                        sessionDocument.qrcodeEmail?.let { setQrcodeEmail(it) }
                    }
                    .applyCalendarFields(sessionDocument)
                    .build()
            })
        }.build()
    }

    override suspend fun getCity(request: ShowMessage.GetCityRequest): ShowMessage.CityProto {
        val cityDoc = cityDBService.loadCity(request.cityId)
            ?: throw StatusException(Status.NOT_FOUND.withDescription("City not found"))

        return ShowMessage.CityProto.newBuilder().setCityId(cityDoc.id).setCityName(cityDoc.name).setCityCode(cityDoc.code).build()
    }

    /** Deletes temp uploads under image_path_temp; paths outside that root are ignored. */
    override suspend fun deleteTempImages(
        request: ShowMessage.DeleteTempImagesRequest,
    ): ShowMessage.DeleteTempImagesResponse {
        val deleted = try {
            metaticket.show.util.ShowImageFiles.deleteTempFiles(
                request.filePathsList,
                imagePathTemp,
                logger,
            )
        } catch (t: Throwable) {
            logger.error("deleteTempImages failed", t)
            throw StatusException(Status.INTERNAL.withDescription(t.message ?: "Cannot delete temp images"))
        }
        return ShowMessage.DeleteTempImagesResponse.newBuilder().setDeletedCount(deleted).build()
    }
}