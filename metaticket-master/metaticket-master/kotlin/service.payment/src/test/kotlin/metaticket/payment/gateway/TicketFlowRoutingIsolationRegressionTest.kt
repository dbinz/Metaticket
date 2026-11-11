package metaticket.payment.gateway

import com.google.protobuf.Empty
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import metaticket.order.notification.grpc.OrderNotificationServiceGrpcKt.OrderNotificationServiceCoroutineStub
import metaticket.payment.datastructure.OrderType
import metaticket.payment.datastructure.PaymentStatus
import metaticket.payment.grpc.PaymentMessage
import metaticket.payment.pojo.db.PaymentDocument
import org.bson.types.ObjectId
import org.junit.jupiter.api.Test
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Regression integration test asserting the ticket flow's default callback routing is unchanged and
 * that the `orderType` discriminator is the only, strictly additive change to the payment request
 * (task 9.4).
 *
 * Property 1 — Ticket flow routing isolation:
 *   ∀ ticket payment p: orderType defaults to TICKET (when absent) and route(p) == service.order,
 *   exactly as before. The `orderType` discriminator is the only additive change to the payment
 *   request and does not alter any existing ticket payment request field or ticket callback behavior.
 *
 * **Validates: Requirements 8.3, 8.4, 18.4**
 */
class TicketFlowRoutingIsolationRegressionTest {

    private fun payment(orderType: String?): PaymentDocument =
        PaymentDocument(
            id = ObjectId().toHexString(),
            orderId = ObjectId().toHexString(),
            pgAccountCode = "PG_ACCT",
            orderCode = "ORD-CODE",
            orderType = orderType,
            refreshStatusJobId = UUID.randomUUID(),
            amount = 100_000L,
            paymentMethods = null,
            status = PaymentStatus.Processing,
        )

    private fun stubsAndGateway(): Triple<OrderNotificationServiceCoroutineStub, OrderNotificationServiceCoroutineStub, OrderNotificationGateway> {
        val orderStub = mockk<OrderNotificationServiceCoroutineStub>(relaxed = true)
        val accessoryStub = mockk<OrderNotificationServiceCoroutineStub>(relaxed = true)
        coEvery { orderStub.notifyPaymentStatusChanged(any(), any()) } returns Empty.getDefaultInstance()
        coEvery { accessoryStub.notifyPaymentStatusChanged(any(), any()) } returns Empty.getDefaultInstance()
        val gw = OrderNotificationGateway(
            mapOf(
                OrderType.TICKET to orderStub,
                OrderType.ACCESSORY to accessoryStub,
            )
        )
        return Triple(orderStub, accessoryStub, gw)
    }

    // --- Property 1: legacy payments (orderType field absent) route only to service.order (Req 8.4) ---

    @Test
    fun `legacy payment with absent orderType routes only to the order service`() = runTest {
        val (orderStub, accessoryStub, gw) = stubsAndGateway()

        // A payment created before the orderType field existed decodes to a null orderType.
        gw.notifyPaymentStatusChanged(payment(orderType = null), PaymentStatus.Succeed)

        coVerify(exactly = 1) { orderStub.notifyPaymentStatusChanged(any(), any()) }
        coVerify(exactly = 0) { accessoryStub.notifyPaymentStatusChanged(any(), any()) }
    }

    // --- Property 1: default TICKET payments route only to service.order (Req 8.3) ---

    @Test
    fun `default ticket payment routes only to the order service`() = runTest {
        val (orderStub, accessoryStub, gw) = stubsAndGateway()

        gw.notifyPaymentStatusChanged(payment(orderType = OrderType.TICKET.name), PaymentStatus.Succeed)

        coVerify(exactly = 1) { orderStub.notifyPaymentStatusChanged(any(), any()) }
        coVerify(exactly = 0) { accessoryStub.notifyPaymentStatusChanged(any(), any()) }
    }

    // --- Property 1: ticket notifyPaymentStarting also routes only to service.order (Req 8.4) ---

