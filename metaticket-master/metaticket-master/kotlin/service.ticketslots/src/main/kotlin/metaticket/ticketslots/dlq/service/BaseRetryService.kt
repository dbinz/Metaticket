package metaticket.ticketslots.dlq.service

import kotlinx.coroutines.*
import metaticket.common.logger.Logging
import metaticket.ticketslots.model.FailedEventStatus
import metaticket.ticketslots.dlq.storage.RetryableEventStorage
import metaticket.ticketslots.pojo.db.RetryableEventDoc
import java.time.Duration
import java.util.*
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.pow

/**
 * Abstract base class cho các retry services
 * Chứa logic chung về lifecycle management, retry mechanism, và cleanup
 * 
 * @param T Event document type
 * @param E Event type
 */
abstract class BaseRetryService<T : RetryableEventDoc, E> : Logging {
    
    // Configuration properties - có thể override trong subclass
    protected open val retryIntervalMinutes: Long = 5
    protected open val maxRetryAttempts: Int = 5
    protected open val maxEventAgeHours: Long = 24
    protected open val batchSize: Int = 100
    protected open val delayBetweenRetries: Long = 100 // ms
    
    // Service state
    private val isRunning = AtomicBoolean(false)
    private var jobScope: CoroutineScope? = null
    private var retryJob: Job? = null
    
    // Abstract properties và methods cần implement
    protected abstract val serviceName: String
    protected abstract val storage: RetryableEventStorage<T>
    
    /**
     * Lấy danh sách events cần retry
     */
    protected abstract suspend fun getEventsToRetry(batchSize: Int): List<T>
    
    /**
     * Retry một event cụ thể
     */
    protected abstract suspend fun retryEvent(eventData: T): Boolean
    
    /**
     * Đánh dấu event đã được xử lý thành công
     */
    protected suspend fun markAsProcessed(eventId: String) {
        storage.markAsProcessed(eventId)
    }
    
    /**
     * Cập nhật thông tin retry cho event
     */
    protected suspend fun updateRetryInfo(
        eventId: String,
        retryCount: Int,
        errorMessage: String,
        status: FailedEventStatus? = null,
    ) {
        storage.updateRetryInfo(
            eventId = eventId,
            retryCount = retryCount,
            errorMessage = errorMessage,
            status = status,
        )
    }
    
    /**
     * Lấy event theo ID
     */
    protected suspend fun getEventById(eventId: String): T? {
        return storage.getById(eventId)
    }
    
    /**
     * Kiểm tra xem event có nên retry ngay bây giờ không
     */
    protected fun shouldRetryNow(eventData: T): Boolean {
        val now = Date()
        
        // Kiểm tra xem đã đủ thời gian để retry chưa
        val lastRetry = eventData.lastRetryAt ?: eventData.createdAt
        val nextRetryTime = Date(lastRetry.time + (retryIntervalMinutes * 60 * 1000))
        if (now.before(nextRetryTime)) {
            return false
        }
        
        // Kiểm tra số lần retry tối đa
        if (eventData.retryCount >= maxRetryAttempts) {
            return false
        }
        
        // Kiểm tra tuổi của event
        if (isEventTooOld(eventData.createdAt)) {
            return false
        }
        
        return true
    }
    
    /**
     * Lấy ID của event
     */
    protected fun getEventId(eventData: T): String = eventData.id
    
    /**
     * Lấy retry count của event
     */
    protected fun getRetryCount(eventData: T): Int = eventData.retryCount
    
    /**
     * Lấy thông tin event để log (ví dụ: orderId, slotId)
     */
    protected abstract fun getEventIdentifier(eventData: T): String
    
    /**
     * Cleanup các events cũ
     */
    protected abstract suspend fun cleanupOldEvents()
    
    /**
     * Khởi tạo service
     */
    open fun initialize() {
        logger.info("{} initialized", serviceName)
    }
    
    /**
     * Bắt đầu retry service
     */
    fun start() {
        if (isRunning.compareAndSet(false, true)) {
            jobScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
            retryJob = jobScope?.launch {
                while (isActive && isRunning.get()) {
                    try {
                        processFailedEvents()
                        cleanupOldEvents()
                    } catch (e: Exception) {
                        logger.error("Error in {} retry job: {}", serviceName, e.message, e)
                    }
                    
                    // Chờ interval trước khi chạy lần tiếp theo
                    delay(retryIntervalMinutes * 60 * 1000)
                }
            }
            logger.info("{} started with interval: {} minutes", serviceName, retryIntervalMinutes)
        } else {
            logger.warn("{} is already running", serviceName)
        }
    }
    
    /**
     * Dừng retry service
     */
    fun stop() {
        if (isRunning.compareAndSet(true, false)) {
            retryJob?.cancel()
            jobScope?.cancel()
            logger.info("{} stopped", serviceName)
        }
    }
    
    /**
     * Kiểm tra xem service có đang chạy không
     */
    fun isRunning(): Boolean {
        return isRunning.get()
    }
    
