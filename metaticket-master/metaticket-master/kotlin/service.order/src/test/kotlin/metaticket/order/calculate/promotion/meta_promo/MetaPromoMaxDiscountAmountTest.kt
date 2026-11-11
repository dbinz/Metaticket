package metaticket.order.calculate.promotion.meta_promo

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.test.runTest
import metaticket.order.calculate.promotion.*
import metaticket.order.database.service.PromotionCodeQuantityDBService
import metaticket.order.database.service.PromotionComboQuantityDBService
import metaticket.order.database.service.PromotionDBService
import metaticket.order.pojo.db.Promotion
import metaticket.order.pojo.db.PromotionCodeQuantity
import metaticket.order.pojo.request.TentativePriceTicketRequest
import metaticket.ticket.grpc.TicketMessage
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.util.*

class MetaPromoMaxDiscountAmountTest {

    private lateinit var plugin: MetaPromoPlugin
    private lateinit var jsonMapper: ObjectMapper
    private lateinit var promotionCodeQuantityDBService: PromotionCodeQuantityDBService
    private lateinit var promotionComboQuantityDBService: PromotionComboQuantityDBService
    private lateinit var promotionDBService: PromotionDBService

    private val showId = "6595773df323909fe23b954e"
    private val sessionId = "65957746f323909fe23b954f"

    @BeforeEach
    fun setup() {
        plugin = MetaPromoPlugin()
        jsonMapper = jacksonObjectMapper()
        promotionCodeQuantityDBService = mockk(relaxed = true)
        promotionComboQuantityDBService = mockk(relaxed = true)
        promotionDBService = mockk(relaxed = true)
    }

    // Helper to build a mocked TicketProto
    private fun buildTicketProto(id: String, sid: String, priceCat: String, basePrice: Long): TicketMessage.TicketProto {
        val ticket = mockk<TicketMessage.TicketProto>()
        every { ticket.id } returns id
        every { ticket.sid } returns sid
        every { ticket.priceCat } returns priceCat
        every { ticket.basePrice } returns basePrice
        every { ticket.hasDname() } returns false
        every { ticket.hasTicketSlots() } returns false
        return ticket
    }

    private fun buildCalculateInput(
        configuration: String,
        tentativeTickets: List<TentativePriceTicketRequest>,
        tickets: List<TicketMessage.TicketProto>,
        promotionCode: String? = null
    ): CalculatePromotionInput {
        return CalculatePromotionInput(
            showId = showId,
            sessionId = sessionId,
            tentativeTickets = tentativeTickets,
            tickets = tickets,
            jsonMapper = jsonMapper,
            configuration = configuration,
            promotionCode = promotionCode,
            promotionCodeQuantityDBService = promotionCodeQuantityDBService,
            promotionComboQuantityDBService = promotionComboQuantityDBService,
        )
    }

