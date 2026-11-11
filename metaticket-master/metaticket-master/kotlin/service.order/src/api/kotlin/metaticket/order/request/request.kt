package metaticket.order.request

import com.fasterxml.jackson.annotation.JsonInclude
import metaticket.order.datastructure.OrderStatus
import metaticket.order.pojo.db.CustomFieldData
import metaticket.order.pojo.db.CustomFieldTmpl
import metaticket.order.pojo.db.DeliveryInfo

data class CreateOrderRequest constructor(
    var reservationId: String,
    val name: String,
    val phone: String,
    val email: String,
    var delivery: DeliveryInfo? = null,
    var customFields: List<CustomFieldData>? = null,
    val promotionCode: String? = null,
)

data class UpdateOrderInfoRequest constructor(
    var orderId: String,
    val name: String,
    val phone: String,
    val email: String,
    var delivery: DeliveryInfo? = null,
    var customFields: List<CustomFieldData>? = null,
)

data class CreateOrderCustomFieldsRequest constructor(
    val showId: String,
    val sessionId: String,
    val customFields: List<CustomFieldTmpl>,
)

data class CreateOrderCustomFieldsMappingRequest constructor(
    var showId: String,
    var sessionId : String,
    var customFields: List<CustomFieldTmpl>,
)

data class UpdateOrderCustomFieldsRequest constructor(
    val showId: String,
    val sessionId: String,
    val customFields: List<CustomFieldTmpl>,
)

data class CreateOrderPromotionRequest constructor(
    val showId: String,
    val sessionId: String,
    val data: String,
    val pluginName: String,
)

data class UpdateOrderPromotionRequest constructor(
    val showId: String,
    val sessionId: String,
    val data: String,
    val pluginName: String,
    val promotionId: String? = null
)

data class LoadInvoiceStatisticsRequest constructor(
    val startDate: Long?,
    val endDate: Long?,
    val status: List<OrderStatus>?,
    val showId: String?,
    val sessionId: String?,
    /**
     * Lọc đơn vé theo lịch theo ngày tham quan "yyyy-MM-dd" (bao gồm hai đầu).
     * NON_NULL: service.order bản cũ (Jackson không bỏ qua field lạ) vẫn nhận request khi field không dùng.
     */
    @get:JsonInclude(JsonInclude.Include.NON_NULL)
    val visitDateFrom: String? = null,
    @get:JsonInclude(JsonInclude.Include.NON_NULL)
    val visitDateTo: String? = null,
) {
    override fun equals(other: Any?): Boolean {
        other?: return false
        return other.hashCode() == hashCode()
    }

    override fun hashCode(): Int {
        var result = startDate?.hashCode() ?: 0
        result = 31 * result + (endDate?.hashCode() ?: 0)
        result = 31 * result + (status?.hashCode() ?: 0)
        result = 31 * result + (showId?.hashCode() ?: 0)
        result = 31 * result + (sessionId?.hashCode() ?: 0)
        // Chỉ cộng khi có giá trị để hash của bộ lọc đã lưu từ trước không đổi.
        visitDateFrom?.let { result = 31 * result + it.hashCode() }
        visitDateTo?.let { result = 31 * result + it.hashCode() }
        return result
    }
}

data class LoadReportRequest constructor(
    val startDate: Long? = null,
    val endDate: Long? = null,
    val status: List<OrderStatus>? = null,
    val showId: String? = null,
    val sessionId: String? = null,
    @get:JsonInclude(JsonInclude.Include.NON_NULL)
    val visitDateFrom: String? = null,
    @get:JsonInclude(JsonInclude.Include.NON_NULL)
    val visitDateTo: String? = null,
)
