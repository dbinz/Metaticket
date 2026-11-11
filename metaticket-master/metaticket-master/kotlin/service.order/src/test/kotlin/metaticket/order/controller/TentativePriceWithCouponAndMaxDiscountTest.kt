package metaticket.order.controller

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.mockk.*
import kotlinx.coroutines.test.runTest
import metaticket.order.OrderCreationService
import metaticket.order.cache.CalculatePromotionClassesCache
import metaticket.order.cache.ShowCache
import metaticket.order.calculate.promotion.*
import metaticket.order.calculate.promotion.meta_promo.*
import metaticket.order.database.service.*
import metaticket.order.gateway.*
import metaticket.order.pojo.db.Promotion
import metaticket.order.pojo.db.PromotionCodeQuantity
import metaticket.order.pojo.request.CalculateTentativePriceRequest
import metaticket.order.pojo.request.TentativePriceTicketRequest
import metaticket.order.pojo.response.TentativePriceResponse
import metaticket.show.grpc.ShowMessage
import metaticket.ticket.grpc.TicketMessage
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.util.*

/**
 * Integration test cho luồng tính giá tentative (/order/price/tentative)
 * với coupon promotion và max discount amount cap.
 *
 * Test này mô phỏng luồng xử lý thực tế:
 * 1. Request gửi lên với promotionCode
 * 2. Hệ thống load promotions từ DB (mocked)
 * 3. MetaPromoPlugin tính discount với coupon
 * 4. MaxDiscountAmount cap được áp dụng
 * 5. Response trả về với giá đã tính
 */
class TentativePriceWithCouponAndMaxDiscountTest {

    private lateinit var plugin: MetaPromoPlugin
    private lateinit var jsonMapper: ObjectMapper
    private lateinit var promotionCodeQuantityDBService: PromotionCodeQuantityDBService
    private lateinit var promotionComboQuantityDBService: PromotionComboQuantityDBService

    private val showId = "6595773df323909fe23b954e"
    private val sessionId = "65957746f323909fe23b954f"
    private val shipPrice = 30000L

    @BeforeEach
    fun setup() {
        plugin = MetaPromoPlugin()
        jsonMapper = jacksonObjectMapper()
        promotionCodeQuantityDBService = mockk(relaxed = true)
        promotionComboQuantityDBService = mockk(relaxed = true)
    }

    /**
     * Helper tạo mock TicketProto
     */
    private fun buildTicketProto(id: String, sid: String, priceCat: String, basePrice: Long): TicketMessage.TicketProto {
        val ticket = mockk<TicketMessage.TicketProto>()
        every { ticket.id } returns id
        every { ticket.sid } returns sid
        every { ticket.showId } returns showId
        every { ticket.sessionId } returns sessionId
        every { ticket.priceCat } returns priceCat
        every { ticket.basePrice } returns basePrice
        every { ticket.hasDname() } returns false
        every { ticket.hasTicketSlots() } returns false
        return ticket
    }

    /**
     * Mô phỏng luồng calculateTentativePrice:
     * 1. Parse request (tickets, promotionCode)
     * 2. Lấy tickets từ gateway
     * 3. Load promotions config từ DB
     * 4. Gọi plugin.calculatePromotionDiscount
     * 5. Trả về response
     */
    private suspend fun simulateTentativePrice(
        tickets: List<TicketMessage.TicketProto>,
        tentativeTickets: List<TentativePriceTicketRequest>,
        promotionConfig: MetaPromoConfiguration,
        promotionCode: String? = null,
    ): TentativePriceResponse {
        val configJson = jsonMapper.writeValueAsString(promotionConfig)

        val input = CalculatePromotionInput(
            showId = showId,
            sessionId = sessionId,
            tentativeTickets = tentativeTickets,
            tickets = tickets,
            jsonMapper = jsonMapper,
            configuration = configJson,
            promotionCode = promotionCode,
            promotionCodeQuantityDBService = promotionCodeQuantityDBService,
            promotionComboQuantityDBService = promotionComboQuantityDBService,
        )

        val result = plugin.calculatePromotionDiscount(input)

        return if (result == null || result.priceDiscount == 0L) {
            var priceOriginal = 0L
            for (ticketReq in tentativeTickets) {
                for (ticket in tickets) {
                    if (ticket.id == ticketReq.ticketId) {
                        priceOriginal += ticket.basePrice * ticketReq.quantity
                    }
                }
            }
            TentativePriceResponse(
                promotionName = "",
                priceDiscount = 0,
                shipPrice = shipPrice,
                priceTotal = priceOriginal + shipPrice,
                priceOriginal = priceOriginal + shipPrice,
                promotionDetails = listOf()
            )
        } else {
            TentativePriceResponse(
                promotionName = "",
                priceDiscount = result.priceDiscount,
                shipPrice = shipPrice,
                priceTotal = result.priceTotal + shipPrice,
                priceOriginal = result.priceOriginal + shipPrice,
                promotionDetails = result.details
            )
        }
    }

