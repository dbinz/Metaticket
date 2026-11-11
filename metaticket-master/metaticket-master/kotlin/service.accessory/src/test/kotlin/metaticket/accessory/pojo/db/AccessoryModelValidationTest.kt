package metaticket.accessory.pojo.db

import io.kotest.assertions.throwables.shouldNotThrowAny
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import org.bson.types.ObjectId

/**
 * Unit tests for data-model validation (task 2.4).
 *
 * Covers the price/stock bounds and unique variant keys on [AccessoryDocument]
 * (Req 2.1, 2.2), the subtotal/total invariants and pickup-only fulfillment on
 * [AccessoryOrderDocument] (Req 6.7, 6.8, 14.1), exercising the pure `validationErrors`/`validate`
 * helpers directly (no database needed).
 */
class AccessoryModelValidationTest : FunSpec({

    val showId = ObjectId().toHexString()
    val accessoryId = ObjectId().toHexString()
    val discountId = ObjectId().toHexString()

    fun accessory(
        price: Long = 100_000,
        stock: Int = 10,
        variants: List<AccessoryVariant> = emptyList(),
    ) = AccessoryDocument(
        accessoryShowId = showId,
        sku = "TSHIRT-1",
        name = "Áo thun",
        price = price,
        stock = stock,
        variants = variants,
    )

    fun line(
        unitPrice: Long,
        quantity: Int,
        variantKey: String? = null,
        variantLabel: String? = null,
    ) = AccessoryOrderLine(
        accessoryId = accessoryId,
        sku = "TSHIRT-1",
        name = "Áo thun",
        variantKey = variantKey,
        variantLabel = variantLabel,
        unitPrice = unitPrice,
        quantity = quantity,
    )

    fun order(
        lines: List<AccessoryOrderLine>,
        subtotal: Long,
        total: Long,
        discount: AccessoryOrderDiscount? = null,
        fulfillment: FulfillmentType = FulfillmentType.PickupAtShow,
    ) = AccessoryOrderDocument(
        accessoryShowId = showId,
        code = "TF2026-0001",
        lines = lines,
        subtotal = subtotal,
        discount = discount,
        total = total,
        customerName = "Nguyen Van A",
        customerPhone = "0900000000",
        customerEmail = "a@example.com",
        fulfillment = fulfillment,
    )

    // --- Accessory price bounds (Req 2.1) ---

    test("accessory with price > 0 passes validation") {
        accessory(price = 1).validationErrors().shouldBeEmpty()
        shouldNotThrowAny { accessory(price = 250_000).validate() }
    }

    test("accessory with price <= 0 is rejected") {
        accessory(price = 0).validationErrors().shouldContain("accessory `price` must be > 0 (was 0)")
        val ex = shouldThrow<AccessoryValidationException> { accessory(price = -5).validate() }
        ex.errors.any { it.contains("`price` must be > 0") } shouldBe true
    }

    // --- Single-SKU stock bounds (Req 2.2) ---

    test("single-SKU stock of -1 (unlimited) and >= 0 are valid") {
        accessory(stock = UNLIMITED_STOCK).validationErrors().shouldBeEmpty()
        accessory(stock = 0).validationErrors().shouldBeEmpty()
        accessory(stock = 999).validationErrors().shouldBeEmpty()
    }

    test("single-SKU stock below -1 is rejected") {
        accessory(stock = -2).validationErrors()
            .shouldContain("accessory `stock` must be >= -1 (was -2)")
    }

    // --- Unique variant keys (Req 2.2) ---

    test("variant product with unique non-blank keys is valid") {
        val variants = listOf(
            AccessoryVariant("S", "Size S", 5),
            AccessoryVariant("M", "Size M", UNLIMITED_STOCK),
            AccessoryVariant("L", "Size L", 0),
        )
        accessory(variants = variants).validationErrors().shouldBeEmpty()
    }

    test("duplicate variant keys are rejected") {
        val variants = listOf(
            AccessoryVariant("M", "Size M", 5),
            AccessoryVariant("M", "Size M again", 3),
        )
        accessory(variants = variants).validationErrors()
            .shouldContain("variant `key` must be unique within the accessory (duplicate: 'M')")
    }

    test("blank variant key is rejected") {
        val variants = listOf(AccessoryVariant("", "no key", 5))
        accessory(variants = variants).validationErrors()
            .shouldContain("variant[0] `key` must be non-blank")
    }

    test("variant stock below -1 is rejected") {
        val variants = listOf(AccessoryVariant("M", "Size M", -3))
        accessory(variants = variants).validationErrors()
            .shouldContain("variant 'M' `stock` must be >= -1 (was -3)")
    }

    // --- Subtotal invariant (Req 6.7) ---

    test("order subtotal must equal the sum of line unitPrice*quantity") {
        val lines = listOf(line(unitPrice = 100_000, quantity = 2), line(unitPrice = 50_000, quantity = 3))
        // correct subtotal = 200_000 + 150_000 = 350_000
        order(lines, subtotal = 350_000, total = 350_000).validationErrors().shouldBeEmpty()

        val wrong = order(lines, subtotal = 300_000, total = 300_000).validationErrors()
        wrong.any { it.contains("`subtotal`") && it.contains("350000") } shouldBe true
    }

    // --- Total invariant: total == max(0, subtotal - discount) (Req 6.8) ---

    test("order total must equal max(0, subtotal - discount)") {
        val lines = listOf(line(unitPrice = 100_000, quantity = 2)) // subtotal 200_000
        val discount = AccessoryOrderDiscount(
            code = "THEFLOB10",
            type = DiscountType.Percentage,
            amount = 20_000,
            discountCodeId = discountId,
        )
        order(lines, subtotal = 200_000, total = 180_000, discount = discount)
            .validationErrors().shouldBeEmpty()

        val wrongTotal = order(lines, subtotal = 200_000, total = 200_000, discount = discount)
            .validationErrors()
        wrongTotal.any { it.contains("`total`") } shouldBe true
    }

    test("order total is floored at zero when discount exceeds subtotal") {
        val lines = listOf(line(unitPrice = 30_000, quantity = 1)) // subtotal 30_000
        val discount = AccessoryOrderDiscount(
            code = "BIG",
            type = DiscountType.FixedAmount,
            amount = 30_000,
            discountCodeId = discountId,
        )
        // max(0, 30_000 - 30_000) = 0
        order(lines, subtotal = 30_000, total = 0, discount = discount)
            .validationErrors().shouldBeEmpty()
    }

    // --- Fulfillment modes ---

    test("default PickupAtShow order validates without delivery/pickup fields") {
        val lines = listOf(line(unitPrice = 100_000, quantity = 1))
        val o = order(lines, subtotal = 100_000, total = 100_000)
        o.fulfillment shouldBe FulfillmentType.PickupAtShow
        o.validationErrors().shouldBeEmpty()
    }

    test("Delivery order requires a complete deliveryAddress") {
        val lines = listOf(line(unitPrice = 100_000, quantity = 1))
        order(lines, subtotal = 100_000, total = 100_000, fulfillment = FulfillmentType.Delivery)
            .validationErrors().any { it.contains("deliveryAddress") } shouldBe true
    }

    // --- Order line pairing rule for variant fields ---

    test("order line variantKey/variantLabel must be paired") {
        val bad = line(unitPrice = 100_000, quantity = 1, variantKey = "M", variantLabel = null)
        bad.validationErrors(0).any { it.contains("must both be null or both be non-null") } shouldBe true

        val good = line(unitPrice = 100_000, quantity = 1, variantKey = "M", variantLabel = "Size M")
        good.validationErrors(0).shouldBeEmpty()
    }

    test("order line quantity must be > 0") {
        line(unitPrice = 100_000, quantity = 0).validationErrors(0)
            .shouldContain("line[0] `quantity` must be > 0 (was 0)")
    }
})
