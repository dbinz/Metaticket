package metaticket.ticketslots.pojo.db

import metaticket.ticketslots.model.FailedEventStatus
import java.util.Date

/**
 * Interface cho các document có thể retry
 */
interface RetryableEventDoc {
    val id: String
    /**
     * Thông báo lỗi chi tiết
     */
    val errorMessage: String

    /**
     * Kafka topic nguồn
     */
    val sourceTopic: String

    /**
     * Loại lỗi (ví dụ: PARSING_ERROR, PROCESSING_ERROR)
     */
    val errorType: String

    /**
     * Số lần đã thử lại
     */
    val retryCount: Int

    /**
     * Thời gian thử lại cuối cùng
     */
    val lastRetryAt: Date?

    /**
     * Trạng thái của consumer event
     */
    val status: FailedEventStatus

    /**
     * Thời gian tạo record
     */
    val createdAt: Date

    /**
     * Thời gian cập nhật cuối cùng
     */
    val updatedAt: Date
}