package metaticket.accessory.service

import io.kotest.core.spec.style.FunSpec
import io.kotest.property.Arb
import io.kotest.property.arbitrary.bind
import io.kotest.property.arbitrary.boolean
import io.kotest.property.arbitrary.element
import io.kotest.property.arbitrary.int
import io.kotest.property.arbitrary.list
import io.kotest.property.checkAll
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import metaticket.accessory.database.CatalogDatabaseService
import metaticket.accessory.database.OrderDatabaseService
import metaticket.accessory.database.ReconciliationDatabaseService
import metaticket.accessory.gateway.AccessoryMailGateway
import metaticket.accessory.gateway.PaymentGateway
import metaticket.accessory.pojo.db.AccessoryOrderDocument
import metaticket.accessory.pojo.db.AccessoryOrderLine
import metaticket.accessory.pojo.db.AccessoryOrderStatus
import metaticket.accessory.pojo.db.FulfillmentType
import metaticket.payment.datastructure.PaymentStatus
import org.bson.types.ObjectId

/**
 * Property 4 (reserve-on-create): stock is reserved at order CREATION, so a payment callback never
 * decrements stock on success and RELEASES the reservation only on a non-success terminal outcome.
 *
 * **Validates: the overselling fix — reserve-on-create inventory model.**
 *
 * ∀ o ∈ O reaching a payment callback: the reserved stock of each line is returned to inventory
 * (`releaseStock` / `releaseVariantStock`) **iff** `o` actually transitions to a non-success terminal
 * state (`PaymentFailed` / `PaymentCancelled`). A successful payment keeps the reservation (no stock
 * change), and a non-winning guard (duplicate/conflicting delivery) changes nothing.
 *
 * The test drives the **real** [AccessoryOrderServiceImpl.onPaymentStatusChanged] over a random
 * lifecycle event whose guarded compare-and-set outcome is modelled by the mocked
 * [OrderStateMachine.transition] (the updated document on a winning guard, `null` otherwise), and
 * asserts the release collaborators are invoked exactly for the fail/cancel-winning case and never
 * on success.
 */
class StockConsumedOnlyOnSuccessPropertyTest : FunSpec({

    // A purchased order line; ~half carry a variant (variant stock cell), the rest are single-SKU.
    val lineArb: Arb<AccessoryOrderLine> = Arb.bind(Arb.boolean(), Arb.int(1..99)) { hasVariant, qty ->
        AccessoryOrderLine(
            accessoryId = ObjectId().toHexString(),
            sku = "SKU-${ObjectId().toHexString().takeLast(6)}",
            name = "Accessory",
            variantKey = if (hasVariant) "M" else null,
            variantLabel = if (hasVariant) "Size M" else null,
            unitPrice = 100_000,
            quantity = qty,
        )
    }

    fun orderOf(lines: List<AccessoryOrderLine>, status: AccessoryOrderStatus) = AccessoryOrderDocument(
        id = ObjectId().toHexString(),
        accessoryShowId = ObjectId().toHexString(),
        code = "TF2026-0001",
        status = status,
        fulfillment = FulfillmentType.PickupAtShow,
        lines = lines,
        subtotal = lines.sumOf { it.unitPrice * it.quantity },
        discount = null, // isolates the stock behaviour: nothing to revert on the fail/cancel path
        total = lines.sumOf { it.unitPrice * it.quantity },
        customerName = "Nguyen Van A",
        customerPhone = "0987654321",
        customerEmail = "a@example.com",
    )

    test("reserved stock is released iff the order transitions to a fail/cancel terminal; never on success") {
        checkAll(
            50,
            Arb.list(lineArb, 1..6),
            Arb.element(PaymentStatus.Succeed, PaymentStatus.Failed, PaymentStatus.Cancelled),
            Arb.boolean(),
        ) { lines, status, transitionSucceeds ->
            val preTerminal = orderOf(lines, AccessoryOrderStatus.WaitingForPayment)

            // Relaxed: release/reserve are no-op defaults; we assert the exact call counts below.
            val catalogDb = mockk<CatalogDatabaseService>(relaxed = true)

            val orderDb = mockk<OrderDatabaseService>(relaxed = true)
            coEvery { orderDb.loadOrder(preTerminal.id) } returns preTerminal
            // The exactly-once stock-release claim is available (so a winning fail/cancel releases).
            coEvery { orderDb.tryClaimStockRelease(any()) } returns true
            // The email claim is not won, so the success-path background dispatch is a no-op.
            coEvery { orderDb.tryClaimEmailDispatch(any()) } returns false

            val osm = mockk<OrderStateMachine>()
            val succeedDoc = preTerminal.copy(status = AccessoryOrderStatus.PaymentSucceed)
            val failedDoc = preTerminal.copy(status = AccessoryOrderStatus.PaymentFailed)
            val cancelledDoc = preTerminal.copy(status = AccessoryOrderStatus.PaymentCancelled)
            coEvery { osm.transition(any(), any(), AccessoryOrderStatus.PaymentSucceed) } returns
                (if (transitionSucceeds) succeedDoc else null)
            coEvery { osm.transition(any(), any(), AccessoryOrderStatus.PaymentFailed) } returns
                (if (transitionSucceeds) failedDoc else null)
            coEvery { osm.transition(any(), any(), AccessoryOrderStatus.PaymentCancelled) } returns
                (if (transitionSucceeds) cancelledDoc else null)

            val service = AccessoryOrderServiceImpl(
                catalogDb = catalogDb,
                orderDb = orderDb,
                discountService = mockk(relaxed = true),
                orderCodeService = mockk(relaxed = true),
                paymentGateway = mockk<PaymentGateway>(relaxed = true),
                orderStateMachine = osm,
                reconciliationDb = mockk<ReconciliationDatabaseService>(relaxed = true),
                mailGateway = mockk<AccessoryMailGateway>(relaxed = true),
                jobrunrGateway = mockk(relaxed = true),
                emailDispatchScope = CoroutineScope(Dispatchers.Unconfined),
            )

            service.onPaymentStatusChanged(preTerminal.id, status)

            val shouldRelease = status != PaymentStatus.Succeed && transitionSucceeds
            val variantLines = lines.count { it.variantKey != null }
            val plainLines = lines.count { it.variantKey == null }

            if (shouldRelease) {
                coVerify(exactly = plainLines) { catalogDb.releaseStock(any(), any()) }
                coVerify(exactly = variantLines) { catalogDb.releaseVariantStock(any(), any(), any()) }
            } else {
                // Success (reservation kept) or a non-winning guard: no stock cell is ever touched.
                coVerify(exactly = 0) { catalogDb.releaseStock(any(), any()) }
                coVerify(exactly = 0) { catalogDb.releaseVariantStock(any(), any(), any()) }
            }
            // A successful payment never decrements stock either (reserved at creation).
            coVerify(exactly = 0) { catalogDb.decrementStock(any(), any()) }
            coVerify(exactly = 0) { catalogDb.decrementVariantStock(any(), any(), any()) }
        }
    }
})
