package metaticket.ticketslots.processor

import com.mongodb.MongoBulkWriteException
import metaticket.common.logger.Logging
import metaticket.order.datastructure.kafka.OrderSucceedEvent
import metaticket.ticketslots.dbservice.TicketSlotDatabaseService
import metaticket.ticketslots.kafka.GenerateTicketSlotQRCodeEvent
import metaticket.ticketslots.kafka.GenerateTicketSlotQRCodeProducer
import metaticket.ticketslots.pojo.db.StateReason
import metaticket.ticketslots.pojo.db.TicketSlotDoc
import metaticket.ticketslots.pojo.db.TicketSlotOptStatus
import metaticket.ticketslots.pojo.db.TicketSlotState
import metaticket.ticketslots.pojo.db.TicketSlotStatus
import org.bson.types.ObjectId
import java.util.*

/**
 * Processes an OrderSucceedEvent, creating ticket slots in the database and triggering QR code generation.
 */
class OrderSucceedProcessor constructor(
    private val db: TicketSlotDatabaseService,
    private val generateTicketSlotQRCodeProducer: GenerateTicketSlotQRCodeProducer,
): Logging {

    /**
     * Processes an [OrderSucceedEvent] by creating ticket slots in the database and triggering QR code generation for the newly created slots.
     */
    suspend fun process(event: OrderSucceedEvent) {
        logger.info("Processing OrderSucceedEvent for orderCode: {}", event.orderCode)
        val now = Date()
        val docs = event.ticketInfo.flatMap { ticket ->
            (0 until ticket.quantity).flatMap { ticketIdx ->
                (0 until ticket.ticketSlots).mapNotNull { slotIdx ->
                    val initialState = TicketSlotState(
                        stateIdx = 0,
                        ownerId = event.ownerId,
                        status = TicketSlotStatus.CREATED,
                        reason = StateReason.INITIAL_PURCHASE,
                        timestamp = now
                        // qrcodeString is null by default
                    )
                    TicketSlotDoc(
                        id = ObjectId().toHexString(),
                        showId = event.showId,
                        sessionId = event.sessionId,
                        orderId = event.orderId,
                        orderCode = event.orderCode,
                        ticketId = ticket.ticketId,
                        priceCat = ticket.priceCat,
                        sid = ticket.sid,
                        ticketIdx = ticketIdx + 1,
                        slotIdx = slotIdx + 1,
                        originalOwnerId = event.ownerId,
                        states = listOf(initialState),
                        currentOwnerId = initialState.ownerId,
                        currentStatus = initialState.status,
                        currentStateIdx = initialState.stateIdx,
                        visitDate = event.visitDate,
                        // Vé theo lịch bán quanh năm, operator không mở tay từng ngày được: slot mở sẵn, cửa soát
                        // vé đã chặn theo ngày tham quan.
                        optStatus = if (event.visitDate != null) TicketSlotOptStatus.OPENED else TicketSlotOptStatus.NONE,
                        createdAt = now,
                        lastModified = now
                    )
                }
            }
        }

        if (docs.isEmpty()) {
            logger.info("No ticket slots to create for orderCode: {}. Skipping database operation.", event.orderCode)
            return
        }

        var newlyCreatedDocs: List<TicketSlotDoc> = emptyList() // Initialize to empty

        try {
            db.createTicketSlots(docs)
            logger.info("Successfully created {} ticket slots for orderCode: {}", docs.size, event.orderCode)
            newlyCreatedDocs = docs // All documents were successfully inserted
        } catch (ex: MongoBulkWriteException) {
            val writeErrors = ex.writeErrors
            val totalDocsAttempted = docs.size

            // 1. Handle critical WriteConcernError first
            if (ex.writeConcernError != null) {
                logger.error(
                    "Failed to create ticket slots for orderCode {} due to WriteConcernError: {}. " +
                            "Total docs attempted: {}. No events will be sent.",
                    event.orderCode,
                    ex.writeConcernError?.message ?: "Unknown WriteConcernError",
                    totalDocsAttempted,
                    ex // Log the full exception
                )
                throw ex // Re-throw for critical write concern errors
            }

            // 2. Handle case where MongoBulkWriteException is thrown but no writeErrors (unusual but safeguard)
            if (writeErrors.isEmpty()) {
                // This scenario is unlikely if MongoBulkWriteException is the specific exception type
                // for insertMany failures, as it should contain writeErrors.
                logger.error(
                    "MongoBulkWriteException thrown for orderCode {} but no write errors reported. " +
                            "Message: {}. Total docs attempted: {}. No events will be sent.",
                    event.orderCode,
                    ex.message,
                    totalDocsAttempted,
                    ex
                )
                throw ex // Re-throw as the state is unclear or a more general DB issue
            }

            // 3. Differentiate based on error types: are all errors just duplicates?
            val allReportedErrorsAreDuplicates = writeErrors.all { it.code == 11000 } // MongoDB duplicate key error code

            if (allReportedErrorsAreDuplicates) {
                // All *reported* errors are duplicate key errors.
                // This means some documents might have been inserted successfully if not all docs failed with this error.
                val failedIndexes = writeErrors.map { it.index }.toSet()
                newlyCreatedDocs = docs.filterIndexed { index, _ -> index !in failedIndexes }

                if (newlyCreatedDocs.isEmpty()) {
                    // This implies all documents attempted resulted in a duplicate key error.
                    logger.warn(
                        "All {} documents for orderCode {} were duplicates (error 11000). " +
                                "No new documents were inserted.",
                        totalDocsAttempted, event.orderCode
                    )
                } else {
                    // Some documents were duplicates, but others were newly inserted.
                    logger.warn(
                        "{} out of {} documents for orderCode {} failed due to duplicate key errors (11000). " +
                                "{} documents were successfully inserted and will have events sent.",
                        writeErrors.size, totalDocsAttempted, event.orderCode, newlyCreatedDocs.size
                    )
                }
                // Log details of duplicate errors for auditing/debugging, even if some succeeded
                writeErrors.forEach { error ->
                    logger.warn(
                        "Duplicate Error Detail for orderCode {}: Index in batch: {}, Code: {}, Message: '{}', Offending document ID (attempted): {}",
                        event.orderCode, error.index, error.code, error.message,
                        docs.getOrNull(error.index)?.id ?: "N/A"
                    )
                }
                // IMPORTANT: Do NOT re-throw the exception here.
                // We have successfully identified the `newlyCreatedDocs` and want to proceed to send events for them.
            } else {
                // There are errors other than (or in addition to) duplicate key errors.
                // This is considered a more general/severe failure for the batch.
                // `newlyCreatedDocs` will remain `emptyList()` (its initial value).
                logger.error(
                    "Failed to create ticket slots for orderCode {} due to MongoBulkWriteException with non-duplicate or mixed errors. " +
                            "Total docs attempted: {}, WriteErrors count: {}. WriteConcernError: {}. No events will be sent for this batch.",
                    event.orderCode,
                    totalDocsAttempted,
                    writeErrors.size,
                    ex.writeConcernError?.message ?: "None", // Already checked but good for complete log
                    ex // Log the full exception
                )
                // Log individual write errors for more detail
                writeErrors.forEach { error ->
                    logger.error(
                        "WriteError Detail for orderCode {}: Index in batch: {}, Code: {}, Message: '{}', Offending document ID (attempted): {}",
                        event.orderCode, error.index, error.code, error.message,
                        docs.getOrNull(error.index)?.id ?: "N/A"
                    )
                }
                throw ex // Re-throw the exception to be handled by the caller (e.g., consumer for DLQ)
            }
        }

        // Send events only for documents that were newly and successfully inserted
        if (newlyCreatedDocs.isNotEmpty()) {
            val qrCodeEvents = newlyCreatedDocs.map { GenerateTicketSlotQRCodeEvent(it.id, it.currentStateIdx) }
            logger.info("Sending {} GenerateTicketSlotQRCodeEvent(s) for newly created ticket slots for orderCode: {}", qrCodeEvents.size, event.orderCode)
            generateTicketSlotQRCodeProducer.sendEvents(qrCodeEvents)
            logger.info(
                "Successfully sent {} GenerateTicketSlotQRCodeEvent(s) for newly created ticket slots for orderCode: {}",
                qrCodeEvents.size, event.orderCode
            )
        } else {
            // This block is reached if newlyCreatedDocs is empty AND no exception was re-thrown from this processor.
            // This implies that either:
            //   a) `docs` was initially empty (handled by early return, so this log won't run for that specific reason).
            //   b) All documents attempted resulted in duplicate key errors (and `allReportedErrorsAreDuplicates` was true, leading to `newlyCreatedDocs` being empty).
            if (docs.isNotEmpty()) { // Ensure this log is for cases where docs were actually attempted
                logger.info(
                    "No new ticket slots were inserted for orderCode: {} (likely all attempted documents were duplicates). " +
                            "No GenerateTicketSlotQRCodeEvents sent.",
                    event.orderCode
                )
            }
            // If docs was empty initially, the early return `logger.info("No ticket slots to create...")` already covered it.
        }
    }
}