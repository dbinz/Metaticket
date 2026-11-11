package metaticket.accessory.pojo.response

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.property.Arb
import io.kotest.property.arbitrary.int
import io.kotest.property.arbitrary.map
import io.kotest.property.checkAll
import metaticket.accessory.configuration.Environment
import metaticket.accessory.pojo.db.AccessoryOrderDocument
import metaticket.accessory.pojo.db.AccessoryOrderLine
import metaticket.accessory.pojo.db.AccessoryOrderStatus
import org.bson.types.ObjectId

/**
 * Property 15: Order line display composition (task 12.7).
 *
 * **Validates: Requirements 13.2, 13.3**
 *
 * ∀ order line returned by order lookup: the display information contains the product name and
 * quantity, includes the variant label iff the line has a variant, and omits the variant label for
 * single-SKU lines.
 *
 * This drives the **real** [AccessoryOrderLine.toLineView] (single-line composer) and
 * [AccessoryOrderDocument.toOrderView] (whole-order composer) builders. Names and variant labels are
 * generated from disjoint token spaces that never contain the separator (` · `) or the quantity
 * marker (`×`), so the containment oracle is unambiguous.
 */
class OrderLineDisplayCompositionPropertyTest : FunSpec({

    // toOrderView() encodes the order id, which needs a configured 16-char AES key.
    Environment.secretKey = "0123456789abcdef"

    // Product names: never contain " · " nor "×".
    val nameArb: Arb<String> = Arb.int(0..1_000_000).map { "Product-$it" }
    // Variant label: null (single-SKU) or one of the size labels; none contain " · " nor "×".
    val variantArb: Arb<String?> = Arb.int(0..4).map { i ->
        if (i == 0) null else listOf("Size S", "Size M", "Size L", "Size XL")[i - 1]
    }
    val qtyArb: Arb<Int> = Arb.int(1..99)

    fun line(name: String, variantLabel: String?, qty: Int) = AccessoryOrderLine(
        accessoryId = ObjectId().toHexString(),
        sku = "SKU-1",
        name = name,
        variantKey = variantLabel?.let { "K" },   // key present iff the line has a variant
        variantLabel = variantLabel,
        unitPrice = 100_000,
        quantity = qty,
    )

    test("toLineView composes name + quantity, and includes the variant label iff the line has a variant") {
        checkAll(100, nameArb, variantArb, qtyArb) { name, variantLabel, qty ->
            val view = line(name, variantLabel, qty).toLineView()

            // Structured fields pass through unchanged.
            view.name shouldBe name
            view.quantity shouldBe qty
            view.variantLabel shouldBe variantLabel

            // Display always contains the product name and the quantity marker (Req 13.2).
            view.display.contains(name) shouldBe true
            view.display.contains("×$qty") shouldBe true

            // Variant label appears iff the line has a variant; omitted for single-SKU (Req 13.3).
            if (variantLabel != null) {
                view.display.contains(variantLabel) shouldBe true
                view.display.contains(" · ") shouldBe true
                view.display shouldBe "$name · $variantLabel ×$qty"
            } else {
                view.display.contains(" · ") shouldBe false
                view.display shouldBe "$name ×$qty"
            }
        }
    }

    test("toOrderView composes the display string for every line, honoring the variant-iff rule") {
        val variantLine = line("Áo thun", "Size M", 2)
        val singleSkuLine = line("Poster", null, 1)
        val order = AccessoryOrderDocument(
            id = ObjectId().toHexString(),
            accessoryShowId = ObjectId().toHexString(),
            code = "TF2026-0148",
            status = AccessoryOrderStatus.PaymentSucceed,
            lines = listOf(variantLine, singleSkuLine),
            subtotal = 2 * 100_000 + 1 * 100_000,
            total = 300_000,
            customerName = "Nguyen Van A",
            customerPhone = "0987654321",
            customerEmail = "a@example.com",
        )

        val view = order.toOrderView()

        view.lines.size shouldBe 2
        // Variant line includes the label; single-SKU line omits it.
        view.lines[0].display shouldBe "Áo thun · Size M ×2"
        view.lines[0].variantLabel shouldBe "Size M"
        view.lines[1].display shouldBe "Poster ×1"
        view.lines[1].variantLabel shouldBe null
        // Fulfillment is always pickup-at-show; no shipping fields exist (Req 14.5).
        view.fulfillment shouldBe "PickupAtShow"
    }
})
