package metaticket.order.kafka

import com.fasterxml.jackson.databind.ObjectMapper
import io.ktor.server.config.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import metaticket.common.logger.Logging
import metaticket.order.cache.ShowCache
import metaticket.order.datastructure.kafka.OrderSucceedEvent
import metaticket.order.datastructure.kafka.TicketInfoDTO
import metaticket.order.pojo.db.OrderDocument
import metaticket.order.storage.FailedOrderSucceedEventStorage
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerConfig
import org.apache.kafka.clients.producer.ProducerRecord
import org.apache.kafka.common.serialization.StringSerializer
import java.time.Duration
import java.time.Instant
import java.util.*

class OrderSucceedProducer constructor(
    private val config: ApplicationConfig,
    private val objectMapper: ObjectMapper,
    private val showCache: ShowCache,
    private val failedEventStorage: FailedOrderSucceedEventStorage,
) : Logging {
    private val kafkaBootstrapServers: String = config.property("kafka.bootstrapServers").getString()
    private val kafkaOrderSucceedTopic: String = config.property("kafka.topicOrderSucceed").getString()

    private val producer: KafkaProducer<String, String>

    private fun createProducerProperties(): Properties {
        return Properties().apply {
            put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, kafkaBootstrapServers)
            put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer::class.java.name)
            put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer::class.java.name)
            put(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, "true")
            put(ProducerConfig.ACKS_CONFIG, "all")
            put(ProducerConfig.RETRIES_CONFIG, Integer.MAX_VALUE.toString())
            put(ProducerConfig.MAX_IN_FLIGHT_REQUESTS_PER_CONNECTION, "5")
            put(ProducerConfig.DELIVERY_TIMEOUT_MS_CONFIG, (120 * 1000).toString())
            put(ProducerConfig.LINGER_MS_CONFIG, "20")
            put(ProducerConfig.BATCH_SIZE_CONFIG, (32 * 1024).toString())
            put(ProducerConfig.COMPRESSION_TYPE_CONFIG, "snappy")
        }
    }

    init {
        try {
            val props = createProducerProperties()
            producer = KafkaProducer(props)
            logger.info(
                "OrderSucceedProducer started. Producing to topic: {}",
                kafkaOrderSucceedTopic
            )
        } catch (e: Throwable) {
            logger.error("Failed to start OrderSucceedProducer: ${e.message}", e)
            throw RuntimeException("Failed to initialize OrderSucceedProducer", e)
        }
    }

    suspend fun scheduleOrSendOrderSucceedEvent(order: OrderDocument) = metaticket.common.coroutine.launch {
        val event = OrderSucceedEvent(
            showId = order.showId,
            sessionId = order.sessionId,
            orderId = order.id,
            reservationId = order.reservationId,
            orderCode = order.code,
            ownerId = order.customerUserId!!,
            ticketInfo = order.tickets.map { ticket ->
                TicketInfoDTO(
                    ticketId = ticket.id,
                    sid = ticket.sid,
                    seatType = ticket.seatType.name,
                    quantity = ticket.quantity,
                    priceCat = ticket.priceCat,
                    ticketSlots = ticket.ticketSlots ?: 1 // default slot per ticket is 1 if not configured
                )
            },
            createdAt = System.currentTimeMillis(),
            lastModified = System.currentTimeMillis(),
            visitDate = order.visitDate,
        )

        if (event.ticketInfo.isEmpty()) {
            logger.warn("TicketInfo is empty, not properly initialized.")
            if (order.tickets.isNotEmpty()) {
                logger.warn(
                    "OrderSucceedEvent for orderCode {} resulted in no ticketInfo items, " +
                            "though the original order had tickets. This might indicate missing data in all tickets.",
                    event.orderCode
                )
            }
            return@launch Unit
        }

        try {
            val showSession = showCache.getShowSession(order.showId, order.sessionId) ?: run {
                logger.error("Failed to get show session for order {} (showId: {}, sessionId: {}).", order.id, order.showId, order.sessionId)
                try {
                    val eventId = failedEventStorage.store(
                        event = event,
                        failureReason = "Kafka send failed: not found show session"
                    )
                    logger.info("Stored failed order succeed event with ID: $eventId for retry")
                } catch (storageException: Throwable) {
                    logger.error("Failed to store order succeed event for retry", storageException)
                }
                return@launch Unit
            }
            if (!showSession.hasManageTicket() || !showSession.manageTicket) return@launch Unit

            val recordValue = objectMapper.writeValueAsString(event)
            val record = ProducerRecord(
                kafkaOrderSucceedTopic,
                order.id,
                recordValue
            )

            logger.debug("Attempting to send order succeed event for orderCode: {}", event.orderCode)

            // The Kafka producer's send method is already asynchronous.
            // We don't need to wrap this in scope.launch unless we want to manage
            // the callback or potential exceptions from send() within the producer's scope explicitly.
            // For now, keeping it direct as the Kafka client handles its own background I/O for sending.
            producer.send(record) { metadata, exception ->
                if (exception != null) {
                    logger.error(
                        "Failed to send order succeed event for orderCode: {}. Error: {}",
                        event.orderCode,
                        exception.message,
                        exception
                    )
                    
                    // Store failed event for retry using coroutine
                    CoroutineScope(Dispatchers.IO).launch {
                        try {
                            val eventId = failedEventStorage.store(
                                event = event,
                                failureReason = "Kafka send failed: ${exception.message}"
                            )
                            logger.info("Stored failed order succeed event with ID: $eventId for retry")
                        } catch (storageException: Throwable) {
                            logger.error("Failed to store order succeed event for retry", storageException)
                        }
                    }
                } else {
                    logger.info(
                        "Successfully sent order succeed event for orderCode: {}. Topic: {}, Partition: {}, Offset: {}",
                        event.orderCode,
                        metadata.topic(),
                        metadata.partition(),
                        metadata.offset()
                    )
                }
            }
        } catch (e: Throwable) {
            logger.error(
                "Exception during preparation or synchronous part of sending order succeed event for orderCode: {}. Error: {}",
                event.orderCode,
                e.message,
                e
            )
        }
    }

    /**
     * Retry failed events from storage
     */
    suspend fun retryFailedEvents(limit: Int = 100) {
        try {
            val failedEvents = failedEventStorage.retrieveEventsForRetry(limit)
            logger.info("Retrieved ${failedEvents.size} failed events for retry")
            
            for (failedEvent in failedEvents) {
                try {
                    val record = ProducerRecord(
                        kafkaOrderSucceedTopic, 
                        failedEvent.event.orderId, 
                        objectMapper.writeValueAsString(failedEvent.event)
                    )
                    
                    // Send synchronously for retry to get immediate result
                    withContext(Dispatchers.IO) {
                        producer.send(record).get()
                    }
                    
                    // If successful, mark as processed
                    failedEventStorage.markAsProcessed(failedEvent.id)
                    logger.info("Successfully retried order succeed event ${failedEvent.event.orderId}, removed from storage")
                    
                } catch (retryException: Exception) {
                    val newRetryCount = failedEvent.retryCount + 1

                    // Update retry info for next attempt
                    val nextRetryTime = calculateNextRetryTime(newRetryCount)
                    failedEventStorage.updateRetryInfo(
                        eventId = failedEvent.id,
                        newFailureReason = "Retry failed: ${retryException.message}",
                        nextRetryAt = nextRetryTime
                    )
                    logger.warn("Retry failed for order succeed event ${failedEvent.event.orderId}, scheduled for next retry at $nextRetryTime")
                }
            }
        } catch (exception: Throwable) {
            logger.error("Error during retry process", exception)
        }
    }
    
    /**
     * Calculate next retry time using exponential backoff
     */
    private fun calculateNextRetryTime(retryCount: Int): Instant {
        val baseDelaySeconds = config.propertyOrNull("kafka.orderSucceedRetry.baseDelaySeconds")?.getString()?.toLongOrNull() ?: 30L
        val maxDelaySeconds = config.propertyOrNull("kafka.orderSucceedRetry.maxDelaySeconds")?.getString()?.toLongOrNull() ?: 3600L
        
        val delaySeconds = minOf(
            baseDelaySeconds * (1L shl retryCount), // Exponential backoff
            maxDelaySeconds
        )
        
        return Instant.now().plusSeconds(delaySeconds)
    }
    
    /**
     * Clean up old failed events
     */
    suspend fun cleanupOldEvents() {
        try {
            val maxRetryCount = config.propertyOrNull("kafka.orderSucceedRetry.maxRetryCount")?.getString()?.toIntOrNull() ?: 10
            val cleanupDays = config.propertyOrNull("kafka.orderSucceedRetry.cleanupOldEventsAfterDays")?.getString()?.toLongOrNull() ?: 7L
            val maxAge = Instant.now().minusSeconds(cleanupDays * 24 * 3600)
            
            val deletedCount = failedEventStorage.cleanupOldEvents(maxAge, maxRetryCount)
            if (deletedCount > 0) {
                logger.info("Cleaned up $deletedCount old failed events")
            }
        } catch (exception: Throwable) {
            logger.error("Error during cleanup process", exception)
        }
    }
    
    /**
     * Gửi một OrderSucceedEvent trực tiếp lên Kafka (dùng cho manual retry)
     * @param event OrderSucceedEvent cần gửi
     * @return RecordMetadata nếu thành công
     * @throws Exception nếu gửi thất bại
     */
    suspend fun sendEventDirectly(event: OrderSucceedEvent): org.apache.kafka.clients.producer.RecordMetadata {
        val recordValue = objectMapper.writeValueAsString(event)
        val record = ProducerRecord(
            kafkaOrderSucceedTopic,
            event.orderId,
            recordValue
        )
        
        // Gửi synchronously để có kết quả ngay
        return withContext(Dispatchers.IO) {
            producer.send(record).get()
        }
    }

    fun stop() {
        logger.info("Stopping OrderSucceedProducer...")

        try {
            logger.info("Flushing pending messages in OrderSucceedProducer...")
            producer.flush()
            logger.info("Closing OrderSucceedProducer...")
            producer.close(Duration.ofSeconds(10)) // Kafka producer close is blocking
            logger.info("OrderSucceedProducer Kafka client closed successfully.")
        } catch (e: Throwable) {
            logger.error("Error while stopping OrderSucceedProducer's Kafka client: ${e.message}", e)
        }

        logger.info("OrderSucceedProducer stop process completed.")
    }
}