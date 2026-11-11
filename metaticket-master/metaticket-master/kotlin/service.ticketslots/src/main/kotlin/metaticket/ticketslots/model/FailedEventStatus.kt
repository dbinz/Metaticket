package metaticket.ticketslots.model

/**
 * Status của failed QR code event
 */
enum class FailedEventStatus {
    /**
     * Event đang chờ retry
     */
    PENDING,

    /**
     * Event đang được retry
     */
    RETRYING,

    /**
     * Event đã được xử lý thành công
     */
    PROCESSED,

    /**
     * Event bị đánh dấu để không retry nữa
     */
    DISABLED,

    /**
     * Event đã được lưu trữ (archived) - không retry nhưng vẫn giữ lại để theo dõi
     */
    ARCHIVED,

    /**
     * Event đánh dấu có vấn để không thể retry được, ví dụ parsing error
     */
    DEAD,
}