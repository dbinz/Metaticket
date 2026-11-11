package metaticket.payment.gateway

import com.google.protobuf.Empty
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import metaticket.order.notification.grpc.OrderNotificationServiceGrpcKt.OrderNotificationServiceCoroutineStub
import metaticket.payment.datastructure.OrderType
import metaticket.payment.datastructure.PaymentStatus
import metaticket.payment.pojo.db.PaymentDocument
import net.jqwik.api.Arbitraries
import net.jqwik.api.Arbitrary
import net.jqwik.api.ForAll
import net.jqwik.api.Property
import net.jqwik.api.Provide
import org.bson.types.ObjectId
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse

/**
 * Property-based tests for `service.payment`'s orderType-keyed callback routing (task 9.3).
 *
 * Property 2 — Callback routing:
 *   ∀ payment p: p.orderType == ACCESSORY ⟹ route(p) == accessory stub ∧
 *                p.orderType == TICKET    ⟹ route(p) == order stub.
 *
 * Property 20 — Callback routing safety for absent/unknown orderType:
 *   p.orderType absent/empty ⟹ route(p) == order stub (TICKET);
 *   p.orderType ∉ {TICKET, ACCESSORY} ⟹ delivered to no order-owning service, status retained,
 *   error recorded; a failed delivery is retried up to 5 attempts (1–60s apart), status preserved.
 *
 * **Validates: Requirements 8.1, 8.2, 8.5, 8.6**
 */
class OrderNotificationGatewayRoutingPropertyTest {

    private val fixedStatus = PaymentStatus.Processing

    private fun payment(orderType: String?, status: PaymentStatus = fixedStatus): PaymentDocument =
        PaymentDocument(
            id = ObjectId().toHexString(),
            orderId = ObjectId().toHexString(),
            pgAccountCode = "PG_ACCT",
            orderCode = "ORD-CODE",
            orderType = orderType,
            refreshStatusJobId = UUID.randomUUID(),
            amount = 100_000L,
            paymentMethods = null,
            status = status,
        )

    private fun freshStubs(): Pair<OrderNotificationServiceCoroutineStub, OrderNotificationServiceCoroutineStub> {
        val orderStub = mockk<OrderNotificationServiceCoroutineStub>(relaxed = true)
        val accessoryStub = mockk<OrderNotificationServiceCoroutineStub>(relaxed = true)
        coEvery { orderStub.notifyPaymentStatusChanged(any(), any()) } returns Empty.getDefaultInstance()
        coEvery { accessoryStub.notifyPaymentStatusChanged(any(), any()) } returns Empty.getDefaultInstance()
        return orderStub to accessoryStub
    }

    private fun gateway(
        orderStub: OrderNotificationServiceCoroutineStub,
        accessoryStub: OrderNotificationServiceCoroutineStub,
    ): OrderNotificationGateway =
        OrderNotificationGateway(
            mapOf(
                OrderType.TICKET to orderStub,
                OrderType.ACCESSORY to accessoryStub,
            )
        )

    // --- Property 2: known orderType routes to exactly the matching stub (Req 8.1, 8.2) ---

    @Property(tries = 20)
    fun `known orderType is delivered to exactly the matching stub`(@ForAll orderType: OrderType) {
        runTest {
            val (orderStub, accessoryStub) = freshStubs()
            val gw = gateway(orderStub, accessoryStub)

            gw.notifyPaymentStatusChanged(payment(orderType.name), PaymentStatus.Succeed)

            when (orderType) {
                OrderType.TICKET -> {
                    coVerify(exactly = 1) { orderStub.notifyPaymentStatusChanged(any(), any()) }
                    coVerify(exactly = 0) { accessoryStub.notifyPaymentStatusChanged(any(), any()) }
                }
                OrderType.ACCESSORY -> {
                    coVerify(exactly = 1) { accessoryStub.notifyPaymentStatusChanged(any(), any()) }
                    coVerify(exactly = 0) { orderStub.notifyPaymentStatusChanged(any(), any()) }
                }
            }
        }
    }

    // --- Property 20: absent/empty/blank orderType defaults to TICKET (Req 8.3, 8.4 via 8.1) ---

    @Property(tries = 20)
    fun `absent or blank orderType routes only to the order stub`(@ForAll("blankOrNull") orderType: String?) {
        runTest {
            val (orderStub, accessoryStub) = freshStubs()
            val gw = gateway(orderStub, accessoryStub)

            gw.notifyPaymentStatusChanged(payment(orderType), PaymentStatus.Succeed)

            coVerify(exactly = 1) { orderStub.notifyPaymentStatusChanged(any(), any()) }
            coVerify(exactly = 0) { accessoryStub.notifyPaymentStatusChanged(any(), any()) }
        }
    }

