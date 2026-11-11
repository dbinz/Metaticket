package metaticket.ticketslots.kafka

import com.fasterxml.jackson.databind.ObjectMapper
import io.ktor.server.config.*
import kotlinx.coroutines.*
import metaticket.common.logger.Logging
import metaticket.ticketslots.processor.GenerateTicketSlotQRCodeProcessor
import metaticket.ticketslots.dlq.storage.QRCodeEventConsumerDLQStorage
import metaticket.ticketslots.model.FailedEventStatus
import org.apache.kafka.clients.consumer.ConsumerConfig
import org.apache.kafka.clients.consumer.KafkaConsumer
import org.apache.kafka.common.errors.WakeupException
import org.apache.kafka.common.serialization.StringDeserializer
import java.time.Duration
import java.util.*

/** Consumes messages from the GenerateTicketSlotQRCode topic and processes them to generate QR codes for ticket slots.
 *
 */
class GenerateTicketSlotQRCodeConsumer(
    private val config: ApplicationConfig,
    private val objectMapper: ObjectMapper,
    private val processor: GenerateTicketSlotQRCodeProcessor,
    private val qrCodeConsumerDLQStorage: QRCodeEventConsumerDLQStorage
): Logging {
    private val bootstrapServers = config.property("kafka.bootstrapServers").getString()
    private val groupId = config.property("kafka.groupIdGenerateTicketSlotQRCode").getString()
    private val topicGenerateTicketSlotQRCode = config.property("kafka.topicGenerateTicketSlotQRCode").getString()

    private val consumer: KafkaConsumer<String, String>
    private val job = SupervisorJob()
    private val scope = CoroutineScope(Dispatchers.IO + job)

    init {
        val consumerProps = Properties().apply {
            put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers)
            put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer::class.java.name)
            put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer::class.java.name)
            put(ConsumerConfig.GROUP_ID_CONFIG, groupId)
            put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest")
            put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, "false") // Manual commit
        }
        consumer = KafkaConsumer(consumerProps)
    }

    /**
     * Starts the consumer, subscribing to the configured topic and processing incoming messages.
     */
    fun start() {
        scope.launch {
            try {
                consumer.subscribe(listOf(topicGenerateTicketSlotQRCode))
                logger.info("GenerateTicketSlotQRCodeConsumer started, subscribed to topic: {}", topicGenerateTicketSlotQRCode)
                while (isActive) {
                    val records = try {
                        consumer.poll(Duration.ofMillis(500))
                    } catch (wue: WakeupException) {
                        logger.info("GenerateTicketSlotQRCodeConsumer poll awakened, initiating shutdown.")
                        break
                    } catch (e: Throwable) {
                        logger.error("Exception during consumer.poll for GenerateTicketSlotQRCode: ${e.message}. Retrying poll shortly.", e)
                        delay(1000)
                        continue
                    }

                    if (!isActive && records.isEmpty) break

                    for (record in records) {
                        if (!isActive) break

                        val event: GenerateTicketSlotQRCodeEvent
                        try {
                            event = objectMapper.readValue(record.value(), GenerateTicketSlotQRCodeEvent::class.java)
                            logger.info("Received GenerateTicketSlotQRCodeEvent for slotId: {}", event.slotId)
                        } catch (ex: Throwable) {
                            logger.error(
                                "Failed to parse GenerateTicketSlotQRCodeEvent. Raw: [${record.value()}]. Error: ${ex.message}. Storing to database.",
                                ex
                            )
                            try {
                                qrCodeConsumerDLQStorage.store(
                                event = null,
                                originalMessage = record.value(),
                                sourceTopic = topicGenerateTicketSlotQRCode,
                                messageKey = record.key(),
                                failureReason = "${ex.javaClass.simpleName}: ${ex.message}",
                                errorType = "PARSING_ERROR",
                                status = FailedEventStatus.DEAD
                            )
                            } catch (storageEx: Throwable) {
                                logger.error("Failed to store parsing error to database: ${storageEx.message}", storageEx)
                            }
                            continue // Skip malformed record
                        }

                        try {
                            processor.process(event)
                            logger.info("Successfully processed GenerateTicketSlotQRCodeEvent for slotId: {}", event.slotId)
                        } catch (t: Throwable) {
                            logger.error(
                                "Failed to process GenerateTicketSlotQRCodeEvent for slotId {}: {}. Storing to database.",
                                event.slotId, t.message, t
                            )
                            try {
                                qrCodeConsumerDLQStorage.store(
                                event = event,
                                originalMessage = record.value(),
                                sourceTopic = topicGenerateTicketSlotQRCode,
                                messageKey = record.key(),
                                failureReason = "${t.javaClass.simpleName}: ${t.message}",
                                errorType = "PROCESSING_ERROR"
                            )
                            } catch (storageEx: Exception) {
                                logger.error("Failed to store processing error to database: ${storageEx.message}", storageEx)
                            }
                            // continue is implicit
                        }
                    }
                    if (!records.isEmpty) { // Only commit if records were processed or attempted
                        consumer.commitAsync { offsets, exception ->
                            if (exception != null) {
                                logger.error("Async commit failed for offsets $offsets ... ", exception)
                            } else {
                                logger.debug("Async commit succeeded for offsets {}", offsets)
                            }
                        }
                    }
                }
            } catch (e: Throwable) {
                logger.error("GenerateTicketSlotQRCodeConsumer loop unexpectedly terminated: ${e.message}", e)
            } finally {
                logger.info("GenerateTicketSlotQRCodeConsumer processing loop finished.")
            }
        }
    }

    /**
     * Stops the consumer gracefully, closing resources and waiting for the processing job to complete.
     */
    fun stop() {
        logger.info("Attempting to stop GenerateTicketSlotQRCodeConsumer...")
        if (scope.isActive) {
            scope.cancel()
        }
        consumer.wakeup()

        runBlocking {
            try {
                withTimeout(10000) { // Shorter timeout for QR code consumer job
                    job.join()
                    logger.info("GenerateTicketSlotQRCodeConsumer coroutine job completed.")
                }
            } catch (e: TimeoutCancellationException) {
                logger.warn("Timeout waiting for GenerateTicketSlotQRCodeConsumer coroutine job to complete.")
            } catch (e: Throwable) {
                logger.error("Error waiting for GenerateTicketSlotQRCodeConsumer coroutine job to complete: ${e.message}", e)
            }
        }

        logger.info("Closing KafkaConsumer for GenerateTicketSlotQRCode...")
        try {
            consumer.close(Duration.ofSeconds(10))
            logger.info("KafkaConsumer for GenerateTicketSlotQRCode closed.")
        } catch (e: Throwable) {
            if (e !is WakeupException) {
                logger.error("Error closing KafkaConsumer for GenerateTicketSlotQRCode: ${e.message}", e)
            }
        }
        logger.info("GenerateTicketSlotQRCodeConsumer stop process completed.")
    }
}