package metaticket.ticketslots.processor

import io.ktor.util.*
import metaticket.common.checksum.Checksum
import metaticket.common.logger.Logging
import metaticket.ticketslots.configuration.Environment
import metaticket.ticketslots.constants.prefixSlotQRCode
import metaticket.ticketslots.dbservice.TicketSlotDatabaseService
import metaticket.ticketslots.kafka.GenerateTicketSlotQRCodeEvent
import qrcode.QRCode

/**
 * Processor for generating QR codes for ticket slots.
 */
class GenerateTicketSlotQRCodeProcessor constructor(
    private val db: TicketSlotDatabaseService,
) : Logging {

    /**
     * Processes a [GenerateTicketSlotQRCodeEvent] to generate and store a QR code for a ticket slot.
     *
     * This function retrieves a ticket slot document from the database based on the provided event data.
     * It then generates a QR code containing a checksum of the slot's ID and current state index,
     * and updates the database with the generated QR code.
     */
    suspend fun process(event: GenerateTicketSlotQRCodeEvent) {
        logger.info("Processing GenerateTicketSlotQRCodeEvent {}", event)
        val document = db.findByIdWithCurrentStateOnly(event.slotId) ?: run {
            logger.error("Failed to find slot with ID: {}", event.slotId)
            return
        }
        if (document.currentStateIdx != event.stateIdx) {
            logger.warn("Failed to process slot with ID: {}, but not correct state idx {} : {}", event.slotId, event.stateIdx, document.currentStateIdx)
            return
        }
        val verifyCode = Checksum.encode("${document.id}:${document.currentStateIdx}", Environment.qrcodeSecretKey)
        val squareQRCode = QRCode.ofSquares().withSize(5).withInnerSpacing(0)
            .build("$prefixSlotQRCode$verifyCode")
        val squarePngData = squareQRCode.renderToBytes()
        val qrcodeString = squarePngData.encodeBase64()

        db.setQRCode(document.id, document.currentStateIdx, qrcodeString)

        logger.info("Successfully processed GenerateTicketSlotQRCodeEvent {}", event)
    }
}