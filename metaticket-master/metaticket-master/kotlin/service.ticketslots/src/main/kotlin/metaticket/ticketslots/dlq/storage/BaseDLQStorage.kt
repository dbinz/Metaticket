package metaticket.ticketslots.dlq.storage

import com.mongodb.client.model.Filters
import com.mongodb.client.model.Sorts
import com.mongodb.client.model.Updates
import com.mongodb.kotlin.client.coroutine.MongoCollection
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.toList
import metaticket.common.logger.Logging
import metaticket.ticketslots.model.FailedEventStatus
import metaticket.ticketslots.pojo.db.RetryableEventDoc
import org.bson.conversions.Bson
import java.time.Duration
import java.util.*

/**
 * Interface chung cho các storage operations có thể retry
 */
interface RetryableEventStorage<TDoc : RetryableEventDoc> {
    suspend fun markAsProcessed(eventId: String)
    suspend fun updateRetryInfo(
        eventId: String,
        retryCount: Int,
        status: FailedEventStatus? = null,
        errorMessage: String? = null
    )
    suspend fun getById(eventId: String): TDoc?
}

/**
 * Base interface cho tất cả DLQ Storage implementations
 * Chứa các phương thức chung để tránh code trùng lặp
 * 
 * @param TDoc Kiểu document MongoDB (ví dụ: QRCodeEventProducerDLQDoc)
 */
interface DLQStorage<TDoc : RetryableEventDoc>: RetryableEventStorage<TDoc> {
    
    /**
     * Cập nhật trạng thái của event
     * 
     * @param eventId ID của event
     * @param status Trạng thái mới
     */
    suspend fun updateEventStatus(eventId: String, status: FailedEventStatus)
    
    /**
     * Xóa các events đã được xử lý và cũ hơn thời gian chỉ định
     * 
     * @param olderThan Thời gian cutoff
     * @return Số lượng events đã xóa
     */
    suspend fun deleteProcessedEvents(olderThan: Date): Int
}

/**
 * Abstract base class cho tất cả DLQ Storage implementations
 * Chứa các phương thức chung để tránh code trùng lặp
 * 
 * @param TDoc Kiểu document MongoDB (ví dụ: QRCodeEventProducerDLQDoc)
 * @param TEvent Kiểu event (ví dụ: GenerateTicketSlotQRCodeEvent)
 */
abstract class BaseDLQStorage<TDoc : RetryableEventDoc, TEvent : Any> : DLQStorage<TDoc>, Logging {
    
    /**
     * MongoDB collection để lưu trữ failed events
     */
    protected abstract val collection: MongoCollection<TDoc>

    abstract suspend fun store(
        failureReason: String,
        errorType: String,
        status: FailedEventStatus = FailedEventStatus.PENDING,
        sourceTopic: String,
        event: TEvent?,
        messageKey: String? = null,
        originalMessage: String? = null,
    ): String

    /**
     * Lấy một failed event theo ID
     * 
     * @param eventId ID của event
     * @return Document hoặc null nếu không tìm thấy
     */
    override suspend fun getById(eventId: String): TDoc? {
        return try {
            val filter = Filters.eq("_id", eventId)
            collection.find(filter).firstOrNull()
        } catch (e: Throwable) {
            logger.error("Failed to get event by ID {}: {}", eventId, e.message, e)
            null
        }
    }
    
    /**
     * Đánh dấu event đã được xử lý thành công
     * 
     * @param eventId ID của event
     */
    override suspend fun markAsProcessed(eventId: String) {
        try {
            val filter = Filters.eq("_id", eventId)
            val update = Updates.combine(
                Updates.set(RetryableEventDoc::status.name, FailedEventStatus.PROCESSED),
                Updates.set(RetryableEventDoc::updatedAt.name, Date())
            )
            
            val result = collection.updateOne(filter, update)
            if (result.modifiedCount > 0) {
                logger.info("Marked event {} as processed", eventId)
            } else {
                logger.warn("Failed to mark event {} as processed - not found", eventId)
            }
        } catch (e: Throwable) {
            logger.error("Failed to mark event {} as processed: {}", eventId, e.message, e)
            throw e
        }
    }
    
