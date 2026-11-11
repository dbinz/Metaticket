package metaticket.accessory.service

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.property.Arb
import io.kotest.property.arbitrary.int
import io.kotest.property.checkAll
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import metaticket.accessory.configuration.Environment
import metaticket.accessory.database.CatalogDatabaseService
import metaticket.accessory.database.OrderDatabaseService
import metaticket.accessory.database.ReconciliationDatabaseService
import metaticket.accessory.gateway.AccessoryMailGateway
import metaticket.accessory.gateway.JobrunrGateway
import metaticket.accessory.gateway.PaymentGateway
import metaticket.accessory.pojo.db.AccessoryOrderDocument
import metaticket.accessory.pojo.db.AccessoryOrderLine
import metaticket.accessory.pojo.db.AccessoryOrderStatus
import metaticket.accessory.pojo.db.AccessoryShowDocument
import metaticket.accessory.pojo.db.FulfillmentType
import metaticket.accessory.pojo.db.ReconKind
import metaticket.accessory.security.PublicId
import org.bson.types.ObjectId

/**
 * Property 17: Confirmation email exactly-once + resend (task 7.6).
 *
 * **Validates: Requirements 9.4, 9.6, 15.1, 15.2, 15.3, 15.4, 18.2**
 *
 * ∀ o ∈ O: at most one confirmation email is dispatched for `o` on its transition to
 * `PaymentSucceed` (guarded by the `emailDispatched` claim) via the **dedicated**
 * `SendMailAccessoryOrder` gRPC method; a dispatch failure retries at most 3 times and leaves `o`
 * `PaymentSucceed` and resend-eligible; a private resend for a `PaymentSucceed` order dispatches
 * exactly one further email through the **same** dedicated method; resend for a non-existent order →
 * `404`; for a non-`PaymentSucceed` order → `409`, with no email sent.
 *
 * The mail path is [AccessoryMailGateway.sendMailAccessoryOrder] — the dedicated
 * `SendMailAccessoryOrder` RPC. The accessory service depends only on [AccessoryMailGateway], which
 * exposes **no** ticket `SendMailOrder` method at all, so it is a compile-time guarantee that the
 * accessory email path never touches the ticket flow (Req 18.2). Every assertion below verifies the
 * dedicated method and never any ticket path.
 */