    @Nested
    inner class MaxDiscountTotalTests {

        @Test
        fun `no cap configured - discount applied without limit`() = runTest {
            // 2 tickets at 100,000đ each, early bird discount 20%
            val futureDate = System.currentTimeMillis() + 86400000L
            val config = MetaPromoConfiguration(
                discountByDates = listOf(
                    MetaPromoDiscountByDateConfig("Early Bird", futureDate, null, 20, null)
                ),
                discountByTicketAmounts = null,
                discountByPriceCategoryAmounts = null,
                promotionCodes = null,
                maxDiscountAmount = null
            )
            val configJson = jsonMapper.writeValueAsString(config)

            val ticket1 = buildTicketProto("t1", "S1", "VIP", 100000)
            val ticket2 = buildTicketProto("t2", "S2", "VIP", 100000)
            val tentative = listOf(
                TentativePriceTicketRequest("t1", 1, "VIP"),
                TentativePriceTicketRequest("t2", 1, "VIP")
            )

            val input = buildCalculateInput(configJson, tentative, listOf(ticket1, ticket2))
            val result = plugin.calculatePromotionDiscount(input)

            assertNotNull(result)
            // 20% of 200,000 = 40,000
            assertEquals(40000L, result!!.priceDiscount)
            assertEquals(160000L, result.priceTotal)
            assertEquals(200000L, result.priceOriginal)
        }

        @Test
        fun `maxDiscountTotal caps total discount`() = runTest {
            // 2 tickets at 100,000đ each, early bird 50% = 100,000đ discount
            // Cap at 30,000đ
            val futureDate = System.currentTimeMillis() + 86400000L
            val config = MetaPromoConfiguration(
                discountByDates = listOf(
                    MetaPromoDiscountByDateConfig("Early Bird", futureDate, null, 50, null)
                ),
                discountByTicketAmounts = null,
                discountByPriceCategoryAmounts = null,
                promotionCodes = null,
                maxDiscountAmount = MetaPromoMaxDiscountAmountConfig(
                    maxDiscountPerType = null,
                    maxDiscountTotal = 30000
                )
            )
            val configJson = jsonMapper.writeValueAsString(config)

            val ticket1 = buildTicketProto("t1", "S1", "VIP", 100000)
            val ticket2 = buildTicketProto("t2", "S2", "VIP", 100000)
            val tentative = listOf(
                TentativePriceTicketRequest("t1", 1, "VIP"),
                TentativePriceTicketRequest("t2", 1, "VIP")
            )

            val input = buildCalculateInput(configJson, tentative, listOf(ticket1, ticket2))
            val result = plugin.calculatePromotionDiscount(input)

            assertNotNull(result)
            // Discount should be capped at 30,000
            assertEquals(30000L, result!!.priceDiscount)
            assertEquals(170000L, result.priceTotal) // 200,000 - 30,000
        }

        @Test
        fun `maxDiscountTotal does not reduce discount below cap`() = runTest {
            // 2 tickets at 100,000đ each, early bird 10% = 20,000đ discount
            // Cap at 50,000đ (higher than actual discount)
            val futureDate = System.currentTimeMillis() + 86400000L
            val config = MetaPromoConfiguration(
                discountByDates = listOf(
                    MetaPromoDiscountByDateConfig("Early Bird", futureDate, null, 10, null)
                ),
                discountByTicketAmounts = null,
                discountByPriceCategoryAmounts = null,
                promotionCodes = null,
                maxDiscountAmount = MetaPromoMaxDiscountAmountConfig(
                    maxDiscountPerType = null,
                    maxDiscountTotal = 50000
                )
            )
            val configJson = jsonMapper.writeValueAsString(config)

            val ticket1 = buildTicketProto("t1", "S1", "VIP", 100000)
            val ticket2 = buildTicketProto("t2", "S2", "VIP", 100000)
            val tentative = listOf(
                TentativePriceTicketRequest("t1", 1, "VIP"),
                TentativePriceTicketRequest("t2", 1, "VIP")
            )

            val input = buildCalculateInput(configJson, tentative, listOf(ticket1, ticket2))
            val result = plugin.calculatePromotionDiscount(input)

            assertNotNull(result)
            // Discount is only 20,000 which is below cap of 50,000 - no change
            assertEquals(20000L, result!!.priceDiscount)
            assertEquals(180000L, result.priceTotal)
        }

        @Test
        fun `maxDiscountTotal caps combined discounts from multiple types`() = runTest {
            // Early bird 10% + Combo 3 tickets = fixed 50,000đ discount
            // Total discount would be 30,000 + 50,000 = 80,000
            // Cap at 60,000
            val futureDate = System.currentTimeMillis() + 86400000L
            val config = MetaPromoConfiguration(
                discountByDates = listOf(
                    MetaPromoDiscountByDateConfig("Early Bird", futureDate, null, 10, null)
                ),
                discountByTicketAmounts = listOf(
                    MetaPromoDiscountByTicketAmountConfig(
                        ticketAmountBelow = 3,
                        ticketAmountAbove = 5,
                        discountPercent = null,
                        discountPrice = 50000,
                        id = null,
                        needCheckQuantity = false,
                        originalQuantity = null,
                        quantityType = null,
                    )
                ),
                discountByPriceCategoryAmounts = null,
                promotionCodes = null,
                maxDiscountAmount = MetaPromoMaxDiscountAmountConfig(
                    maxDiscountPerType = null,
                    maxDiscountTotal = 60000
                )
            )
            val configJson = jsonMapper.writeValueAsString(config)

            val ticket1 = buildTicketProto("t1", "S1", "VIP", 100000)
            val ticket2 = buildTicketProto("t2", "S2", "VIP", 100000)
            val ticket3 = buildTicketProto("t3", "S3", "VIP", 100000)
            val tentative = listOf(
                TentativePriceTicketRequest("t1", 1, "VIP"),
                TentativePriceTicketRequest("t2", 1, "VIP"),
                TentativePriceTicketRequest("t3", 1, "VIP")
            )

            val input = buildCalculateInput(configJson, tentative, listOf(ticket1, ticket2, ticket3))
            val result = plugin.calculatePromotionDiscount(input)

            assertNotNull(result)
            // Without cap: 10% of 300,000 = 30,000 (date) + 50,000 (combo) = 80,000
            // With cap: 60,000
            assertEquals(60000L, result!!.priceDiscount)
            assertEquals(240000L, result.priceTotal) // 300,000 - 60,000
        }
    }

