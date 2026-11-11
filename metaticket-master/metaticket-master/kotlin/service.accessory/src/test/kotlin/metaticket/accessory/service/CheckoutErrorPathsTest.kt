package metaticket.accessory.service

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.runBlocking
import metaticket.accessory.configuration.Environment
import metaticket.accessory.database.CatalogDatabaseService
import metaticket.accessory.database.OrderDatabaseService
import metaticket.accessory.database.ReconciliationDatabaseService
import metaticket.accessory.gateway.AccessoryMailGateway
import metaticket.accessory.gateway.PaymentGateway
import metaticket.accessory.pojo.db.AccessoryDocument
import metaticket.accessory.pojo.db.AccessoryOrderDocument
import metaticket.accessory.pojo.db.AccessoryShowDocument
import metaticket.accessory.pojo.db.AccessoryShowStatus
import metaticket.accessory.pojo.db.AccessoryVariant
import metaticket.accessory.pojo.db.FulfillmentType
import metaticket.accessory.pojo.db.UNLIMITED_STOCK
import metaticket.accessory.pojo.request.CheckoutInput
import metaticket.accessory.pojo.request.CheckoutLine
import metaticket.accessory.pojo.request.CheckoutValidationCode
import metaticket.accessory.pojo.request.CheckoutValidationException
import metaticket.accessory.pojo.request.CheckoutValidator
import metaticket.accessory.security.PublicId
import org.bson.types.ObjectId

/**
 * Unit tests for checkout error paths (task 6.8) and Property 10 (pickup-only fulfillment).
 *
 * **Property 10: Pickup-only fulfillment — Validates: Requirements 6.2, 6.3, 6.5, 6.6, 6.14, 14.1, 14.2**
 *
 * These tests exercise the exact status classification the requirements mandate for each rejection,
 * and confirm that a successful checkout always produces a `PickupAtShow` order with no shipping
 * concept (none exists in the model). The DB-backed rejections (`404` unknown id) and the success
 * path drive the **real** [AccessoryOrderServiceImpl.checkout] with mocked catalog/order/gateway
 * collaborators (no live Mongo/gRPC). The purely structural rejections (empty cart, invalid
 * quantity, shipping/fulfillment) are driven through the real pure [CheckoutValidator] gate, and the
 * variant/stock rejections through the real [AccessoryOrderServiceImpl.priceLine] core.
 */
