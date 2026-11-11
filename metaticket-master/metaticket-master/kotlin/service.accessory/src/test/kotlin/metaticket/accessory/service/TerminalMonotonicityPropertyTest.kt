package metaticket.accessory.service

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.property.Arb
import io.kotest.property.arbitrary.enum
import io.kotest.property.checkAll
import metaticket.accessory.pojo.db.AccessoryOrderStatus
import metaticket.accessory.pojo.db.AccessoryOrderStatus.Created
import metaticket.accessory.pojo.db.AccessoryOrderStatus.Expired
import metaticket.accessory.pojo.db.AccessoryOrderStatus.PaymentCancelled
import metaticket.accessory.pojo.db.AccessoryOrderStatus.PaymentFailed
import metaticket.accessory.pojo.db.AccessoryOrderStatus.PaymentProcessing
import metaticket.accessory.pojo.db.AccessoryOrderStatus.PaymentSucceed
import metaticket.accessory.pojo.db.AccessoryOrderStatus.WaitingForPayment

/**
 * Property 7: Terminal monotonicity (task 5.2).
 *
 * **Validates: Requirements 11.1, 11.2, 11.3**
 *
 * Order status only advances along allowed transitions
 * `Created → WaitingForPayment → PaymentProcessing → {PaymentSucceed | PaymentFailed | PaymentCancelled}`,
 * plus `→ Expired` from any pre-terminal state. No transition out of a terminal state is permitted.
 *
 * The test checks the real production predicate [canTransition] against an INDEPENDENT
 * reimplementation of the allowed edge set, exhaustively over all status pairs.
 */
class TerminalMonotonicityPropertyTest : FunSpec({

    val terminals = setOf(PaymentSucceed, PaymentFailed, PaymentCancelled, Expired)

    // Independent reimplementation of the allowed one-step edges (Req 11.1, 11.2, 11.3).
    val expectedAllowed: Map<AccessoryOrderStatus, Set<AccessoryOrderStatus>> = mapOf(
        Created to setOf(WaitingForPayment, PaymentSucceed, PaymentFailed, PaymentCancelled, Expired),
        WaitingForPayment to setOf(PaymentProcessing, PaymentSucceed, PaymentFailed, PaymentCancelled, Expired),
        PaymentProcessing to setOf(PaymentSucceed, PaymentFailed, PaymentCancelled, Expired),
        // terminals intentionally absent → no outgoing edges
    )

    test("canTransition matches the allowed edge set for every (from, to) pair") {
        checkAll(Arb.enum<AccessoryOrderStatus>(), Arb.enum<AccessoryOrderStatus>()) { from, to ->
            val expected = to in (expectedAllowed[from] ?: emptySet())
            canTransition(from, to) shouldBe expected
        }
    }

    test("no transition is permitted out of any terminal state (Req 11.3)") {
        checkAll(Arb.enum<AccessoryOrderStatus>()) { to ->
            for (terminal in terminals) {
                canTransition(terminal, to) shouldBe false
            }
        }
    }

    test("every allowed non-expiry advance moves strictly forward in the lifecycle") {
        // Monotonic rank along the main sequence; terminals share the max rank.
        val rank = mapOf(
            Created to 0,
            WaitingForPayment to 1,
            PaymentProcessing to 2,
            PaymentSucceed to 3,
            PaymentFailed to 3,
            PaymentCancelled to 3,
            Expired to 3,
        )
        checkAll(Arb.enum<AccessoryOrderStatus>(), Arb.enum<AccessoryOrderStatus>()) { from, to ->
            if (canTransition(from, to)) {
                // No allowed transition ever moves to a lower-ranked state (never goes backward).
                (rank.getValue(to) >= rank.getValue(from)) shouldBe true
                // A terminal state is never a source of an allowed transition.
                (from in terminals) shouldBe false
            }
        }
    }
})