    @Nested
    inner class CouponWithMaxDiscountTotal {

        @Test
        fun `coupon 50percent on 2 tickets - no max cap`() = runTest {
            // 2 VIP tickets @ 200,000 each = 400,000 total
            // Coupon SALE50 gives 50% = 200,000 discount
            // No cap configured
            val config = MetaPromoConfiguration(
                discountByDates = null,
                discountByTicketAmounts = null,
                discountByPriceCategoryAmounts = null,
                promotionCodes = listOf(
                    MetaPromoPromotionCodeConfig(
                        name = "SALE50",
                        code = "SALE50",
                        discountPercent = 50,
                        discountPrice = null,
                        needCheckQuantity = false,
                        originalQuantity = null,
                    )
                ),
                maxDiscountAmount = null
            )

            val ticket1 = buildTicketProto("t1", "A1", "VIP", 200000)
            val ticket2 = buildTicketProto("t2", "A2", "VIP", 200000)
            val tentative = listOf(
                TentativePriceTicketRequest("t1", 1, "VIP"),
                TentativePriceTicketRequest("t2", 1, "VIP")
            )

            val response = simulateTentativePrice(
                listOf(ticket1, ticket2), tentative, config, promotionCode = "SALE50"
            )

            // 50% of 400,000 = 200,000 discount
            assertEquals(200000L, response.priceDiscount)
            assertEquals(430000L, response.priceOriginal) // 400,000 + 30,000 ship
            assertEquals(230000L, response.priceTotal)     // 200,000 + 30,000 ship
            assertEquals(1, response.promotionDetails.size)
            assertEquals("SALE50", response.promotionDetails[0].name)
            assertEquals(PromotionType.COUPON, response.promotionDetails[0].type)
        }

        @Test
        fun `coupon 50percent capped at 100K by maxDiscountTotal`() = runTest {
            // 2 VIP tickets @ 200,000 each = 400,000 total
            // Coupon SALE50 gives 50% = 200,000 discount
            // Max total cap = 100,000
            val config = MetaPromoConfiguration(
                discountByDates = null,
                discountByTicketAmounts = null,
                discountByPriceCategoryAmounts = null,
                promotionCodes = listOf(
                    MetaPromoPromotionCodeConfig(
                        name = "SALE50",
                        code = "SALE50",
                        discountPercent = 50,
                        discountPrice = null,
                        needCheckQuantity = false,
                        originalQuantity = null,
                    )
                ),
                maxDiscountAmount = MetaPromoMaxDiscountAmountConfig(
                    maxDiscountPerType = null,
                    maxDiscountTotal = 100000
                )
            )

            val ticket1 = buildTicketProto("t1", "A1", "VIP", 200000)
            val ticket2 = buildTicketProto("t2", "A2", "VIP", 200000)
            val tentative = listOf(
                TentativePriceTicketRequest("t1", 1, "VIP"),
                TentativePriceTicketRequest("t2", 1, "VIP")
            )

            val response = simulateTentativePrice(
                listOf(ticket1, ticket2), tentative, config, promotionCode = "SALE50"
            )

            // Discount capped at 100,000
            assertEquals(100000L, response.priceDiscount)
            assertEquals(430000L, response.priceOriginal) // 400,000 + 30,000 ship
            assertEquals(330000L, response.priceTotal)     // 300,000 + 30,000 ship
        }

        @Test
        fun `fixed coupon 150K capped by promotionCode perType at 80K`() = runTest {
            // 1 ticket @ 300,000
            // Coupon BIG150K gives fixed 150,000 discount
            // Cap promotionCode type at 80,000
            val config = MetaPromoConfiguration(
                discountByDates = null,
                discountByTicketAmounts = null,
                discountByPriceCategoryAmounts = null,
                promotionCodes = listOf(
                    MetaPromoPromotionCodeConfig(
                        name = "BIG150K",
                        code = "BIG150K",
                        discountPercent = null,
                        discountPrice = 150000,
                        needCheckQuantity = false,
                        originalQuantity = null,
                    )
                ),
                maxDiscountAmount = MetaPromoMaxDiscountAmountConfig(
                    maxDiscountPerType = mapOf("promotionCode" to 80000L),
                    maxDiscountTotal = null
                )
            )

            val ticket1 = buildTicketProto("t1", "A1", "STANDARD", 300000)
            val tentative = listOf(TentativePriceTicketRequest("t1", 1, "STANDARD"))

            val response = simulateTentativePrice(
                listOf(ticket1), tentative, config, promotionCode = "BIG150K"
            )

            // Discount capped at 80,000 (per-type cap for promotionCode)
            assertEquals(80000L, response.priceDiscount)
            assertEquals(330000L, response.priceOriginal) // 300,000 + 30,000 ship
            assertEquals(250000L, response.priceTotal)     // 220,000 + 30,000 ship
        }

        @Test
        fun `coupon below cap - no reduction applied`() = runTest {
            // 1 ticket @ 100,000
            // Coupon SMALL10 gives 10% = 10,000 discount
            // Cap at 50,000 (higher than actual discount)
            val config = MetaPromoConfiguration(
                discountByDates = null,
                discountByTicketAmounts = null,
                discountByPriceCategoryAmounts = null,
                promotionCodes = listOf(
                    MetaPromoPromotionCodeConfig(
                        name = "SMALL10",
                        code = "SMALL10",
                        discountPercent = 10,
                        discountPrice = null,
                        needCheckQuantity = false,
                        originalQuantity = null,
                    )
                ),
                maxDiscountAmount = MetaPromoMaxDiscountAmountConfig(
                    maxDiscountPerType = mapOf("promotionCode" to 50000L),
                    maxDiscountTotal = null
                )
            )

            val ticket1 = buildTicketProto("t1", "A1", "VIP", 100000)
            val tentative = listOf(TentativePriceTicketRequest("t1", 1, "VIP"))

            val response = simulateTentativePrice(
                listOf(ticket1), tentative, config, promotionCode = "SMALL10"
            )

            // 10% of 100,000 = 10,000 < cap 50,000. No capping
            assertEquals(10000L, response.priceDiscount)
            assertEquals(130000L, response.priceOriginal) // 100,000 + 30,000 ship
            assertEquals(120000L, response.priceTotal)     // 90,000 + 30,000 ship
        }
    }

