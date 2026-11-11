package metaticket.ticketslots.kafka

import com.fasterxml.jackson.databind.ObjectMapper
import io.ktor.server.config.*
import kotlinx.coroutines.*
import metaticket.common.logger.Logging
import metaticket.order.datastructure.kafka.OrderSucceedEvent
import metaticket.ticketslots.model.FailedEventStatus
import metaticket.ticketslots.processor.OrderSucceedProcessor
import metaticket.ticketslots.dlq.storage.OrderSucceedEventConsumerDLQStorage
import org.apache.kafka.clients.consumer.ConsumerConfig
import org.apache.kafka.clients.consumer.KafkaConsumer
import org.apache.kafka.common.errors.WakeupException
import org.apache.kafka.common.serialization.StringDeserializer
import java.time.Duration
import java.util.*

/**
 *  ## OrderSucceedConsumer
 *
 *  This class is responsible for consuming `OrderSucceedEvent` messages from a Kafka topic,
 *  processing them using an `OrderSucceedProcessor`, and handling failures by storing failed
 *  events to a database-based Dead Letter Queue (DLQ) via `FailedOrderSucceedEventStorage`.
 *
 *  **Key Components:**
 *  - **Kafka Consumer:**  Subscribes to the configured Kafka topic for order success events.
 *  - **Coroutine Scope:** Manages the asynchronous consumption and processing of messages.
 *  - **Error Handling:** Includes robust error handling for message parsing and processing,
 *    with a mechanism to store failed events to database for retry processing.
 *  - **Graceful Shutdown:** Provides a `stop()` method to gracefully shut down the consumer,
 *    ensuring proper resource cleanup and preventing data loss.
 */
