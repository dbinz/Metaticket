package metaticket.accessory.service

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.property.Arb
import io.kotest.property.arbitrary.arbitrary
import io.kotest.property.arbitrary.element
import io.kotest.property.arbitrary.int
import io.kotest.property.arbitrary.list
import io.kotest.property.arbitrary.orNull
import io.kotest.property.arbitrary.string
import io.kotest.property.checkAll
import metaticket.accessory.pojo.db.AccessoryDocument
import metaticket.accessory.pojo.db.AccessoryVariant
import metaticket.accessory.pojo.db.UNLIMITED_STOCK
import metaticket.accessory.pojo.request.CheckoutLine
import org.bson.types.ObjectId

/**
 * Property 9: Variant selection consistency (task 6.5).
 *
 * **Validates: Requirements 2.3, 2.4, 6.5**
 *
 * ∀ o ∈ O, ∀ line ∈ o.lines: the referenced accessory has variants ⟺ `line.variantKey != null`,
 * and when present `line.variantKey` matches an existing variant (with `variantLabel` snapshotting
 * that variant's label). A single-SKU accessory line never carries a variant.
 *
 * The test drives the **real** checkout variant-resolution core ([AccessoryOrderServiceImpl.priceLine])
 * and checks its accept/reject decision against an INDEPENDENT reimplementation of the consistency
 * predicate. All stock is unlimited so the stock gate never confounds the variant decision.
 */
class VariantSelectionConsistencyPropertyTest : FunSpec({

    val variantKeys = listOf("S", "M", "L", "XL")

    // A single-SKU accessory (no variants), unlimited stock.
    val singleSkuArb: Arb<AccessoryDocument> = arbitrary {
        AccessoryDocument(
            id = ObjectId().toHexString(),
            accessoryShowId = ObjectId().toHexString(),
            sku = "SKU-${ObjectId().toHexString().takeLast(6)}",
            name = "Single",
            price = Arb.int(1..500_000).bind().toLong(),
            stock = UNLIMITED_STOCK,
        )
    }

    // A variant accessory with a non-empty, distinct-key variant list, each unlimited stock.
    val variantAccArb: Arb<AccessoryDocument> = arbitrary {
        val keys = Arb.list(Arb.element(variantKeys), 1..4).bind().distinct()
        AccessoryDocument(
            id = ObjectId().toHexString(),
            accessoryShowId = ObjectId().toHexString(),
            sku = "SKU-${ObjectId().toHexString().takeLast(6)}",
            name = "Variant",
            price = Arb.int(1..500_000).bind().toLong(),
            stock = 0,
            variants = keys.map { AccessoryVariant(key = it, label = "Size $it", stock = UNLIMITED_STOCK) },
        )
    }

    val accArb: Arb<AccessoryDocument> = arbitrary {
        if (Arb.int(0..1).bind() == 0) singleSkuArb.bind() else variantAccArb.bind()
    }

    // A candidate variant key on the line: sometimes null, sometimes a real key, sometimes a bogus one.
    val candidateKeyArb: Arb<String?> = Arb.string(1..3).orNull(0.4)

    test("priceLine accepts a line iff its variant selection is consistent with the catalog") {
        checkAll(100, accArb, candidateKeyArb, Arb.int(1..99)) { acc, candidateKey, qty ->
            val line = CheckoutLine(accessoryId = "enc-${acc.id}", variantKey = candidateKey, quantity = qty)

            // Independent reimplementation of the consistency predicate (Req 6.5).
            val expectedConsistent = if (acc.hasVariants) {
                candidateKey != null && acc.variants.any { it.key == candidateKey }
            } else {
                candidateKey == null
            }

            if (expectedConsistent) {
                val orderLine = AccessoryOrderServiceImpl.priceLine(line, acc)
                // has-variants ⟺ variantKey != null on the resulting order line.
                (orderLine.variantKey != null) shouldBe acc.hasVariants
                if (acc.hasVariants) {
                    val variant = acc.variants.first { it.key == candidateKey }
                    orderLine.variantKey shouldBe variant.key
                    // variantLabel snapshots the matched variant's label.
                    orderLine.variantLabel shouldBe variant.label
                } else {
                    orderLine.variantKey shouldBe null
                    orderLine.variantLabel shouldBe null
                }
            } else {
                // An inconsistent selection is rejected with a 400 invalid-variant error (Req 6.5).
                val ex = shouldThrow<CheckoutException.InvalidVariant> {
                    AccessoryOrderServiceImpl.priceLine(line, acc)
                }
                ex.status shouldBe 400
            }
        }
    }

    test("a single-SKU line never carries a variant, and a has-variants line always does") {
        checkAll(100, accArb, Arb.int(1..99)) { acc, qty ->
            // Build the canonically-consistent line for this accessory.
            val consistentKey = if (acc.hasVariants) acc.variants.first().key else null
            val line = CheckoutLine(accessoryId = "enc-${acc.id}", variantKey = consistentKey, quantity = qty)

            val orderLine = AccessoryOrderServiceImpl.priceLine(line, acc)
            if (acc.hasVariants) {
                orderLine.variantKey.shouldNotBeNull()
                orderLine.variantLabel.shouldNotBeNull()
            } else {
                orderLine.variantKey shouldBe null
                orderLine.variantLabel shouldBe null
            }
        }
    }
})
