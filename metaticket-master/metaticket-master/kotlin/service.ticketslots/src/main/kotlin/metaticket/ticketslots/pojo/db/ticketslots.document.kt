package metaticket.ticketslots.pojo.db

import org.bson.BsonType
import org.bson.codecs.pojo.annotations.BsonId
import org.bson.codecs.pojo.annotations.BsonRepresentation
import org.bson.types.ObjectId
import java.util.*

enum class TicketSlotOptStatus {
    NONE,       // There no action for this slot
    OPENED,     // Mark this slot as Opened, then this slot can be use to show QR and check in
    CLOSED,     // Mark this slot as closed, then this slot cannot be use to share/show qr/check in
}

enum class TicketSlotStatus {
    CREATED,        // Newly created or received via transfer
    QR_GENERATED,   // QR code was generated
    CHECKED_IN,     // The QR code was successfully scanned (e.g., at check-in)
}

/**
 * Describes the reason why a new state was added to the slot's history.
 */
enum class StateReason {
    INITIAL_PURCHASE,   // Slot created from the initial order
    TRANSFER_RECEIVED,  // Slot received from another user
    TRANSFERRED_AWAY,   // Slot was transferred to another user
}

/**
 * Represents a specific state snapshot in the lifecycle of a TicketSlot.
 * Each transfer or significant status change creates a new state entry.
 */
data class TicketSlotState constructor(
    val stateIdx: Int,                  // The index of state in list of state of ticket slot
    val ownerId: String? = null,        // User ID of the owner during this state (nullable for none register user)
    val ownerPhone: String? = null,     // Phone number of the owner during this state (nullable for none registered user)
    val status: TicketSlotStatus,       // The status of the slot during this state
    val reason: StateReason,            // The reason this state was created/changed
    val senderId: String? = null,       // User ID of the sender (if applicable)
    val recipientId: String? = null,    // User ID of the recipient (if applicable)
    val qrcodeString: String? = null,   // The QR code string associated with this owner and state (nullable)
    val timestamp: Date = Date(),       // Timestamp when this state changed
)

/**
 * Represents a single ticket slot document in the database.
 * It contains immutable information about the slot's origin and a list of states
 * representing its history of ownership and status changes.
 */
data class TicketSlotDoc constructor(
    @BsonId @BsonRepresentation(BsonType.OBJECT_ID)
    val id: String = ObjectId().toHexString(), // Unique identifier for this ticket slot document

    // --- Immutable Information (Set once at creation) ---
    val showId: String,             // ID of the show
    val sessionId: String,          // ID of the specific session/performance
    val orderId: String,            // ID of the original order
    val orderCode: String,          // Human-readable order code from the original order
    val ticketId: String,
    val priceCat: String,           // Price category or type
    val sid: String,                // Seat ID or general admission identifier

    /**
     * Example we have two tickets sid-1, sid-2.
     * sid-1 have seat type is zone, and two slot for each ticket.
     * sid-2 have seat type is seat, and three slot for each ticket.
     *
     * Then user buy an order with two tickets above, sid-1 with quantity is 3, sid-2 with quantity is 2.
     *
     * --- order
     * ------ sid-1 (sid ~ seat id ~ ticket id)
     * ---------- ticket-idx-0 (ticketIdx = 0)
     * ------------- slot-idx-0 (slotIdx = 0)
     * ------------- slot-idx-1 (slotIdx = 1)
     * ---------- ticket-idx-1 (slotIdx = 1)
     * ------------- slot-idx-0 (slotIdx = 0)
     * ------------- slot-idx-1 (slotIdx = 1)
     * ---------- ticket-idx-2 (ticketIdx = 2)
     * ------------- slot-idx-0 (slotIdx = 0)
     * ------------- slot-idx-1 (slotIdx = 1)
     * ------ sid-2
     * ---------- ticket-idx-0 (ticketIdx = 0)
     * ------------- slot-idx-0 (slotIdx = 0)
     * ------------- slot-idx-1 (slotIdx = 1)
     * ------------- slot-idx-2 (slotIdx = 2)
     * ---------- ticket-idx-1 (ticketIdx = 1)
     * ------------- slot-idx-0 (slotIdx = 0)
     * ------------- slot-idx-1 (slotIdx = 1)
     * ------------- slot-idx-2 (slotIdx = 2)
     */
    val ticketIdx: Int,             // Index of the ticket within the ticket quantity (example: the zone ticket, user buy ticket with quantity = 5, so the ticketIdx is 0,1,2,3,4)
    val slotIdx: Int,               // Index of the slot within a ticket (if a ticket has multiple slots)

    val originalOwnerId: String,    // User ID of the initial purchaser

    // --- Denormalized Current State (For easier querying and indexing) ---
    // These fields are updated atomically whenever a new state is pushed to the 'states' list.
    val currentOwnerId: String? = null,             // User ID of the current owner (from the last state)
    val currentOwnerPhone: String? = null,          // Phone number of the current owner (from the last state)
    val currentStatus: TicketSlotStatus? = null,    // Current status (from the last state)
    val currentQRString: String? = null,            // Current QR code string (from the last state)
    val currentStateIdx: Int,                       // Current state index (from the last state)

    val optStatus: TicketSlotOptStatus? = TicketSlotOptStatus.NONE,  // Status of operator set for this slot

    /** Ngày tham quan "yyyy-MM-dd" của vé theo lịch (chốt từ đơn), chỉ soát được vào đúng ngày này; null với vé suất thường. */
    val visitDate: String? = null,

    val lastModified: Date = Date(),    // Timestamp of the last modification to this document
    val createdAt: Date = Date(),       // Timestamp when this slot document was first created (immutable)
    val version: Int = 1,

    // --- State History ---
    // A list containing the chronological history of states for this slot.
    // The *last* element in this list represents the current state.
    val states: List<TicketSlotState> = listOf()
)