class CheckoutErrorPathsTest : FunSpec({

    // PublicId.encode/decode need a configured secret key (checksum secret).
    // AES/CBC needs a 16-byte key + 16-byte IV (the Checksum util derives the IV from the key),
    // so the secret must be exactly 16 ASCII characters.
    Environment.secretKey = "0123456789abcdef"

    // ---- Shared catalog fixtures -------------------------------------------------------------

    val show = AccessoryShowDocument(
        id = ObjectId().toHexString(),
        code = "TF",
        codePrefix = "TF",
        name = "The Flob",
        status = AccessoryShowStatus.Open,
        pickupNote = "Nhận sản phẩm tại show",
    )
    val singleSku = AccessoryDocument(
        id = ObjectId().toHexString(),
        accessoryShowId = show.id,
        sku = "POSTER",
        name = "Poster",
        price = 100_000,
        stock = 5,
    )
    val variantAcc = AccessoryDocument(
        id = ObjectId().toHexString(),
        accessoryShowId = show.id,
        sku = "TEE",
        name = "Áo thun",
        price = 250_000,
        stock = 0,
        variants = listOf(
            AccessoryVariant(key = "M", label = "Size M", stock = 3),
            AccessoryVariant(key = "L", label = "Size L", stock = UNLIMITED_STOCK),
        ),
    )

    fun encoded(rawId: String) = PublicId.encode(rawId)

    fun checkoutInput(
        lines: List<CheckoutLine>,
        name: String = "Nguyen Van A",
        phone: String = "0987654321",
        email: String = "a@example.com",
        discountCode: String? = null,
    ) = CheckoutInput(
        lines = lines,
        customerName = name,
        customerPhone = phone,
        customerEmail = email,
        discountCode = discountCode,
    )

    /**
     * Build a service whose catalog returns [accessories] (and [show] when queried), with every
     * other collaborator relaxed/mocked. Sufficient for the pre-order-creation error paths (which
     * throw before any order/payment work) and for the success path below.
     */
    fun buildService(
        accessories: List<AccessoryDocument>,
        orderDb: OrderDatabaseService = mockk(relaxed = true),
        orderCodeService: OrderCodeService = mockk(relaxed = true),
        paymentGateway: PaymentGateway = mockk(relaxed = true),
    ): AccessoryOrderServiceImpl {
        // Relaxed so the reserve-on-create stock reservation defaults to StockReserveOutcome.RESERVED
        // (its first enum constant) on the happy paths; the error paths throw at validation before
        // reservation is ever reached.
        val catalogDb = mockk<CatalogDatabaseService>(relaxed = true)
        coEvery { catalogDb.loadAccessoriesByIds(any()) } returns accessories
        coEvery { catalogDb.loadShowById(show.id) } returns show
        return AccessoryOrderServiceImpl(
            catalogDb = catalogDb,
            orderDb = orderDb,
            discountService = mockk(relaxed = true),
            orderCodeService = orderCodeService,
            paymentGateway = paymentGateway,
            orderStateMachine = mockk(relaxed = true),
            reconciliationDb = mockk<ReconciliationDatabaseService>(relaxed = true),
            mailGateway = mockk<AccessoryMailGateway>(relaxed = true),
            jobrunrGateway = mockk(relaxed = true),
        )
    }

    // ---- Empty cart → 400 (Req 6.2) ---------------------------------------------------------

    test("empty cart is rejected as a 400 empty-cart") {
        // The pure gate classifies an empty cart as EMPTY_CART, which the controller maps to 400.
        val errors = CheckoutValidator.validate(checkoutInput(emptyList()))
        errors.map { it.code } shouldContain CheckoutValidationCode.EMPTY_CART
        // The domain exception for the same condition carries a 400 status.
        CheckoutException.EmptyCart().status shouldBe 400

        // Driven through checkout, an empty cart is rejected before any DB work.
        val service = buildService(emptyList())
        val ex = shouldThrow<CheckoutValidationException> {
            runBlocking { service.checkout(checkoutInput(emptyList())) }
        }
        ex.errors.map { it.code } shouldContain CheckoutValidationCode.EMPTY_CART
    }

    // ---- Invalid quantity → 400 (Req 6.14) --------------------------------------------------

    test("a non-positive or over-max quantity is rejected as invalid-quantity (400)") {
        val zeroQty = checkoutInput(listOf(CheckoutLine(encoded(singleSku.id), null, 0)))
        CheckoutValidator.validate(zeroQty).map { it.code } shouldContain CheckoutValidationCode.INVALID_QUANTITY

        val overMax = checkoutInput(listOf(CheckoutLine(encoded(singleSku.id), null, 100)))
        CheckoutValidator.validate(overMax).map { it.code } shouldContain CheckoutValidationCode.INVALID_QUANTITY
    }

    // ---- Unknown accessory id → 404 (Req 6.3) -----------------------------------------------

    test("an unknown accessory id is rejected as a 404 unknown-accessory") {
        // The catalog returns no rows for the referenced id.
        val service = buildService(emptyList())
        val input = checkoutInput(listOf(CheckoutLine(encoded(ObjectId().toHexString()), null, 1)))
        val ex = shouldThrow<CheckoutException.UnknownAccessory> {
            runBlocking { service.checkout(input) }
        }
        ex.status shouldBe 404
    }

    // ---- Not a single Open show / inactive accessory → 422 (Req 6.4) ------------------------

    test("an inactive accessory is rejected as a 422 not-single-open-show") {
        val inactive = singleSku.copy(active = false)
        val service = buildService(listOf(inactive))
        val input = checkoutInput(listOf(CheckoutLine(encoded(inactive.id), null, 1)))
        val ex = shouldThrow<CheckoutException.NotSingleOpenShow> {
            runBlocking { service.checkout(input) }
        }
        ex.status shouldBe 422
    }

    test("a closed owning show is rejected as a 422 show-not-on-sale") {
        val closedShow = show.copy(status = AccessoryShowStatus.Closed)
        val catalogDb = mockk<CatalogDatabaseService>()
        coEvery { catalogDb.loadAccessoriesByIds(any()) } returns listOf(singleSku)
        coEvery { catalogDb.loadShowById(show.id) } returns closedShow
        val service = AccessoryOrderServiceImpl(
            catalogDb = catalogDb,
            orderDb = mockk(relaxed = true),
            discountService = mockk(relaxed = true),
            orderCodeService = mockk(relaxed = true),
            paymentGateway = mockk(relaxed = true),
            orderStateMachine = mockk(relaxed = true),
            reconciliationDb = mockk(relaxed = true),
            mailGateway = mockk(relaxed = true),
            jobrunrGateway = mockk(relaxed = true),
        )
        val input = checkoutInput(listOf(CheckoutLine(encoded(singleSku.id), null, 1)))
        val ex = shouldThrow<CheckoutException.ShowNotOnSale> {
            runBlocking { service.checkout(input) }
        }
        ex.status shouldBe 422
        ex.showStatus shouldBe AccessoryShowStatus.Closed.name
    }

    // ---- Invalid variant → 400 (Req 6.5) ----------------------------------------------------

    test("variant-selection inconsistencies are rejected as 400 invalid-variant") {
        val service = buildService(listOf(variantAcc, singleSku))

        // has-variants accessory but no variantKey supplied.
        shouldThrow<CheckoutException.InvalidVariant> {
            runBlocking { service.checkout(checkoutInput(listOf(CheckoutLine(encoded(variantAcc.id), null, 1)))) }
        }.status shouldBe 400

        // has-variants accessory with an unknown variantKey.
        shouldThrow<CheckoutException.InvalidVariant> {
            runBlocking { service.checkout(checkoutInput(listOf(CheckoutLine(encoded(variantAcc.id), "XXL", 1)))) }
        }.status shouldBe 400

        // single-SKU accessory carrying a variantKey.
        shouldThrow<CheckoutException.InvalidVariant> {
            runBlocking { service.checkout(checkoutInput(listOf(CheckoutLine(encoded(singleSku.id), "M", 1)))) }
        }.status shouldBe 400
    }

    // ---- Out of stock → 409 (Req 6.6) -------------------------------------------------------

    test("a line exceeding its applicable stock is rejected as 409 out-of-stock") {
        // Use a low-stock single-SKU so the quantity stays within the per-line cap (5) yet exceeds stock.
        val lowStock = singleSku.copy(stock = 2)
        val service = buildService(listOf(lowStock, variantAcc))

        // single-SKU: stock 2, request 3 (within the per-line cap but over stock).
        shouldThrow<CheckoutException.OutOfStock> {
            runBlocking { service.checkout(checkoutInput(listOf(CheckoutLine(encoded(lowStock.id), null, 3)))) }
        }.status shouldBe 409

        // variant M: stock 3, request 4.
        shouldThrow<CheckoutException.OutOfStock> {
            runBlocking { service.checkout(checkoutInput(listOf(CheckoutLine(encoded(variantAcc.id), "M", 4)))) }
        }.status shouldBe 409
    }

    // ---- Inactive variant → 422 (Req 6.4 parity) -------------------------------------------

    test("a line selecting an inactive variant is rejected as 422 inactive-variant") {
        // variant M is withdrawn from sale (active = false) while still in stock.
        val withInactiveM = variantAcc.copy(
            variants = listOf(
                AccessoryVariant(key = "M", label = "Size M", stock = 3, active = false),
                AccessoryVariant(key = "L", label = "Size L", stock = UNLIMITED_STOCK),
            ),
        )
        val service = buildService(listOf(withInactiveM))
        val ex = shouldThrow<CheckoutException.InactiveVariant> {
            runBlocking { service.checkout(checkoutInput(listOf(CheckoutLine(encoded(withInactiveM.id), "M", 1)))) }
        }
        ex.status shouldBe 422

        // The still-active variant L on the same accessory remains purchasable.
        val orderLine = AccessoryOrderServiceImpl.priceLine(
            CheckoutLine(encoded(withInactiveM.id), "L", 1), withInactiveM,
        )
        orderLine.variantKey shouldBe "L"
    }

    test("an unlimited-stock variant never blocks on quantity") {
        // variant L has unlimited stock; a large quantity must NOT raise OutOfStock in pricing.
        val orderLine = AccessoryOrderServiceImpl.priceLine(
            CheckoutLine(encoded(variantAcc.id), "L", 99), variantAcc,
        )
        orderLine.quantity shouldBe 99
        orderLine.variantKey shouldBe "L"
    }

    // ---- Aggregate stock across multiple lines → 409 (Req 6.6) ------------------------------

    test("the same variant split across lines is rejected when the combined quantity exceeds stock") {
        // variant M: stock 3. Two lines of 2 each pass the per-line gate but sum to 4 > 3.
        val service = buildService(listOf(variantAcc))
        val input = checkoutInput(
            listOf(
                CheckoutLine(encoded(variantAcc.id), "M", 2),
                CheckoutLine(encoded(variantAcc.id), "M", 2),
            ),
        )
        shouldThrow<CheckoutException.OutOfStock> {
            runBlocking { service.checkout(input) }
        }.status shouldBe 409
    }

    test("the same single-SKU accessory split across lines is rejected when the combined quantity exceeds stock") {
        // singleSku: stock 5. Lines of 3 + 3 = 6 > 5.
        val service = buildService(listOf(singleSku))
        val input = checkoutInput(
            listOf(
                CheckoutLine(encoded(singleSku.id), null, 3),
                CheckoutLine(encoded(singleSku.id), null, 3),
            ),
        )
        shouldThrow<CheckoutException.OutOfStock> {
            runBlocking { service.checkout(input) }
        }.status shouldBe 409
    }

    test("splitting different variants of one accessory checks each variant's stock independently") {
        // variant M (stock 3) and variant L (unlimited) split across lines: M=3 exactly at limit and
        // L=5 (unlimited, within the per-line cap) must BOTH pass — aggregate gate is per stock cell.
        val insertedSlot = slot<AccessoryOrderDocument>()
        val orderDb = mockk<OrderDatabaseService>(relaxed = true)
        coEvery { orderDb.insertOrder(capture(insertedSlot)) } answers { insertedSlot.captured }
        val orderCodeService = mockk<OrderCodeService>()
        coEvery {
            orderCodeService.allocateWithRetry<AccessoryOrderDocument>(any(), any(), any(), any())
        } coAnswers {
            val block = arg<suspend (String) -> AccessoryOrderDocument>(3)
            block.invoke("TF2026-0002")
        }
        val paymentGateway = mockk<PaymentGateway>()
        coEvery { paymentGateway.createPayment(any(), any(), any(), any(), any(), any()) } returns ObjectId().toHexString()

        val service = buildService(
            accessories = listOf(variantAcc),
            orderDb = orderDb,
            orderCodeService = orderCodeService,
            paymentGateway = paymentGateway,
        )
        val input = checkoutInput(
            listOf(
                CheckoutLine(encoded(variantAcc.id), "M", 3),
                CheckoutLine(encoded(variantAcc.id), "L", 5),
            ),
        )
        runBlocking { service.checkout(input) }
        insertedSlot.captured.lines.size shouldBe 2
    }

    // ---- Pickup-only / shipping rejection (Req 14.2, 14.3, 14.4) ----------------------------

    test("any shipping address/method field is rejected (pickup-only)") {
        val input = checkoutInput(listOf(CheckoutLine(encoded(singleSku.id), null, 1)))
        val errors = CheckoutValidator.validate(
            input,
            presentTopLevelFields = setOf("shippingAddress"),
        )
        errors.map { it.code } shouldContain CheckoutValidationCode.SHIPPING_NOT_SUPPORTED
    }

    test("an unknown fulfillment value is rejected") {
        val input = checkoutInput(listOf(CheckoutLine(encoded(singleSku.id), null, 1)))
        val errors = CheckoutValidator.validate(input, fulfillment = "Shipping")
        errors.map { it.code } shouldContain CheckoutValidationCode.INVALID_FULFILLMENT
    }

    // ---- Property 10: a successful default checkout still produces a PickupAtShow order ---------

    test("a successful checkout creates exactly one PickupAtShow order by default (Property 10)") {
        val insertedSlot = slot<AccessoryOrderDocument>()
        val orderDb = mockk<OrderDatabaseService>(relaxed = true)
        coEvery { orderDb.insertOrder(capture(insertedSlot)) } answers { insertedSlot.captured }

        // OrderCodeService.allocateWithRetry runs the supplied block with an allocated code.
        val orderCodeService = mockk<OrderCodeService>()
        coEvery {
            orderCodeService.allocateWithRetry<AccessoryOrderDocument>(any(), any(), any(), any())
        } coAnswers {
            val block = arg<suspend (String) -> AccessoryOrderDocument>(3)
            block.invoke("TF2026-0001")
        }

        val paymentGateway = mockk<PaymentGateway>()
        coEvery { paymentGateway.createPayment(any(), any(), any(), any(), any(), any()) } returns ObjectId().toHexString()

        val service = buildService(
            accessories = listOf(singleSku, variantAcc),
            orderDb = orderDb,
            orderCodeService = orderCodeService,
            paymentGateway = paymentGateway,
        )

        val input = checkoutInput(
            listOf(
                CheckoutLine(encoded(singleSku.id), null, 2),
                CheckoutLine(encoded(variantAcc.id), "M", 1),
            ),
        )
        val result = runBlocking { service.checkout(input) }

        // Exactly one order was inserted, and it is a PickupAtShow order (Property 10).
        insertedSlot.captured.fulfillment shouldBe FulfillmentType.PickupAtShow
        insertedSlot.captured.code shouldBe "TF2026-0001"
        // subtotal = 2*100_000 + 1*250_000 = 450_000 (catalog-derived).
        insertedSlot.captured.subtotal shouldBe 450_000
        result.orderCode shouldBe "TF2026-0001"

        // Payment was created for the accessory flow (orderType=ACCESSORY default).
        coVerify(exactly = 1) { paymentGateway.createPayment(any(), any(), any(), any(), any(), any()) }
        coVerify(exactly = 1) { orderDb.insertOrder(any()) }
    }
})