    // --- Property 20: unknown orderType delivered to no one, status retained (Req 8.5) ---

    @Property(tries = 20)
    fun `unknown orderType is delivered to no order-owning service and retains status`(
        @ForAll("unknownOrderTypes") orderType: String,
    ) {
        runTest {
            val (orderStub, accessoryStub) = freshStubs()
            val gw = gateway(orderStub, accessoryStub)
            val p = payment(orderType, status = PaymentStatus.Processing)

            // Must not throw for an unroutable value: it is a no-op delivery (Req 8.5).
            gw.notifyPaymentStatusChanged(p, PaymentStatus.Succeed)

            coVerify(exactly = 0) { orderStub.notifyPaymentStatusChanged(any(), any()) }
            coVerify(exactly = 0) { accessoryStub.notifyPaymentStatusChanged(any(), any()) }
            // Status is never mutated by the gateway -> retained across the unroutable callback.
            assertEquals(PaymentStatus.Processing, p.status)
        }
    }

    // --- Property 20: failed delivery is retried up to 5 attempts, status preserved (Req 8.6) ---

    @Property(tries = 5)
    fun `failed delivery is retried up to five attempts then propagates with status preserved`(
        @ForAll orderType: OrderType,
    ) {
        runTest {
            val (orderStub, accessoryStub) = freshStubs()
            // Force the routed stub to always fail so the full retry budget is exercised.
            coEvery { orderStub.notifyPaymentStatusChanged(any(), any()) } throws RuntimeException("delivery failed")
            coEvery { accessoryStub.notifyPaymentStatusChanged(any(), any()) } throws RuntimeException("delivery failed")
            val gw = gateway(orderStub, accessoryStub)
            val p = payment(orderType.name, status = PaymentStatus.Processing)

            assertFailsWith<RuntimeException> {
                gw.notifyPaymentStatusChanged(p, PaymentStatus.Succeed)
            }

            val routed = if (orderType == OrderType.TICKET) orderStub else accessoryStub
            coVerify(exactly = OrderNotificationGateway.MAX_DELIVERY_ATTEMPTS) {
                routed.notifyPaymentStatusChanged(any(), any())
            }
            // Status is preserved across every retry attempt (the gateway never mutates it).
            assertEquals(PaymentStatus.Processing, p.status)
        }
    }

    // --- Property 20: retry loop stops as soon as an attempt succeeds (Req 8.6) ---

    @Property(tries = 5)
    fun `retry loop stops once an attempt succeeds`(@ForAll orderType: OrderType) {
        runTest {
            val (orderStub, accessoryStub) = freshStubs()
            // Fail the first two attempts, then succeed on the third.
            coEvery { orderStub.notifyPaymentStatusChanged(any(), any()) } throws
                RuntimeException("1") andThenThrows RuntimeException("2") andThen Empty.getDefaultInstance()
            coEvery { accessoryStub.notifyPaymentStatusChanged(any(), any()) } throws
                RuntimeException("1") andThenThrows RuntimeException("2") andThen Empty.getDefaultInstance()
            val gw = gateway(orderStub, accessoryStub)

            // Should complete normally (no exception) after the successful third attempt.
            gw.notifyPaymentStatusChanged(payment(orderType.name), PaymentStatus.Succeed)

            val routed = if (orderType == OrderType.TICKET) orderStub else accessoryStub
            val other = if (orderType == OrderType.TICKET) accessoryStub else orderStub
            coVerify(exactly = 3) { routed.notifyPaymentStatusChanged(any(), any()) }
            coVerify(exactly = 0) { other.notifyPaymentStatusChanged(any(), any()) }
            // Fewer than the full budget of attempts were needed.
            assertFalse(3 > OrderNotificationGateway.MAX_DELIVERY_ATTEMPTS)
        }
    }

    @Provide
    fun blankOrNull(): Arbitrary<String?> =
        Arbitraries.of(null, "", " ", "   ", "\t", "\n")

    @Provide
    fun unknownOrderTypes(): Arbitrary<String> =
        Arbitraries.strings()
            .ofMinLength(1)
            .ofMaxLength(12)
            .filter { raw ->
                raw.isNotBlank() && runCatching { OrderType.valueOf(raw.trim()) }.isFailure
            }
}
