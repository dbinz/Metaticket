package metaticket.order.database.service

import com.mongodb.client.model.*
import com.mongodb.kotlin.client.coroutine.MongoCollection
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import metaticket.common.logger.Logging
import metaticket.order.datastructure.OrderStatus
import metaticket.order.pojo.db.OrderDocument
import metaticket.order.request.UpdateOrderInfoRequest
import org.bson.types.ObjectId
import java.util.*


class OrderDBService(
    private val orderCol: MongoCollection<OrderDocument>,
): Logging {

    init {
        CoroutineScope(Dispatchers.Unconfined).launch {
            // make sure order code is unique
            val idx = orderCol.createIndexes(
                listOf(
                    IndexModel(Indexes.ascending(OrderDocument::code.name), IndexOptions().unique(true)),
                    IndexModel(Indexes.ascending(OrderDocument::reservationId.name), IndexOptions().unique(true)),
                )
            )
            logger.info("created index {}", idx)
        }
    }

    suspend fun createOrder(entity: OrderDocument): String? {
        return withContext(Dispatchers.IO) { orderCol.insertOne(entity) }.insertedId?.toString()
    }

    suspend fun loadOrderById(id: String): OrderDocument? {
        val filter = Filters.eq("_id", ObjectId(id))
        return withContext(Dispatchers.IO) { orderCol.find(filter) }.firstOrNull()
    }

    suspend fun loadOrderByIds(ids: List<String>): List<OrderDocument> {
        val filter = Filters.`in`("_id", ids.map { ObjectId(it) } )
        return withContext(Dispatchers.IO) { orderCol.find(filter) }.toList()
    }

    suspend fun loadOrderByStatus(status: List<OrderStatus>): List<OrderDocument> {
        val filter = Filters.`in`("status", status)
        return withContext(Dispatchers.IO) { orderCol.find(filter).toList() }
    }

    suspend fun loadOrderByShowAndOrderStatus(showId: String, status: OrderStatus): List<OrderDocument> {
        val filter = Filters.and(
            Filters.eq("showId", ObjectId(showId)),
            Filters.eq("status", status),
        )
        return withContext(Dispatchers.IO) { orderCol.find(filter) }.toList()
    }

    suspend fun loadOrderByShowAndSession(showId: String, sessionId: String? = null): List<OrderDocument> {
        val filter = Filters.and(listOfNotNull(
            Filters.eq("showId", ObjectId(showId)),
            sessionId?.let { Filters.eq("sessionId", ObjectId(sessionId)) },
        ))
        return withContext(Dispatchers.IO) { orderCol.find(filter) }.toList()
    }

    suspend fun setPaymentId(orderId: String, paymentId: String): OrderDocument? {
        val filter = Filters.eq("_id", ObjectId(orderId))

        val update = Updates.combine(
            Updates.set(OrderDocument::paymentId.name, ObjectId(paymentId)),
            Updates.set(OrderDocument::status.name, OrderStatus.WaitingForPayment),
            Updates.inc(OrderDocument::version.name, 1),
            Updates.set(OrderDocument::lastModified.name, Date())
        )

        val options = FindOneAndUpdateOptions().returnDocument(ReturnDocument.AFTER)
        return withContext(Dispatchers.IO) { orderCol.findOneAndUpdate(filter, update, options)}
    }

    suspend fun updateStatus(id: String, status: OrderStatus): OrderDocument? {
        val filter = Filters.eq("_id", ObjectId(id))

        val updates = Updates.combine(
            Updates.set(OrderDocument::status.name, status),
            Updates.inc(OrderDocument::version.name, 1),
            Updates.set(OrderDocument::lastModified.name, Date())
        )

        val option = FindOneAndUpdateOptions().returnDocument(ReturnDocument.AFTER)
        return withContext(Dispatchers.IO) { orderCol.findOneAndUpdate(filter, updates, option) }
    }

    suspend fun updateOrderInfo(id: String, updateData: UpdateOrderInfoRequest): OrderDocument? {
        val filter = Filters.eq("_id", ObjectId(id))

        val updates = Updates.combine(
            Updates.set(OrderDocument::customerName.name, updateData.name),
            Updates.set(OrderDocument::customerPhone.name, updateData.phone),
            Updates.set(OrderDocument::customerEmail.name, updateData.email),
            Updates.set(OrderDocument::delivery.name, updateData.delivery),
            Updates.set(OrderDocument::customFields.name, updateData.customFields),
            Updates.inc(OrderDocument::version.name, 1),
            Updates.set(OrderDocument::lastModified.name, Date()),
        )

        val options = FindOneAndUpdateOptions().returnDocument(ReturnDocument.AFTER)
        return withContext(Dispatchers.IO) { orderCol.findOneAndUpdate(filter, updates, options) }
    }

    suspend fun loadOrder(
        startDate: Long? = null,
        endDate: Long? = null,
        status: List<OrderStatus>? = null,
        showId: String? = null,
        sessionId: String? = null,
        visitDateFrom: String? = null,
        visitDateTo: String? = null,
    ): List<OrderDocument> {
        val filter = Filters.and(listOfNotNull(
            startDate?.let { Filters.gt(OrderDocument::lastModified.name, Date(it)) },
            endDate?.let { Filters.lt(OrderDocument::lastModified.name, Date(it)) },
            status?.let { Filters.`in`(OrderDocument::status.name, it) },
            showId?.let { Filters.`in`(OrderDocument::showId.name, ObjectId(it)) },
            sessionId?.let { Filters.`in`(OrderDocument::sessionId.name, ObjectId(it)) },
            // Ngày dạng "yyyy-MM-dd" so sánh được theo thứ tự chuỗi.
            visitDateFrom?.let { Filters.gte(OrderDocument::visitDate.name, it) },
            visitDateTo?.let { Filters.lte(OrderDocument::visitDate.name, it) },
        ))
        return withContext(Dispatchers.IO) { orderCol.find(filter) }.toList()
    }

    suspend fun loadOrderByReservationIds(resIds: List<String>): List<OrderDocument> {
        val filters = Filters.`in`(OrderDocument::reservationId.name, resIds.map { ObjectId(it) })
        return withContext(Dispatchers.IO) { orderCol.find(filters) }.toList()
    }
}