    @Nested
    inner class CouponWithQuantityCheckAndMaxDiscount {

        @Test
        fun `coupon with quantity check - quantity available and capped`() = runTest {
            // Coupon VIP30 gives 30% on 500,000 = 150,000 discount
            // Has quantity check (limit usage)
            // Cap total at 100,000
            val config = MetaPromoConfiguration(
                discountByDates = null,
                discountByTicketAmounts = null,
                discountByPriceCategoryAmounts = null,
                promotionCodes = listOf(
                    MetaPromoPromotionCodeConfig(
                        name = "VIP30",
                        code = "VIP30",
                        discountPercent = 30,
                        discountPrice = null,
                        needCheckQuantity = true,
                        originalQuantity = 100,
                    )
                ),
                maxDiscountAmount = MetaPromoMaxDiscountAmountConfig(
                    maxDiscountPerType = null,
                    maxDiscountTotal = 100000
                )
            )

            // Mock: quantity available
            coEvery {
                promotionCodeQuantityDBService.loadPromotionCodeQuantity(showId, sessionId, "VIP30")
            } returns PromotionCodeQuantity(
                id = "6595773df323909fe23b0001",
                showId = showId,
                sessionId = sessionId,
                code = "VIP30",
                originalQuantity = 100,
                quantity = 50,
                lastModified = Date()
            )

            val ticket1 = buildTicketProto("t1", "A1", "VIP", 250000)
            val ticket2 = buildTicketProto("t2", "A2", "VIP", 250000)
            val tentative = listOf(
                TentativePriceTicketRequest("t1", 1, "VIP"),
                TentativePriceTicketRequest("t2", 1, "VIP")
            )

            val response = simulateTentativePrice(
                listOf(ticket1, ticket2), tentative, config, promotionCode = "VIP30"
            )

            // 30% of 500,000 = 150,000. Capped at 100,000
            assertEquals(100000L, response.priceDiscount)
            assertEquals(530000L, response.priceOriginal) // 500,000 + 30,000 ship
            assertEquals(430000L, response.priceTotal)     // 400,000 + 30,000 ship
        }

        @Test
        fun `coupon with quantity check - quantity exhausted - no discount`() = runTest {
            // Coupon SOLD_OUT gives 50% but quantity is 0
            // Even with cap configured, no discount should apply
            val config = MetaPromoConfiguration(
                discountByDates = null,
                discountByTicketAmounts = null,
                discountByPriceCategoryAmounts = null,
                promotionCodes = listOf(
                    MetaPromoPromotionCodeConfig(
                        name = "SOLD_OUT",
                        code = "SOLD_OUT",
                        discountPercent = 50,
                        discountPrice = null,
                        needCheckQuantity = true,
                        originalQuantity = 10,
                    )
                ),
                maxDiscountAmount = MetaPromoMaxDiscountAmountConfig(
                    maxDiscountPerType = null,
                    maxDiscountTotal = 200000
                )
            )

            // Mock: quantity exhausted
            coEvery {
                promotionCodeQuantityDBService.loadPromotionCodeQuantity(showId, sessionId, "SOLD_OUT")
            } returns PromotionCodeQuantity(
                id = "6595773df323909fe23b0002",
                showId = showId,
                sessionId = sessionId,
                code = "SOLD_OUT",
                originalQuantity = 10,
                quantity = 0,
                lastModified = Date()
            )

            val ticket1 = buildTicketProto("t1", "A1", "VIP", 200000)
            val tentative = listOf(TentativePriceTicketRequest("t1", 1, "VIP"))

            val response = simulateTentativePrice(
                listOf(ticket1), tentative, config, promotionCode = "SOLD_OUT"
            )

            // Coupon exhausted -> detail has error, no real discount applied
            // The priceDiscount is still 0 because error coupon doesn't add discount
            assertEquals(0L, response.priceDiscount)
            assertEquals(230000L, response.priceOriginal) // 200,000 + 30,000 ship
            assertEquals(230000L, response.priceTotal)
        }

        @Test
        fun `invalid coupon code - no discount applied`() = runTest {
            // User enters WRONG_CODE but promotion only has VALID_CODE
            val config = MetaPromoConfiguration(
                discountByDates = null,
                discountByTicketAmounts = null,
                discountByPriceCategoryAmounts = null,
                promotionCodes = listOf(
                    MetaPromoPromotionCodeConfig(
                        name = "VALID_CODE",
                        code = "VALID_CODE",
                        discountPercent = 50,
                        discountPrice = null,
                        needCheckQuantity = false,
                        originalQuantity = null,
                    )
                ),
                maxDiscountAmount = MetaPromoMaxDiscountAmountConfig(
                    maxDiscountPerType = null,
                    maxDiscountTotal = 50000
                )
            )

            val ticket1 = buildTicketProto("t1", "A1", "VIP", 200000)
            val tentative = listOf(TentativePriceTicketRequest("t1", 1, "VIP"))

            val response = simulateTentativePrice(
                listOf(ticket1), tentative, config, promotionCode = "WRONG_CODE"
            )

            // No matching code -> no discount
            assertEquals(0L, response.priceDiscount)
            assertEquals(230000L, response.priceTotal)
        }
    }

