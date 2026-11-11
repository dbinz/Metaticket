package metaticket.accessory.service

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.property.Arb
import io.kotest.property.arbitrary.arbitrary
import io.kotest.property.arbitrary.boolean
import io.kotest.property.arbitrary.element
import io.kotest.property.arbitrary.int
import io.kotest.property.arbitrary.long
import io.kotest.property.arbitrary.map
import io.kotest.property.arbitrary.orNull
import io.kotest.property.checkAll
import metaticket.accessory.pojo.db.DiscountApplyMode
import metaticket.accessory.pojo.db.DiscountCodeDocument
import metaticket.accessory.pojo.db.DiscountType
import org.bson.types.ObjectId
import java.util.Date

/**
 * Property 8: Discount applicability (task 3.3).
 *
 * **Validates: Requirements 4.1, 4.2, 4.6, 6.12**
 *
 * A discount is applied at checkout time iff it is valid: active, in window,
 * `subtotal >= minOrderAmount`, show matches (or global), usage remaining. Evaluation is pure and
 * does not mutate any stored usage count.
 *
 * The test drives the real production decision ([DiscountCodeServiceImpl.evaluatePure]) and checks
 * it against an INDEPENDENT reimplementation of the applicability predicate, so the assertion is not
 * tautological.
 */
class DiscountApplicabilityPropertyTest : FunSpec({

    // Fixed evaluation instant so generated [start, end] windows are deterministic relative to it.
    val now = Date(1_700_000_000_000L)
    val dayMs = 24L * 60 * 60 * 1000

    val shows = listOf("SHOW_A", "SHOW_B", "SHOW_C")

    // Nullable dates offset from `now` by -30..+30 days.
    val dateArb = Arb.long(-30L..30L).map { Date(now.time + it * dayMs) }.orNull(0.4)

    val docArb: Arb<DiscountCodeDocument> = arbitrary {
        // Coupon types only — Tiered / EarlyBird have separate unit coverage.
        val type = Arb.element(DiscountType.Percentage, DiscountType.FixedAmount).bind()
        val value = when (type) {
            DiscountType.Percentage -> Arb.long(1L..100L).bind()
            DiscountType.FixedAmount -> Arb.long(1L..2_000_000L).bind()
            DiscountType.Tiered, DiscountType.EarlyBird -> error("unreachable")
        }
        DiscountCodeDocument(
            id = ObjectId().toHexString(),
            code = "CODE" + Arb.int(1..9999).bind(),
            accessoryShowId = Arb.element(shows).orNull(0.5).bind(), // null = global code
            type = type,
            value = value,
            maxDiscountAmount = if (type == DiscountType.Percentage) {
                Arb.long(1L..300_000L).orNull(0.5).bind()
            } else {
                null
            },
            minOrderAmount = Arb.long(0L..500_000L).bind(),
            usageLimit = Arb.int(1..12).orNull(0.5).bind(),
            usedCount = Arb.int(0..14).bind(),
            startAt = dateArb.bind(),
            endAt = dateArb.bind(),
            active = Arb.boolean().bind(),
            applyMode = DiscountApplyMode.Manual,
        )
    }

    test("evaluatePure is non-null iff all applicability predicates hold, with amount in [0, subtotal]") {
        checkAll(100, docArb, Arb.element(shows), Arb.long(-100_000L..2_000_000L)) { doc, showId, subtotal ->
            // Independent reimplementation of the spec's applicability predicate (Req 4.1/4.2/6.12).
            val expectedApplicable = subtotal > 0L &&
                doc.active &&
                (doc.startAt == null || !now.before(doc.startAt)) &&
                (doc.endAt == null || !now.after(doc.endAt)) &&
                (doc.accessoryShowId == null || doc.accessoryShowId == showId) &&
                subtotal >= doc.minOrderAmount &&
                (doc.usageLimit == null || doc.usedCount < doc.usageLimit)

            val usedBefore = doc.usedCount
            val result = DiscountCodeServiceImpl.evaluatePure(doc, showId, subtotal, now)

            (result != null) shouldBe expectedApplicable

            // Purity (Req 4.7): evaluation never mutates the stored usage count.
            doc.usedCount shouldBe usedBefore

            if (result != null) {
                // Amount bounds (Req 4.6).
                (result.amount in 0L..subtotal) shouldBe true
                result.type shouldBe doc.type
                result.code shouldBe doc.canonicalCode
                result.discountCodeId shouldBe doc.id
            }
        }
    }
})
