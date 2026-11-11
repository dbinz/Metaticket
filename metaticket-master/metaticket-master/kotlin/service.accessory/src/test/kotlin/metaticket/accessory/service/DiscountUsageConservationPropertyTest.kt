package metaticket.accessory.service

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.property.Arb
import io.kotest.property.arbitrary.boolean
import io.kotest.property.arbitrary.int
import io.kotest.property.arbitrary.list
import io.kotest.property.arbitrary.orNull
import io.kotest.property.checkAll

/**
 * Property 5: Discount usage conservation (task 3.4).
 *
 * **Validates: Requirements 6.11, 10.2, 12.1, 12.2, 12.3**
 *
 * For any interleaving of reserve/revert operations, `0 <= usedCount <= usageLimit` always holds,
 * a reserved discount is either kept or reverted exactly once, and there is no double reserve
 * (beyond the limit) nor double revert (below zero).
 *
 * The reserve/revert `$inc` operations in [DiscountCodeServiceImpl] are guarded by MongoDB filters
 * that mirror the pure predicates [DiscountCodeServiceImpl.canReserve] / `canRevert`. This model
 * test drives those exact production predicates over random operation streams, asserting the
 * conservation invariant after every step.
 */
class DiscountUsageConservationPropertyTest : FunSpec({

    // Apply one guarded reserve using the real production predicate.
    fun applyReserve(active: Boolean, usageLimit: Int?, usedCount: Int): Int =
        if (DiscountCodeServiceImpl.canReserve(active, usageLimit, usedCount)) usedCount + 1 else usedCount

    // Apply one guarded revert using the real production predicate.
    fun applyRevert(usedCount: Int): Int =
        if (DiscountCodeServiceImpl.canRevert(usedCount)) usedCount - 1 else usedCount

    test("usedCount stays within [0, usageLimit] across any reserve/revert interleaving") {
        val opsArb = Arb.list(Arb.boolean(), 0..60) // true = reserve, false = revert
        val limitArb = Arb.int(1..8).orNull(0.3)    // null = unlimited

        checkAll(100, opsArb, limitArb) { ops, usageLimit ->
            var used = 0
            for (op in ops) {
                used = if (op) applyReserve(active = true, usageLimit = usageLimit, usedCount = used)
                else applyRevert(used)

                // Invariant: never below zero (no double/over revert)...
                (used >= 0) shouldBe true
                // ...and never above the ceiling (no over-reserve / double reserve past limit).
                if (usageLimit != null) (used <= usageLimit) shouldBe true
            }
        }
    }

    test("an inactive code is never reserved") {
        checkAll(100, Arb.int(1..8).orNull(0.3), Arb.int(0..8)) { usageLimit, startUsed ->
            applyReserve(active = false, usageLimit = usageLimit, usedCount = startUsed) shouldBe startUsed
        }
    }

    test("reserve-then-revert restores the original usage count (exactly-once revert on failure path)") {
        checkAll(100, Arb.int(1..8)) { usageLimit ->
            // Start below the ceiling so the reserve succeeds.
            val start = 0
            val afterReserve = applyReserve(active = true, usageLimit = usageLimit, usedCount = start)
            afterReserve shouldBe start + 1
            // Exactly one revert brings it back; a second revert (double revert) would be guarded.
            val afterRevert = applyRevert(afterReserve)
            afterRevert shouldBe start
        }
    }

    test("revert never drives usedCount below zero") {
        checkAll(100, Arb.list(Arb.boolean(), 0..30)) { reverts ->
            var used = 0
            // A stream of reverts against an already-zero counter must stay at zero.
            for (r in reverts) {
                if (!r) used = applyRevert(used)
                (used >= 0) shouldBe true
            }
        }
    }
})