class ConfirmationEmailExactlyOncePropertyTest : FunSpec({

    // Checksum-encoded public ids used by the resend endpoint need a configured 16-byte secret key.
    Environment.secretKey = "0123456789abcdef"

    val show = AccessoryShowDocument(
        id = ObjectId().toHexString(), code = "TF", codePrefix = "TF", name = "The Flob",
        pickupNote = "Nhận sản phẩm tại show",
    )

    fun orderOf(status: AccessoryOrderStatus, id: String = ObjectId().toHexString()) = AccessoryOrderDocument(
        id = id,
        accessoryShowId = show.id,
        code = "TF2026-0001",
        status = status,
        fulfillment = FulfillmentType.PickupAtShow,
        lines = listOf(
            AccessoryOrderLine(
                accessoryId = ObjectId().toHexString(), sku = "POSTER", name = "Poster",
                unitPrice = 100_000, quantity = 1,
            ),
        ),
        subtotal = 100_000,
        total = 100_000,
        customerName = "Nguyen Van A",
        customerPhone = "0987654321",
        customerEmail = "a@example.com",
    )

    fun buildService(
        catalogDb: CatalogDatabaseService = mockk(relaxed = true),
        orderDb: OrderDatabaseService = mockk(relaxed = true),
        mailGateway: AccessoryMailGateway = mockk(relaxed = true),
        reconciliationDb: ReconciliationDatabaseService = mockk(relaxed = true),
        orderStateMachine: OrderStateMachine = mockk(relaxed = true),
        jobrunrGateway: JobrunrGateway = mockk(relaxed = true),
    ) = AccessoryOrderServiceImpl(
        catalogDb = catalogDb,
        orderDb = orderDb,
        discountService = mockk(relaxed = true),
        orderCodeService = mockk(relaxed = true),
        paymentGateway = mockk<PaymentGateway>(relaxed = true),
        orderStateMachine = orderStateMachine,
        reconciliationDb = reconciliationDb,
        mailGateway = mailGateway,
        jobrunrGateway = jobrunrGateway,
        emailDispatchScope = CoroutineScope(Dispatchers.Unconfined),
    )

    // ---- Exactly-once dispatch: the emailDispatched claim caps sends at one ------------------

    test("repeated dispatch attempts send at most one email (emailDispatched claim, Req 9.4)") {
        checkAll(50, Arb.int(2..6)) { repeats ->
            val order = orderOf(AccessoryOrderStatus.PaymentSucceed)

            val catalogDb = mockk<CatalogDatabaseService>()
            coEvery { catalogDb.loadShowById(show.id) } returns show

            val orderDb = mockk<OrderDatabaseService>(relaxed = true)
            // The dispatch slot is claimable exactly once (false→true), like the atomic DB guard.
            var first = true
            coEvery { orderDb.tryClaimEmailDispatch(order.id) } coAnswers { if (first) { first = false; true } else false }

            val mailGateway = mockk<AccessoryMailGateway>(relaxed = true)
            val service = buildService(catalogDb = catalogDb, orderDb = orderDb, mailGateway = mailGateway)

            repeat(repeats) { service.dispatchConfirmationEmail(order) }

            // Exactly one email via the dedicated SendMailAccessoryOrder RPC, never more (Req 9.4, 18.2).
            coVerify(exactly = 1) { mailGateway.sendMailAccessoryOrder(order, show) }
            coVerify(exactly = 1) { orderDb.markEmailDispatched(order.id) }
        }
    }

    // ---- Dispatch failure: retries <= 3, order stays PaymentSucceed + resend-eligible --------

    test("a dispatch that fails k<3 times then succeeds attempts k+1 times and keeps the order eligible") {
        checkAll(30, Arb.int(0..2)) { failuresBeforeSuccess ->
            runTest {
                val order = orderOf(AccessoryOrderStatus.PaymentSucceed)

                val catalogDb = mockk<CatalogDatabaseService>()
                coEvery { catalogDb.loadShowById(show.id) } returns show

                val orderDb = mockk<OrderDatabaseService>(relaxed = true)
                coEvery { orderDb.tryClaimEmailDispatch(order.id) } returns true

                val mailGateway = mockk<AccessoryMailGateway>()
                var attempt = 0
                coEvery { mailGateway.sendMailAccessoryOrder(order, show) } coAnswers {
                    attempt += 1
                    if (attempt <= failuresBeforeSuccess) throw RuntimeException("transient mail failure #$attempt")
                    // success on attempt failuresBeforeSuccess+1
                }

                val osm = mockk<OrderStateMachine>(relaxed = true)
                val service = buildService(
                    catalogDb = catalogDb, orderDb = orderDb, mailGateway = mailGateway, orderStateMachine = osm,
                )

                service.dispatchConfirmationEmail(order)

                val expectedAttempts = failuresBeforeSuccess + 1
                coVerify(exactly = expectedAttempts) { mailGateway.sendMailAccessoryOrder(order, show) }
                // Retries are bounded at 3 (Req 9.6).
                (expectedAttempts <= AccessoryOrderServiceImpl.EMAIL_DISPATCH_MAX_ATTEMPTS) shouldBe true
                // Success: stamp completion, keep the claim, and never reset (order not re-eligible).
                coVerify(exactly = 1) { orderDb.markEmailDispatched(order.id) }
                coVerify(exactly = 0) { orderDb.resetEmailDispatchClaim(order.id) }
                // The order status is never transitioned by the email path (stays PaymentSucceed, Req 15.2).
                coVerify(exactly = 0) { osm.transition(any(), any(), any()) }
            }
        }
    }

    test("a dispatch that always fails retries exactly 3 times, keeps PaymentSucceed, and stays resend-eligible") {
        runTest {
            val order = orderOf(AccessoryOrderStatus.PaymentSucceed)

            val catalogDb = mockk<CatalogDatabaseService>()
            coEvery { catalogDb.loadShowById(show.id) } returns show

            val orderDb = mockk<OrderDatabaseService>(relaxed = true)
            coEvery { orderDb.tryClaimEmailDispatch(order.id) } returns true

            val mailGateway = mockk<AccessoryMailGateway>()
            coEvery { mailGateway.sendMailAccessoryOrder(order, show) } throws RuntimeException("mail down")

            val reconciliationDb = mockk<ReconciliationDatabaseService>(relaxed = true)
            val osm = mockk<OrderStateMachine>(relaxed = true)
            val service = buildService(
                catalogDb = catalogDb, orderDb = orderDb, mailGateway = mailGateway,
                reconciliationDb = reconciliationDb, orderStateMachine = osm,
            )

            service.dispatchConfirmationEmail(order)

            // Bounded retry budget of exactly 3 attempts (Req 9.6).
            coVerify(exactly = AccessoryOrderServiceImpl.EMAIL_DISPATCH_MAX_ATTEMPTS) {
                mailGateway.sendMailAccessoryOrder(order, show)
            }
            // Never marked dispatched; claim released so the order stays resend-eligible (Req 9.6, 15.2).
            coVerify(exactly = 0) { orderDb.markEmailDispatched(order.id) }
            coVerify(exactly = 1) { orderDb.resetEmailDispatchClaim(order.id) }
            coVerify(exactly = 1) { reconciliationDb.record(ReconKind.EMAIL_DISPATCH_FAILED, any(), order.id) }
            // Order status untouched by the email path (remains PaymentSucceed).
            coVerify(exactly = 0) { osm.transition(any(), any(), any()) }
        }
    }

    test("when the direct send is exhausted a durable JobRunr fallback is scheduled and the order stays resend-eligible") {
        runTest {
            val order = orderOf(AccessoryOrderStatus.PaymentSucceed)

            val catalogDb = mockk<CatalogDatabaseService>()
            coEvery { catalogDb.loadShowById(show.id) } returns show

            val orderDb = mockk<OrderDatabaseService>(relaxed = true)
            coEvery { orderDb.tryClaimEmailDispatch(order.id) } returns true

            val mailGateway = mockk<AccessoryMailGateway>()
            coEvery { mailGateway.sendMailAccessoryOrder(order, show) } throws RuntimeException("mail down")

            // JobRunr accepts the fallback job (durable retry now owns delivery).
            val jobrunrGateway = mockk<JobrunrGateway>()
            coEvery { jobrunrGateway.resendEmailAccessoryOrder(order, show) } returns true

            val service = buildService(
                catalogDb = catalogDb, orderDb = orderDb, mailGateway = mailGateway, jobrunrGateway = jobrunrGateway,
            )

            service.dispatchConfirmationEmail(order)

            // Direct send exhausted, then exactly one durable fallback scheduled (mirrors service.order).
            coVerify(exactly = AccessoryOrderServiceImpl.EMAIL_DISPATCH_MAX_ATTEMPTS) {
                mailGateway.sendMailAccessoryOrder(order, show)
            }
            coVerify(exactly = 1) { jobrunrGateway.resendEmailAccessoryOrder(order, show) }
            // Delivery handed to JobRunr; claim released so the order also stays resend-eligible.
            coVerify(exactly = 0) { orderDb.markEmailDispatched(order.id) }
            coVerify(exactly = 1) { orderDb.resetEmailDispatchClaim(order.id) }
        }
    }

    // ---- Resend: PaymentSucceed dispatches exactly one further email via the dedicated method --

    test("resend for a PaymentSucceed order dispatches exactly one further email via SendMailAccessoryOrder") {
        val order = orderOf(AccessoryOrderStatus.PaymentSucceed)
        val encodedId = PublicId.encode(order.id)

        val catalogDb = mockk<CatalogDatabaseService>()
        coEvery { catalogDb.loadShowById(show.id) } returns show

        val orderDb = mockk<OrderDatabaseService>(relaxed = true)
        coEvery { orderDb.loadOrder(order.id) } returns order
        // After the resend releases the claim, a fresh claim is winnable exactly once.
        coEvery { orderDb.tryClaimEmailDispatch(order.id) } returns true

        val mailGateway = mockk<AccessoryMailGateway>(relaxed = true)
        val service = buildService(catalogDb = catalogDb, orderDb = orderDb, mailGateway = mailGateway)

        service.resendConfirmation(encodedId)

        // Resend releases the prior claim, then sends exactly one further email via the dedicated RPC (Req 15.1).
        coVerify(exactly = 1) { orderDb.resetEmailDispatchClaim(order.id) }
        coVerify(exactly = 1) { mailGateway.sendMailAccessoryOrder(order, show) }
    }

    // ---- Resend gates: 404 for non-existent / malformed, 409 for non-PaymentSucceed ----------

    test("resend for a non-existent order is a 404 and dispatches no email (Req 15.3)") {
        val missingEncoded = PublicId.encode(ObjectId().toHexString())
        val orderDb = mockk<OrderDatabaseService>(relaxed = true)
        coEvery { orderDb.loadOrder(any()) } returns null
        val mailGateway = mockk<AccessoryMailGateway>(relaxed = true)
        val service = buildService(orderDb = orderDb, mailGateway = mailGateway)

        val ex = shouldThrow<OrderAccessException.OrderNotFound> {
            service.resendConfirmation(missingEncoded)
        }
        ex.status shouldBe 404
        coVerify(exactly = 0) { mailGateway.sendMailAccessoryOrder(any(), any()) }
    }

    test("resend for a malformed identifier is a 404 and dispatches no email (Req 15.3)") {
        val mailGateway = mockk<AccessoryMailGateway>(relaxed = true)
        val service = buildService(mailGateway = mailGateway)

        val ex = shouldThrow<OrderAccessException.OrderNotFound> {
            service.resendConfirmation("not-a-valid-encoded-id")
        }
        ex.status shouldBe 404
        coVerify(exactly = 0) { mailGateway.sendMailAccessoryOrder(any(), any()) }
    }

    test("resend for a non-PaymentSucceed order is a 409 and dispatches no email (Req 15.4)") {
        checkAll(
            io.kotest.property.exhaustive.exhaustive(
                listOf(
                    AccessoryOrderStatus.Created,
                    AccessoryOrderStatus.WaitingForPayment,
                    AccessoryOrderStatus.PaymentProcessing,
                    AccessoryOrderStatus.PaymentFailed,
                    AccessoryOrderStatus.PaymentCancelled,
                    AccessoryOrderStatus.Expired,
                ),
            ),
        ) { status ->
            val order = orderOf(status)
            val encodedId = PublicId.encode(order.id)

            val orderDb = mockk<OrderDatabaseService>(relaxed = true)
            coEvery { orderDb.loadOrder(order.id) } returns order
            val mailGateway = mockk<AccessoryMailGateway>(relaxed = true)
            val service = buildService(orderDb = orderDb, mailGateway = mailGateway)

            val ex = shouldThrow<OrderAccessException.ResendNotAllowed> {
                service.resendConfirmation(encodedId)
            }
            ex.status shouldBe 409
            coVerify(exactly = 0) { mailGateway.sendMailAccessoryOrder(any(), any()) }
        }
    }
})