    @Nested
    inner class CouponCombinedWithOtherDiscountsAndMaxCap {

        @Test
        fun `early bird + coupon combined - total capped`() = runTest {
            // 2 tickets @ 200,000 = 400,000
            // Early bird 10% = 40,000
            // Coupon EXTRA20 gives 20% of remaining (400,000 - 40,000 = 360,000) = 72,000
            // Total discount = 40,000 + 72,000 = 112,000
            // Max total cap = 80,000
            val futureDate = System.currentTimeMillis() + 86400000L
            val config = MetaPromoConfiguration(
                discountByDates = listOf(
                    MetaPromoDiscountByDateConfig("Early Bird", futureDate, null, 10, null)
                ),
                discountByTicketAmounts = null,
                discountByPriceCategoryAmounts = null,
                promotionCodes = listOf(
                    MetaPromoPromotionCodeConfig(
                        name = "EXTRA20",
                        code = "EXTRA20",
                        discountPercent = 20,
                        discountPrice = null,
                        needCheckQuantity = false,
                        originalQuantity = null,
                    )
                ),
                maxDiscountAmount = MetaPromoMaxDiscountAmountConfig(
                    maxDiscountPerType = null,
                    maxDiscountTotal = 80000
                )
            )

            val ticket1 = buildTicketProto("t1", "A1", "VIP", 200000)
            val ticket2 = buildTicketProto("t2", "A2", "VIP", 200000)
            val tentative = listOf(
                TentativePriceTicketRequest("t1", 1, "VIP"),
                TentativePriceTicketRequest("t2", 1, "VIP")
            )

            val response = simulateTentativePrice(
                listOf(ticket1, ticket2), tentative, config, promotionCode = "EXTRA20"
            )

            // Total capped at 80,000
            assertEquals(80000L, response.priceDiscount)
            assertEquals(430000L, response.priceOriginal) // 400,000 + 30,000 ship
            assertEquals(350000L, response.priceTotal)     // 320,000 + 30,000 ship
        }

        @Test
        fun `early bird + coupon - coupon type capped but total not`() = runTest {
            // 2 tickets @ 200,000 = 400,000
            // Early bird 10% = 40,000
            // Coupon EXTRA gives fixed 100,000
            // Per-type cap: promotionCode = 50,000
            // No total cap
            // Expected: date discount 40,000 (uncapped) + coupon 50,000 (capped from 100,000) = 90,000
            val futureDate = System.currentTimeMillis() + 86400000L
            val config = MetaPromoConfiguration(
                discountByDates = listOf(
                    MetaPromoDiscountByDateConfig("Early Bird", futureDate, null, 10, null)
                ),
                discountByTicketAmounts = null,
                discountByPriceCategoryAmounts = null,
                promotionCodes = listOf(
                    MetaPromoPromotionCodeConfig(
                        name = "EXTRA",
                        code = "EXTRA",
                        discountPercent = null,
                        discountPrice = 100000,
                        needCheckQuantity = false,
                        originalQuantity = null,
                    )
                ),
                maxDiscountAmount = MetaPromoMaxDiscountAmountConfig(
                    maxDiscountPerType = mapOf("promotionCode" to 50000L),
                    maxDiscountTotal = null
                )
            )

            val ticket1 = buildTicketProto("t1", "A1", "VIP", 200000)
            val ticket2 = buildTicketProto("t2", "A2", "VIP", 200000)
            val tentative = listOf(
                TentativePriceTicketRequest("t1", 1, "VIP"),
                TentativePriceTicketRequest("t2", 1, "VIP")
            )

            val response = simulateTentativePrice(
                listOf(ticket1, ticket2), tentative, config, promotionCode = "EXTRA"
            )

            // Date: 40,000 (no cap). Coupon: 100,000 -> capped at 50,000.
            // Total: 90,000
            assertEquals(90000L, response.priceDiscount)
            assertEquals(430000L, response.priceOriginal)
            assertEquals(340000L, response.priceTotal) // 310,000 + 30,000 ship
        }

        @Test
        fun `combo + coupon - both capped by perType then total`() = runTest {
            // 3 tickets @ 100,000 = 300,000
            // Combo discount: 50% = 150,000
            // Coupon gives fixed 80,000
            // Per-type: discountByTicketAmount cap = 60,000, promotionCode cap = 40,000
            // Total cap = 90,000
            // After per-type: combo 60,000 + coupon 40,000 = 100,000
            // After total cap: 90,000
            val config = MetaPromoConfiguration(
                discountByDates = null,
                discountByTicketAmounts = listOf(
                    MetaPromoDiscountByTicketAmountConfig(
                        ticketAmountBelow = 3,
                        ticketAmountAbove = 5,
                        discountPercent = 50,
                        discountPrice = null,
                        id = null,
                        needCheckQuantity = false,
                        originalQuantity = null,
                        quantityType = null,
                    )
                ),
                discountByPriceCategoryAmounts = null,
                promotionCodes = listOf(
                    MetaPromoPromotionCodeConfig(
                        name = "BONUS80K",
                        code = "BONUS80K",
                        discountPercent = null,
                        discountPrice = 80000,
                        needCheckQuantity = false,
                        originalQuantity = null,
                    )
                ),
                maxDiscountAmount = MetaPromoMaxDiscountAmountConfig(
                    maxDiscountPerType = mapOf(
                        "discountByTicketAmount" to 60000L,
                        "promotionCode" to 40000L
                    ),
                    maxDiscountTotal = 90000
                )
            )

            val ticket1 = buildTicketProto("t1", "A1", "VIP", 100000)
            val ticket2 = buildTicketProto("t2", "A2", "VIP", 100000)
            val ticket3 = buildTicketProto("t3", "A3", "VIP", 100000)
            val tentative = listOf(
                TentativePriceTicketRequest("t1", 1, "VIP"),
                TentativePriceTicketRequest("t2", 1, "VIP"),
                TentativePriceTicketRequest("t3", 1, "VIP")
            )

            val response = simulateTentativePrice(
                listOf(ticket1, ticket2, ticket3), tentative, config, promotionCode = "BONUS80K"
            )

            // Per-type: combo 150,000->60,000, coupon 80,000->40,000 = 100,000
            // Total cap 90,000
            assertEquals(90000L, response.priceDiscount)
            assertEquals(330000L, response.priceOriginal) // 300,000 + 30,000 ship
            assertEquals(240000L, response.priceTotal)     // 210,000 + 30,000 ship
        }
    }