    @Nested
    inner class MaxDiscountPerTypeTests {

        @Test
        fun `cap discountByDate type only`() = runTest {
            // Early bird 50% on 200,000 = 100,000. Cap date discount at 30,000
            val futureDate = System.currentTimeMillis() + 86400000L
            val config = MetaPromoConfiguration(
                discountByDates = listOf(
                    MetaPromoDiscountByDateConfig("Early Bird", futureDate, null, 50, null)
                ),
                discountByTicketAmounts = null,
                discountByPriceCategoryAmounts = null,
                promotionCodes = null,
                maxDiscountAmount = MetaPromoMaxDiscountAmountConfig(
                    maxDiscountPerType = mapOf("discountByDate" to 30000L),
                    maxDiscountTotal = null
                )
            )
            val configJson = jsonMapper.writeValueAsString(config)

            val ticket1 = buildTicketProto("t1", "S1", "VIP", 100000)
            val ticket2 = buildTicketProto("t2", "S2", "VIP", 100000)
            val tentative = listOf(
                TentativePriceTicketRequest("t1", 1, "VIP"),
                TentativePriceTicketRequest("t2", 1, "VIP")
            )

            val input = buildCalculateInput(configJson, tentative, listOf(ticket1, ticket2))
            val result = plugin.calculatePromotionDiscount(input)

            assertNotNull(result)
            // Date discount capped at 30,000 (was 100,000)
            assertEquals(30000L, result!!.priceDiscount)
            assertEquals(170000L, result.priceTotal) // 200,000 - 30,000
        }

        @Test
        fun `cap discountByTicketAmount (combo) type only`() = runTest {
            // Combo 3 tickets = 50% discount = 150,000. Cap combo at 50,000
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
                promotionCodes = null,
                maxDiscountAmount = MetaPromoMaxDiscountAmountConfig(
                    maxDiscountPerType = mapOf("discountByTicketAmount" to 50000L),
                    maxDiscountTotal = null
                )
            )
            val configJson = jsonMapper.writeValueAsString(config)

            val ticket1 = buildTicketProto("t1", "S1", "VIP", 100000)
            val ticket2 = buildTicketProto("t2", "S2", "VIP", 100000)
            val ticket3 = buildTicketProto("t3", "S3", "VIP", 100000)
            val tentative = listOf(
                TentativePriceTicketRequest("t1", 1, "VIP"),
                TentativePriceTicketRequest("t2", 1, "VIP"),
                TentativePriceTicketRequest("t3", 1, "VIP")
            )

            val input = buildCalculateInput(configJson, tentative, listOf(ticket1, ticket2, ticket3))
            val result = plugin.calculatePromotionDiscount(input)

            assertNotNull(result)
            // Combo discount: 50% of 300,000 = 150,000. Capped at 50,000
            assertEquals(50000L, result!!.priceDiscount)
            assertEquals(250000L, result.priceTotal) // 300,000 - 50,000
        }

