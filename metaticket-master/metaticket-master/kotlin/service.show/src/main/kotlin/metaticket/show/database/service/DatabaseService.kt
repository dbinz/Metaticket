package metaticket.show.database.service

import com.mongodb.client.model.*
import com.mongodb.kotlin.client.coroutine.MongoCollection
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import metaticket.payment.datastructure.PaymentType
import metaticket.show.datastructure.MailPluginTemplate
import metaticket.show.datastructure.SessionType
import metaticket.show.datastructure.ShowSessionStatus
import metaticket.show.logger
import metaticket.show.pojo.db.CityDocument
import metaticket.show.pojo.db.EventCategory
import metaticket.show.pojo.db.SaleMode
import metaticket.show.pojo.db.SessionCalendarConfig
import metaticket.show.pojo.db.ShowDocument
import metaticket.show.pojo.db.ShowSessionDocument
import org.bson.BsonType
import org.bson.types.ObjectId
import java.util.*


/**
 * Outcome of an application-level Session_Slug uniqueness write.
 *
 * Sessions are embedded in [ShowDocument.sessions] (not a separate collection), so a MongoDB unique
 * index cannot scope uniqueness to a single parent Show. Uniqueness is therefore enforced in
 * application logic via a conditional [DatabaseService.updateSessionSlug] backed by the optimistic
 * `version` lock. This result lets the calling layer (controller/service) translate the outcome into
 * the proper HTTP response.
 */
sealed interface SessionSlugUpdateResult {
    /** The canonical slug was persisted on the target session. Carries the updated Show document. */
    data class Success(val show: ShowDocument) : SessionSlugUpdateResult

    /** Another session within the same Show already holds [conflictingSlug]. */
    data class Duplicate(val conflictingSlug: String) : SessionSlugUpdateResult

    /** No Show exists for the supplied id. */
    object ShowNotFound : SessionSlugUpdateResult

    /** The Show exists but contains no session with the supplied id. */
    object SessionNotFound : SessionSlugUpdateResult

    /** The document `version` kept changing under concurrent writes and the retry budget was exhausted. */
    object VersionConflict : SessionSlugUpdateResult
}