    @Nested
    inner class CouponCaseInsensitiveAndTrimTests {

        @Test
        fun `coupon code case insensitive match with cap`() = runTest {
            // Code configured as "SUMMER25", user enters "summer25"
            val config = MetaPromoConfiguration(
                discountByDates = null,
                discountByTicketAmounts = null,
                discountByPriceCategoryAmounts = null,
                promotionCodes = listOf(
                    MetaPromoPromotionCodeConfig(
                        name = "SUMMER25",
                        code = "SUMMER25",
                        discountPercent = 25,
                        discountPrice = null,
                        needCheckQuantity = false,
                        originalQuantity = null,
                    )
                ),
                maxDiscountAmount = MetaPromoMaxDiscountAmountConfig(
                    maxDiscountPerType = null,
                    maxDiscountTotal = 30000
                )
            )

            val ticket1 = buildTicketProto("t1", "A1", "VIP", 200000)
            val tentative = listOf(TentativePriceTicketRequest("t1", 1, "VIP"))

            val response = simulateTentativePrice(
                listOf(ticket1), tentative, config, promotionCode = "summer25"
            )

            // 25% of 200,000 = 50,000. Capped at 30,000.
            assertEquals(30000L, response.priceDiscount)
            assertEquals(230000L, response.priceOriginal)
            assertEquals(200000L, response.priceTotal) // 170,000 + 30,000 ship
        }

        @Test
        fun `coupon code with spaces trimmed`() = runTest {
            val config = MetaPromoConfiguration(
                discountByDates = null,
                discountByTicketAmounts = null,
                discountByPriceCategoryAmounts = null,
                promotionCodes = listOf(
                    MetaPromoPromotionCodeConfig(
                        name = "CODE123",
                        code = "CODE123",
                        discountPercent = null,
                        discountPrice = 60000,
                        needCheckQuantity = false,
                        originalQuantity = null,
                    )
                ),
                maxDiscountAmount = MetaPromoMaxDiscountAmountConfig(
                    maxDiscountPerType = mapOf("promotionCode" to 40000L),
                    maxDiscountTotal = null
                )
            )

            val ticket1 = buildTicketProto("t1", "A1", "VIP", 150000)
            val tentative = listOf(TentativePriceTicketRequest("t1", 1, "VIP"))

            val response = simulateTentativePrice(
                listOf(ticket1), tentative, config, promotionCode = "  CODE123  "
            )

            // Fixed 60,000 discount, capped at 40,000
            assertEquals(40000L, response.priceDiscount)
        }
    }

