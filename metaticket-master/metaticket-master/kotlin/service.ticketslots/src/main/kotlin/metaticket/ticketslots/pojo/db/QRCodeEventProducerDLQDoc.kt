package metaticket.ticketslots.pojo.db

import metaticket.ticketslots.model.FailedEventStatus
import metaticket.ticketslots.kafka.GenerateTicketSlotQRCodeEvent
import org.bson.BsonType
import org.bson.codecs.pojo.annotations.BsonId
import org.bson.codecs.pojo.annotations.BsonRepresentation
import org.bson.types.ObjectId
import java.util.*

/**
 * Document chuyên dành để lưu trữ các GenerateTicketSlotQRCodeEvent bị lỗi khi gửi lên Kafka (Producer errors)
 */
data class QRCodeEventProducerDLQDoc constructor(
    @BsonId @BsonRepresentation(BsonType.OBJECT_ID)
    override val id: String = ObjectId().toHexString(),
    override val errorType: String,
    override val errorMessage: String,
    override val retryCount: Int = 0,
    override val lastRetryAt: Date? = null,
    override val status: FailedEventStatus = FailedEventStatus.PENDING,
    override val createdAt: Date = Date(),
    override val updatedAt: Date = Date(),
    override val sourceTopic: String,

    /**
     * Event GenerateTicketSlotQRCodeEvent gốc cần gửi lên Kafka
     */
    val event: GenerateTicketSlotQRCodeEvent,

    /**
     * Message key cho Kafka
     */
    val messageKey: String?,
    /**
     * Ticket slot ID liên quan
     */
    val ticketSlotId: String? = event.slotId,

    /**
     * Ticket slot state idx liên quan đến QR code generation
     */
    val stateIdx: Int? = event.stateIdx,
) : RetryableEventDoc {

}