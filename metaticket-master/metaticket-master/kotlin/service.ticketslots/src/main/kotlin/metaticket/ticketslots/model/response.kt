package metaticket.ticketslots.model

import metaticket.common.checksum.Checksum
import metaticket.ticketslots.configuration.Environment
import metaticket.ticketslots.pojo.db.*

data class CaptchaSettingResponse(
    val enable: Boolean,
    val captchaGateway: String,
    val siteKey: String
)

data class TicketSlotStateResponse(
    val stateIdx: Int,                  // The index of state in list of state of ticket slot
    val ownerId: String? = null,        // User ID of the owner during this state (nullable for none register user)
    val ownerPhone: String? = null,     // Phone number of the owner during this state (nullable for none registered user)
    val status: TicketSlotStatus,       // The status of the slot during this state
    val reason: StateReason,            // The reason this state was created/changed
    val senderId: String? = null,       // User ID of the sender (if applicable)
    val recipientId: String? = null,    // User ID of the recipient (if applicable)
    val timestamp: Long,                // Timestamp when this state changed
)

data class TicketSlotResponse(
    val id: String,                 // Unique identifier for this ticket slot document
    val showId: String,             // ID of the show
    val sessionId: String,          // ID of the specific session/performance
    val orderId: String,            // ID of the original order
    val orderCode: String,          // Human-readable order code from the original order
    val ticketId: String,
    val priceCat: String,           // Price category or type
    val sid: String,                // Seat ID or general admission identifier
    val ticketIdx: Int,             // Index of the ticket within the original order
    val slotIdx: Int,               // Index of the slot within the ticket (if a ticket has multiple slots)
    val originalOwnerId: String,    // User ID of the initial purchaser
    val lastModified: Long,         // Timestamp of the last modification to this document
    val createdAt: Long,            // Timestamp when this slot document was first created (immutable)
    val version: Int,
    
    // --- Current State (Denormalized for easier access) ---
    val currentStatus: TicketSlotStatus?,   // Current status from the last state

    val optStatus: TicketSlotOptStatus?,    // Operator status

    /** Ngày tham quan "yyyy-MM-dd" của vé theo lịch; null với vé suất thường. */
    val visitDate: String? = null,

    // --- State History ---
    // A list containing the chronological history of states for this slot.
    val states: List<TicketSlotStateResponse> = listOf()
)

// Helper extension function to convert TicketSlotState to TicketSlotStateResponse
private fun TicketSlotState.toTicketSlotStateResponse(): TicketSlotStateResponse {
    return TicketSlotStateResponse(
        stateIdx = this.stateIdx,
        ownerId = this.ownerId,
        ownerPhone = this.ownerPhone,
        status = this.status,
        reason = this.reason,
        senderId = this.senderId,
        recipientId = this.recipientId,
        timestamp = this.timestamp.time,
        // Note: qrcodeString from TicketSlotState is intentionally omitted
        // as it's not part of TicketSlotStateResponse.
    )
}

fun TicketSlotDoc.toTicketSlotResponse(): TicketSlotResponse {
    return TicketSlotResponse(
        id = Checksum.encode(this.id, Environment.secretKey),
        showId = this.showId,
        sessionId = this.sessionId,
        orderId = Checksum.encode(this.orderId, Environment.secretKey),
        orderCode = this.orderCode,
        ticketId = this.ticketId,
        priceCat = this.priceCat,
        sid = this.sid,
        ticketIdx = this.ticketIdx,
        slotIdx = this.slotIdx,
        originalOwnerId = this.originalOwnerId,
        lastModified = this.lastModified.time,
        createdAt = this.createdAt.time,
        version = this.version,
        currentStatus = this.currentStatus,
        optStatus = this.optStatus,
        visitDate = this.visitDate,

        states = this.states.map { it.toTicketSlotStateResponse() }
    )
}