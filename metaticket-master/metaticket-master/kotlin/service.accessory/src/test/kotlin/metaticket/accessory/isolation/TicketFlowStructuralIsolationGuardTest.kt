package metaticket.accessory.isolation

import com.google.protobuf.Descriptors
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.collections.shouldContainAll
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import metaticket.accessory.gateway.AccessoryMailGateway
import metaticket.accessory.pojo.db.AccessoryOrderDocument
import metaticket.accessory.pojo.db.AccessoryOrderLine
import metaticket.accessory.pojo.db.AccessoryOrderStatus
import metaticket.accessory.pojo.db.AccessoryShowDocument
import metaticket.accessory.pojo.db.FulfillmentType
import com.google.protobuf.Empty
import metaticket.notification.mail.grpc.MailMessage
import metaticket.notification.mail.grpc.MailNotificationServiceGrpcKt
import metaticket.payment.grpc.PaymentMessage
import org.bson.types.ObjectId

/**
 * Property 21: Ticket-flow structural isolation (task 16.4).
 *
 * **Validates: Requirements 18.1, 18.2, 18.3, 18.4, 18.5**
 *
 * The Accessory_Sales_Feature persists all accessory data only in `service.accessory`-owned
 * models/collections and never modifies or writes to `OrderDocument` or any other Ticket_Flow data
 * model (Req 18.1). Every change to a shared service is strictly additive and introduces a new
 * dedicated structure/method for accessories: the payment change is limited to the additive
 * `orderType` discriminator on `CreatePaymentRequest` (Req 18.4), and the accessory confirmation
 * email is dispatched exclusively through the new dedicated `SendMailAccessoryOrder` method with its
 * `SendMailAccessoryOrderRequest` message (Req 18.2), leaving the ticket `SendMailOrder` method,
 * `SendMailOrderRequest` message, and `OrderSummaryProto` message unmodified (Req 18.3, 18.5).
 *
 * Formally: `∀ accessory operation op: writes(op) ⊆ metaticket_accessory ∪ {payment.orderType-additive}`
 * ∧ `emailMethod(o ∈ O) == SendMailAccessoryOrder ≠ SendMailOrder`.
 *
 * This is a static / structural + descriptor-based guard (no live DB is needed): it mirrors the
 * descriptor-based checks used in `service.payment`'s `TicketFlowRoutingIsolationRegressionTest`
 * (task 9.4) and extends them to the notification proto and the accessory email path.
 */
