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
import metaticket.accessory.pojo.db.AccessoryOrderDiscount
import metaticket.accessory.pojo.db.AccessoryOrderDocument
import metaticket.accessory.pojo.db.AccessoryOrderLine
import metaticket.accessory.pojo.db.AccessoryOrderStatus
import metaticket.accessory.pojo.db.DiscountType
import metaticket.accessory.pojo.db.FulfillmentType
import metaticket.payment.datastructure.PaymentStatus
import org.bson.types.ObjectId

/**
 * Property 6: Idempotent status handling (task 7.5).
 *
 * **Validates: Requirements 9.1, 9.3, 9.4, 10.1, 10.3**
 *
 * Repeated `onPaymentStatusChanged(orderId, s)` for the same `s` produces the same final state,
 * decrements each applicable stock cell **at most once**, and sends **at most one** confirmation
 * email for that order.
 *
 * Idempotency is modelled exactly as it works in production: the guarded compare-and-set
 * [OrderStateMachine.transition] returns the updated document only on the **first** call (the winning
 * guard) and `null` on every repeat (the order has already left the pre-terminal source set). The
 * exactly-once side-effect claims ([OrderDatabaseService.tryClaimEmailDispatch] for email,
 * [OrderDatabaseService.tryClaimRevert] for discount) likewise flip `false→true` once. We drive the
 * real [AccessoryOrderServiceImpl.onPaymentStatusChanged] a random number of times (2..6) with the
 * same status and assert the side effects happen at most once.
 */
