package metaticket.payment.database.service

import com.mongodb.client.model.*
import com.mongodb.kotlin.client.coroutine.MongoCollection
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import metaticket.common.logger.Logging
import metaticket.payment.datastructure.PaymentStatus
import metaticket.payment.datastructure.PaymentType
import metaticket.payment.gateway.worker.VnPayConstants.Companion.vnpayParseDate
import metaticket.payment.pojo.db.PaymentDetail
import metaticket.payment.pojo.db.PaymentDocument
import org.bson.BsonDocument
import org.bson.types.ObjectId
import java.time.Instant
import java.util.*

class PaymentDBService(
    private val paymentCol: MongoCollection<PaymentDocument>,
): Logging {
    init {
        CoroutineScope(Dispatchers.Unconfined).launch {
            // make sure order code is unique
            val idx = paymentCol.createIndexes(
                listOf(
                    IndexModel(Indexes.ascending(PaymentDocument::orderId.name), IndexOptions().unique(true)),
                    IndexModel(Indexes.ascending(PaymentDocument::orderCode.name), IndexOptions().unique(true)),
                )
            )
            logger.info("created index {}", idx)
        }
    }

    suspend fun loadPayment(paymentId: String): PaymentDocument? {
        val filter = Filters.eq("_id", ObjectId(paymentId))
        return withContext(Dispatchers.IO) {
            paymentCol.find(filter).firstOrNull()
        }
    }

    suspend fun loadPaymentBayIds(paymentIds: List<String>): List<PaymentDocument> {
        val filter = Filters.`in`("_id", paymentIds.map { ObjectId(it) })
        return withContext(Dispatchers.IO) { paymentCol.find(filter) }.toList()
    }

    suspend fun loadPaymentByStatus(status: List<PaymentStatus>): List<PaymentDocument> {
        val filter = Filters.`in`("status", status)
        return withContext(Dispatchers.IO) { paymentCol.find(filter).toList() }
    }

    suspend fun createPayment(entity: PaymentDocument): String? {
        val result = withContext(Dispatchers.IO) { paymentCol.insertOne(entity) }
        return result.insertedId?.toString()
    }

    suspend fun updatePaymentStatus(paymentId: String, status: PaymentStatus, oldStatus: List<PaymentStatus> = emptyList()): PaymentDocument? {
        val filter = Filters.and(listOfNotNull(
            Filters.eq("_id", ObjectId(paymentId)),
            if (oldStatus.isNotEmpty()) Filters.`in`(PaymentDocument::status.name, oldStatus) else null
        ))

        val update = Updates.combine(
            Updates.set(PaymentDocument::status.name, status),
            Updates.inc(PaymentDocument::version.name, 1),
            Updates.set(PaymentDocument::lastModified.name, Date()),
        )

        val options = FindOneAndUpdateOptions().returnDocument(ReturnDocument.AFTER)
        return withContext(Dispatchers.IO) { paymentCol.findOneAndUpdate(filter, update, options) }
    }

    suspend fun updateWhenStartPayment(
        docId: String,
        paymentType: PaymentType,
        paymentDetail: PaymentDetail
    ): PaymentDocument? {
        val filter = Filters.eq("_id", ObjectId(docId))

        val update = Updates.combine(
            Updates.set(PaymentDocument::status.name, PaymentStatus.Processing),
            Updates.set(PaymentDocument::paymentType.name, paymentType),
            Updates.set(PaymentDocument::paymentDetail.name, paymentDetail),
            Updates.inc(PaymentDocument::version.name, 1),
            Updates.set(PaymentDocument::lastModified.name, Date()),
        )

        val options = FindOneAndUpdateOptions().returnDocument(ReturnDocument.AFTER)
        return withContext(Dispatchers.IO) { paymentCol.findOneAndUpdate(filter, update, options) }
    }


    suspend fun updateNinePayPaymentResult(
        paymentId: String,
        status: PaymentStatus,
        createdAt: String?,
        paymentNo: String?,
        response: String
    ): PaymentDocument? {
        val filter = Filters.eq("_id", ObjectId(paymentId))

        val update = listOfNotNull(
            Updates.set("${PaymentDocument::paymentDetail.name}.${PaymentDetail::response.name}", BsonDocument.parse(response)),
            createdAt?.let { Updates.set(PaymentDocument::payTime.name, Date.from(Instant.parse(it))) },
            paymentNo?.let { Updates.set(PaymentDocument::paymentNo.name, it) },
            Updates.set(PaymentDocument::status.name, status),
            Updates.inc(PaymentDocument::version.name, 1),
            Updates.set(PaymentDocument::lastModified.name, Date()),
        )

        val options = FindOneAndUpdateOptions().returnDocument(ReturnDocument.AFTER)
        return withContext(Dispatchers.IO) { paymentCol.findOneAndUpdate(filter, Updates.combine(update), options) }
    }

    suspend fun updateVnPayPaymentResult(
        vnpTxnRef: String?, // paymentId
        status: PaymentStatus,
        vnpPayDate: String?,
        vnpTransactionNo: String?,
        response: String,
    ): PaymentDocument? {
        val filter = Filters.eq("_id", ObjectId(vnpTxnRef))

        val update = listOfNotNull(
            Updates.set("${PaymentDocument::paymentDetail.name}.${PaymentDetail::response.name}", BsonDocument.parse(response)),
            vnpTransactionNo?.let { Updates.set(PaymentDocument::paymentNo.name, it) },
            vnpPayDate?.let { Updates.set(PaymentDocument::payTime.name, vnpayParseDate(it)) },
            Updates.set(PaymentDocument::status.name, status),
            Updates.inc(PaymentDocument::version.name, 1),
            Updates.set(PaymentDocument::lastModified.name, Date()),
        )

        val options = FindOneAndUpdateOptions().returnDocument(ReturnDocument.AFTER)
        return withContext(Dispatchers.IO) { paymentCol.findOneAndUpdate(filter, Updates.combine(update), options) }
    }

    suspend fun updateOnePayPaymentResult(
        vpc_MerchTxnRef: String?, // paymentId
        status: PaymentStatus,
        vpc_TransactionNo: String?,
        response: String,
    ): PaymentDocument? {
        val filter = Filters.eq("_id", ObjectId(vpc_MerchTxnRef))

        val update = listOfNotNull(
            Updates.set("${PaymentDocument::paymentDetail.name}.${PaymentDetail::response.name}", BsonDocument.parse(response)),
            vpc_TransactionNo?.let { Updates.set(PaymentDocument::paymentNo.name, it) },
            Updates.set(PaymentDocument::payTime.name, Date()),
            Updates.set(PaymentDocument::status.name, status),
            Updates.inc(PaymentDocument::version.name, 1),
            Updates.set(PaymentDocument::lastModified.name, Date()),
        )

        val options = FindOneAndUpdateOptions().returnDocument(ReturnDocument.AFTER)
        return withContext(Dispatchers.IO) { paymentCol.findOneAndUpdate(filter, Updates.combine(update), options) }
    }
}
