package metaticket.accessory.service

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import metaticket.accessory.pojo.db.AccessoryOrderStatus.Created
import metaticket.accessory.pojo.db.AccessoryOrderStatus.Expired
import metaticket.accessory.pojo.db.AccessoryOrderStatus.PaymentCancelled
import metaticket.accessory.pojo.db.AccessoryOrderStatus.PaymentFailed
import metaticket.accessory.pojo.db.AccessoryOrderStatus.PaymentProcessing
import metaticket.accessory.pojo.db.AccessoryOrderStatus.PaymentSucceed
import metaticket.accessory.pojo.db.AccessoryOrderStatus.WaitingForPayment

/**
 * Unit tests for [canTransition] (task 5.3).
 *
 * **Validates: Requirements 11.3, 11.4**
 *
 * Verifies the allowed edges, expiry from every pre-terminal state, and rejection of illegal moves
 * and any transition out of (or into a fresh outgoing edge from) a terminal state.
 */
class CanTransitionTest : FunSpec({

    // --- Allowed edges along the main sequence (Req 11.1) ---

    test("main-sequence one-step advances are allowed") {
        canTransition(Created, WaitingForPayment) shouldBe true
        canTransition(WaitingForPayment, PaymentProcessing) shouldBe true
        canTransition(PaymentProcessing, PaymentSucceed) shouldBe true
        canTransition(PaymentProcessing, PaymentFailed) shouldBe true
        canTransition(PaymentProcessing, PaymentCancelled) shouldBe true
    }

    test("pre-terminal states may reach a final payment status directly") {
        // Gateways may report a final status without an interim PaymentProcessing callback.
        canTransition(Created, PaymentSucceed) shouldBe true
        canTransition(Created, PaymentFailed) shouldBe true
        canTransition(Created, PaymentCancelled) shouldBe true
        canTransition(WaitingForPayment, PaymentSucceed) shouldBe true
        canTransition(WaitingForPayment, PaymentFailed) shouldBe true
        canTransition(WaitingForPayment, PaymentCancelled) shouldBe true
    }

    // --- Expiry from any pre-terminal state (Req 11.2) ---

    test("expiry is allowed from every pre-terminal state") {
        canTransition(Created, Expired) shouldBe true
        canTransition(WaitingForPayment, Expired) shouldBe true
        canTransition(PaymentProcessing, Expired) shouldBe true
    }

    // --- Illegal moves rejected (Req 11.4) ---

    test("skipping / backward / self transitions are rejected") {
        // skip forward (no direct Created → PaymentProcessing)
        canTransition(Created, PaymentProcessing) shouldBe false
        // backward
        canTransition(WaitingForPayment, Created) shouldBe false
        canTransition(PaymentProcessing, WaitingForPayment) shouldBe false
        // self-loops
        canTransition(Created, Created) shouldBe false
        canTransition(WaitingForPayment, WaitingForPayment) shouldBe false
        canTransition(PaymentProcessing, PaymentProcessing) shouldBe false
    }

    // --- Terminal states have no outgoing edges (Req 11.3) ---

    test("no transition is allowed out of a terminal state") {
        val terminals = listOf(PaymentSucceed, PaymentFailed, PaymentCancelled, Expired)
        val allStates = listOf(
            Created, WaitingForPayment, PaymentProcessing,
            PaymentSucceed, PaymentFailed, PaymentCancelled, Expired,
        )
        for (from in terminals) {
            for (to in allStates) {
                canTransition(from, to) shouldBe false
            }
        }
    }
})