class IdempotentStatusHandlingPropertyTest : FunSpec({

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

    /** A transition mock that wins its guard exactly once (returns [updated]) then always returns null. */
    fun firstOnly(updated: AccessoryOrderDocument): () -> AccessoryOrderDocument? {
        var first = true
        return { if (first) { first = false; updated } else null }
    }

    test("repeated Succeed never changes stock (reserved at creation) and sends at most one email") {
        checkAll(50, Arb.list(lineArb, 1..6), Arb.int(2..6)) { lines, repeats ->
            val order = AccessoryOrderDocument(
                id = ObjectId().toHexString(),
                accessoryShowId = ObjectId().toHexString(),
                code = "TF2026-0001",
                status = AccessoryOrderStatus.WaitingForPayment,
                fulfillment = FulfillmentType.PickupAtShow,
                lines = lines,
                subtotal = lines.sumOf { it.unitPrice * it.quantity },
                total = lines.sumOf { it.unitPrice * it.quantity },
                customerName = "Nguyen Van A",
                customerPhone = "0987654321",
                customerEmail = "a@example.com",
            )
            val succeedDoc = order.copy(status = AccessoryOrderStatus.PaymentSucceed)

            // Relaxed so any stock method is a no-op default; we then assert none was ever called —
            // a successful payment must not touch inventory (it was reserved at order creation).
            val catalogDb = mockk<CatalogDatabaseService>(relaxed = true)
            coEvery { catalogDb.loadShowById(order.accessoryShowId) } returns
                metaticket.accessory.pojo.db.AccessoryShowDocument(
                    id = order.accessoryShowId, code = "TF", codePrefix = "TF", name = "The Flob",
                )

            val orderDb = mockk<OrderDatabaseService>(relaxed = true)
            coEvery { orderDb.loadOrder(order.id) } returns order
            // The email dispatch slot is claimable exactly once (false→true) across all repeats.
            val emailClaim = firstOnly(succeedDoc)
            coEvery { orderDb.tryClaimEmailDispatch(any()) } coAnswers { emailClaim() != null }

            val osm = mockk<OrderStateMachine>()
            val guard = firstOnly(succeedDoc)
            coEvery { osm.transition(any(), any(), AccessoryOrderStatus.PaymentSucceed) } coAnswers { guard() }

            val mailGateway = mockk<AccessoryMailGateway>(relaxed = true)

            val service = AccessoryOrderServiceImpl(
                catalogDb = catalogDb,
                orderDb = orderDb,
                discountService = mockk(relaxed = true),
                orderCodeService = mockk(relaxed = true),
                paymentGateway = mockk<PaymentGateway>(relaxed = true),
                orderStateMachine = osm,
                reconciliationDb = mockk<ReconciliationDatabaseService>(relaxed = true),
                mailGateway = mailGateway,
                jobrunrGateway = mockk(relaxed = true),
                emailDispatchScope = CoroutineScope(Dispatchers.Unconfined),
            )

            repeat(repeats) { service.onPaymentStatusChanged(order.id, PaymentStatus.Succeed) }

            // A successful payment performs NO stock mutation — neither a (legacy) decrement nor a
            // release; the units were already reserved when the order was created.
            coVerify(exactly = 0) { catalogDb.decrementStock(any(), any()) }
            coVerify(exactly = 0) { catalogDb.decrementVariantStock(any(), any(), any()) }
            coVerify(exactly = 0) { catalogDb.releaseStock(any(), any()) }
            coVerify(exactly = 0) { catalogDb.releaseVariantStock(any(), any(), any()) }

            // At most one confirmation email dispatched for the order (Req 9.4).
            coVerify(exactly = 1) { mailGateway.sendMailAccessoryOrder(any(), any()) }
        }
    }

    test("repeated Failed/Cancelled reverts a reserved discount at most once") {
        checkAll(
            50,
            Arb.element(PaymentStatus.Failed, PaymentStatus.Cancelled),
            Arb.int(2..6),
        ) { status, repeats ->
            val discountCodeId = ObjectId().toHexString()
            val order = AccessoryOrderDocument(
                id = ObjectId().toHexString(),
                accessoryShowId = ObjectId().toHexString(),
                code = "TF2026-0001",
                status = AccessoryOrderStatus.WaitingForPayment,
                fulfillment = FulfillmentType.PickupAtShow,
                lines = listOf(
                    AccessoryOrderLine(
                        accessoryId = ObjectId().toHexString(), sku = "POSTER", name = "Poster",
                        unitPrice = 100_000, quantity = 1,
                    ),
                ),
                subtotal = 100_000,
                discount = AccessoryOrderDiscount(
                    code = "THEFLOB10", type = DiscountType.Percentage, amount = 10_000,
                    discountCodeId = discountCodeId,
                ),
                total = 90_000,
                customerName = "Nguyen Van A",
                customerPhone = "0987654321",
                customerEmail = "a@example.com",
            )
            val target = when (status) {
                PaymentStatus.Failed -> AccessoryOrderStatus.PaymentFailed
                PaymentStatus.Cancelled -> AccessoryOrderStatus.PaymentCancelled
                else -> error("unreachable: generator only yields Failed/Cancelled")
            }
            val terminalDoc = order.copy(status = target)

            val orderDb = mockk<OrderDatabaseService>(relaxed = true)
            coEvery { orderDb.loadOrder(order.id) } returns order
            // The revert claim (discountReverted false→true) succeeds exactly once across repeats.
            val revertClaim = firstOnly(terminalDoc)
            coEvery { orderDb.tryClaimRevert(any()) } coAnswers { revertClaim() != null }
            // The stock-release claim (stockReleased false→true) also succeeds exactly once.
            val releaseClaim = firstOnly(terminalDoc)
            coEvery { orderDb.tryClaimStockRelease(any()) } coAnswers { releaseClaim() != null }

            val osm = mockk<OrderStateMachine>()
            val guard = firstOnly(terminalDoc)
            coEvery { osm.transition(any(), any(), target) } coAnswers { guard() }

            val discountService = mockk<DiscountCodeService>(relaxed = true)
            val catalogDb = mockk<CatalogDatabaseService>(relaxed = true)

            val service = AccessoryOrderServiceImpl(
                catalogDb = catalogDb,
                orderDb = orderDb,
                discountService = discountService,
                orderCodeService = mockk(relaxed = true),
                paymentGateway = mockk<PaymentGateway>(relaxed = true),
                orderStateMachine = osm,
                reconciliationDb = mockk<ReconciliationDatabaseService>(relaxed = true),
                mailGateway = mockk<AccessoryMailGateway>(relaxed = true),
                jobrunrGateway = mockk(relaxed = true),
                emailDispatchScope = CoroutineScope(Dispatchers.Unconfined),
            )

            repeat(repeats) { service.onPaymentStatusChanged(order.id, status) }

            // The reserved discount is reverted exactly once regardless of duplicate deliveries (Req 10.2/10.3).
            coVerify(exactly = 1) { discountService.revert(discountCodeId) }
            // The reserved stock (single POSTER line) is released back exactly once (reserve-on-create).
            coVerify(exactly = 1) { catalogDb.releaseStock(order.lines[0].accessoryId, 1) }
        }
    }
})