class OrderSucceedConsumer(
    private val config: ApplicationConfig,
    private val objectMapper: ObjectMapper,
    private val orderSucceedProcessor: OrderSucceedProcessor,
    private val failedEventStorage: OrderSucceedEventConsumerDLQStorage,
): Logging {
    private val bootstrapServers = config.property("kafka.bootstrapServers").getString()
    private val groupId = config.property("kafka.groupIdOrderSucceed").getString()
    private val topicOrderSucceed = config.property("kafka.topicOrderSucceed").getString()

    private val consumer: KafkaConsumer<String, String>
    private val job = SupervisorJob() // Use SupervisorJob for the scope
    private val scope = CoroutineScope(Dispatchers.IO + job)

    init {
        val consumerProps = Properties().apply {
            put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers)
            put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer::class.java.name)
            put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer::class.java.name)
            put(ConsumerConfig.GROUP_ID_CONFIG, groupId)
            put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest")
            // Disabling auto-commit, especially with DLQ to ensure messages are processed or DLQ'd before commit.
             put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, "false")
        }
        consumer = KafkaConsumer(consumerProps)
    }

    /**
     * Starts the Kafka consumer to listen for `OrderSucceedEvent` messages on the configured topic.
     * The consumer runs in a coroutine, continuously polling for new messages.  Each message is
     * parsed, processed by the `OrderSucceedProcessor`, and any processing errors result in the
     * failed event being stored to database for retry processing.
     */
    fun start() {
        scope.launch {
            try {
                consumer.subscribe(listOf(topicOrderSucceed))
                logger.info("OrderSucceedConsumer started, subscribed to topic: {}", topicOrderSucceed)
                while (isActive) {
                    val records = try {
                        consumer.poll(Duration.ofMillis(500))
                    } catch (wue: WakeupException) {
                        logger.info("KafkaConsumer poll awakened, initiating shutdown of consumer loop.")
                        break // Exit loop on wakeup
                    } catch (e: Throwable) {
                        logger.error("Exception during consumer.poll: ${e.message}. Retrying poll shortly.", e)
                        delay(1000) // Avoid tight loop on persistent poll errors
                        continue
                    }

                    if (!isActive && records.isEmpty) { // Loop termination check
                        break
                    }

                    for (record in records) {
                        if (!isActive) break // Check before processing each record

                        val event: OrderSucceedEvent = try {
                            val parsedEvent = objectMapper.readValue(record.value(), OrderSucceedEvent::class.java)
                            logger.info("Received OrderSucceedEvent for orderCode: {}", parsedEvent.orderCode)
                            parsedEvent
                        } catch (ex: Throwable) {
                            logger.error(
                                "Failed to parse OrderSucceedEvent. Raw: [${record.value()}]. Error: ${ex.message}. Storing to failed events database.",
                                ex
                            )
                            // Store parsing errors to database
                            try {
                                failedEventStorage.store(
                                    event = null, // Cannot parse event
                                    failureReason = "Failed to parse OrderSucceedEvent: ${ex.message}",
                                    errorType = "PARSING_ERROR",
                                    messageKey = record.key(),
                                    originalMessage = record.value(),
                                    status = FailedEventStatus.DEAD,
                                    sourceTopic = topicOrderSucceed
                                )
                            } catch (storageEx: Throwable) {
                                logger.error("Failed to store parsing error to database: ${storageEx.message}", storageEx)
                            }
                            continue // Skip this malformed record
                        }

                        try {
                            orderSucceedProcessor.process(event)
                        } catch (t: Throwable) {
                            // This catch block is for errors from orderSucceedProcessor.process
                            // The processor itself handles the "all duplicates" case by not throwing.
                            logger.error(
                                "Failed to process OrderSucceedEvent for orderCode {}: {}. Storing to failed events database.",
                                event.orderCode, t.message, t // Log with full exception
                            )
                            // Store processing errors to database
                            try {
                                failedEventStorage.store(
                                    event = event,
                                    failureReason = "Failed to process OrderSucceedEvent: ${t.message}",
                                    errorType = "PROCESSING_ERROR",
                                    messageKey = record.key(),
                                    originalMessage = record.value(),
                                    sourceTopic = topicOrderSucceed
                                )
                            } catch (storageEx: Throwable) {
                                logger.error("Failed to store processing error to database: ${storageEx.message}", storageEx)
                            }
                            // continue is implicit for the for-loop iteration
                        }
                    }
                    consumer.commitAsync()
                }
            } catch (e: Throwable) {
                // Catch other unexpected exceptions from the loop itself or setup (e.g., subscribe)
                logger.error("OrderSucceedConsumer loop unexpectedly terminated: ${e.message}", e)
            } finally {
                logger.info("OrderSucceedConsumer processing loop finished.")
                // Actual closing of consumer and producer is primarily handled in stop()
            }
        }
    }

    /**
     * Stops the Kafka consumer gracefully. This involves:
     * 1. Cancelling the coroutine scope to signal the consumer loop to exit.
     * 2. Waking up the Kafka consumer to interrupt any ongoing poll operations.
     * 3. Waiting for the coroutine job to complete, ensuring all resources are released.
     * 4. Closing the Kafka consumer to release its resources and connections.
     *
     */
    fun stop() {
        logger.info("Attempting to stop OrderSucceedConsumer...")

        // 1. Signal coroutines to cancel and wake up consumer
        if (scope.isActive) {
            scope.cancel() // This will set isActive to false in the loop
        }
        consumer.wakeup()  // Interrupts consumer.poll()

        // 2. Wait for the coroutine job to complete (good for graceful shutdown)
         runBlocking { // Be careful with runBlocking on main/sensitive threads
             try {
                withTimeout(60000) { // Timeout for job completion
                    job.join()
                    logger.info("Consumer coroutine job completed.")
                }
            } catch (e: TimeoutCancellationException) {
                logger.warn("Timeout waiting for consumer coroutine job to complete.")
            } catch (e: Throwable) {
                logger.error("Error waiting for consumer coroutine job to complete: ${e.message}", e)
            }
         }

        // 3. Close Kafka consumer
        logger.info("Closing KafkaConsumer...")
        try {
            // Consumer.close() should be called after the poll loop has exited.
            // WakeupException should have caused the loop to terminate.
            consumer.close(Duration.ofSeconds(10)) // Close with timeout
            logger.info("KafkaConsumer closed.")
        } catch (e: Throwable) {
            // Ignore WakeupException if it's thrown again by close, though unlikely
            if (e !is WakeupException) {
                logger.error("Error closing KafkaConsumer: ${e.message}", e)
            }
        }
        logger.info("OrderSucceedConsumer stop process completed.")
    }
}