    /**
     * Chạy retry một lần (manual trigger)
     */
    suspend fun runOnce() {
        processFailedEvents()
        cleanupOldEvents()
    }
    
    /**
     * Retry một event cụ thể theo eventId
     */
    suspend fun retrySpecificEvent(eventId: String): Boolean {
        return try {
            val eventData = getEventById(eventId)
            if (eventData != null) {
                val success = retryEvent(eventData)
                if (success) {
                    markAsProcessed(getEventId(eventData))
                    logger.info("Successfully retried specific {} event: {}", serviceName, eventId)
                } else {
                    logger.warn("Failed to retry specific {} event: {}", serviceName, eventId)
                }
                success
            } else {
                logger.warn("{} event not found: {}", serviceName, eventId)
                false
            }
        } catch (e: Throwable) {
            logger.error("Error retrying specific {} event {}: {}", serviceName, eventId, e.message, e)
            false
        }
    }
    
    /**
     * Xử lý các failed events
     */
    private suspend fun processFailedEvents() {
        logger.debug("Processing failed {} events", serviceName)
        
        try {
            // Lấy các event cần retry
            val eventsToRetry = getEventsToRetry(batchSize)
            
            if (eventsToRetry.isEmpty()) {
                logger.debug("No failed {} events to retry", serviceName)
                return
            }
            
            logger.info("Found {} failed {} events to retry", eventsToRetry.size, serviceName)
            
            var successCount = 0
            var failureCount = 0
            
            for (eventData in eventsToRetry) {
                try {
                    // Kiểm tra xem đã đủ thời gian để retry chưa
                    if (!shouldRetryNow(eventData)) {
                        continue
                    }
                    
                    val success = retryEvent(eventData)
                    if (success) {
                        successCount++
                        markAsProcessed(getEventId(eventData))
                        logger.info(
                            "Successfully retried {} event for {} (attempt {})",
                            serviceName, getEventIdentifier(eventData), getRetryCount(eventData) + 1
                        )
                    } else {
                        failureCount++
                        
                        // Cập nhật thông tin retry
                        updateRetryInfo(
                            getEventId(eventData),
                            getRetryCount(eventData) + 1,
                            "Retry failed at ${Date()}",
                        )
                        logger.warn(
                            "Failed to retry {} event for {} (attempt {})",
                            serviceName, getEventIdentifier(eventData), getRetryCount(eventData) + 1
                        )
                    }
                } catch (e: Throwable) {
                    failureCount++
                    
                    logger.error(
                        "Exception while retrying {} event for {}: {}",
                        serviceName, getEventIdentifier(eventData), e.message, e
                    )
                    
                    // Cập nhật thông tin retry với lỗi chi tiết
                    try {
                        updateRetryInfo(
                            getEventId(eventData),
                            getRetryCount(eventData) + 1,
                            "Exception during retry: ${e.message}",
                        )
                    } catch (storageException: Exception) {
                        logger.error(
                            "Failed to update retry info for {} event {}: {}",
                            serviceName, getEventId(eventData), storageException.message
                        )
                    }
                }
                
                // Thêm delay nhỏ giữa các retry để tránh quá tải
                delay(delayBetweenRetries)
            }
            
            logger.info(
                "Completed {} retry processing: {} successful, {} failed",
                serviceName, successCount, failureCount
            )
            
        } catch (e: Throwable) {
            logger.error("Error retrieving failed {} events for retry", serviceName, e)
        }
    }
    
    /**
     * Tính toán thời gian delay cho retry (exponential backoff)
     */
    protected fun calculateRetryDelay(retryCount: Int): Long {
        // Exponential backoff: 1min, 2min, 4min, 8min, 16min
        val baseDelayMinutes = 1
        val delayMinutes = (baseDelayMinutes * (2.0.pow(retryCount.toDouble()))).toLong()
        // Giới hạn tối đa 30 phút
        return minOf(delayMinutes, 30) * 60 * 1000
    }

    /**
     * Kiểm tra xem event có quá cũ không
     */
    protected fun isEventTooOld(createdAt: Date): Boolean {
        val eventAge = Duration.ofMillis(Date().time - createdAt.time)
        return eventAge.toHours() > maxEventAgeHours
    }
    
    /**
     * Kiểm tra xem đã vượt quá số lần retry tối đa chưa
     */
    protected fun hasExceededMaxRetries(retryCount: Int): Boolean {
        return retryCount >= maxRetryAttempts
    }
    
    /**
     * Lấy trạng thái của service
     */
    fun getStatus(): ServiceStatus {
        return ServiceStatus(
            serviceName = serviceName,
            running = isRunning.get(),
            retryIntervalMinutes = retryIntervalMinutes,
            maxRetryAttempts = maxRetryAttempts,
            maxEventAgeHours = maxEventAgeHours
        )
    }
    
    /**
     * Data class cho trạng thái service
     */
    data class ServiceStatus(
        val serviceName: String,
        val running: Boolean,
        val retryIntervalMinutes: Long,
        val maxRetryAttempts: Int,
        val maxEventAgeHours: Long
    )
}