package metaticket.order.datastructure

/**
 * Status của failed order succeed event
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
    DISABLED
}