package metaticket.accessory.service

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.property.Arb
import io.kotest.property.arbitrary.arbitrary
import io.kotest.property.arbitrary.element
import io.kotest.property.arbitrary.int
import io.kotest.property.arbitrary.long
import io.kotest.property.checkAll
import metaticket.accessory.pojo.db.AccessoryDocument
import metaticket.accessory.pojo.db.AccessoryVariant
import metaticket.accessory.pojo.db.UNLIMITED_STOCK
import metaticket.accessory.pojo.request.CheckoutLine
import org.bson.types.ObjectId

/**
 * Property 12: Checkout uses catalog-derived pricing (task 6.6).
 *
 * **Validates: Requirements 6.9**
 *
 * ∀ checkout: each priced line's `unitPrice` equals the current stored catalog price of the
 * referenced accessory, independent of any client-supplied price.
 *
 * The checkout payload ([CheckoutLine]) deliberately carries **no price field at all**, so a client
 * has no channel to influence pricing. This test asserts the stronger positive guarantee that the
 * real pricing core ([AccessoryOrderServiceImpl.priceLine]) always snapshots the *stored catalog
 * price* onto the order line, for both single-SKU and variant accessories, over a wide range of
 * catalog prices and quantities. Since variants share the accessory price in phase 1, the unit price
 * is the accessory price regardless of the selected variant.
 */
class CatalogPricingPropertyTest : FunSpec({

    // Single-SKU accessory with a random catalog price and unlimited stock.
    val singleSkuArb: Arb<AccessoryDocument> = arbitrary {
        AccessoryDocument(
            id = ObjectId().toHexString(),
            accessoryShowId = ObjectId().toHexString(),
            sku = "SKU-${ObjectId().toHexString().takeLast(6)}",
            name = "Single",
            price = Arb.long(1L..5_000_000L).bind(),
            stock = UNLIMITED_STOCK,
        )
    }

    // Variant accessory with a random catalog price shared by all variants (phase 1), unlimited stock.
    val variantAccArb: Arb<AccessoryDocument> = arbitrary {
        val keys = listOf("S", "M", "L", "XL")
        AccessoryDocument(
            id = ObjectId().toHexString(),
            accessoryShowId = ObjectId().toHexString(),
            sku = "SKU-${ObjectId().toHexString().takeLast(6)}",
            name = "Variant",
            price = Arb.long(1L..5_000_000L).bind(),
            stock = 0,
            variants = keys.map { AccessoryVariant(key = it, label = "Size $it", stock = UNLIMITED_STOCK) },
        )
    }

    test("single-SKU line unit price equals the stored catalog price") {
        checkAll(100, singleSkuArb, Arb.int(1..99)) { acc, qty ->
            val line = CheckoutLine(accessoryId = "enc-${acc.id}", variantKey = null, quantity = qty)
            val orderLine = AccessoryOrderServiceImpl.priceLine(line, acc)
            orderLine.unitPrice shouldBe acc.price
        }
    }

    test("variant line unit price equals the stored catalog price regardless of selected variant") {
        checkAll(100, variantAccArb, Arb.element("S", "M", "L", "XL"), Arb.int(1..99)) { acc, key, qty ->
            val line = CheckoutLine(accessoryId = "enc-${acc.id}", variantKey = key, quantity = qty)
            val orderLine = AccessoryOrderServiceImpl.priceLine(line, acc)
            // Every variant shares the accessory's catalog price (phase 1).
            orderLine.unitPrice shouldBe acc.price
        }
    }
})