    /**
     * Cập nhật thông tin retry cho một event thất bại
     * 
     * @param eventId ID của event
     * @param retryCount Số lần retry mới
     * @param status Trạng thái mới
     * @param errorMessage Lý do thất bại mới (nếu có)
     */
    override suspend fun updateRetryInfo(
        eventId: String,
        retryCount: Int,
        status: FailedEventStatus?,
        errorMessage: String?
    ) {
        try {
            val filter = Filters.eq("_id", eventId)
            val updates = mutableListOf<Bson>(
                Updates.set(RetryableEventDoc::retryCount.name, retryCount),
                Updates.set(RetryableEventDoc::updatedAt.name, Date())
            )
            
            // Thêm status update nếu có
            status?.let { updates.add(Updates.set(RetryableEventDoc::status.name, it)) }
            errorMessage?.let { updates.add(Updates.set(RetryableEventDoc::errorMessage.name, it)) }
            
            val update = Updates.combine(updates)
            val result = collection.updateOne(filter, update)
            
            if (result.modifiedCount > 0) {
                logger.info("Updated retry info for event {}: retryCount={}", eventId, retryCount)
            } else {
                logger.warn("Failed to update retry info for event {} - not found", eventId)
            }
        } catch (e: Throwable) {
            logger.error("Failed to update retry info for event {}: {}", eventId, e.message, e)
            throw e
        }
    }

    /**
     * Cập nhật trạng thái của event
     * 
     * @param eventId ID của event
     * @param status Trạng thái mới
     */
    override suspend fun updateEventStatus(eventId: String, status: FailedEventStatus) {
        try {
            val filter = Filters.eq("_id", eventId)
            val update = Updates.combine(
                Updates.set(RetryableEventDoc::status.name, status),
                Updates.set(RetryableEventDoc::updatedAt.name, Date())
            )
            
            val result = collection.updateOne(filter, update)
            if (result.modifiedCount > 0) {
                logger.info("Updated event {} status to {}", eventId, status)
            } else {
                logger.warn("Failed to update event {} status - not found", eventId)
            }
        } catch (e: Throwable) {
            logger.error("Failed to update event {} status: {}", eventId, e.message, e)
            throw e
        }
    }
    
    /**
     * Xóa các events đã được xử lý và cũ hơn thời gian chỉ định
     * 
     * @param olderThan Thời gian cutoff
     * @return Số lượng events đã xóa
     */
    override suspend fun deleteProcessedEvents(olderThan: Date): Int {
        return try {
            val filter = Filters.and(
                Filters.lt(RetryableEventDoc::updatedAt.name, olderThan),
                Filters.eq(RetryableEventDoc::status.name, FailedEventStatus.PROCESSED)
            )
            
            val result = collection.deleteMany(filter)
            if (result.deletedCount > 0) {
                logger.info("Deleted {} processed events older than {}", result.deletedCount, olderThan)
            }
            result.deletedCount.toInt()
        } catch (e: Throwable) {
            logger.error("Failed to delete processed events: {}", e.message, e)
            0
        }
    }

    suspend fun getEventsToRetry(
        limit: Int,
        errorTypes: List<String>? = null,
    ): List<TDoc> {
        val filter = Filters.and(listOfNotNull(
            errorTypes?.let { Filters.`in`(RetryableEventDoc::errorType.name, errorTypes) },
            Filters.eq(RetryableEventDoc::status.name, FailedEventStatus.PENDING.name)
        ))

        return collection.find(filter)
            .sort(Sorts.ascending(RetryableEventDoc::lastRetryAt.name))
            .limit(limit)
            .toList()
    }
    
    /**
     * Lấy danh sách failed events với phân trang
     * 
     * @param page Trang hiện tại (bắt đầu từ 0)
     * @param size Kích thước trang
     * @param status Filter theo trạng thái (optional)
     * @return Danh sách events
     */
    open suspend fun getFailedEvents(
        page: Int,
        size: Int,
        status: FailedEventStatus? = null,
        errorType: String? = null,
    ): List<TDoc> {
        return try {
            val filters = mutableListOf<Bson>()
            status?.let { filters.add(Filters.eq(RetryableEventDoc::status.name, it)) }
            errorType?.let { filters.add(Filters.eq(RetryableEventDoc::errorType.name, it)) }
            
            collection.find(Filters.and(filters))
                .sort(Sorts.descending(RetryableEventDoc::createdAt.name))
                .skip(page * size)
                .limit(size)
                .toList()
        } catch (e: Throwable) {
            logger.error("Failed to get failed events: {}", e.message, e)
            emptyList()
        }
    }

    suspend fun countFailedEvents(
        status: FailedEventStatus? = null,
        errorType: String? = null
    ): Long {
        val filters = mutableListOf<Bson>()

        status?.let { filters.add(Filters.eq(RetryableEventDoc::status.name, it.name)) }
        errorType?.let { filters.add(Filters.eq(RetryableEventDoc::errorType.name, it)) }

        val filter = if (filters.isNotEmpty()) {
            Filters.and(filters)
        } else {
            Filters.empty()
        }

        return collection.countDocuments(filter)
    }

    /**
     * Abstract method để lấy tên service cho logging
     */
    protected abstract val serviceName: String
}