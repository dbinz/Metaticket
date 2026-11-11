package metaticket.show.controller

import com.fasterxml.jackson.databind.node.JsonNodeFactory
import com.mongodb.ErrorCategory
import com.mongodb.MongoCommandException
import com.mongodb.MongoException
import com.mongodb.MongoWriteException
import io.ktor.http.*
import io.ktor.http.content.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import metaticket.common.coroutine.launch
import metaticket.common.logger.Logging
import metaticket.show.cloudflare.CloudflareClient
import metaticket.show.database.service.CityDBService
import metaticket.show.database.service.DatabaseService
import metaticket.show.database.service.SessionSlugUpdateResult
import metaticket.show.datastructure.SessionType
import metaticket.show.datastructure.ShowSessionStatus
import metaticket.show.datastructure.ShowSessionStatus.*
import metaticket.show.gateway.JobrunrGateway
import metaticket.show.gateway.TicketGateway
import metaticket.show.metasearch.MetaSearchClient
import metaticket.show.pojo.BANNER_PIN_SLOT_COUNT
import metaticket.show.pojo.BannerPinBoardResponse
import metaticket.show.pojo.BannerPinCandidatePage
import metaticket.show.pojo.BannerPinSlotResponse
import metaticket.show.pojo.db.ShowDocument
import metaticket.show.pojo.request.*
import metaticket.show.pojo.toBannerPinSummary
import metaticket.show.pojo.toResponse
import metaticket.show.service.CalendarSessionValidator
import metaticket.show.service.HomepageConfig
import metaticket.show.service.ShareLinkBuilder
import metaticket.show.service.SlugCheck
import metaticket.show.service.SlugNormalizer
import metaticket.show.service.SlugValidator
import metaticket.show.util.HtmlContentNormalizer
import org.bson.types.ObjectId
import java.io.File
import java.util.*

private val TYPE_CHANGEABLE_STATUSES = listOf(ShowSessionStatus.Created, ShowSessionStatus.Rejected)