        @Test
        fun `cap promotionCode type only`() = runTest {
            // Coupon gives 100,000đ fixed discount. Cap coupon at 40,000
            val config = MetaPromoConfiguration(
                discountByDates = null,
                discountByTicketAmounts = null,
                discountByPriceCategoryAmounts = null,
                promotionCodes = listOf(
                    MetaPromoPromotionCodeConfig(
                        name = "SALE100K",
                        code = "SALE100K",
                        discountPercent = null,
                        discountPrice = 100000,
                        needCheckQuantity = false,
                        originalQuantity = null,
                    )
                ),
                maxDiscountAmount = MetaPromoMaxDiscountAmountConfig(
                    maxDiscountPerType = mapOf("promotionCode" to 40000L),
                    maxDiscountTotal = null
                )
            )
            val configJson = jsonMapper.writeValueAsString(config)

            val ticket1 = buildTicketProto("t1", "S1", "VIP", 100000)
            val tentative = listOf(TentativePriceTicketRequest("t1", 1, "VIP"))

            val input = buildCalculateInput(configJson, tentative, listOf(ticket1), promotionCode = "SALE100K")
            val result = plugin.calculatePromotionDiscount(input)

            assertNotNull(result)
            // Coupon discount was 100,000, capped at 40,000
            assertEquals(40000L, result!!.priceDiscount)
            assertEquals(60000L, result.priceTotal) // 100,000 - 40,000
        }

        @Test
        fun `per-type cap applied before total cap`() = runTest {
            // Early bird 50% = 100,000. Combo 50,000 fixed.
            // Cap date at 30,000. Cap total at 70,000.
            // After per-type: 30,000 + 50,000 = 80,000
            // After total cap: 70,000
            val futureDate = System.currentTimeMillis() + 86400000L
            val config = MetaPromoConfiguration(
                discountByDates = listOf(
                    MetaPromoDiscountByDateConfig("Early Bird", futureDate, null, 50, null)
                ),
                discountByTicketAmounts = listOf(
                    MetaPromoDiscountByTicketAmountConfig(
                        ticketAmountBelow = 2,
                        ticketAmountAbove = 5,
                        discountPercent = null,
                        discountPrice = 50000,
                        id = null,
                        needCheckQuantity = false,
                        originalQuantity = null,
                        quantityType = null,
                    )
                ),
                discountByPriceCategoryAmounts = null,
                promotionCodes = null,
                maxDiscountAmount = MetaPromoMaxDiscountAmountConfig(
                    maxDiscountPerType = mapOf("discountByDate" to 30000L),
                    maxDiscountTotal = 70000
                )
            )
            val configJson = jsonMapper.writeValueAsString(config)

            val ticket1 = buildTicketProto("t1", "S1", "VIP", 100000)
            val ticket2 = buildTicketProto("t2", "S2", "VIP", 100000)
            val tentative = listOf(
                TentativePriceTicketRequest("t1", 1, "VIP"),
                TentativePriceTicketRequest("t2", 1, "VIP")
            )

            val input = buildCalculateInput(configJson, tentative, listOf(ticket1, ticket2))
            val result = plugin.calculatePromotionDiscount(input)

            assertNotNull(result)
            // Per-type cap applied: date reduced from 100,000 to 30,000. Combo stays at 50,000.
            // Total = 80,000 > 70,000 cap. Final = 70,000
            assertEquals(70000L, result!!.priceDiscount)
            assertEquals(130000L, result.priceTotal) // 200,000 - 70,000
        }

