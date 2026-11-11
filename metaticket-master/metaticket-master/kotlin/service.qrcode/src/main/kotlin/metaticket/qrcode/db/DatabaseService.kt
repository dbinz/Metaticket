package metaticket.qrcode.db

import com.mongodb.client.model.*
import com.mongodb.client.result.InsertOneResult
import com.mongodb.kotlin.client.coroutine.MongoCollection
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import metaticket.common.logger.Logging
import metaticket.qrcode.db.pojo.QRCodeDocument
import org.bson.types.ObjectId
import java.util.*

class DatabaseService(
    private val col: MongoCollection<QRCodeDocument>
): Logging {

    init {
        CoroutineScope(Dispatchers.Unconfined).launch {
            // make sure the orderCode_cat_sid_ticketId is unique
            val idx = col.createIndex(Indexes.ascending(
                QRCodeDocument::orderCode.name,
                QRCodeDocument::cat.name,
                QRCodeDocument::sid.name,
                QRCodeDocument::no.name,
            ), IndexOptions().unique(true))
            logger.info("created index {}", idx)
        }
    }

    suspend fun insert(document: QRCodeDocument): InsertOneResult {
        return withContext(Dispatchers.IO) { col.insertOne(document) }
    }

    suspend fun fetchById(qrId: String): QRCodeDocument? {
        return withContext(Dispatchers.IO) { col.find(Filters.eq("_id", ObjectId(qrId))) }.firstOrNull()
    }

    suspend fun fetchByShowAndSession(showId: String, sessionId: String, orderIds: List<String>? = null): List<QRCodeDocument> {
        val filters = if (orderIds.isNullOrEmpty()) {
            Filters.and(
                Filters.eq(QRCodeDocument::showId.name, ObjectId(showId)),
                Filters.eq(QRCodeDocument::sessionId.name, ObjectId(sessionId)),
            )
        } else {
            Filters.and(Filters.eq(QRCodeDocument::showId.name, ObjectId(showId)),
                Filters.eq(QRCodeDocument::sessionId.name, ObjectId(sessionId)),
                Filters.`in`(QRCodeDocument::orderId.name, orderIds.map { id -> ObjectId(id) })
            )
        }
        logger.info("filter {}", filters)
        return withContext(Dispatchers.IO) { col.find(filters) }.toList()
    }

    suspend fun fetch(orderCode: String, cat: String, sid: String, ticketIdx: Int): QRCodeDocument? {
        val filters = Filters.and(
            Filters.eq(QRCodeDocument::orderCode.name, orderCode),
            Filters.eq(QRCodeDocument::cat.name, cat),
            Filters.eq(QRCodeDocument::sid.name, sid),
            Filters.eq(QRCodeDocument::no.name, ticketIdx),
        )
        return withContext(Dispatchers.IO) { col.find(filters) }.firstOrNull()
    }

    suspend fun updateDisableStateOrder(orderId: String, disabled: Boolean?): Long {
        val filters = Filters.eq(QRCodeDocument::orderId.name, ObjectId(orderId))
        val updates = Updates.set(QRCodeDocument::disable.name, disabled)
        return withContext(Dispatchers.IO) {
            col.updateMany(filters, updates)
        }.modifiedCount
    }

    suspend fun delete(qrId: String) {
        return withContext(Dispatchers.IO) { col.deleteOne(Filters.eq("_id", ObjectId(qrId))) }
    }

    suspend fun markQRCodeScanned(qrId: String, limit: Int): QRCodeDocument? {
        val filters = Filters.and(
            Filters.eq("_id", ObjectId(qrId)),
            Filters.not(Filters.eq(QRCodeDocument::disable.name, true)),
            Filters.lt(QRCodeDocument::scanned.name, limit),
        )
        val updates = Updates.combine(
            Updates.inc(QRCodeDocument::scanned.name, 1),
            Updates.set(QRCodeDocument::lastUpdated.name, Date())
        )
        val updateOption = FindOneAndUpdateOptions().returnDocument(ReturnDocument.AFTER)

        return withContext(Dispatchers.IO) {
            col.findOneAndUpdate(filters, updates, updateOption)
        }
    }
}