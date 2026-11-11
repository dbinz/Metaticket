package metaticket.order.datastructure.kafka

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonInclude

// Bỏ qua field lạ để consumer bản cũ/mới đọc chéo được event khi deploy cuốn chiếu hoặc rollback.
@JsonIgnoreProperties(ignoreUnknown = true)
data class TicketInfoDTO constructor(
    val ticketId: String,
    val sid: String,
    val seatType: String,
    val quantity: Int,
    var priceCat : String,      // price category
    var ticketSlots: Int,
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class OrderSucceedEvent constructor(
    val showId: String,
    val sessionId: String,
    val orderId: String,
    val reservationId: String,
    val orderCode: String,
    val ownerId: String,
    val ticketInfo: List<TicketInfoDTO>,
    val createdAt: Long,
    val lastModified: Long,
    /**
     * Ngày tham quan "yyyy-MM-dd" của đơn vé theo lịch. Chỉ ghi khi có giá trị: consumer ticketslots bản trước tính năng
     * này báo lỗi với field lạ, nên event của đơn suất thường phải giữ nguyên dạng JSON cũ.
     */
    @get:JsonInclude(JsonInclude.Include.NON_NULL)
    val visitDate: String? = null,
)