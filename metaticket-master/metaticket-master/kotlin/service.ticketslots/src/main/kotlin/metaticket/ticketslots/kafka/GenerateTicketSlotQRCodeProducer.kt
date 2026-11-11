package metaticket.ticketslots.kafka

import com.fasterxml.jackson.databind.ObjectMapper
import io.ktor.server.config.*
import metaticket.common.logger.Logging
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerConfig
import org.apache.kafka.clients.producer.ProducerRecord
import org.apache.kafka.common.serialization.StringSerializer
import java.time.Duration
import java.util.*
import java.util.concurrent.TimeUnit

/**
 * Producer for sending [GenerateTicketSlotQRCodeEvent] to Kafka.
 */
class GenerateTicketSlotQRCodeProducer(
    private val config: ApplicationConfig,
    private val objectMapper: ObjectMapper
): Logging {
    private val bootstrapServers = config.property("kafka.bootstrapServers").getString()
    private val topicGenerateTicketSlotQRCode = config.property("kafka.topicGenerateTicketSlotQRCode").getString()

    private var producer: KafkaProducer<String, String>

    init {
        try {
            val producerProps = Properties().apply {
                put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers)
                put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer::class.java.name)
                put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer::class.java.name) // Event will be serialized to JSON String
                
                // Reliability configurations
                put(ProducerConfig.ACKS_CONFIG, "all") // Wait for all replicas to acknowledge
                put(ProducerConfig.RETRIES_CONFIG, 5) // Increased from 3 to 5
                put(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, true) // Prevent duplicate messages
                
                // Timeout configurations
                put(ProducerConfig.REQUEST_TIMEOUT_MS_CONFIG, 30000) // 30 seconds
                put(ProducerConfig.DELIVERY_TIMEOUT_MS_CONFIG, 120000) // 2 minutes total delivery timeout
                put(ProducerConfig.RETRY_BACKOFF_MS_CONFIG, 1000) // 1 second between retries
                
                // Performance optimizations
                put(ProducerConfig.BATCH_SIZE_CONFIG, 16384) // 16KB batch size
                put(ProducerConfig.LINGER_MS_CONFIG, 10) // Wait up to 10ms to batch messages
                put(ProducerConfig.COMPRESSION_TYPE_CONFIG, "snappy") // Enable compression
                put(ProducerConfig.BUFFER_MEMORY_CONFIG, 33554432) // 32MB buffer
                
                // Connection configurations
                put(ProducerConfig.CONNECTIONS_MAX_IDLE_MS_CONFIG, 540000) // 9 minutes
                put(ProducerConfig.MAX_IN_FLIGHT_REQUESTS_PER_CONNECTION, 1) // Ensure ordering with idempotence
            }
            producer = KafkaProducer(producerProps)
            logger.info("GenerateTicketSlotQRCodeProducer initialized for topic: {}", topicGenerateTicketSlotQRCode)
        } catch (e: Throwable) {
            logger.error("Failed to initialize GenerateTicketSlotQRCodeProducer: ${e.message}", e)
            throw e
        }
    }

    /**
     * Sends a list of [GenerateTicketSlotQRCodeEvent] to the Kafka topic.
     */
    fun sendEvents(events: List<GenerateTicketSlotQRCodeEvent>) {
        events.forEach { event -> sendEvent(event) }
    }

    /**
     * Sends a single [GenerateTicketSlotQRCodeEvent] to the Kafka topic.
     */
    fun sendEvent(event: GenerateTicketSlotQRCodeEvent) {
        try {
            val eventJson = objectMapper.writeValueAsString(event)
            // Using slotId as key for potential partitioning
            val record = ProducerRecord(topicGenerateTicketSlotQRCode, event.slotId, eventJson)

            producer.send(record) { metadata, exception ->
                if (exception != null) {
                    logger.error(
                        "Failed to send GenerateTicketSlotQRCodeEvent for slotId {}: {}",
                        event.slotId, exception.message, exception
                    )
                } else {
                    logger.info(
                        "Successfully sent GenerateTicketSlotQRCodeEvent for slotId {}. Topic: {}, Partition: {}, Offset: {}",
                        event.slotId, metadata.topic(), metadata.partition(), metadata.offset()
                    )
                }
            }
        } catch (e: Throwable) {
            logger.error(
                "Error serializing or sending GenerateTicketSlotQRCodeEvent for slotId {}: {}",
                event.slotId, e.message, e
            )
            throw e
        }
    }
    
    /**
     * Sends a single event synchronously with timeout.
     * This method blocks until the send completes or times out.
     * 
     * @param event The event to send
     * @param timeoutMs Timeout in milliseconds (default: 30 seconds)
     * @return true if sent successfully, false otherwise
     */
    fun sendEventSync(event: GenerateTicketSlotQRCodeEvent, timeoutMs: Long = 30000): Boolean {
        return try {
            val eventJson = objectMapper.writeValueAsString(event)
            val record = ProducerRecord(topicGenerateTicketSlotQRCode, event.slotId, eventJson)
            
            val future = producer.send(record)
            val metadata = future.get(timeoutMs, TimeUnit.MILLISECONDS)
            
            logger.info(
                "Successfully sent GenerateTicketSlotQRCodeEvent for slotId {} synchronously. Topic: {}, Partition: {}, Offset: {}",
                event.slotId, metadata.topic(), metadata.partition(), metadata.offset()
            )
            true
        } catch (e: Exception) {
            logger.error(
                "Failed to send GenerateTicketSlotQRCodeEvent for slotId {} synchronously: {}",
                event.slotId, e.message, e
            )
            false
        }
    }

    /**
     * Flushes any pending records to ensure they are sent.
     */
    fun flush() {
        try {
            producer.flush()
            logger.debug("GenerateTicketSlotQRCodeProducer flushed successfully")
        } catch (e: Exception) {
            logger.error("Error flushing GenerateTicketSlotQRCodeProducer: ${e.message}", e)
        }
    }
    
    /**
     * Stops the producer, flushing any pending records and closing the connection.
     */
    fun stop() {
        logger.info("Attempting to stop GenerateTicketSlotQRCodeProducer...")
        try {
            producer.flush()
            producer.close(Duration.ofSeconds(10))
            logger.info("GenerateTicketSlotQRCodeProducer closed.")
        } catch (e: Exception) {
            logger.error("Error closing GenerateTicketSlotQRCodeProducer: ${e.message}", e)
        }
        logger.info("GenerateTicketSlotQRCodeProducer stop process completed.")
    }
}