        @Test
        fun `per-type cap does not affect uncapped types`() = runTest {
            // Early bird 10% = 20,000. Combo fixed 50,000.
            // Only cap date at 10,000. Combo has no cap.
            val futureDate = System.currentTimeMillis() + 86400000L
            val config = MetaPromoConfiguration(
                discountByDates = listOf(
                    MetaPromoDiscountByDateConfig("Early Bird", futureDate, null, 10, null)
                ),
                discountByTicketAmounts = listOf(
                    MetaPromoDiscountByTicketAmountConfig(
                        ticketAmountBelow = 2,
                        ticketAmountAbove = 5,
                        discountPercent = null,
                        discountPrice = 50000,
                        id = null,
                        needCheckQuantity = false,
                        originalQuantity = null,
                        quantityType = null,
                    )
                ),
                discountByPriceCategoryAmounts = null,
                promotionCodes = null,
                maxDiscountAmount = MetaPromoMaxDiscountAmountConfig(
                    maxDiscountPerType = mapOf("discountByDate" to 10000L),
                    maxDiscountTotal = null
                )
            )
            val configJson = jsonMapper.writeValueAsString(config)

            val ticket1 = buildTicketProto("t1", "S1", "VIP", 100000)
            val ticket2 = buildTicketProto("t2", "S2", "VIP", 100000)
            val tentative = listOf(
                TentativePriceTicketRequest("t1", 1, "VIP"),
                TentativePriceTicketRequest("t2", 1, "VIP")
            )

            val input = buildCalculateInput(configJson, tentative, listOf(ticket1, ticket2))
            val result = plugin.calculatePromotionDiscount(input)

            assertNotNull(result)
            // Date: was 20,000, capped at 10,000. Excess = 10,000.
            // Combo: 50,000 (no cap).
            // Total: 10,000 + 50,000 = 60,000
            assertEquals(60000L, result!!.priceDiscount)
            assertEquals(140000L, result.priceTotal) // 200,000 - 60,000
        }
    }

    @Nested
    inner class CreatePromotionTests {

        @Test
        fun `createPromotion with maxDiscountAmount type using flat fields`() = runTest {
            val data = """
                [
                    {
                        "type": "discountByDate",
                        "name": "Early Bird",
                        "date": "2026-12-31T00:00:00+07:00",
                        "discountOption": "DiscountPercent",
                        "discountValue": 10
                    },
                    {
                        "type": "maxDiscountAmount",
                        "maxDiscountTotal": 50000,
                        "enablePerType": true,
                        "maxDiscountByDate": 20000,
                        "maxDiscountByTicketAmount": 30000,
                        "maxDiscountByPromotionCode": null
                    }
                ]
            """.trimIndent()

            val promotionSlot = slot<Promotion>()
            coEvery { promotionDBService.createPromotion(capture(promotionSlot)) } returns "someId"

            val input = CreatePromotionInput(
                data = data,
                showId = showId,
                sessionId = sessionId,
                promotionDBService = promotionDBService,
                promotionCodeQuantityDBService = promotionCodeQuantityDBService,
                promotionComboQuantityDBService = promotionComboQuantityDBService,
                jsonMapper = jsonMapper,
            )

            val result = plugin.createPromotion(input)
            assertTrue(result)

            // Parse the saved configuration to verify maxDiscountAmount is included
            val savedConfig = jsonMapper.readValue(promotionSlot.captured.configuration, MetaPromoConfiguration::class.java)
            assertNotNull(savedConfig.maxDiscountAmount)
            assertEquals(50000L, savedConfig.maxDiscountAmount!!.maxDiscountTotal)
            assertNotNull(savedConfig.maxDiscountAmount!!.maxDiscountPerType)
            assertEquals(20000L, savedConfig.maxDiscountAmount!!.maxDiscountPerType!!["discountByDate"])
            assertEquals(30000L, savedConfig.maxDiscountAmount!!.maxDiscountPerType!!["discountByTicketAmount"])
            assertNull(savedConfig.maxDiscountAmount!!.maxDiscountPerType!!["promotionCode"])
        }

        @Test
        fun `createPromotion without maxDiscountAmount type keeps maxDiscountAmount null`() = runTest {
            val data = """
                [
                    {
                        "type": "discountByDate",
                        "name": "Early Bird",
                        "date": "2026-12-31T00:00:00+07:00",
                        "discountOption": "DiscountPercent",
                        "discountValue": 10
                    }
                ]
            """.trimIndent()

            val promotionSlot = slot<Promotion>()
            coEvery { promotionDBService.createPromotion(capture(promotionSlot)) } returns "someId"

            val input = CreatePromotionInput(
                data = data,
                showId = showId,
                sessionId = sessionId,
                promotionDBService = promotionDBService,
                promotionCodeQuantityDBService = promotionCodeQuantityDBService,
                promotionComboQuantityDBService = promotionComboQuantityDBService,
                jsonMapper = jsonMapper,
            )

            val result = plugin.createPromotion(input)
            assertTrue(result)

            val savedConfig = jsonMapper.readValue(promotionSlot.captured.configuration, MetaPromoConfiguration::class.java)
            assertNull(savedConfig.maxDiscountAmount)
        }

        @Test
        fun `createPromotion with maxDiscountTotal only (no per-type)`() = runTest {
            val data = """
                [
                    {
                        "type": "maxDiscountAmount",
                        "maxDiscountTotal": 100000,
                        "enablePerType": false,
                        "maxDiscountByDate": null,
                        "maxDiscountByTicketAmount": null,
                        "maxDiscountByPromotionCode": null
                    }
                ]
            """.trimIndent()

            val promotionSlot = slot<Promotion>()
            coEvery { promotionDBService.createPromotion(capture(promotionSlot)) } returns "someId"

            val input = CreatePromotionInput(
                data = data,
                showId = showId,
                sessionId = sessionId,
                promotionDBService = promotionDBService,
                promotionCodeQuantityDBService = promotionCodeQuantityDBService,
                promotionComboQuantityDBService = promotionComboQuantityDBService,
                jsonMapper = jsonMapper,
            )

            val result = plugin.createPromotion(input)
            assertTrue(result)

            val savedConfig = jsonMapper.readValue(promotionSlot.captured.configuration, MetaPromoConfiguration::class.java)
            assertNotNull(savedConfig.maxDiscountAmount)
            assertEquals(100000L, savedConfig.maxDiscountAmount!!.maxDiscountTotal)
            // enablePerType is false, no per-type fields set => perType map should be null or empty
            assertTrue(savedConfig.maxDiscountAmount!!.maxDiscountPerType == null || savedConfig.maxDiscountAmount!!.maxDiscountPerType!!.isEmpty())
        }
    }

    @Nested
    inner class EdgeCaseTests {

        @Test
        fun `zero max discount total makes discount zero`() = runTest {
            val futureDate = System.currentTimeMillis() + 86400000L
            val config = MetaPromoConfiguration(
                discountByDates = listOf(
                    MetaPromoDiscountByDateConfig("Early Bird", futureDate, null, 20, null)
                ),
                discountByTicketAmounts = null,
                discountByPriceCategoryAmounts = null,
                promotionCodes = null,
                maxDiscountAmount = MetaPromoMaxDiscountAmountConfig(
                    maxDiscountPerType = null,
                    maxDiscountTotal = 0
                )
            )
            val configJson = jsonMapper.writeValueAsString(config)

            val ticket1 = buildTicketProto("t1", "S1", "VIP", 100000)
            val tentative = listOf(TentativePriceTicketRequest("t1", 1, "VIP"))

            val input = buildCalculateInput(configJson, tentative, listOf(ticket1))
            val result = plugin.calculatePromotionDiscount(input)

            assertNotNull(result)
            assertEquals(0L, result!!.priceDiscount)
            assertEquals(100000L, result.priceTotal)
        }

        @Test
        fun `no discount applied - cap has no effect`() = runTest {
            // No early bird (date in the past), no combo match, no code
            val pastDate = System.currentTimeMillis() - 86400000L
            val config = MetaPromoConfiguration(
                discountByDates = listOf(
                    MetaPromoDiscountByDateConfig("Expired", pastDate, null, 50, null)
                ),
                discountByTicketAmounts = null,
                discountByPriceCategoryAmounts = null,
                promotionCodes = null,
                maxDiscountAmount = MetaPromoMaxDiscountAmountConfig(
                    maxDiscountPerType = null,
                    maxDiscountTotal = 10000
                )
            )
            val configJson = jsonMapper.writeValueAsString(config)

            val ticket1 = buildTicketProto("t1", "S1", "VIP", 100000)
            val tentative = listOf(TentativePriceTicketRequest("t1", 1, "VIP"))

            val input = buildCalculateInput(configJson, tentative, listOf(ticket1))
            val result = plugin.calculatePromotionDiscount(input)

            assertNotNull(result)
            assertEquals(0L, result!!.priceDiscount)
            assertEquals(100000L, result.priceTotal)
        }

        @Test
        fun `price category amount discount capped via combo type cap`() = runTest {
            // Price cat condition: need at least 2 VIP tickets, gives 30% discount on matched items
            // 2 VIP tickets at 100,000 = price for discount base = 200,000. 30% = 60,000
            // Cap combo at 25,000
            val config = MetaPromoConfiguration(
                discountByDates = null,
                discountByTicketAmounts = null,
                discountByPriceCategoryAmounts = listOf(
                    MetaPromoDiscountByPriceCategoryAmountConfig(
                        conditions = listOf(
                            MetaPromoPriceCatConditionConfig("VIP", 2, null)
                        ),
                        discountPercent = 30,
                        discountPrice = null,
                        applyDiscountOn = ApplyDiscountOnType.MatchedItems
                    )
                ),
                promotionCodes = null,
                maxDiscountAmount = MetaPromoMaxDiscountAmountConfig(
                    maxDiscountPerType = mapOf("discountByTicketAmount" to 25000L),
                    maxDiscountTotal = null
                )
            )
            val configJson = jsonMapper.writeValueAsString(config)

            val ticket1 = buildTicketProto("t1", "S1", "VIP", 100000)
            val ticket2 = buildTicketProto("t2", "S2", "VIP", 100000)
            val tentative = listOf(
                TentativePriceTicketRequest("t1", 1, "VIP"),
                TentativePriceTicketRequest("t2", 1, "VIP")
            )

            val input = buildCalculateInput(configJson, tentative, listOf(ticket1, ticket2))
            val result = plugin.calculatePromotionDiscount(input)

            assertNotNull(result)
            // Discount was 60,000 (COMBO type), capped at 25,000
            assertEquals(25000L, result!!.priceDiscount)
            assertEquals(175000L, result.priceTotal) // 200,000 - 25,000
        }

        @Test
        fun `coupon discount with quantity check still respects cap`() = runTest {
            // Coupon gives 80% discount on 100,000 = 80,000. Cap coupon at 50,000.
            val config = MetaPromoConfiguration(
                discountByDates = null,
                discountByTicketAmounts = null,
                discountByPriceCategoryAmounts = null,
                promotionCodes = listOf(
                    MetaPromoPromotionCodeConfig(
                        name = "VIP80",
                        code = "VIP80",
                        discountPercent = 80,
                        discountPrice = null,
                        needCheckQuantity = true,
                        originalQuantity = 10,
                    )
                ),
                maxDiscountAmount = MetaPromoMaxDiscountAmountConfig(
                    maxDiscountPerType = mapOf("promotionCode" to 50000L),
                    maxDiscountTotal = null
                )
            )
            val configJson = jsonMapper.writeValueAsString(config)

            // Mock: code quantity exists and has remaining quantity
            coEvery {
                promotionCodeQuantityDBService.loadPromotionCodeQuantity(showId, sessionId, "VIP80")
            } returns PromotionCodeQuantity(
                id = "6595773df323909fe23b0001",
                showId = showId,
                sessionId = sessionId,
                code = "VIP80",
                originalQuantity = 10,
                quantity = 5,
                lastModified = Date()
            )

            val ticket1 = buildTicketProto("t1", "S1", "VIP", 100000)
            val tentative = listOf(TentativePriceTicketRequest("t1", 1, "VIP"))

            val input = buildCalculateInput(configJson, tentative, listOf(ticket1), promotionCode = "VIP80")
            val result = plugin.calculatePromotionDiscount(input)

            assertNotNull(result)
            // 80% of 100,000 = 80,000. Capped at 50,000
            assertEquals(50000L, result!!.priceDiscount)
            assertEquals(50000L, result.priceTotal) // 100,000 - 50,000
        }
    }
}
