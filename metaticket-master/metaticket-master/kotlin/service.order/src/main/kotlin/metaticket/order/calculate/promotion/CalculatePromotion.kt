package metaticket.order.calculate.promotion

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.databind.ObjectMapper
import metaticket.order.calculate.promotion.meta_promo.QuantityType
import metaticket.order.database.service.PromotionCodeQuantityDBService
import metaticket.order.database.service.PromotionComboQuantityDBService
import metaticket.order.database.service.PromotionDBService
import metaticket.order.pojo.db.OrderPromotionDetail
import metaticket.order.pojo.request.TentativePriceTicketRequest
import metaticket.ticket.grpc.TicketMessage
import java.util.*


interface CalculatePromotion {
    suspend fun calculatePromotionDiscount(input: CalculatePromotionInput): PromotionData?
    suspend fun applyPromotionDiscount(input: ApplyPromotionInput)
    suspend fun revertPromotionDiscount(input: RevertPromotionInput)
    suspend fun createPromotion(input: CreatePromotionInput): Boolean
    suspend fun deletePromotion(input: DeletePromotionInput): Boolean
}

data class CalculatedPromotion constructor(
    val promotionPluginId: String,
    val plugin: CalculatePromotion,
    val promotionData: PromotionData,
)

data class ApplyPromotionInput constructor(
    val promotionData: PromotionData,
    val promotionCodeQuantityDBService: PromotionCodeQuantityDBService,
    val promotionComboQuantityDBService: PromotionComboQuantityDBService,
)

data class CalculatePromotionInput constructor(
    val showId: String,
    val sessionId: String,
    val priceDiscount: String? = null,
    val date: Date? = null,
    val tentativeTickets: List<TentativePriceTicketRequest>? = null,
    val ticketsFromReservation: List<TicketMessage.ReservationDetailProto>? = null,
    var jsonMapper: ObjectMapper? = null,
    var promotionCode: String? = null,
    var tickets: List<TicketMessage.TicketProto>,
    var configuration: String? = null,
    val promotionCodeQuantityDBService: PromotionCodeQuantityDBService,
    val promotionComboQuantityDBService: PromotionComboQuantityDBService,
)

data class CreatePromotionInput constructor(
    val data: String,
    val showId: String,
    val sessionId: String,
    val promotionDBService: PromotionDBService,
    val promotionCodeQuantityDBService: PromotionCodeQuantityDBService,
    val promotionComboQuantityDBService: PromotionComboQuantityDBService,
    var jsonMapper: ObjectMapper? = null,
)

data class DeletePromotionInput constructor(
    val promotionDBService: PromotionDBService,
    var promotionId: String,
)

data class TicketPromotionData constructor(
    var sid: String,
    var ticketId: String,
    var priceCat: String,
    var basePrice: Long,
    var quantity: Int
)

//interface RevertPromotionInput {
//    val details: List<OrderPromotionDetail>
//}

data class RevertPromotionInput constructor(
    val details: List<OrderPromotionDetail>,
    val promotionCodeQuantityDBService: PromotionCodeQuantityDBService,
    val promotionComboQuantityDBService: PromotionComboQuantityDBService
)

data class PromotionData constructor(
    var promotionName: String,
    var priceDiscount: Long, //price discount
    var priceOriginal: Long, //price before discount
    var priceTotal: Long, //price after discount
    val details: List<PromotionDetailData>,
    var applyPromotionFailed: Boolean = false
)

data class PromotionDetailData constructor(
    var name: String,
    var priceDiscount: Long,
    var type: PromotionType,
    var error: Boolean = false,
    var errorMessage: String? = null,
    var promotionCodeQuantityId: String? = null,
    var promotionComboQuantityId: String? = null,
    var promotionComboQuantity: Int? = null
)

// Built from admin session drafts saved earlier; ignoring unknown fields keeps drafts written by a newer
// admin portal publishable on a rolled-back build.
@JsonIgnoreProperties(ignoreUnknown = true)
data class CreatePromotionData constructor(
    val type: String,
    val ticketAmountMin: Int? = null,
    val ticketAmountMax: Int? = null,
    val discountOption: String? = null,
    val discountValue: Long? = null,
    val needCheckQuantity: Boolean? = null,
    val originalQuantity: Int? = null,
    val quantityOption: QuantityType? = null,
    val code: String? = null,
    val codes: String? = null,
    val name: String? = null,
    val date: String? = null,
    val priceCatConditions: List<CreatePromotionPriceCatConditionData>? = null,
    val applyDiscountOn: String? = null,
    // Max discount amount fields
    val maxDiscountPerType: Map<String, Long>? = null,
    val maxDiscountTotal: Long? = null,
    // Flat per-type fields (alternative to map, used by frontend)
    val enablePerType: Boolean? = null,
    val maxDiscountByDate: Long? = null,
    val maxDiscountByTicketAmount: Long? = null,
    val maxDiscountByPromotionCode: Long? = null
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class CreatePromotionPriceCatConditionData constructor(
    val priceCat: String,
    val minQuantity: Int,
    val maxQuantity: Int?
)

enum class PromotionType {
    COMBO,
    COUPON,
    NONE
}