    @Nested
    inner class MultipleTicketsWithCouponCapTests {

        @Test
        fun `many tickets high value - coupon percent capped`() = runTest {
            // 5 tickets @ 500,000 = 2,500,000
            // Coupon MEGA20 gives 20% = 500,000
            // Cap total at 200,000
            val config = MetaPromoConfiguration(
                discountByDates = null,
                discountByTicketAmounts = null,
                discountByPriceCategoryAmounts = null,
                promotionCodes = listOf(
                    MetaPromoPromotionCodeConfig(
                        name = "MEGA20",
                        code = "MEGA20",
                        discountPercent = 20,
                        discountPrice = null,
                        needCheckQuantity = false,
                        originalQuantity = null,
                    )
                ),
                maxDiscountAmount = MetaPromoMaxDiscountAmountConfig(
                    maxDiscountPerType = null,
                    maxDiscountTotal = 200000
                )
            )

            val tickets = (1..5).map { buildTicketProto("t$it", "A$it", "PREMIUM", 500000) }
            val tentative = (1..5).map { TentativePriceTicketRequest("t$it", 1, "PREMIUM") }

            val response = simulateTentativePrice(tickets, tentative, config, promotionCode = "MEGA20")

            // 20% of 2,500,000 = 500,000. Capped at 200,000
            assertEquals(200000L, response.priceDiscount)
            assertEquals(2530000L, response.priceOriginal) // 2,500,000 + 30,000 ship
            assertEquals(2330000L, response.priceTotal)     // 2,300,000 + 30,000 ship
        }

        @Test
        fun `multiple quantity tickets with coupon capped`() = runTest {
            // 1 ticket with quantity 4 @ 150,000 = 600,000
            // Coupon FLAT100K gives fixed 100,000
            // Cap at 70,000
            val config = MetaPromoConfiguration(
                discountByDates = null,
                discountByTicketAmounts = null,
                discountByPriceCategoryAmounts = null,
                promotionCodes = listOf(
                    MetaPromoPromotionCodeConfig(
                        name = "FLAT100K",
                        code = "FLAT100K",
                        discountPercent = null,
                        discountPrice = 100000,
                        needCheckQuantity = false,
                        originalQuantity = null,
                    )
                ),
                maxDiscountAmount = MetaPromoMaxDiscountAmountConfig(
                    maxDiscountPerType = null,
                    maxDiscountTotal = 70000
                )
            )

            val ticket1 = buildTicketProto("t1", "A1", "VIP", 150000)
            val tentative = listOf(TentativePriceTicketRequest("t1", 4, "VIP"))

            val response = simulateTentativePrice(listOf(ticket1), tentative, config, promotionCode = "FLAT100K")

            // Fixed 100,000 capped at 70,000
            assertEquals(70000L, response.priceDiscount)
            assertEquals(630000L, response.priceOriginal) // 600,000 + 30,000 ship
            assertEquals(560000L, response.priceTotal)     // 530,000 + 30,000 ship
        }
    }
}