class DatabaseService(
    private val col: MongoCollection<ShowDocument>,
    private val eventCategoryCol: MongoCollection<EventCategory>,
) {

    init {
        CoroutineScope(Dispatchers.Unconfined).launch {
            val idx = col.createIndex(Indexes.ascending(ShowDocument::code.name), IndexOptions().unique(true))
            logger.info("created index {}", idx)

            // Partial unique index on the Show_Slug. The partialFilterExpression restricts the
            // uniqueness constraint to documents whose slug is a string, so the many slug-absent
            // (null) documents do not collide. Enforces show-slug uniqueness
            // and provides atomic concurrency safety for show slugs.
            val slugIdx = col.createIndex(
                Indexes.ascending(ShowDocument::slug.name),
                IndexOptions().unique(true).partialFilterExpression(
                    Filters.type(ShowDocument::slug.name, BsonType.STRING)
                )
            )
            logger.info("created index {}", slugIdx)
        }
    }

    suspend fun createShow(entity: ShowDocument): String? {
        val result = withContext(Dispatchers.IO) { col.insertOne(entity) }
        return result.insertedId?.asObjectId()?.value?.toString()
    }

    suspend fun updateShow(
        showId: String,
        showName: String?,
        code: String,
        faq: String?,
        description: String?,
        ticketDescription: String?,
        policy: String?,
        eventCategoryCode: String?,
        eventCategory: String?,
        bannerImageUrl: String?,
        detailImageUrl: String?,
        thumbnailUrl: String?,
        logoHostUrl: String?,
        logoShowUrl: String?,
        hideSessions: Boolean?,
        slug: String? = null,
    ): ShowDocument? {
        val filter = Filters.eq("_id", ObjectId(showId))

        val update = Updates.combine(
            listOfNotNull(
                showName?.let { Updates.set(ShowDocument::showName.name, it) }
                    ?: Updates.unset(ShowDocument::showName.name),
                Updates.set(ShowDocument::code.name, code),
                bannerImageUrl?.let { Updates.set(ShowDocument::bannerImageUrl.name, it) }
                    ?: Updates.unset(ShowDocument::bannerImageUrl.name),
                detailImageUrl?.let { Updates.set(ShowDocument::detailImageUrl.name, it) }
                    ?: Updates.unset(ShowDocument::detailImageUrl.name),
                thumbnailUrl?.let { Updates.set(ShowDocument::thumbnailUrl.name, it) }
                    ?: Updates.unset(ShowDocument::thumbnailUrl.name),
                eventCategoryCode?.let { Updates.set(ShowDocument::eventCategoryCode.name, it) }
                    ?: Updates.unset(ShowDocument::eventCategoryCode.name),
                eventCategory?.let { Updates.set(ShowDocument::eventCategory.name, it) }
                    ?: Updates.unset(ShowDocument::eventCategory.name),
                logoHostUrl?.let { Updates.set(ShowDocument::logoHostUrl.name, it) }
                    ?: Updates.unset(ShowDocument::logoHostUrl.name),
                logoShowUrl?.let { Updates.set(ShowDocument::logoShowUrl.name, it) }
                    ?: Updates.unset(ShowDocument::logoShowUrl.name),
                faq?.let { Updates.set(ShowDocument::faq.name, it) } ?: Updates.unset(ShowDocument::faq.name),
                description?.let { Updates.set(ShowDocument::description.name, it) }
                    ?: Updates.unset(ShowDocument::description.name),
                ticketDescription?.let { Updates.set(ShowDocument::ticketDescription.name, it) } ?: Updates.unset(
                    ShowDocument::ticketDescription.name
                ),
                policy?.let { Updates.set(ShowDocument::policy.name, it) }
                    ?: Updates.unset(ShowDocument::policy.name),
                logoHostUrl?.let { Updates.set(ShowDocument::logoHostUrl.name, it) }
                    ?: Updates.unset(ShowDocument::logoHostUrl.name),
                hideSessions?.let { Updates.set(ShowDocument::hideSessions.name, it) }
                    ?: Updates.unset(ShowDocument::hideSessions.name),
                // Persist the canonical Show_Slug, or store an absent slug (unset) when none is
                // provided. Stored values are always canonical.
                slug?.let { Updates.set(ShowDocument::slug.name, it) }
                    ?: Updates.unset(ShowDocument::slug.name),
                Updates.inc(ShowDocument::version.name, 1),
                Updates.set(ShowDocument::lastModified.name, Date())
            )
        )

        val options = FindOneAndUpdateOptions().returnDocument(ReturnDocument.AFTER)
        return withContext(Dispatchers.IO) { col.findOneAndUpdate(filter, update, options) }
    }


    suspend fun loadShow(showId: String): ShowDocument? {
        val filter = Filters.eq("_id", ObjectId(showId))
        return withContext(Dispatchers.IO) { col.find(filter) }.firstOrNull()
    }

    /**
     * Resolve a Show by its Canonical_Show_Slug using the partial unique index on [ShowDocument.slug].
     * Returns null when no Show holds the given canonical slug.
     */
    suspend fun loadShowBySlug(canonicalShowSlug: String): ShowDocument? {
        val filter = Filters.eq(ShowDocument::slug.name, canonicalShowSlug)
        return withContext(Dispatchers.IO) { col.find(filter) }.firstOrNull()
    }

    suspend fun loadAllShow(): List<ShowDocument> {
        return withContext(Dispatchers.IO) { col.find<ShowDocument>() }.toList()
    }

    suspend fun createShowSession(entity: ShowSessionDocument, showId: String): String {
        val filter = Filters.eq("_id", ObjectId(showId))
        val update = Updates.combine(
            Updates.addToSet(ShowDocument::sessions.name, entity),
            Updates.inc(ShowDocument::version.name, 1),
            Updates.set(ShowDocument::lastModified.name, Date())
        )

        val options = FindOneAndUpdateOptions().returnDocument(ReturnDocument.AFTER)
        withContext(Dispatchers.IO) { col.findOneAndUpdate(filter, update, options) }

        return entity.id
    }

    suspend fun updateShowSession(
        showId: String,
        sessionId: String,
        sessionName: String?,
        code: String,
        venue: String?,
        startTime: Date,
        endTime: Date,
        requireDelivery: Boolean?,
        orderNote: String?,
        city: CityDocument,
        isFree: Boolean?,
        showInHome: Boolean?,
        ledgendImageUrl: String?,
        eventSquareImageUrl: String?,
        eventNameImageUrl: String?,
        manageTicket: Boolean?,
        qrcodeEmail: Boolean?,
    ): ShowDocument? {
        val filter = Filters.and(
            Filters.eq("_id", ObjectId(showId)),
        )

        val sessionPrefix = "${ShowDocument::sessions.name}.$[${ShowDocument::sessions.name}]"

        val update = Updates.combine(
            listOfNotNull(
                sessionName?.let { Updates.set("$sessionPrefix.${ShowSessionDocument::sessionName.name}", it) }
                    ?: Updates.unset("$sessionPrefix.${ShowSessionDocument::sessionName.name}"),
                Updates.set("$sessionPrefix.${ShowSessionDocument::code.name}", code),
                venue?.let { Updates.set("$sessionPrefix.${ShowSessionDocument::venue.name}", it) }
                    ?: Updates.unset("$sessionPrefix.${ShowSessionDocument::venue.name}"),
                Updates.set("$sessionPrefix.${ShowSessionDocument::startTime.name}", startTime),
                Updates.set("$sessionPrefix.${ShowSessionDocument::endTime.name}", endTime),
                requireDelivery?.let { Updates.set("$sessionPrefix.${ShowSessionDocument::requireDelivery.name}", it) }
                    ?: Updates.unset("$sessionPrefix.${ShowSessionDocument::requireDelivery.name}"),
                orderNote?.let { Updates.set("$sessionPrefix.${ShowSessionDocument::orderNote.name}", it) }
                    ?: Updates.unset("$sessionPrefix.${ShowSessionDocument::orderNote.name}"),
                Updates.set("$sessionPrefix.${ShowSessionDocument::city.name}", city),
                isFree?.let { Updates.set("$sessionPrefix.${ShowSessionDocument::isFree.name}", it) }
                    ?: Updates.unset("$sessionPrefix.${ShowSessionDocument::isFree.name}"),
                showInHome?.let { Updates.set("$sessionPrefix.${ShowSessionDocument::showInHome.name}", it) }
                    ?: Updates.unset("$sessionPrefix.${ShowSessionDocument::showInHome.name}"),
                ledgendImageUrl?.let { Updates.set("$sessionPrefix.${ShowSessionDocument::ledgendImageUrl.name}", it) }
                    ?: Updates.unset("$sessionPrefix.${ShowSessionDocument::ledgendImageUrl.name}"),
                eventSquareImageUrl?.let { Updates.set("$sessionPrefix.${ShowSessionDocument::eventSquareImageUrl.name}", it) }
                    ?: Updates.unset("$sessionPrefix.${ShowSessionDocument::eventSquareImageUrl.name}"),
                eventNameImageUrl?.let { Updates.set("$sessionPrefix.${ShowSessionDocument::eventNameImageUrl.name}", it) }
                    ?: Updates.unset("$sessionPrefix.${ShowSessionDocument::eventNameImageUrl.name}"),
                manageTicket?.let { Updates.set("$sessionPrefix.${ShowSessionDocument::manageTicket.name}", it) }
                    ?: Updates.unset("$sessionPrefix.${ShowSessionDocument::manageTicket.name}"),
                qrcodeEmail?.let { Updates.set("$sessionPrefix.${ShowSessionDocument::qrcodeEmail.name}", it) }
                    ?: Updates.unset("$sessionPrefix.${ShowSessionDocument::qrcodeEmail.name}"),
                Updates.inc(ShowDocument::version.name, 1),
                Updates.set(ShowDocument::lastModified.name, Date())
            )
        )

        val options = FindOneAndUpdateOptions().returnDocument(ReturnDocument.AFTER).arrayFilters(
            listOf(Filters.eq("${ShowDocument::sessions.name}._id", ObjectId(sessionId)))
        )
        return withContext(Dispatchers.IO) { col.findOneAndUpdate(filter, update, options) }
    }

    /**
     * Ghi loại session và cấu hình lịch. Khi [allowedStatuses] khác null, chỉ ghi nếu session còn ở một trong các
     * trạng thái đó — kiểm tra nằm trong filter nên đổi loại session không chen được vào lúc session vừa mở bán.
     * @return null khi không tìm thấy show/session hoặc trạng thái không còn hợp lệ.
     */
    suspend fun updateSessionCalendar(
        showId: String,
        sessionId: String,
        sessionType: SessionType,
        calendar: SessionCalendarConfig?,
        allowedStatuses: List<ShowSessionStatus>?,
    ): ShowDocument? {
        val sessionMatch = Filters.and(listOfNotNull(
            Filters.eq("_id", ObjectId(sessionId)),
            allowedStatuses?.let { Filters.`in`(ShowSessionDocument::status.name, it) },
        ))
        val filter = Filters.and(
            Filters.eq("_id", ObjectId(showId)),
            Filters.elemMatch(ShowDocument::sessions.name, sessionMatch),
        )

        val sessionPrefix = "${ShowDocument::sessions.name}.$[${ShowDocument::sessions.name}]"
        val update = Updates.combine(
            Updates.set("$sessionPrefix.${ShowSessionDocument::sessionType.name}", sessionType),
            calendar?.let { Updates.set("$sessionPrefix.${ShowSessionDocument::calendar.name}", it) }
                ?: Updates.unset("$sessionPrefix.${ShowSessionDocument::calendar.name}"),
            Updates.inc(ShowDocument::version.name, 1),
            Updates.set(ShowDocument::lastModified.name, Date())
        )

        val options = FindOneAndUpdateOptions().returnDocument(ReturnDocument.AFTER).arrayFilters(
            listOf(Filters.eq("${ShowDocument::sessions.name}._id", ObjectId(sessionId)))
        )
        return withContext(Dispatchers.IO) { col.findOneAndUpdate(filter, update, options) }
    }

    /**
     * Persist [canonicalSlug] onto the session identified by [sessionId] inside the Show [showId],
     * enforcing Session_Slug uniqueness *within the parent Show* at the application level.
     *
     * Sessions are embedded in [ShowDocument.sessions], so a global unique index cannot express
     * "unique within one Show". Instead this uses a conditional `findOneAndUpdate` whose filter:
     *   - pins the document `_id` to [showId],
     *   - pins the current optimistic-lock `version` (so a concurrent write invalidates this attempt),
     *   - asserts NO *other* session (`_id != sessionId`) already holds [canonicalSlug].
     *
     * When the conditional update matches no document the cause is ambiguous, so the document is
     * reloaded to distinguish:
     *   - a genuine duplicate (another session holds the slug) → [SessionSlugUpdateResult.Duplicate],
     *   - a stale `version` from concurrent modification → retried up to [maxRetries] times.
     *
     * Self-assignment is permitted: when the target session already holds [canonicalSlug] the
     * `_id != sessionId` clause makes the "other session holds it" assertion false, so the update
     * proceeds.
     */
    suspend fun updateSessionSlug(
        showId: String,
        sessionId: String,
        canonicalSlug: String,
        maxRetries: Int = 3,
    ): SessionSlugUpdateResult {
        val showObjectId = ObjectId(showId)
        val sessionObjectId = ObjectId(sessionId)
        val sessionPrefix = "${ShowDocument::sessions.name}.$[${ShowDocument::sessions.name}]"

        // One initial attempt plus up to maxRetries retries on stale-version contention.
        repeat(maxRetries + 1) {
            val current = loadShow(showId) ?: return SessionSlugUpdateResult.ShowNotFound
            if (current.sessions.none { it.id == sessionId }) {
                return SessionSlugUpdateResult.SessionNotFound
            }
            val expectedVersion = current.version

            val filter = Filters.and(
                Filters.eq("_id", showObjectId),
                // Optimistic lock: only proceed if the document has not changed since we read it.
                Filters.eq(ShowDocument::version.name, expectedVersion),
                // No OTHER session may already hold the target canonical slug. Excluding the target
                // session itself (`_id != sessionId`) is what makes self-assignment valid.
                Filters.not(
                    Filters.elemMatch(
                        ShowDocument::sessions.name,
                        Filters.and(
                            Filters.eq(ShowSessionDocument::slug.name, canonicalSlug),
                            Filters.ne("_id", sessionObjectId)
                        )
                    )
                )
            )

            val update = Updates.combine(
                Updates.set("$sessionPrefix.${ShowSessionDocument::slug.name}", canonicalSlug),
                Updates.inc(ShowDocument::version.name, 1),
                Updates.set(ShowDocument::lastModified.name, Date())
            )

            val options = FindOneAndUpdateOptions().returnDocument(ReturnDocument.AFTER).arrayFilters(
                listOf(Filters.eq("${ShowDocument::sessions.name}._id", sessionObjectId))
            )

            val updated = withContext(Dispatchers.IO) { col.findOneAndUpdate(filter, update, options) }
            if (updated != null) {
                return SessionSlugUpdateResult.Success(updated)
            }

            // The conditional update matched nothing. Reload to learn why.
            val reloaded = loadShow(showId) ?: return SessionSlugUpdateResult.ShowNotFound
            if (reloaded.sessions.none { it.id == sessionId }) {
                return SessionSlugUpdateResult.SessionNotFound
            }
            val genuineDuplicate = reloaded.sessions.any { it.id != sessionId && it.slug == canonicalSlug }
            if (genuineDuplicate) {
                return SessionSlugUpdateResult.Duplicate(canonicalSlug)
            }
            // Not a duplicate → the version moved under us (concurrent write). Loop and retry.
        }

        // Retry budget exhausted while only ever seeing stale-version contention.
        return SessionSlugUpdateResult.VersionConflict
    }

    suspend fun updateShowSessionPayment(
        showId: String,
        sessionId: String,
        paymentOffline: Boolean?,
        paymentNote: String?,
        pgAccountCode: String?,
        paymentMethods: List<PaymentType>?,
    ): ShowDocument? {
        val filter = Filters.and(
            Filters.eq("_id", ObjectId(showId)),
        )

        val sessionPrefix = "${ShowDocument::sessions.name}.$[${ShowDocument::sessions.name}]"

        val update = Updates.combine(
            listOfNotNull(
                paymentOffline?.let { Updates.set("$sessionPrefix.${ShowSessionDocument::paymentOffline.name}", it) }
                    ?: Updates.unset("$sessionPrefix.${ShowSessionDocument::paymentOffline.name}"),
                paymentNote?.let { Updates.set("$sessionPrefix.${ShowSessionDocument::paymentNote.name}", it) }
                    ?: Updates.unset("$sessionPrefix.${ShowSessionDocument::paymentNote.name}"),
                pgAccountCode?.let { Updates.set("$sessionPrefix.${ShowSessionDocument::pgAccountCode.name}", it) }
                    ?: Updates.unset("$sessionPrefix.${ShowSessionDocument::pgAccountCode.name}"),
                paymentMethods?.let { Updates.set("$sessionPrefix.${ShowSessionDocument::paymentMethods.name}", it) }
                    ?: Updates.unset("$sessionPrefix.${ShowSessionDocument::paymentMethods.name}"),

                Updates.inc(ShowDocument::version.name, 1),
                Updates.set(ShowDocument::lastModified.name, Date()),
            )
        )

        val options = FindOneAndUpdateOptions().returnDocument(ReturnDocument.AFTER).arrayFilters(
            listOf(Filters.eq("${ShowDocument::sessions.name}._id", ObjectId(sessionId)))
        )
        return withContext(Dispatchers.IO) { col.findOneAndUpdate(filter, update, options) }
    }

    suspend fun updateShowSessionSeatPlan(
        showId: String,
        sessionId: String,
        seatPlanId: String?,
        edType: String,
        extraInfo: String? = null,
        clearExtraInfo: Boolean = false,
    ): ShowDocument? {
        val filter = Filters.and(
            Filters.eq("_id", ObjectId(showId)),
        )

        val sessionPrefix = "${ShowDocument::sessions.name}.$[${ShowDocument::sessions.name}]"

        val update = Updates.combine(
            listOfNotNull(
                seatPlanId?.let { Updates.set("$sessionPrefix.${ShowSessionDocument::seatPlanId.name}", ObjectId(it)) }
                    ?: Updates.unset("$sessionPrefix.${ShowSessionDocument::seatPlanId.name}"),
                Updates.set("$sessionPrefix.${ShowSessionDocument::edType.name}", edType),
                when {
                    clearExtraInfo -> Updates.unset("$sessionPrefix.${ShowSessionDocument::extraInfo.name}")
                    extraInfo != null -> Updates.set("$sessionPrefix.${ShowSessionDocument::extraInfo.name}", extraInfo)
                    else -> null
                },
                Updates.inc(ShowDocument::version.name, 1),
                Updates.set(ShowDocument::lastModified.name, Date())
            )
        )

        val options = FindOneAndUpdateOptions().returnDocument(ReturnDocument.AFTER).arrayFilters(
            listOf(Filters.eq("${ShowDocument::sessions.name}._id", ObjectId(sessionId)))
        )
        return withContext(Dispatchers.IO) { col.findOneAndUpdate(filter, update, options) }
    }

    suspend fun updateShowSessionMailTemplate(
        showId: String,
        sessionId: String,
        mailTemplates: List<MailPluginTemplate>,
    ): ShowDocument? {
        val filter = Filters.and(
            Filters.eq("_id", ObjectId(showId)),
        )

        val sessionPrefix = "${ShowDocument::sessions.name}.$[${ShowDocument::sessions.name}]"

        val update = Updates.combine(
            Updates.set("$sessionPrefix.${ShowSessionDocument::mailPluginTemplate.name}", mailTemplates),
            Updates.inc(ShowDocument::version.name, 1),
            Updates.set(ShowDocument::lastModified.name, Date())
        )

        val options = FindOneAndUpdateOptions().returnDocument(ReturnDocument.AFTER).arrayFilters(
            listOf(Filters.eq("${ShowDocument::sessions.name}._id", ObjectId(sessionId)))
        )
        return withContext(Dispatchers.IO) { col.findOneAndUpdate(filter, update, options) }
    }


    suspend fun loadShowAndSession(showId: String, sessionId: String): ShowDocument? {
        val filter = Filters.and(
            Filters.eq("_id", ObjectId(showId)),
            Filters.eq("${ShowDocument::sessions.name}._id", ObjectId(sessionId))
        )
        return withContext(Dispatchers.IO) { col.find(filter) }.firstOrNull()
    }

    suspend fun increaseUniqueIncrement(showId: String, sessionId: String): ShowDocument? {
        val filter = Filters.and(
            Filters.eq("_id", ObjectId(showId)),
            Filters.eq("${ShowDocument::sessions.name}._id", ObjectId(sessionId))
        )

        val sessionPrefix = ShowDocument::sessions.name

        val updates = Updates.combine(
            Updates.inc("$sessionPrefix.$.${ShowSessionDocument::uniqueIncrement.name}", 1),
            Updates.inc("$sessionPrefix.$.${ShowSessionDocument::version.name}", 1),
            Updates.set("$sessionPrefix.$.${ShowSessionDocument::lastModified.name}", Date())
        )

        val options = FindOneAndUpdateOptions().returnDocument(ReturnDocument.AFTER)
        return withContext(Dispatchers.IO) { col.findOneAndUpdate(filter, updates, options) }
    }

    /**
     * - Change the show service so that update show can only change statuses that in the allow list:
     *   + Created => ApprovalWait
     *   + ApprovalWait => OpeningWait
     *   + ApprovalWait => Rejected
     *   + Rejected => ApprovalWait, Terminated
     *   + OpeningWait => Open
     *   + Open => Paused
     *   + Paused => Open
     *   + Open / Paused => Stopped
     *   + Stopped => Terminated
     */
    suspend fun updateShowSessionStatus(
        showId: String,
        sessionId: String,
        fromStatus: List<ShowSessionStatus>,
        newStatus: ShowSessionStatus,
    ): ShowSessionDocument? {
        val sessionPrefix = ShowDocument::sessions.name

        val filter = Filters.and(
            listOfNotNull(
                Filters.eq("_id", ObjectId(showId)),
                Filters.elemMatch(
                    sessionPrefix, Filters.and(
                        Filters.eq("_id", ObjectId(sessionId)),
                        if (fromStatus.isNotEmpty()) Filters.`in`(
                            ShowSessionDocument::status.name,
                            fromStatus
                        ) else null,
                        Filters.not(Filters.eq(ShowSessionDocument::status.name, newStatus)),
                    )
                )
            )
        )

        val update = Updates.combine(
            Updates.set("$sessionPrefix.$.${ShowSessionDocument::status.name}", newStatus),
            Updates.inc("$sessionPrefix.$.${ShowSessionDocument::version.name}", 1),
            Updates.set("$sessionPrefix.$.${ShowSessionDocument::lastModified.name}", Date())
        )

        val options = FindOneAndUpdateOptions().returnDocument(ReturnDocument.AFTER)
        return withContext(Dispatchers.IO) {
            col.findOneAndUpdate(filter, update, options)
        }?.sessions?.firstOrNull { it.id == sessionId }
    }

    suspend fun updateSaleModeShowSession(showId: String, sessionId: String, saleMode: SaleMode): ShowSessionDocument? {
        val sessionPrefix = ShowDocument::sessions.name

        val filter = Filters.and(
            Filters.eq("_id", ObjectId(showId)),
            Filters.eq("$sessionPrefix._id", ObjectId(sessionId)),
        )

        val update = Updates.combine(
            Updates.set("$sessionPrefix.$.${ShowSessionDocument::saleMode.name}", saleMode),
            Updates.inc("$sessionPrefix.$.${ShowSessionDocument::version.name}", 1),
            Updates.set("$sessionPrefix.$.${ShowSessionDocument::lastModified.name}", Date()),
        )

        val options = FindOneAndUpdateOptions().returnDocument(ReturnDocument.AFTER)
        return withContext(Dispatchers.IO) {
            col.findOneAndUpdate(filter, update, options)
        }?.sessions?.firstOrNull { it.id == sessionId }
    }

    suspend fun updateSoldTicket(showId: String, soldTicket: Int): ShowDocument? {
        val filters = Filters.eq("_id", ObjectId(showId))
        val updates = Updates.combine(
            Updates.inc(ShowDocument::soldTicket.name, soldTicket.toLong()),
            Updates.inc(ShowDocument::version.name, 1),
            Updates.set(ShowDocument::lastModified.name, Date()),
        )
        return withContext(Dispatchers.IO) { col.findOneAndUpdate(filters, updates) }
    }

    suspend fun deleteShow(showId: String): ShowDocument? {
        return withContext(Dispatchers.IO) {
            col.findOneAndDelete(
                Filters.and(
                    Filters.eq("_id", ObjectId(showId)),
                )
            )
        }
    }

    suspend fun getEventCategory(): List<EventCategory> {
        return withContext(Dispatchers.IO) { eventCategoryCol.find() }.toList()
    }

    /**
     * Shows currently occupying banner slots 1..[slotCount] (unique pinOrder).
     * Does not bump lastModified — pin rank is editorial, not an edit of the show.
     */
    suspend fun loadShowsByPinOrder(slotCount: Int): List<ShowDocument> {
        val filter = Filters.and(
            Filters.exists(ShowDocument::pinOrder.name),
            Filters.gte(ShowDocument::pinOrder.name, 1),
            Filters.lte(ShowDocument::pinOrder.name, slotCount),
        )
        return withContext(Dispatchers.IO) { col.find(filter) }.toList()
    }

    /** Newest [limit] shows by lastModified — used by “Làm mới danh sách ghim”. */
    suspend fun loadMostRecentlyModifiedShows(limit: Int): List<ShowDocument> {
        return withContext(Dispatchers.IO) {
            col.find().sort(Sorts.descending(ShowDocument::lastModified.name)).limit(limit)
        }.toList()
    }

    data class ShowSearchPage(
        val items: List<ShowDocument>,
        val total: Long,
    )

    /** Paginated picker list (1-based [page]); optional name/code search and category filter. */
    suspend fun searchShowsForPinPicker(
        page: Int,
        size: Int,
        keyword: String?,
        eventCategoryCode: String?,
    ): ShowSearchPage {
        val safePage = page.coerceAtLeast(1)
        val safeSize = size.coerceIn(1, 50)
        val filters = mutableListOf<org.bson.conversions.Bson>()
        val q = keyword?.trim().orEmpty()
        if (q.isNotEmpty()) {
            val escaped = java.util.regex.Pattern.quote(q)
            filters += Filters.or(
                Filters.regex(ShowDocument::showName.name, escaped, "i"),
                Filters.regex(ShowDocument::code.name, escaped, "i"),
            )
        }
        val cat = eventCategoryCode?.trim().orEmpty()
        if (cat.isNotEmpty()) {
            filters += Filters.eq(ShowDocument::eventCategoryCode.name, cat)
        }
        val filter = if (filters.isEmpty()) Filters.empty() else Filters.and(filters)
        val skip = (safePage - 1) * safeSize
        return withContext(Dispatchers.IO) {
            val total = col.countDocuments(filter)
            val items = col.find(filter)
                .sort(Sorts.descending(ShowDocument::lastModified.name))
                .skip(skip)
                .limit(safeSize)
                .toList()
            ShowSearchPage(items, total)
        }
    }

    /**
     * Rewrite banner slots: clear previous pins, then assign [orderedShowIds] as pinOrder 1..n.
     * Duplicate ids allowed — later slot overwrites pinOrder on that show. Does not touch lastModified.
     * Returns ids whose pin flags changed (for metasearch sync).
     */
    suspend fun replaceBannerPins(orderedShowIds: List<String?>): Set<String> {
        val previous = withContext(Dispatchers.IO) {
            col.find(
                Filters.or(
                    Filters.eq(ShowDocument::pinned.name, true),
                    Filters.exists(ShowDocument::pinOrder.name),
                ),
            ).toList()
        }
        val previousIds = previous.map { it.id }.toSet()
        withContext(Dispatchers.IO) {
            col.updateMany(
                Filters.or(
                    Filters.eq(ShowDocument::pinned.name, true),
                    Filters.exists(ShowDocument::pinOrder.name),
                ),
                Updates.combine(
                    Updates.set(ShowDocument::pinned.name, false),
                    Updates.unset(ShowDocument::pinOrder.name),
                    Updates.inc(ShowDocument::version.name, 1),
                ),
            )
        }
        val assigned = linkedSetOf<String>()
        orderedShowIds.forEachIndexed { index, rawId ->
            val id = rawId?.trim().orEmpty()
            if (id.isEmpty() || !ObjectId.isValid(id)) return@forEachIndexed
            val pinOrder = index + 1
            withContext(Dispatchers.IO) {
                col.updateOne(
                    Filters.eq("_id", ObjectId(id)),
                    Updates.combine(
                        Updates.set(ShowDocument::pinned.name, true),
                        Updates.set(ShowDocument::pinOrder.name, pinOrder),
                        Updates.inc(ShowDocument::version.name, 1),
                    ),
                )
            }
            assigned += id
        }
        return previousIds + assigned
    }
}
