package metaticket.accessory.service

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.property.Arb
import io.kotest.property.arbitrary.bind
import io.kotest.property.arbitrary.int
import io.kotest.property.arbitrary.list
import io.kotest.property.arbitrary.long
import io.kotest.property.arbitrary.map
import io.kotest.property.checkAll
import metaticket.accessory.pojo.db.AccessoryDocument
import metaticket.accessory.pojo.db.AccessoryOrderDiscount
import metaticket.accessory.pojo.db.AccessoryOrderDocument
import metaticket.accessory.pojo.db.AccessoryOrderStatus
import metaticket.accessory.pojo.db.DiscountType
import metaticket.accessory.pojo.db.FulfillmentType
import metaticket.accessory.pojo.db.UNLIMITED_STOCK
import metaticket.accessory.pojo.db.validationErrors
import metaticket.accessory.pojo.request.CheckoutLine
import org.bson.types.ObjectId
import kotlin.math.max

/**
 * Property 3: Total integrity (task 6.4).
 *
 * **Validates: Requirements 4.3, 4.4, 4.5, 6.7, 6.8**
 *
 * ∀ o ∈ O: `o.subtotal == Σ line.unitPrice * line.quantity` ∧
 * `o.total == max(0, o.subtotal - (o.discount?.amount ?: 0))` ∧ `0 <= o.total <= o.subtotal`.
 *
 * The test drives the **real** production pricing core — [AccessoryOrderServiceImpl.priceLine],
 * [AccessoryOrderServiceImpl.computeSubtotal], [AccessoryOrderServiceImpl.computeTotal] — over
 * generated catalog accessories and cart lines, assembles the resulting [AccessoryOrderDocument]
 * exactly as `checkout` does, and checks the three integrity invariants against an INDEPENDENT
 * recomputation, plus the order document's own validation rules. A live catalog/DB is not needed
 * because pricing is pure over already-loaded accessories.
 */
class TotalIntegrityPropertyTest : FunSpec({

    // A single-SKU, unlimited-stock accessory (stock never confounds pricing) with a random price.
    // Unlimited stock guarantees [priceLine] never rejects on the stock gate for these lines.
    val pricedLineArb: Arb<Pair<AccessoryDocument, CheckoutLine>> = Arb.bind(
        Arb.long(1L..2_000_000L),   // catalog unit price (VND)
        Arb.int(1..99),             // line quantity (Req 6.14)
    ) { price, qty ->
        val acc = AccessoryDocument(
            id = ObjectId().toHexString(),
            accessoryShowId = ObjectId().toHexString(),
            sku = "SKU-${ObjectId().toHexString().takeLast(6)}",
            name = "Accessory",
            price = price,
            stock = UNLIMITED_STOCK,
        )
        acc to CheckoutLine(accessoryId = "enc-${acc.id}", variantKey = null, quantity = qty)
    }

    test("subtotal, total, and 0 <= total <= subtotal hold for every priced order") {
        checkAll(100, Arb.list(pricedLineArb, 1..12), Arb.long(0L..1_000L)) { pairs, discountFraction ->
            // Price each line through the real production core.
            val orderLines = pairs.map { (acc, line) -> AccessoryOrderServiceImpl.priceLine(line, acc) }
            val subtotal = AccessoryOrderServiceImpl.computeSubtotal(orderLines)

            // A realistic discount amount is always in [0, subtotal] (Discount_Service guarantee,
            // Property 8). discountFraction/1000 maps into that range.
            val discountAmount = (subtotal * discountFraction) / 1_000L
            val total = AccessoryOrderServiceImpl.computeTotal(subtotal, discountAmount)

            // Invariant 1 (Req 6.7): subtotal == Σ unitPrice*qty (independent recomputation).
            subtotal shouldBe orderLines.sumOf { it.unitPrice.toLong() * it.quantity }

            // Invariant 2 (Req 6.8): total == max(0, subtotal - discount) (independent recomputation).
            total shouldBe max(0L, subtotal - discountAmount)

            // Invariant 3 (Req 4.3, 4.6): 0 <= total <= subtotal.
            (total in 0L..subtotal) shouldBe true

            // The assembled order document (exactly as checkout builds it) satisfies its own
            // subtotal/total validation rules (design "AccessoryOrder → Validation Rules").
            val discount = if (discountAmount > 0L) {
                AccessoryOrderDiscount(
                    code = "DISC",
                    type = DiscountType.Percentage,
                    amount = discountAmount,
                    discountCodeId = ObjectId().toHexString(),
                )
            } else {
                null
            }
            val order = AccessoryOrderDocument(
                accessoryShowId = ObjectId().toHexString(),
                code = "TF2026-0001",
                status = AccessoryOrderStatus.Created,
                fulfillment = FulfillmentType.PickupAtShow,
                lines = orderLines,
                subtotal = subtotal,
                discount = discount,
                total = total,
                customerName = "Nguyen Van A",
                customerPhone = "0987654321",
                customerEmail = "a@example.com",
            )
            order.validationErrors() shouldBe emptyList()
        }
    }
})