class ShowController(
    env : ApplicationEnvironment,
    private val db : DatabaseService,
    private val cityDBService: CityDBService,
    private val jsonFactory : JsonNodeFactory,
    private val jobrunrGateway: JobrunrGateway,
    private val ticketGateway: TicketGateway,
    private val metaSearchClient: MetaSearchClient,
    private val cloudflareClient: CloudflareClient,
    private val homepageConfig: HomepageConfig,
) : Logging {
    private val imagePath = env.config.property("storage_path.image_path").getString()
    private val imagePathTemp = env.config.property("storage_path.image_path_temp").getString()
    private val imagePrefix = env.config.property("storage_path.image_prefix").getString()

    /**
     * Whether [e] is a duplicate-key violation on the partial unique index for [ShowDocument.slug].
     *
     * `insertOne` (create) surfaces this as [MongoWriteException], while `findOneAndUpdate` (update)
     * runs the `findAndModify` command and surfaces the same duplicate-key (11000) as a
     * [MongoCommandException]. Both must be recognised so a duplicate slug maps to a 409 instead of
     * escaping as a generic 500.
     */
    private fun isDuplicateSlugError(e: MongoException): Boolean = when (e) {
        is MongoWriteException ->
            e.error.category == ErrorCategory.DUPLICATE_KEY &&
                e.error.message.contains(ShowDocument::slug.name)
        is MongoCommandException ->
            e.errorCode == 11000 &&
                e.errorMessage.contains(ShowDocument::slug.name)
        else -> false
    }

    suspend fun createShow(call : ApplicationCall) {
        val createShowRequest = call.receive<CreateShowRequest>()

        if(createShowRequest.showName.isEmpty()) {
            return call.respond(HttpStatusCode.BadRequest,
                jsonFactory.objectNode().put("message", "Missing show name")
            )
        } else if(createShowRequest.code.isEmpty()) {
            return call.respond(HttpStatusCode.BadRequest,
                jsonFactory.objectNode().put("message", "Missing show code")
            )
        } else if(createShowRequest.bannerImage.isNullOrBlank()) {
            return call.respond(HttpStatusCode.BadRequest,
                jsonFactory.objectNode().put("message", "Missing show banner image")
            )
        } else if(createShowRequest.detailImageUrl.isNullOrBlank()) {
            return call.respond(HttpStatusCode.BadRequest,
                jsonFactory.objectNode().put("message", "Missing show detail image")
            )
        } else if(createShowRequest.thumbnailUrl.isNullOrBlank()) {
            return call.respond(HttpStatusCode.BadRequest,
                jsonFactory.objectNode().put("message", "Missing show thumbnail image")
            )
        }

        // Normalize + validate the optional Show_Slug BEFORE any stored or filesystem mutation.
        // Absent (null/empty/whitespace) → stored as null (normalization/validation skipped).
        // Invalid → 400 with the original rejected slug, no mutation.
        val rawSlug = createShowRequest.slug
        val canonicalSlug: String? = if (rawSlug.isNullOrBlank()) {
            null
        } else when (SlugValidator.checkFormat(rawSlug)) {
            is SlugCheck.Invalid -> return call.respond(HttpStatusCode.BadRequest,
                jsonFactory.objectNode().put("message", "Invalid slug").put("slug", rawSlug)
            )
            SlugCheck.Valid -> SlugNormalizer.normalize(rawSlug)
        }

        val showDoc = ShowDocument(
            id = ObjectId().toHexString(),
            code = createShowRequest.code,
            showName = createShowRequest.showName,
            slug = canonicalSlug,
            website = createShowRequest.website,
            faq = createShowRequest.faq,
            eventCategoryCode = createShowRequest.eventCategoryCode,
            eventCategory = createShowRequest.eventCategory,
            description = HtmlContentNormalizer.normalize(createShowRequest.description),
            ticketDescription = HtmlContentNormalizer.normalize(createShowRequest.ticketDescription),
            policy = HtmlContentNormalizer.normalize(createShowRequest.policy),
            pinned = false,
            pinOrder = null,
            hideSessions = createShowRequest.hideSessions,
        )

        if(!createShowRequest.bannerImage.isNullOrBlank()) {
            val sourceBanner = File(createShowRequest.bannerImage!!)
            val targetBanner = File("${imagePath}/${showDoc.id}-show-banner-image.png")
            sourceBanner.renameTo(targetBanner)
            showDoc.bannerImageUrl = "${imagePrefix}${imagePath}/${showDoc.id}-show-banner-image.png?v=${Date().time}"
        }

        if(!createShowRequest.detailImageUrl.isNullOrBlank()) {
            val sourceBanner = File(createShowRequest.detailImageUrl!!)
            val targetBanner = File("${imagePath}/${showDoc.id}-show-detail-image.png")
            sourceBanner.renameTo(targetBanner)
            showDoc.detailImageUrl = "${imagePrefix}${imagePath}/${showDoc.id}-show-detail-image.png?v=${Date().time}"
        }

        if(!createShowRequest.thumbnailUrl.isNullOrBlank()) {
            val sourceBanner = File(createShowRequest.thumbnailUrl!!)
            val targetBanner = File("${imagePath}/${showDoc.id}-show-thumbnail-image.png")
            sourceBanner.renameTo(targetBanner)
            showDoc.thumbnailUrl = "${imagePrefix}${imagePath}/${showDoc.id}-show-thumbnail-image.png?v=${Date().time}"
        }

        if(!createShowRequest.logoShowImage.isNullOrBlank()) {
            val sourceLogoShow = File(createShowRequest.logoShowImage!!)
            val targetLogoShow = File("${imagePath}/${showDoc.id}-show-logo-show-image.png")
            sourceLogoShow.renameTo(targetLogoShow)
            showDoc.logoShowUrl = "${imagePrefix}${imagePath}/${showDoc.id}-show-logo-show-image.png?v=${Date().time}"
        }

        if(!createShowRequest.logoHostImage.isNullOrBlank()) {
            val sourceLogoHost = File(createShowRequest.logoHostImage!!)
            val targetLogoHost = File("${imagePath}/${showDoc.id}-show-logo-host-image.png")
            sourceLogoHost.renameTo(targetLogoHost)
            showDoc.logoHostUrl = "${imagePrefix}${imagePath}/${showDoc.id}-show-logo-host-image.png?v=${Date().time}"
        }

        try {
            db.createShow(showDoc) ?: run {
                logger.error("cannot insert db when create show for request {}", createShowRequest)
                return call.respond(HttpStatusCode.BadRequest,
                    jsonFactory.objectNode().put("message", "Create Image failed")
                )
            }
        } catch (e: MongoException) {
            // Duplicate-key from the partial unique index on the Show_Slug → 409.
            if (canonicalSlug != null && isDuplicateSlugError(e)) {
                logger.info("duplicate show slug {} on create", canonicalSlug)
                return call.respond(HttpStatusCode.Conflict,
                    jsonFactory.objectNode().put("message", "Duplicate slug").put("slug", canonicalSlug)
                )
            }
            throw e
        }

        call.respond(HttpStatusCode.OK, showDoc.toResponse())

        jobrunrGateway.onShowChange(showDoc.id)
    }

    suspend fun uploadImage(call : ApplicationCall) {
        val fileName = "${imagePathTemp}/temp-show-image-${Date().time}.png"
        val multipart = call.receiveMultipart()

        multipart.forEachPart { part ->
            when (part) {
                is PartData.FileItem -> {
                    when(part.name) {
                        "imageFile" -> {
                            val imageFile = part.streamProvider().readBytes()
                            File(fileName).writeBytes(imageFile)
                        }
                    }

                }
                else -> {}
            }
            part.dispose()
        }

        call.respond(HttpStatusCode.OK, jsonFactory.objectNode().put(
            "fileName", fileName
        ).put("fileUrl", "${imagePrefix}${fileName}"))
    }

    suspend fun updateShow(call : ApplicationCall) {
        val updateShowRequest = call.receive<UpdateShowRequest>()
        if(!ObjectId.isValid(updateShowRequest.id)) {
            return call.respond(HttpStatusCode.BadRequest,
                jsonFactory.objectNode().put("message", "Missing show id")
            )
        } else if(updateShowRequest.showName.isNullOrBlank()) {
            return call.respond(HttpStatusCode.BadRequest,
                jsonFactory.objectNode().put("message", "Missing show name")
            )
        } else if(updateShowRequest.code.isEmpty()) {
            return call.respond(HttpStatusCode.BadRequest,
                jsonFactory.objectNode().put("message", "Missing show code")
            )
        }

        val show = db.loadShow(updateShowRequest.id) ?: return call.respond(HttpStatusCode.BadRequest,
            jsonFactory.objectNode().put("message", "Invalid show")
        )

        // Normalize + validate the optional Show_Slug BEFORE any stored or filesystem mutation.
        // Absent (null/empty/whitespace) → stored as null. Invalid → 400 with the original rejected
        // slug, no mutation.
        val rawSlug = updateShowRequest.slug
        val canonicalSlug: String? = if (rawSlug.isNullOrBlank()) {
            null
        } else when (SlugValidator.checkFormat(rawSlug)) {
            is SlugCheck.Invalid -> return call.respond(HttpStatusCode.BadRequest,
                jsonFactory.objectNode().put("message", "Invalid slug").put("slug", rawSlug)
            )
            SlugCheck.Valid -> SlugNormalizer.normalize(rawSlug)
        }

        var bannerImageUrl = show.bannerImageUrl
        var detailImageUrl = show.detailImageUrl
        var thumbnailUrl = show.thumbnailUrl
        var logoShowUrl = show.logoShowUrl
        var logoHostUrl = show.logoHostUrl

        if(!updateShowRequest.bannerImage.isNullOrBlank()) {
            val sourceBanner = File(updateShowRequest.bannerImage!!)
            val targetBanner = File("${imagePath}/${show.id}-show-banner-image.png")
            sourceBanner.renameTo(targetBanner)
            bannerImageUrl = "${imagePrefix}${imagePath}/${show.id}-show-banner-image.png?v=${Date().time}"
        }

        if(!updateShowRequest.detailImageUrl.isNullOrBlank()) {
            val sourceBanner = File(updateShowRequest.detailImageUrl!!)
            val targetBanner = File("${imagePath}/${show.id}-show-detail-image.png")
            sourceBanner.renameTo(targetBanner)
            detailImageUrl = "${imagePrefix}${imagePath}/${show.id}-show-detail-image.png?v=${Date().time}"
        }

        if(!updateShowRequest.thumbnailUrl.isNullOrBlank()) {
            val sourceBanner = File(updateShowRequest.thumbnailUrl!!)
            val targetBanner = File("${imagePath}/${show.id}-show-thumbnail-image.png")
            sourceBanner.renameTo(targetBanner)
            thumbnailUrl = "${imagePrefix}${imagePath}/${show.id}-show-thumbnail-image.png?v=${Date().time}"
        }

        if(!updateShowRequest.logoShowImage.isNullOrBlank()) {
            val sourceLogoShow = File(updateShowRequest.logoShowImage!!)
            val targetLogoShow = File("${imagePath}/${show.id}-show-logo-show-image.png")
            sourceLogoShow.renameTo(targetLogoShow)
            logoShowUrl = "${imagePrefix}${imagePath}/${show.id}-show-logo-show-image.png?v=${Date().time}"
        }

        if(!updateShowRequest.logoHostImage.isNullOrBlank()) {
            val sourceLogoHost = File(updateShowRequest.logoHostImage!!)
            val targetLogoHost = File("${imagePath}/${show.id}-show-logo-host-image.png")
            sourceLogoHost.renameTo(targetLogoHost)
            logoHostUrl = "${imagePrefix}${imagePath}/${show.id}-show-logo-host-image.png?v=${Date().time}"
        }


        val showUpdated = try {
            db.updateShow(
                updateShowRequest.id,
                updateShowRequest.showName,
                updateShowRequest.code,
                updateShowRequest.faq,
                HtmlContentNormalizer.normalize(updateShowRequest.description),
                HtmlContentNormalizer.normalize(updateShowRequest.ticketDescription),
                HtmlContentNormalizer.normalize(updateShowRequest.policy),
                updateShowRequest.eventCategoryCode,
                updateShowRequest.eventCategory,
                bannerImageUrl,
                detailImageUrl,
                thumbnailUrl,
                logoHostUrl,
                logoShowUrl,
                updateShowRequest.hideSessions,
                canonicalSlug,
            )
        } catch (e: MongoException) {
            // Duplicate-key from the partial unique index on the Show_Slug. `findOneAndUpdate` surfaces
            // this as a MongoCommandException (code 11000), not a MongoWriteException, so both are handled
            // via isDuplicateSlugError. Reload by slug to confirm the conflict belongs to a *different*
            // show; self-assignment (a show keeping its own slug) is permitted by the index and never
            // raises here.
            if (canonicalSlug != null && isDuplicateSlugError(e)) {
                val conflicting = db.loadShowBySlug(canonicalSlug)
                if (conflicting == null || conflicting.id != updateShowRequest.id) {
                    logger.info("duplicate show slug {} on update of show {}", canonicalSlug, updateShowRequest.id)
                    return call.respond(HttpStatusCode.Conflict,
                        jsonFactory.objectNode().put("message", "Duplicate slug").put("slug", canonicalSlug)
                    )
                }
            }
            throw e
        } ?: run {
            logger.error("failed to update show {}", updateShowRequest.id)
            return call.respond(HttpStatusCode.BadRequest,
                jsonFactory.objectNode().put("message", "Missing or malformed id")
            )
        }

        logger.info("show {} updated", showUpdated.toResponse())

        call.respond(showUpdated.toResponse())

        jobrunrGateway.onShowChange(showUpdated.id)
    }

    suspend fun syncShowDataToMetasearch(call: ApplicationCall) {
        db.loadAllShow().forEach {
            val req = metaSearchClient.buildCreateUpdateShowRequest(it)
            metaSearchClient.executeSendCreateUpdateShow(req)
            launch {
                cloudflareClient.purgeCache(it)
            }
        }
        call.respond("Success sync show data")
    }

    suspend fun fetchShowById(call : ApplicationCall) {
        val id = call.parameters["id"] ?: return call.respond(HttpStatusCode.BadRequest,
            jsonFactory.objectNode().put("message", "Missing or malformed id"),
        )
        if (!ObjectId.isValid(id)) return call.respond(HttpStatusCode.BadRequest,
            jsonFactory.objectNode().put("message", "Invalid show id"),
        )

        val document = db.loadShow(id) ?: return call.respond(HttpStatusCode.BadRequest,
            jsonFactory.objectNode().put("message", "show isn't found"),
        )

        call.respond(document.toResponse())
    }

    suspend fun fetchAllShow(call: ApplicationCall) {
        val allShows = db.loadAllShow().map { it.toResponse() }
        return call.respond(allShows)
    }

    /**
     * Fetch_By_Slug_Endpoint — resolve a Show_Slug (and optionally a Session_Slug) to Show/Session
     * data. Read-only: this handler never mutates stored data.
     *
     * Steps:
     *   1. Read `showSlug` (required) and `sessionSlug` (optional) query params.
     *   2. Normalize both with [SlugNormalizer], defending against non-canonical input from links.
     *   3. [DatabaseService.loadShowBySlug] using an indexed lookup; null → 404 "Not found show".
     *   4. If a Session_Slug was supplied: find the Session whose stored canonical slug equals the
     *      normalized value; none → 404 "Not found session".
     *   5. Respond 200 with the [ShowResponse] plus `matchedSessionId` when a Session was requested
     *      and matched.
     */
    suspend fun fetchShowBySlug(call: ApplicationCall) {
        val rawShowSlug = call.parameters["showSlug"]
        if (rawShowSlug.isNullOrBlank()) {
            return call.respond(HttpStatusCode.BadRequest,
                jsonFactory.objectNode().put("message", "Missing or malformed show slug")
            )
        }

        val canonicalShowSlug = SlugNormalizer.normalize(rawShowSlug)

        val show = db.loadShowBySlug(canonicalShowSlug) ?: return call.respond(HttpStatusCode.NotFound,
            jsonFactory.objectNode().put("message", "Not found show")
        )

        val rawSessionSlug = call.parameters["sessionSlug"]
        var matchedSessionId: String? = null
        if (!rawSessionSlug.isNullOrBlank()) {
            val canonicalSessionSlug = SlugNormalizer.normalize(rawSessionSlug)
            val matchedSession = show.sessions.firstOrNull { it.slug == canonicalSessionSlug }
                ?: return call.respond(HttpStatusCode.NotFound,
                    jsonFactory.objectNode().put("message", "Not found session")
                )
            matchedSessionId = matchedSession.id
        }

        call.respond(HttpStatusCode.OK, show.toResponse().copy(matchedSessionId = matchedSessionId))
    }

    suspend fun updateSession(call: ApplicationCall) {
        val input = call.receive<UpdateShowSessionRequest>()

        if (!ObjectId.isValid(input.showId)) return call.respond(HttpStatusCode.BadRequest,
            jsonFactory.objectNode().put("message", "Invalid show id"),
        )

        if (!ObjectId.isValid(input.sessionId)) return call.respond(HttpStatusCode.BadRequest,
            jsonFactory.objectNode().put("message", "Invalid session id"),
        )

        if(!ObjectId.isValid(input.cityId)) return call.respond(HttpStatusCode.BadRequest,
            jsonFactory.objectNode().put("message", "Invalid city id"),
        )

        if (input.qrcodeEmail == true && input.manageTicket != true) {
            return call.respond(
                HttpStatusCode.BadRequest,
                jsonFactory.objectNode().put("message", "qrcodeEmail requires manageTicket"),
            )
        }

        val cityDoc = cityDBService.loadCity(input.cityId) ?: return call.respond(HttpStatusCode.BadRequest,
            jsonFactory.objectNode().put("message", "Invalid city"),
        )

        val showSessionDoc = db.loadShowAndSession(input.showId, input.sessionId) ?: return call.respond(HttpStatusCode.BadRequest,
            jsonFactory.objectNode().put("message", "Invalid show"),
        )

        val currentSession = showSessionDoc.sessions.firstOrNull { it.id == input.sessionId }
            ?: return call.respond(HttpStatusCode.NotFound,
                jsonFactory.objectNode().put("message", "Not found session")
            )

        // Client cũ không gửi sessionType thì giữ nguyên loại suất đang lưu.
        val currentType = currentSession.sessionType ?: SessionType.SINGLE
        val targetType = input.sessionType ?: currentType
        val targetCalendar = if (targetType == SessionType.CALENDAR) input.calendar ?: currentSession.calendar else null
        val typeChanged = targetType != currentType
        // Vé đã sinh theo kiểu cũ không dùng được cho kiểu mới, nên chỉ cho đổi loại khi suất chưa gửi duyệt.
        if (typeChanged && currentSession.status !in TYPE_CHANGEABLE_STATUSES) {
            return call.respond(HttpStatusCode.Conflict,
                jsonFactory.objectNode().put("message", "Chỉ đổi loại suất khi suất chưa gửi duyệt")
            )
        }
        val calendarError = CalendarSessionValidator.validate(
            targetType, targetCalendar, input.manageTicket, Date(input.startTime), Date(input.endTime)
        ) ?: CalendarSessionValidator.validateSeatPlan(targetType, currentSession.edType, currentSession.seatPlanId)
        if (calendarError != null) {
            return call.respond(HttpStatusCode.BadRequest, jsonFactory.objectNode().put("message", calendarError))
        }
        if (input.sessionType != null) {
            db.updateSessionCalendar(
                input.showId, input.sessionId, targetType, targetCalendar,
                allowedStatuses = if (typeChanged) TYPE_CHANGEABLE_STATUSES else null,
            ) ?: return call.respond(HttpStatusCode.Conflict,
                jsonFactory.objectNode().put("message", "Suất đã đổi trạng thái, vui lòng tải lại")
            )
        }

        // Normalize + validate the optional Session_Slug and enforce within-show uniqueness BEFORE
        // any stored or filesystem mutation, so a rejected update leaves stored data unchanged.
        //   - absent (null/empty/whitespace) → skipped, the stored slug is left unchanged
        //   - invalid format/length → 400 { message: "Invalid slug", slug }
        //   - duplicate within the parent Show → 409 { message: "Duplicate slug", slug }
        // Self-assignment of the session's own slug is permitted by updateSessionSlug's `_id != sessionId`
        // clause. The conditional update runs before the image renames / field update
        // below, so a duplicate rejection mutates nothing.
        val rawSessionSlug = input.slug
        if (!rawSessionSlug.isNullOrBlank()) {
            when (SlugValidator.checkFormat(rawSessionSlug)) {
                is SlugCheck.Invalid -> return call.respond(HttpStatusCode.BadRequest,
                    jsonFactory.objectNode().put("message", "Invalid slug").put("slug", rawSessionSlug)
                )
                SlugCheck.Valid -> {}
            }
            val canonicalSessionSlug = SlugNormalizer.normalize(rawSessionSlug)
            when (val slugResult = db.updateSessionSlug(input.showId, input.sessionId, canonicalSessionSlug)) {
                is SessionSlugUpdateResult.Success -> {}
                is SessionSlugUpdateResult.Duplicate -> return call.respond(HttpStatusCode.Conflict,
                    jsonFactory.objectNode().put("message", "Duplicate slug").put("slug", slugResult.conflictingSlug)
                )
                SessionSlugUpdateResult.ShowNotFound -> return call.respond(HttpStatusCode.BadRequest,
                    jsonFactory.objectNode().put("message", "Invalid show")
                )
                SessionSlugUpdateResult.SessionNotFound -> return call.respond(HttpStatusCode.NotFound,
                    jsonFactory.objectNode().put("message", "Not found session")
                )
                SessionSlugUpdateResult.VersionConflict -> return call.respond(HttpStatusCode.Conflict,
                    jsonFactory.objectNode().put("message", "Slug update conflict, please retry")
                )
            }
        }

        var ledgendImageUrl: String? = null
        var eventSquareImageUrl: String? = null
        var eventNameImageUrl: String? = null
        for(item in showSessionDoc.sessions) {
            if(item.id == input.sessionId) {
                ledgendImageUrl = item.ledgendImageUrl
                eventSquareImageUrl = item.eventSquareImageUrl
                eventNameImageUrl = item.eventNameImageUrl
            }
        }

        if(!input.ledgendImage.isNullOrBlank()) {
            val sourceLogoHost = File(input.ledgendImage)
            val targetLogoHost = File("${imagePath}/${input.showId}-${input.sessionId}-session-ledgend-image.png")
            sourceLogoHost.renameTo(targetLogoHost)
            ledgendImageUrl = "${imagePrefix}${imagePath}/${input.showId}-${input.sessionId}-session-ledgend-image.png?v=${Date().time}"
        }

        if(!input.eventSquareImage.isNullOrBlank()) {
            val sourceLogoHost = File(input.eventSquareImage)
            val targetLogoHost = File("${imagePath}/${input.showId}-${input.sessionId}-session-event-square-image.png")
            sourceLogoHost.renameTo(targetLogoHost)
            eventSquareImageUrl = "${imagePrefix}${imagePath}/${input.showId}-${input.sessionId}-session-event-square-image.png?v=${Date().time}"
        }

        if(!input.eventNameImage.isNullOrBlank()) {
            val sourceLogoHost = File(input.eventNameImage)
            val targetLogoHost = File("${imagePath}/${input.showId}-${input.sessionId}-session-event-name-image.png")
            sourceLogoHost.renameTo(targetLogoHost)
            eventNameImageUrl = "${imagePrefix}${imagePath}/${input.showId}-${input.sessionId}-session-event-name-image.png?v=${Date().time}"
        }

        val showDoc = db.updateShowSession(
            showId = input.showId,
            sessionId = input.sessionId,
            sessionName = input.sessionName,
            code = input.code,
            venue = input.address,
            startTime = Date(input.startTime),
            endTime = Date(input.endTime),
            requireDelivery = input.requireDelivery,
            orderNote = input.orderNote,
            city = cityDoc,
            isFree = input.isFree,
            showInHome = input.showInHome,
            ledgendImageUrl = ledgendImageUrl,
            eventSquareImageUrl = eventSquareImageUrl,
            eventNameImageUrl = eventNameImageUrl,
            manageTicket = input.manageTicket,
            qrcodeEmail = input.qrcodeEmail,
        )

        logger.info("show session {} updated", showDoc)

        if(showDoc != null) {
            for(item in showDoc.sessions) {
                if(item.id == input.sessionId) {

                    jobrunrGateway.onShowChange(input.showId)

                    return call.respond(HttpStatusCode.OK, item.toResponse())
                }
            }
        }
        call.respond(HttpStatusCode.NotFound)

    }

    suspend fun updateSessionPayment(call: ApplicationCall) {
        val input = call.receive<UpdateShowSessionPaymentRequest>()

        if (!ObjectId.isValid(input.showId)) return call.respond(HttpStatusCode.BadRequest,
            jsonFactory.objectNode().put("message", "Invalid show id"),
        )

        if (!ObjectId.isValid(input.sessionId)) return call.respond(HttpStatusCode.BadRequest,
            jsonFactory.objectNode().put("message", "Invalid session id"),
        )

        val showDoc = db.updateShowSessionPayment(
            showId = input.showId,
            sessionId = input.sessionId,
            paymentOffline = input.paymentOffline,
            paymentNote = input.paymentNote,
            pgAccountCode = input.pgAccountCode,
            paymentMethods = input.paymentMethods,
        )

        logger.info("show session payment {} updated", showDoc)

        if(showDoc != null) {
            for(item in showDoc.sessions) {
                if(item.id == input.sessionId) {

                    jobrunrGateway.onShowChange(input.showId)

                    return call.respond(HttpStatusCode.OK, item.toResponse())
                }
            }
        }
        call.respond(HttpStatusCode.NotFound)
    }

    suspend fun updateSessionMailTemplate(call: ApplicationCall) {
        val input = call.receive<UpdateShowSessionMailTemplateRequest>()

        if (!ObjectId.isValid(input.showId)) return call.respond(HttpStatusCode.BadRequest,
            jsonFactory.objectNode().put("message", "Invalid show id"),
        )

        if (!ObjectId.isValid(input.sessionId)) return call.respond(HttpStatusCode.BadRequest,
            jsonFactory.objectNode().put("message", "Invalid session id"),
        )

        val showDoc = db.updateShowSessionMailTemplate(
            showId = input.showId,
            sessionId = input.sessionId,
            mailTemplates = input.mailTemplates,
        )

        logger.info("show session mail template {} updated", showDoc)

        if(showDoc != null) {
            for(item in showDoc.sessions) {
                if(item.id == input.sessionId) {

                    jobrunrGateway.onShowChange(input.showId)

                    return call.respond(HttpStatusCode.OK, item.toResponse())
                }
            }
        }
        call.respond(HttpStatusCode.NotFound)
    }

    suspend fun updateSessionSeatPlan(call: ApplicationCall) {
        val input = call.receive<UpdateShowSessionSeatPlanRequest>()

        if (!ObjectId.isValid(input.showId)) return call.respond(HttpStatusCode.BadRequest,
            jsonFactory.objectNode().put("message", "Invalid show id"),
        )

        if (!ObjectId.isValid(input.sessionId)) return call.respond(HttpStatusCode.BadRequest,
            jsonFactory.objectNode().put("message", "Invalid session id"),
        )

        val currentSession = db.loadShow(input.showId)?.sessions?.firstOrNull { it.id == input.sessionId }
            ?: return call.respond(HttpStatusCode.NotFound,
                jsonFactory.objectNode().put("message", "Not found session")
            )
        CalendarSessionValidator.validateSeatPlan(currentSession.sessionType, input.edType, input.seatPlanId)?.let {
            return call.respond(HttpStatusCode.BadRequest, jsonFactory.objectNode().put("message", it))
        }

        // Persist HTML as typed — never auto-insert <br> for whitespace; blank → clear field.
        val normalizedExtraInfo = HtmlContentNormalizer.normalize(input.extraInfo)

        val showSessionDoc = db.updateShowSessionSeatPlan(
            showId = input.showId,
            sessionId = input.sessionId,
            seatPlanId = input.seatPlanId,
            edType = input.edType,
            extraInfo = normalizedExtraInfo,
            clearExtraInfo = input.extraInfo != null && normalizedExtraInfo == null,
        )?.sessions?.firstOrNull { it.id == input.sessionId } ?: run {
            logger.error("show session {} cannot update seat plan {}", input.sessionId, input.seatPlanId)
            return call.respond(HttpStatusCode.NotFound,
                jsonFactory.objectNode().put("message", "Not found session")
            )
        }

        logger.info("show session {} seat plan {} updated", input.sessionId, showSessionDoc)

        jobrunrGateway.onShowChange(input.showId)

        return call.respond(HttpStatusCode.OK, showSessionDoc.toResponse())
    }

    suspend fun loadShowSessions(call: ApplicationCall) {
        val showId = call.parameters["showId"]
            ?: return call.respond(HttpStatusCode.BadRequest,
                jsonFactory.objectNode().put("message", "Missing or malformed show id")
            )

        val showDocument = db.loadShow(showId)
            ?: return call.respond(HttpStatusCode.NotFound,
                jsonFactory.objectNode().put("message", "Not found show")
            )

        call.respond(showDocument.sessions.map { it.toResponse() })
    }

    suspend fun loadSession(call: ApplicationCall) {
        val showId = call.parameters["showId"]
            ?: return call.respond(HttpStatusCode.BadRequest,
                jsonFactory.objectNode().put("message", "Missing or malformed show id")
            )

        val sessionId = call.parameters["sessionId"]
            ?: return call.respond(HttpStatusCode.BadRequest,
                jsonFactory.objectNode().put("message", "Missing or malformed session id")
            )

        val sessionDocument = db.loadShow(showId)?.sessions?.firstOrNull { it.id == sessionId }
            ?: return call.respond(HttpStatusCode.NotFound,
                jsonFactory.objectNode().put("message", "Not found session")
            )

        call.respond(sessionDocument.toResponse())
    }

    /**
     * Booking_Link_Endpoint — return the homepage Booking_Page_Link for a session.
     *
     * Steps:
     *   1. Read `showId`/`sessionId` query params; missing/blank → 400 naming the missing
     *      identifier, no link (Requirement 1.5).
     *   2. [DatabaseService.loadShowAndSession] and resolve the in-document session; not found —
     *      including an invalid `ObjectId` hex (guarded with [ObjectId.isValid], consistent with the
     *      other handlers) — → 404 with a not-found message, no link (Requirement 1.6).
     *   3. Build the link via [ShareLinkBuilder.bookingLink] preferring the slug form when both the
     *      Show_Slug and Session_Slug are present, else the id form, and respond 200 with the link
     *      string (Requirements 1.1, 1.2, 1.3, 1.4).
     */
    suspend fun generateLink(call: ApplicationCall) {
        val showId = call.parameters["showId"]
        if (showId.isNullOrBlank()) {
            return call.respond(HttpStatusCode.BadRequest,
                jsonFactory.objectNode().put("message", "Missing or malformed show id")
            )
        }

        val sessionId = call.parameters["sessionId"]
        if (sessionId.isNullOrBlank()) {
            return call.respond(HttpStatusCode.BadRequest,
                jsonFactory.objectNode().put("message", "Missing or malformed session id")
            )
        }

        // Invalid ObjectId hex can never resolve to a stored Show/Session; treat it as not found
        // rather than letting ObjectId(...) throw inside the DB lookup.
        if (!ObjectId.isValid(showId) || !ObjectId.isValid(sessionId)) {
            return call.respond(HttpStatusCode.NotFound,
                jsonFactory.objectNode().put("message", "Not found session")
            )
        }

        val show = db.loadShowAndSession(showId, sessionId) ?: return call.respond(HttpStatusCode.NotFound,
            jsonFactory.objectNode().put("message", "Not found session")
        )

        val session = show.sessions.firstOrNull { it.id == sessionId } ?: return call.respond(HttpStatusCode.NotFound,
            jsonFactory.objectNode().put("message", "Not found session")
        )

        val link = ShareLinkBuilder.bookingLink(
            homepageConfig.baseUrl,
            showId,
            show.slug,
            sessionId,
            session.slug,
        )
        call.respond(HttpStatusCode.OK, link)
    }

    /**
     * Detail_Link_Endpoint — return the homepage Show_Detail_Page_Link for a show.
     *
     * Steps:
     *   1. Read the `showId` query param; missing/blank/whitespace-only → 400 with a missing-show-id
     *      message, no link (Requirement 2.5).
     *   2. [DatabaseService.loadShow]; not found — including an invalid `ObjectId` hex (guarded with
     *      [ObjectId.isValid], consistent with the other handlers) — → 404 with a not-found message,
     *      no link (Requirement 2.6).
     *   3. Build the link via [ShareLinkBuilder.showDetailLink] preferring the slug form when the
     *      Show_Slug is present, else the id form, and respond 200 with the link string
     *      (Requirements 2.1, 2.2, 2.3, 2.4).
     */
    suspend fun generateShowDetailLink(call: ApplicationCall) {
        val showId = call.parameters["showId"]
        if (showId.isNullOrBlank()) {
            return call.respond(HttpStatusCode.BadRequest,
                jsonFactory.objectNode().put("message", "Missing or malformed show id")
            )
        }

        // Invalid ObjectId hex can never resolve to a stored Show; treat it as not found rather than
        // letting ObjectId(...) throw inside the DB lookup.
        if (!ObjectId.isValid(showId)) {
            return call.respond(HttpStatusCode.NotFound,
                jsonFactory.objectNode().put("message", "Not found show")
            )
        }

        val show = db.loadShow(showId) ?: return call.respond(HttpStatusCode.NotFound,
            jsonFactory.objectNode().put("message", "Not found show")
        )

        val link = ShareLinkBuilder.showDetailLink(
            homepageConfig.baseUrl,
            showId,
            show.slug,
        )
        call.respond(HttpStatusCode.OK, link)
    }

    suspend fun requestApproveSession(call: ApplicationCall) {
        val req = call.receive<ShowSessionRequest>()
        val res = db.updateShowSessionStatus(req.showId, req.sessionId, listOf(Created, Rejected), ApprovalWait) ?: run {
            logger.error("show session {} failed to waiting to approve", req.sessionId)
            return call.respond(HttpStatusCode.Forbidden, "fail to waiting to approve session")
        }

        logger.info("show session {} success waiting to approve", req.sessionId)
        call.respond(HttpStatusCode.OK, res)
    }

    suspend fun approveSession(call: ApplicationCall) {
        val req = call.receive<ShowSessionRequest>()
        val res = db.updateShowSessionStatus(req.showId, req.sessionId, listOf(ApprovalWait), OpeningWait) ?: run {
            logger.error("show session {} failed to approve session", req.sessionId)
            return call.respond(HttpStatusCode.Forbidden, "fail to approve session")
        }

        jobrunrGateway.onShowChange(req.showId)

        logger.info("show session {} success to approve", req.sessionId)
        call.respond(HttpStatusCode.OK, res)
    }

    suspend fun rejectSession(call: ApplicationCall) {
        val req = call.receive<ShowSessionRequest>()
        val res = db.updateShowSessionStatus(req.showId, req.sessionId, listOf(ApprovalWait), Rejected) ?: run {
            logger.error("show session {} failed to reject session", req.sessionId)
            return call.respond(HttpStatusCode.Forbidden, "fail to reject session")
        }

        logger.info("show session {} success to reject", req.sessionId)
        call.respond(HttpStatusCode.OK, res)
    }

    suspend fun terminateSession(call: ApplicationCall) {
        val req = call.receive<ShowSessionRequest>()
        val res = db.updateShowSessionStatus(req.showId, req.sessionId, listOf(Rejected, Stopped), Terminated) ?: run {
            logger.error("show session {} failed to terminate session", req.sessionId)
            return call.respond(HttpStatusCode.Forbidden, "fail to terminate session")
        }

        jobrunrGateway.onShowChange(req.showId)

        logger.info("show session {} success to terminate", req.sessionId)
        call.respond(HttpStatusCode.OK, res)
    }

    suspend fun startSession(call: ApplicationCall) {
        val req = call.receive<ShowSessionRequest>()
        val res = db.updateShowSessionStatus(req.showId, req.sessionId, fromStatus = listOf(OpeningWait, Paused), Open) ?: run {
            logger.error("show session {} failed to start", req.sessionId)
            return call.respond(HttpStatusCode.Forbidden, "Có lỗi xảy ra")
        }

        ticketGateway.startTicket(req.showId, req.sessionId) ?: kotlin.run {
            logger.error("show session {} failed to start cause cannot start ticket", req.sessionId)
            db.updateShowSessionStatus(req.showId, req.sessionId, listOf(), Paused)
            jobrunrGateway.onShowChange(req.showId)
            return call.respond(HttpStatusCode.InternalServerError, "cannot start ticket")
        }

        jobrunrGateway.onShowChange(req.showId)

        logger.info("show session {} success started", req.sessionId)
        call.respond(HttpStatusCode.OK, res)
    }

    suspend fun pauseSession(call: ApplicationCall) {
        val req = call.receive<ShowSessionRequest>()

        val res = db.updateShowSessionStatus(req.showId, req.sessionId, listOf(Open), Paused) ?: run {
            logger.error("show session {} failed to pause", req.sessionId)
            return call.respond(HttpStatusCode.Forbidden, "fail to pause session")
        }

        jobrunrGateway.onShowChange(req.showId)

        logger.info("show session {} success pause", req.sessionId)
        call.respond(HttpStatusCode.OK, res)
    }

    suspend fun stopSession(call: ApplicationCall) {
        val req = call.receive<ShowSessionRequest>()

        val res = db.updateShowSessionStatus(req.showId, req.sessionId, listOf(Open, Paused), Stopped) ?: run {
            logger.error("show session {} failed to stop", req.sessionId)
            return call.respond(HttpStatusCode.Forbidden, "fail to stop session")
        }

        ticketGateway.stopTicket(req.showId, req.sessionId) ?: kotlin.run {
            logger.error("show session {} stopped but cannot stop ticket", req.sessionId)
        }

        jobrunrGateway.onShowChange(req.showId)

        logger.info("show session {} success stopped", req.sessionId)
        call.respond(HttpStatusCode.OK, res)
    }

    suspend fun changeSessionSaleMode(call: ApplicationCall) {
        val req = call.receive<ChangeSaleModeShowSessionRequest>()
        db.updateSaleModeShowSession(req.showId, req.sessionId, req.saleMode) ?: run {
            logger.error("show session {} failed to change sale mode to {}", req.sessionId, req.saleMode)
            return call.respond(HttpStatusCode.Forbidden, "fail to change session sale mode")
        }

        jobrunrGateway.onShowChange(req.showId)

        logger.info("show session {} success change sale mode", req.sessionId)
        return call.respond(HttpStatusCode.OK, "session change sale mode")
    }

    suspend fun loadEventCategory(call: ApplicationCall) {
        val category = db.getEventCategory()
        return call.respond(HttpStatusCode.OK, category)
    }

    /** 10 slots from pinOrder; missing ranks are vacant so the first admin visit is 200 not 500. */
    private suspend fun bannerPinBoard(): BannerPinBoardResponse {
        val byOrder = db.loadShowsByPinOrder(BANNER_PIN_SLOT_COUNT)
            .mapNotNull { show -> show.pinOrder?.let { order -> order to show } }
            .toMap()
        val slots = (1..BANNER_PIN_SLOT_COUNT).map { order ->
            BannerPinSlotResponse(
                pinOrder = order,
                show = byOrder[order]?.toBannerPinSummary(),
            )
        }
        return BannerPinBoardResponse(slots)
    }

    /** Persist [orderedIds] as pinOrder 1..10 and sync metasearch for affected shows. */
    private suspend fun applyBannerPins(orderedIds: List<String?>): BannerPinBoardResponse {
        val changed = db.replaceBannerPins(orderedIds)
        changed.forEach { jobrunrGateway.onShowChange(it) }
        return bannerPinBoard()
    }

    /** GET /private/show/pin/banner — 10 fixed slots for the pin-management page. */
    suspend fun getBannerPins(call: ApplicationCall) {
        call.respond(HttpStatusCode.OK, bannerPinBoard())
    }

    /** POST /private/show/pin/banner/refresh — replace the board with the 10 newest lastModified shows. */
    suspend fun refreshBannerPins(call: ApplicationCall) {
        val newest = db.loadMostRecentlyModifiedShows(BANNER_PIN_SLOT_COUNT)
        val ids = (0 until BANNER_PIN_SLOT_COUNT).map { newest.getOrNull(it)?.id }
        call.respond(HttpStatusCode.OK, applyBannerPins(ids))
    }

    /** POST /private/show/pin/banner/confirm — apply FE slot order / replacements. */
    suspend fun confirmBannerPins(call: ApplicationCall) {
        val req = call.receive<ConfirmBannerPinsRequest>()
        if (req.slots.size != BANNER_PIN_SLOT_COUNT) {
            return call.respond(
                HttpStatusCode.BadRequest,
                jsonFactory.objectNode().put("message", "Expected $BANNER_PIN_SLOT_COUNT slots"),
            )
        }
        val byOrder = req.slots.associateBy { it.pinOrder }
        if ((1..BANNER_PIN_SLOT_COUNT).any { !byOrder.containsKey(it) }) {
            return call.respond(
                HttpStatusCode.BadRequest,
                jsonFactory.objectNode().put("message", "pinOrder must cover 1..$BANNER_PIN_SLOT_COUNT"),
            )
        }
        val orderedIds = (1..BANNER_PIN_SLOT_COUNT).map { order ->
            val id = byOrder.getValue(order).showId?.trim().orEmpty()
            if (id.isNotEmpty() && !ObjectId.isValid(id)) {
                return call.respond(
                    HttpStatusCode.BadRequest,
                    jsonFactory.objectNode().put("message", "Malformed showId").put("showId", id),
                )
            }
            id.ifEmpty { null }
        }
        call.respond(HttpStatusCode.OK, applyBannerPins(orderedIds))
    }

    /** GET /private/show/pin/candidates — paginated picker (page is 1-based). */
    suspend fun searchPinCandidates(call: ApplicationCall) {
        val page = call.request.queryParameters["page"]?.toIntOrNull() ?: 1
        val size = call.request.queryParameters["size"]?.toIntOrNull() ?: BANNER_PIN_SLOT_COUNT
        val keyword = call.request.queryParameters["keyword"]
        val category = call.request.queryParameters["eventCategoryCode"]
        val result = db.searchShowsForPinPicker(page, size, keyword, category)
        val totalPages = if (result.total == 0L) 0 else ((result.total + size.coerceIn(1, 50) - 1) / size.coerceIn(1, 50)).toInt()
        call.respond(
            HttpStatusCode.OK,
            BannerPinCandidatePage(
                content = result.items.map { it.toBannerPinSummary() },
                page = page.coerceAtLeast(1),
                size = size.coerceIn(1, 50),
                totalElements = result.total,
                totalPages = totalPages,
            ),
        )
    }
}