class TicketFlowStructuralIsolationGuardTest : FunSpec({

    // -------------------------------------------------------------------------------------------
    // (a) Structural isolation — no reference to service.order's OrderDocument / Ticket_Flow model,
    //     and AccessoryMailGateway exposes only the dedicated accessory send method (Req 18.1, 18.2).
    // -------------------------------------------------------------------------------------------

    test("the accessory module has no dependency on service.order's OrderDocument (Ticket_Flow model, Req 18.1)") {
        // The accessory service depends only on service.order's `grpc` artifact (the shared
        // OrderNotificationService proto), never on service.order's main source. Its Ticket_Flow
        // domain model `metaticket.order.pojo.db.OrderDocument` must therefore be entirely absent
        // from the accessory (test) classpath — proving accessory operations cannot read or write it.
        val loaded = runCatching {
            Class.forName("metaticket.order.pojo.db.OrderDocument")
        }
        loaded.isFailure.shouldBeTrue()
        (loaded.exceptionOrNull() is ClassNotFoundException ||
            loaded.exceptionOrNull() is NoClassDefFoundError).shouldBeTrue()
    }

    test("AccessoryMailGateway exposes only sendMailAccessoryOrder and no ticket SendMailOrder method (Req 18.2)") {
        val declaredNames = AccessoryMailGateway::class.java.declaredMethods.map { it.name }

        // The dedicated accessory send method is present...
        declaredNames.any { it == "sendMailAccessoryOrder" }.shouldBeTrue()

        // ...and there is NO ticket-order send method surface on the accessory gateway. The accessory
        // email path is a compile-time-separate surface: it cannot invoke SendMailOrder because it
        // does not expose it at all (Req 18.2).
        declaredNames.none { it.equals("sendMailOrder", ignoreCase = true) }.shouldBeTrue()
    }

    // -------------------------------------------------------------------------------------------
    // (b) The accessory email path invokes ONLY the dedicated SendMailAccessoryOrder RPC and never
    //     the ticket SendMailOrder RPC (Req 18.2).
    // -------------------------------------------------------------------------------------------

    test("sending an accessory confirmation invokes only SendMailAccessoryOrder, never SendMailOrder (Req 18.2)") {
        runTest {
            val stub = mockk<MailNotificationServiceGrpcKt.MailNotificationServiceCoroutineStub>(relaxed = true)
            coEvery { stub.sendMailAccessoryOrder(any(), any()) } returns Empty.getDefaultInstance()

            val gateway = AccessoryMailGateway(stub)

            val show = AccessoryShowDocument(
                id = ObjectId().toHexString(), code = "TF", codePrefix = "TF", name = "The Flob",
                pickupNote = "Nhận sản phẩm tại show",
            )
            val order = AccessoryOrderDocument(
                id = ObjectId().toHexString(),
                accessoryShowId = show.id,
                code = "TF2026-0001",
                status = AccessoryOrderStatus.PaymentSucceed,
                fulfillment = FulfillmentType.PickupAtShow,
                lines = listOf(
                    AccessoryOrderLine(
                        accessoryId = ObjectId().toHexString(), sku = "TSHIRT", name = "T-Shirt",
                        variantKey = "M", variantLabel = "Size M", unitPrice = 250_000, quantity = 2,
                    ),
                ),
                subtotal = 500_000,
                total = 500_000,
                customerName = "Nguyen Van A",
                customerPhone = "0987654321",
                customerEmail = "a@example.com",
            )

            gateway.sendMailAccessoryOrder(order, show)

            // Exactly one call to the dedicated accessory RPC; the ticket SendMailOrder RPC is never
            // touched by the accessory email path (Req 18.2).
            coVerify(exactly = 1) { stub.sendMailAccessoryOrder(any(), any()) }
            coVerify(exactly = 0) { stub.sendMailOrder(any(), any()) }
        }
    }

    // -------------------------------------------------------------------------------------------
    // (c) Proto compatibility — the ticket notification contract (SendMailOrderRequest /
    //     OrderSummaryProto) is unchanged, the new accessory messages exist, and the payment request
    //     gains only the additive order_type field (Req 18.3, 18.4, 18.5).
    // -------------------------------------------------------------------------------------------

    test("ticket SendMailOrderRequest keeps its pre-existing fields unchanged (Req 18.3)") {
        val d = MailMessage.SendMailOrderRequest.getDescriptor()

        // Pre-existing ticket fields: name -> field number, all preserved byte-for-byte.
        mapOf(
            "reqId" to 1,
            "showId" to 2,
            "sessionId" to 3,
            "mailPluginTemplate" to 6,
            "orderSummary" to 7,
        ).forEach { (name, number) ->
            val field = d.findFieldByName(name)
            field.shouldNotBeNull()
            field.number shouldBe number
        }

        // The order summary is still carried as the ticket OrderSummaryProto message type.
        val orderSummary = d.findFieldByName("orderSummary")!!
        orderSummary.type shouldBe Descriptors.FieldDescriptor.Type.MESSAGE
        orderSummary.messageType.name shouldBe "OrderSummaryProto"
    }

    test("ticket OrderSummaryProto keeps its pre-existing fields unchanged (Req 18.3)") {
        val d = MailMessage.OrderSummaryProto.getDescriptor()

        mapOf(
            "reservationId" to 1,
            "orderId" to 2,
            "orderCode" to 3,
            "orderStatus" to 4,
            "email" to 5,
            "orderDetail" to 9,
            "buyerInfo" to 10,
            "createdAt" to 11,
        ).forEach { (name, number) ->
            val field = d.findFieldByName(name)
            field.shouldNotBeNull()
            field.number shouldBe number
        }

        // createdAt is still an int64; the nested detail/buyer are still message types.
        d.findFieldByName("createdAt")!!.type shouldBe Descriptors.FieldDescriptor.Type.INT64
        d.findFieldByName("orderDetail")!!.type shouldBe Descriptors.FieldDescriptor.Type.MESSAGE
        d.findFieldByName("buyerInfo")!!.type shouldBe Descriptors.FieldDescriptor.Type.MESSAGE
    }

    test("the new dedicated SendMailAccessoryOrderRequest message exists with its accessory fields (Req 18.2)") {
        val d = MailMessage.SendMailAccessoryOrderRequest.getDescriptor()

        mapOf(
            "req_id" to 1,
            "mail_plugin" to 2,
            "mail_template" to 3,
            "recipient_email" to 4,
            "order_code" to 5,
            "show_name" to 6,
            "lines" to 7,
            "subtotal" to 8,
            "has_discount" to 9,
            "discount_code" to 10,
            "discount_amount" to 11,
            "total" to 12,
            "pickup_note" to 13,
            "customer_name" to 14,
        ).forEach { (name, number) ->
            val field = d.findFieldByName(name)
            field.shouldNotBeNull()
            field.number shouldBe number
        }

        // The line items are a repeated AccessoryMailLineItem message.
        val lines = d.findFieldByName("lines")!!
        lines.isRepeated.shouldBeTrue()
        lines.type shouldBe Descriptors.FieldDescriptor.Type.MESSAGE
        lines.messageType.name shouldBe "AccessoryMailLineItem"
    }

    test("the new AccessoryMailLineItem message exists with its per-line fields (Req 18.2)") {
        val d = MailMessage.AccessoryMailLineItem.getDescriptor()

        mapOf(
            "product_name" to 1,
            "variant_label" to 2,
            "quantity" to 3,
            "unit_price" to 4,
            "line_total" to 5,
        ).forEach { (name, number) ->
            val field = d.findFieldByName(name)
            field.shouldNotBeNull()
            field.number shouldBe number
        }

        d.findFieldByName("quantity")!!.type shouldBe Descriptors.FieldDescriptor.Type.INT32
        d.findFieldByName("unit_price")!!.type shouldBe Descriptors.FieldDescriptor.Type.INT64
        d.findFieldByName("line_total")!!.type shouldBe Descriptors.FieldDescriptor.Type.INT64
    }

    test("payment CreatePaymentRequest keeps its 5 ticket fields and adds only the additive order_type (Req 18.4)") {
        val d = PaymentMessage.CreatePaymentRequest.getDescriptor()

        // The pre-existing ticket contract: field name -> (number, optional?) must be intact.
        data class Expected(val number: Int, val optional: Boolean)
        val ticketContract = mapOf(
            "order_id" to Expected(1, false),
            "order_code" to Expected(2, false),
            "amount" to Expected(3, false),
            "payment_methods" to Expected(4, false), // repeated
            "pg_account_code" to Expected(5, true),
        )

        ticketContract.forEach { (name, expected) ->
            val field = d.findFieldByName(name)
            field.shouldNotBeNull()
            field.number shouldBe expected.number
            if (name == "payment_methods") {
                field.isRepeated.shouldBeTrue()
            } else {
                field.hasPresence() shouldBe expected.optional
            }
        }

        // The single additive discriminator: order_type, optional string, field number 6.
        val orderType = d.findFieldByName("order_type")
        orderType.shouldNotBeNull()
        orderType.number shouldBe 6
        orderType.hasPresence().shouldBeTrue()
        orderType.type shouldBe Descriptors.FieldDescriptor.Type.STRING

        // Nothing beyond the 5 ticket fields + the 1 additive field exists (no other structural change).
        val expectedFieldNames = ticketContract.keys + "order_type"
        val actualFieldNames = d.fields.map { it.name }.toSet()
        actualFieldNames shouldBe expectedFieldNames
    }

    test("the payment request additive-only invariant: order_type is the sole field beyond the ticket set (Req 18.4, 18.5)") {
        val d = PaymentMessage.CreatePaymentRequest.getDescriptor()
        val ticketFieldNames = listOf("order_id", "order_code", "amount", "payment_methods", "pg_account_code")
        // All 5 ticket fields are still present (the accessory feature added nothing to them).
        d.fields.map { it.name } shouldContainAll ticketFieldNames
        // Exactly one additive field beyond the ticket set.
        (d.fields.size - ticketFieldNames.size) shouldBe 1
    }
})
