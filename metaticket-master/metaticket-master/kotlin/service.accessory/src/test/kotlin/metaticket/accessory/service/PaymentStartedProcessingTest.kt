package metaticket.accessory.service

import io.grpc.Status
import io.grpc.StatusException
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import metaticket.accessory.database.CatalogDatabaseService
import metaticket.accessory.database.OrderDatabaseService
import metaticket.accessory.pojo.db.AccessoryOrderDocument
import metaticket.accessory.pojo.db.AccessoryOrderLine
import metaticket.accessory.pojo.db.AccessoryOrderStatus
import metaticket.accessory.pojo.db.FulfillmentType
import metaticket.order.notification.grpc.OrderMessage
import org.bson.types.ObjectId
import java.util.Date

/**
 * Regression for orders expired while the customer was still paying at the gateway
 * (DKHTF2026-0197): once the customer is handed to the gateway the order must leave the platform
 * TTL (→ `PaymentProcessing`), and a start for an order the sweep already expired must be refused.
 */
class PaymentStartedProcessingTest : FunSpec({

    fun order(status: AccessoryOrderStatus) = AccessoryOrderDocument(
        id = ObjectId().toHexString(),
        accessoryShowId = ObjectId().toHexString(),
        code = "DKHTF2026-0197",
        status = status,
        fulfillment = FulfillmentType.PickupAtShow,
        lines = listOf(
            AccessoryOrderLine(
                accessoryId = ObjectId().toHexString(), sku = "VN-LANYARD", name = "Lanyard",
                unitPrice = 95_000, quantity = 1,
            ),
        ),
        subtotal = 95_000,
        total = 95_000,
        customerName = "Nguyen Van A",
        customerPhone = "0987654321",
        customerEmail = "a@example.com",
    )

    fun service(osm: OrderStateMachine, orderDb: OrderDatabaseService, catalogDb: CatalogDatabaseService) =
        AccessoryOrderServiceImpl(
            catalogDb = catalogDb,
            orderDb = orderDb,
            discountService = mockk(relaxed = true),
            orderCodeService = mockk(relaxed = true),
            paymentGateway = mockk(relaxed = true),
            orderStateMachine = osm,
            reconciliationDb = mockk(relaxed = true),
            mailGateway = mockk(relaxed = true),
            jobrunrGateway = mockk(relaxed = true),
            emailDispatchScope = CoroutineScope(Dispatchers.Unconfined),
        )

    fun startRequest(orderId: String) =
        OrderMessage.NotifyPaymentStartedRequest.newBuilder().setOrderId(orderId).build()

    test("payment start moves WaitingForPayment to PaymentProcessing and is acknowledged") {
        val waiting = order(AccessoryOrderStatus.WaitingForPayment)
        val osm = mockk<OrderStateMachine>()
        coEvery { osm.markPaymentProcessing(waiting.id) } returns
            waiting.copy(status = AccessoryOrderStatus.PaymentProcessing, paymentStartedAt = Date())
        val svc = service(osm, mockk(relaxed = true), mockk(relaxed = true))

        OrderNotificationServiceImpl(svc).notifyPaymentStarted(startRequest(waiting.id))

        coVerify(exactly = 1) { osm.markPaymentProcessing(waiting.id) }
    }

    test("a duplicate start for an order already PaymentProcessing is accepted (idempotent)") {
        val processing = order(AccessoryOrderStatus.PaymentProcessing)
        val osm = mockk<OrderStateMachine>()
        coEvery { osm.markPaymentProcessing(processing.id) } returns null
        val orderDb = mockk<OrderDatabaseService>(relaxed = true)
        coEvery { orderDb.loadOrder(processing.id) } returns processing

        service(osm, orderDb, mockk(relaxed = true)).onPaymentStarted(processing.id) shouldBe true
    }

    test("payment start for an order the sweep already expired is refused with FAILED_PRECONDITION") {
        val expired = order(AccessoryOrderStatus.Expired)
        val osm = mockk<OrderStateMachine>()
        coEvery { osm.markPaymentProcessing(expired.id) } returns null
        val orderDb = mockk<OrderDatabaseService>(relaxed = true)
        coEvery { orderDb.loadOrder(expired.id) } returns expired
        val svc = service(osm, orderDb, mockk(relaxed = true))

        val error = shouldThrow<StatusException> {
            OrderNotificationServiceImpl(svc).notifyPaymentStarted(startRequest(expired.id))
        }
        error.status.code shouldBe Status.Code.FAILED_PRECONDITION
    }

    test("payment start for an unknown order is refused") {
        val osm = mockk<OrderStateMachine>()
        coEvery { osm.markPaymentProcessing(any()) } returns null
        val orderDb = mockk<OrderDatabaseService>(relaxed = true)
        coEvery { orderDb.loadOrder(any()) } returns null

        service(osm, orderDb, mockk(relaxed = true)).onPaymentStarted(ObjectId().toHexString()) shouldBe false
    }

    test("stale-processing safety net expires only from PaymentProcessing and releases stock once") {
        val processing = order(AccessoryOrderStatus.PaymentProcessing)
        val osm = mockk<OrderStateMachine>()
        coEvery {
            osm.transition(processing.id, setOf(AccessoryOrderStatus.PaymentProcessing), AccessoryOrderStatus.Expired)
        } returns processing.copy(status = AccessoryOrderStatus.Expired)
        val orderDb = mockk<OrderDatabaseService>(relaxed = true)
        coEvery { orderDb.tryClaimStockRelease(processing.id) } returns true
        val catalogDb = mockk<CatalogDatabaseService>(relaxed = true)

        service(osm, orderDb, catalogDb).expireStaleProcessingOrder(processing) shouldBe true

        coVerify(exactly = 1) { catalogDb.releaseStock(processing.lines[0].accessoryId, 1) }
    }

    test("sweep hands stale PaymentProcessing orders to the safety net, and 0 disables it") {
        val processing = order(AccessoryOrderStatus.PaymentProcessing)
        val orderDb = mockk<OrderDatabaseService>()
        coEvery { orderDb.findExpirableOrdersOlderThan(any()) } returns emptyList()
        coEvery { orderDb.findStaleProcessingOrdersStartedBefore(any()) } returns listOf(processing)
        val orderService = mockk<AccessoryOrderService>()
        coEvery { orderService.expireStaleProcessingOrder(processing) } returns true

        ExpirySweepService(orderDb, orderService, orderTtlMinutes = 5, paymentProcessingStaleMinutes = 60)
            .sweep() shouldBe 1
        coVerify(exactly = 1) { orderService.expireStaleProcessingOrder(processing) }

        ExpirySweepService(orderDb, orderService, orderTtlMinutes = 5, paymentProcessingStaleMinutes = 0)
            .sweep() shouldBe 0
        coVerify(exactly = 1) { orderDb.findStaleProcessingOrdersStartedBefore(any()) }
    }
})
