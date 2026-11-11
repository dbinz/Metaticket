package metaticket.admin.database.service

import com.mongodb.client.model.Filters
import com.mongodb.client.model.FindOneAndUpdateOptions
import com.mongodb.client.model.ReturnDocument
import com.mongodb.client.model.Updates
import com.mongodb.kotlin.client.coroutine.MongoCollection
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.withContext
import metaticket.admin.pojo.db.CityDocument
import metaticket.admin.pojo.db.SessionCalendarDraft
import metaticket.admin.pojo.db.ShowSessionDraftDocument
import metaticket.admin.pojo.db.ShowSessionDraftStatus
import metaticket.payment.datastructure.PaymentType
import metaticket.show.datastructure.MailPluginTemplate
import metaticket.show.datastructure.SessionType
import org.bson.types.ObjectId

class ShowSessionDraftDBService(
    private val showSessionDraftCol: MongoCollection<ShowSessionDraftDocument>,
) {
    suspend fun createShowSessionDraft(entity: ShowSessionDraftDocument): String {
        val result = withContext(Dispatchers.IO) { showSessionDraftCol.insertOne(entity) }
        return result.insertedId!!.toString()
    }

    suspend fun updateShowSessionDraft(
        sessionDraftId: String,
        sessionName: String,
        code: String,
        address: String,
        startTime: Long,
        endTime: Long,
        requireDelivery: Boolean,
        ledgendImage: String?,
        ledgendImageUrl: String?,
        eventSquareImage: String?,
        eventSquareImageUrl: String?,
        eventNameImage: String?,
        eventNameImageUrl: String?,
        orderNote: String?,
        city: CityDocument,
        isFree: Boolean,
        showInHome: Boolean,
        manageTicket: Boolean?,
        qrcodeEmail: Boolean?,
        slug: String?,
        sessionType: SessionType?,
        calendar: SessionCalendarDraft?,
    ): ShowSessionDraftDocument? {
        val filter = Filters.eq("_id", ObjectId(sessionDraftId))

        val update = Updates.combine(
            Updates.set(ShowSessionDraftDocument::sessionName.name, sessionName),
            Updates.set(ShowSessionDraftDocument::code.name, code),
            Updates.set(ShowSessionDraftDocument::address.name, address),
            Updates.set(ShowSessionDraftDocument::startTime.name, startTime),
            Updates.set(ShowSessionDraftDocument::endTime.name, endTime),
            Updates.set(ShowSessionDraftDocument::requireDelivery.name, requireDelivery),
            orderNote?.let { Updates.set(ShowSessionDraftDocument::orderNote.name, orderNote) }
                ?: Updates.unset(ShowSessionDraftDocument::orderNote.name),
            Updates.set(ShowSessionDraftDocument::city.name, city),
            Updates.set(ShowSessionDraftDocument::isFree.name, isFree),
            Updates.set(ShowSessionDraftDocument::showInHome.name, showInHome),
            ledgendImage?.let { Updates.set(ShowSessionDraftDocument::ledgendImage.name, it) }
                ?: Updates.unset(ShowSessionDraftDocument::ledgendImage.name),
            ledgendImageUrl?.let { Updates.set(ShowSessionDraftDocument::ledgendImageUrl.name, it) }
                ?: Updates.unset(ShowSessionDraftDocument::ledgendImageUrl.name),
            eventSquareImage?.let { Updates.set(ShowSessionDraftDocument::eventSquareImage.name, it) }
                ?: Updates.unset(ShowSessionDraftDocument::eventSquareImage.name),
            eventSquareImageUrl?.let { Updates.set(ShowSessionDraftDocument::eventSquareImageUrl.name, it) }
                ?: Updates.unset(ShowSessionDraftDocument::eventSquareImageUrl.name),
            eventNameImage?.let { Updates.set(ShowSessionDraftDocument::eventNameImage.name, it) }
                ?: Updates.unset(ShowSessionDraftDocument::eventNameImage.name),
            eventNameImageUrl?.let { Updates.set(ShowSessionDraftDocument::eventNameImageUrl.name, it) }
                ?: Updates.unset(ShowSessionDraftDocument::eventNameImageUrl.name),
            manageTicket?.let { Updates.set(ShowSessionDraftDocument::manageTicket.name, it) }
                ?: Updates.unset(ShowSessionDraftDocument::manageTicket.name),
            qrcodeEmail?.let { Updates.set(ShowSessionDraftDocument::qrcodeEmail.name, it) }
                ?: Updates.unset(ShowSessionDraftDocument::qrcodeEmail.name),
            slug?.takeIf { it.isNotBlank() }?.let { Updates.set(ShowSessionDraftDocument::slug.name, it) }
                ?: Updates.unset(ShowSessionDraftDocument::slug.name),
            sessionType?.let { Updates.set(ShowSessionDraftDocument::sessionType.name, it) }
                ?: Updates.unset(ShowSessionDraftDocument::sessionType.name),
            calendar?.let { Updates.set(ShowSessionDraftDocument::calendar.name, it) }
                ?: Updates.unset(ShowSessionDraftDocument::calendar.name),
        )

        val options = FindOneAndUpdateOptions().returnDocument(ReturnDocument.AFTER)
        return withContext(Dispatchers.IO) { showSessionDraftCol.findOneAndUpdate(filter, update, options) }
    }

    suspend fun updateShowSessionDraftPayment(
        sessionDraftId: String,
        paymentOffline: Boolean?,
        paymentNote: String?,
        pgAccountCode: String?,
        paymentMethods: List<PaymentType>?,
    ): ShowSessionDraftDocument? {
        val filter = Filters.eq("_id", ObjectId(sessionDraftId))

        val update = Updates.combine(
            paymentOffline?.let { Updates.set(ShowSessionDraftDocument::paymentOffline.name, it) }
                ?: Updates.unset(ShowSessionDraftDocument::paymentOffline.name),
            paymentNote?.let { Updates.set(ShowSessionDraftDocument::paymentNote.name, it) }
                ?: Updates.unset(ShowSessionDraftDocument::paymentNote.name),
            pgAccountCode?.let { Updates.set(ShowSessionDraftDocument::pgAccountCode.name, it) }
                ?: Updates.unset(ShowSessionDraftDocument::pgAccountCode.name),
            paymentMethods?.let { Updates.set(ShowSessionDraftDocument::paymentMethods.name, it) }
                ?: Updates.unset(ShowSessionDraftDocument::paymentMethods.name),
        )

        val options = FindOneAndUpdateOptions().returnDocument(ReturnDocument.AFTER)
        return withContext(Dispatchers.IO) { showSessionDraftCol.findOneAndUpdate(filter, update, options) }
    }

    suspend fun updateShowSessionDraftSeatPlan(
        sessionDraftId: String,
        seatPlanId: String?,
        edType: String?,
        extraInfo: String? = null,
        clearExtraInfo: Boolean = false,
    ): ShowSessionDraftDocument? {
        val filter = Filters.eq("_id", ObjectId(sessionDraftId))

        val update = Updates.combine(
            listOfNotNull(
                seatPlanId?.let { Updates.set(ShowSessionDraftDocument::seatPlanId.name, ObjectId(it)) }
                    ?: Updates.unset(ShowSessionDraftDocument::seatPlanId.name),
                edType?.let { Updates.set(ShowSessionDraftDocument::edType.name, it) }
                    ?: Updates.unset(ShowSessionDraftDocument::edType.name),
                when {
                    clearExtraInfo -> Updates.unset(ShowSessionDraftDocument::extraInfo.name)
                    extraInfo != null -> Updates.set(ShowSessionDraftDocument::extraInfo.name, extraInfo)
                    else -> null
                },
            )
        )

        val options = FindOneAndUpdateOptions().returnDocument(ReturnDocument.AFTER)
        return withContext(Dispatchers.IO) { showSessionDraftCol.findOneAndUpdate(filter, update, options) }
    }

    suspend fun updateShowSessionDraftMailTemplates(
        sessionDraftId: String,
        mailTemplates: List<MailPluginTemplate>,
    ): ShowSessionDraftDocument? {
        val filter = Filters.eq("_id", ObjectId(sessionDraftId))

        val update = Updates.combine(
            Updates.set(ShowSessionDraftDocument::mailPluginTemplate.name, mailTemplates),
        )

        val options = FindOneAndUpdateOptions().returnDocument(ReturnDocument.AFTER)
        return withContext(Dispatchers.IO) { showSessionDraftCol.findOneAndUpdate(filter, update, options) }
    }

    suspend fun updateShowSessionDraftSessionId(sessionDraftId: String, sessionId: String): ShowSessionDraftDocument? {
        val filter = Filters.eq("_id", ObjectId(sessionDraftId))

        val update = Updates.combine(
            Updates.set(ShowSessionDraftDocument::sessionId.name, ObjectId(sessionId)),
            Updates.set(ShowSessionDraftDocument::status.name, ShowSessionDraftStatus.Achieved),
        )

        val options = FindOneAndUpdateOptions().returnDocument(ReturnDocument.AFTER)
        return withContext(Dispatchers.IO) { showSessionDraftCol.findOneAndUpdate(filter, update, options) }
    }

    suspend fun loadSessionsDraft(showId: String): List<ShowSessionDraftDocument> {
        val filter = Filters.and(
            Filters.eq(ShowSessionDraftDocument::showId.name, ObjectId(showId)),
            Filters.eq(ShowSessionDraftDocument::status.name, ShowSessionDraftStatus.None),
        )
        return withContext(Dispatchers.IO) { showSessionDraftCol.find(filter) }.toList()
    }

    suspend fun loadSessionDraft(sessionDraftId: String): ShowSessionDraftDocument? {
        val filter = Filters.and(
            Filters.eq("_id", ObjectId(sessionDraftId)),
        )
        return withContext(Dispatchers.IO) { showSessionDraftCol.find(filter) }.firstOrNull()
    }

    /** Soft-deletes a draft (status → Deleted). Only drafts still in None are eligible. */
    suspend fun softDeleteShowSessionDraft(sessionDraftId: String): ShowSessionDraftDocument? {
        val filter = Filters.and(
            Filters.eq("_id", ObjectId(sessionDraftId)),
            Filters.eq(ShowSessionDraftDocument::status.name, ShowSessionDraftStatus.None),
        )
        val update = Updates.set(ShowSessionDraftDocument::status.name, ShowSessionDraftStatus.Deleted)
        val options = FindOneAndUpdateOptions().returnDocument(ReturnDocument.AFTER)
        return withContext(Dispatchers.IO) { showSessionDraftCol.findOneAndUpdate(filter, update, options) }
    }
}