    @Test
    fun `ticket notifyPaymentStarting routes only to the order service`() = runTest {
        val (orderStub, accessoryStub, gw) = stubsAndGateway()
        coEvery { orderStub.notifyPaymentStarted(any(), any()) } returns Empty.getDefaultInstance()
        coEvery { accessoryStub.notifyPaymentStarted(any(), any()) } returns Empty.getDefaultInstance()

        gw.notifyPaymentStarting(payment(orderType = null))

        coVerify(exactly = 1) { orderStub.notifyPaymentStarted(any(), any()) }
        coVerify(exactly = 0) { accessoryStub.notifyPaymentStarted(any(), any()) }
    }

    // --- Property 1: the PaymentDocument default preserves the ticket flow (Req 8.3, 18.4) ---

    @Test
    fun `payment document defaults orderType to TICKET`() {
        // Constructing a payment the way the pre-orderType code did (omitting orderType) must default
        // to TICKET so every pre-existing/ticket payment keeps routing to service.order.
        val doc = PaymentDocument(
            orderId = ObjectId().toHexString(),
            pgAccountCode = "PG_ACCT",
            orderCode = "ORD-CODE",
            refreshStatusJobId = UUID.randomUUID(),
            amount = 100_000L,
            paymentMethods = null,
        )
        assertEquals(OrderType.TICKET.name, doc.orderType)
    }

    // --- Property 1: orderType is the ONLY additive change to CreatePaymentRequest (Req 18.4) ---

    @Test
    fun `CreatePaymentRequest adds only the additive optional orderType field`() {
        val descriptor = PaymentMessage.CreatePaymentRequest.getDescriptor()

        // The pre-existing ticket contract: field name -> (number, optional?) must be byte-for-byte intact.
        data class Expected(val number: Int, val optional: Boolean)
        val ticketContract = mapOf(
            "order_id" to Expected(1, false),
            "order_code" to Expected(2, false),
            "amount" to Expected(3, false),
            "payment_methods" to Expected(4, false), // repeated
            "pg_account_code" to Expected(5, true),
        )

        ticketContract.forEach { (name, expected) ->
            val field = descriptor.findFieldByName(name)
            assertNotNull(field, "pre-existing ticket field '$name' must still exist")
            assertEquals(expected.number, field.number, "field '$name' number must be unchanged")
            if (name == "payment_methods") {
                assertTrue(field.isRepeated, "payment_methods must remain repeated")
            } else {
                // proto3 `optional` scalars have explicit presence; plain scalars do not.
                assertEquals(expected.optional, field.hasPresence(), "field '$name' optionality must be unchanged")
            }
        }

        // The single additive discriminator: order_type, optional string, field number 6.
        val orderTypeField = descriptor.findFieldByName("order_type")
        assertNotNull(orderTypeField, "the additive order_type discriminator must exist")
        assertEquals(6, orderTypeField.number)
        assertTrue(orderTypeField.hasPresence(), "order_type must be an optional (additive) field")
        assertEquals(
            com.google.protobuf.Descriptors.FieldDescriptor.Type.STRING,
            orderTypeField.type,
            "order_type must be a string discriminator",
        )

        // Nothing beyond the 5 ticket fields + the 1 additive field exists (no other structural change).
        val expectedFieldNames = ticketContract.keys + "order_type"
        val actualFieldNames = descriptor.fields.map { it.name }.toSet()
        assertEquals(expectedFieldNames, actualFieldNames, "orderType must be the only additive change")
    }

    // --- Property 1: an ACCESSORY payment still routes away from service.order (isolation guard) ---

    @Test
    fun `accessory payment does not touch the ticket order service`() = runTest {
        val (orderStub, accessoryStub, gw) = stubsAndGateway()

        gw.notifyPaymentStatusChanged(payment(orderType = OrderType.ACCESSORY.name), PaymentStatus.Succeed)

        coVerify(exactly = 0) { orderStub.notifyPaymentStatusChanged(any(), any()) }
        coVerify(exactly = 1) { accessoryStub.notifyPaymentStatusChanged(any(), any()) }
    }
}
