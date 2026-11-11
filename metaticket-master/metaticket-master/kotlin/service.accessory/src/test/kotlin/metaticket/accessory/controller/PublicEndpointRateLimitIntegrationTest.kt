package metaticket.accessory.controller

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.JsonNodeFactory
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.KotlinModule
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.serialization.jackson.jackson
import io.ktor.server.application.install
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.plugins.ratelimit.rateLimit
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import io.ktor.server.routing.routing
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import metaticket.accessory.configuration.Environment
import metaticket.accessory.database.CatalogDatabaseService
import metaticket.accessory.pojo.request.CheckoutResult
import metaticket.accessory.security.AccessoryPublicRateLimit
import metaticket.accessory.security.PublicId
import metaticket.accessory.security.configureRateLimiting
import metaticket.accessory.service.AccessoryOrderService
import metaticket.accessory.service.DiscountCodeService
import org.bson.types.ObjectId

/**
 * Property 19: Public endpoint rate limiting (task 12.8), plus payload-validation rejection.
 *
 * **Validates: Requirements 16.4, 16.5, 16.6**
 *
 * ∀ client IP `c`, ∀ 60-second window `w`: the number of processed requests from `c` on
 * `/accessory/checkout` (resp. `/discount/validate`) is ≤ 20; every request beyond the limit
 * is rejected with a `429` rate-limit error and produces no order and no discount evaluation
 * (Req 16.5, 16.6). Payload-validation failures return `400`/`422` without creating an order
 * (Req 16.4).
 *
 * These integration tests install the **same** [configureRateLimiting] plugin configuration used in
 * production (the [AccessoryPublicRateLimit] limiter, 20 req / 60 s per client IP, keyed by the real
 * client IP resolved from `X-Forwarded-For`) and route the sensitive endpoints exactly as
 * `service.accessory` does, driving the **real** [AccessoryController] with mocked domain services
 * through the Ktor test host.
 *
 * Note on the test host: nginx normally sets `X-Forwarded-For`; here the test issues that header
 * directly, so the per-IP keying (`requestKey`) is exercised end-to-end — the token bucket is proven
 * both to cap a single IP at 20 and to be independent across IPs.
 */
class PublicEndpointRateLimitIntegrationTest : FunSpec({

    Environment.secretKey = "0123456789abcdef"

    val parseMapper: ObjectMapper = ObjectMapper()
        .registerModule(KotlinModule.Builder().build())
        .registerModule(JavaTimeModule())

    // A structurally valid checkout body (passes the pure CheckoutValidator gate) so that, absent
    // rate limiting, it would reach AccessoryOrderService.checkout.
    fun validCheckoutBody(): String {
        val encodedAccessoryId = PublicId.encode(ObjectId().toHexString())
        return """
            {
              "lines": [ { "accessoryId": "$encodedAccessoryId", "quantity": 1 } ],
              "customerName": "Nguyen Van A",
              "customerPhone": "0987654321",
              "customerEmail": "a@example.com"
            }
        """.trimIndent()
    }

    /**
     * Build a test app wiring the checkout + discount-validate routes under the production rate-limit
     * plugin, backed by the supplied (mocked) services.
     */
    fun ApplicationTestBuilder.installApp(
        orderService: AccessoryOrderService,
        discountService: DiscountCodeService,
    ) {
        val controller = AccessoryController(
            catalogDb = mockk<CatalogDatabaseService>(relaxed = true),
            orderService = orderService,
            discountService = discountService,
            discountDb = mockk(relaxed = true),
            orderDb = mockk(relaxed = true),
            reportFilterDb = mockk(relaxed = true),
            objectMapper = parseMapper,
            jsonFactory = JsonNodeFactory.instance,
            showOpenService = mockk(relaxed = true),
        )
        application {
            configureRateLimiting() // same 20/60 per-IP limiter as production (defaults, no HOCON).
            install(ContentNegotiation) {
                jackson {
                    registerModule(KotlinModule.Builder().build())
                    registerModule(JavaTimeModule())
                }
            }
            routing {
                // Mirror production routing (Route.kt `apiAccessoryRoutes`): the sensitive write
                // endpoints live under `/accessory` (not `/public/accessory`), matching the paths the
                // test client posts to. Using the wrong prefix here makes every request 404.
                route("/accessory") {
                    rateLimit(AccessoryPublicRateLimit) {
                        post("/checkout") { controller.checkout(call) }
                        post("/discount/validate") { controller.validateDiscount(call) }
                    }
                }
            }
        }
    }

    test("a single client IP is capped at 20 checkout requests / window; the excess is 429 and creates no order") {
        val orderService = mockk<AccessoryOrderService>()
        coEvery { orderService.checkout(any(), any()) } returns
            CheckoutResult("encPay", "encOrder", "TF2026-0001", 100_000)
        val discountService = mockk<DiscountCodeService>(relaxed = true)

        testApplication {
            installApp(orderService, discountService)

            val ip = "203.0.113.10"
            val responses = mutableListOf<HttpResponse>()
            repeat(25) {
                responses += client.post("/accessory/checkout") {
                    header("X-Forwarded-For", ip)
                    header("X-Customer-User-Id", "customer-1")
                    contentType(ContentType.Application.Json)
                    setBody(validCheckoutBody())
                }
            }

            val okCount = responses.count { it.status == HttpStatusCode.OK }
            val tooManyCount = responses.count { it.status == HttpStatusCode.TooManyRequests }

            // Exactly the configured limit are processed; the remainder are rate-limited (Req 16.5).
            okCount shouldBe 20
            tooManyCount shouldBe 5

            // Over-limit requests never reached the handler ⇒ no order was created for them (Req 16.6).
            coVerify(exactly = 20) { orderService.checkout(any(), any()) }
        }
    }

    test("the rate limiter is per client IP: exhausting one IP does not throttle another") {
        val orderService = mockk<AccessoryOrderService>()
        coEvery { orderService.checkout(any(), any()) } returns
            CheckoutResult("encPay", "encOrder", "TF2026-0002", 100_000)
        val discountService = mockk<DiscountCodeService>(relaxed = true)

        testApplication {
            installApp(orderService, discountService)

            val ipA = "198.51.100.1"
            val ipB = "198.51.100.2"

            // Exhaust IP-A's window (20 OK, then 429).
            repeat(20) {
                client.post("/accessory/checkout") {
                    header("X-Forwarded-For", ipA)
                    header("X-Customer-User-Id", "customer-1")
                    contentType(ContentType.Application.Json)
                    setBody(validCheckoutBody())
                }.status shouldBe HttpStatusCode.OK
            }
            client.post("/accessory/checkout") {
                header("X-Forwarded-For", ipA)
                header("X-Customer-User-Id", "customer-1")
                contentType(ContentType.Application.Json)
                setBody(validCheckoutBody())
            }.status shouldBe HttpStatusCode.TooManyRequests

            // IP-B has its own independent bucket ⇒ still served.
            client.post("/accessory/checkout") {
                header("X-Forwarded-For", ipB)
                header("X-Customer-User-Id", "customer-1")
                contentType(ContentType.Application.Json)
                setBody(validCheckoutBody())
            }.status shouldBe HttpStatusCode.OK
        }
    }

    test("checkout payload-validation failures return 400/422 and create no order (Req 16.4)") {
        val orderService = mockk<AccessoryOrderService>(relaxed = true)
        val discountService = mockk<DiscountCodeService>(relaxed = true)

        testApplication {
            installApp(orderService, discountService)

            // Empty cart ⇒ 400 empty-cart, no order created.
            val emptyCart = client.post("/accessory/checkout") {
                header("X-Forwarded-For", "203.0.113.30")
                header("X-Customer-User-Id", "customer-1")
                contentType(ContentType.Application.Json)
                setBody("""{ "lines": [], "customerName": "A", "customerPhone": "0987654321", "customerEmail": "a@example.com" }""")
            }
            emptyCart.status shouldBe HttpStatusCode.BadRequest

            // Well-formed structure but invalid email ⇒ 422, no order created.
            val encodedAccessoryId = PublicId.encode(ObjectId().toHexString())
            val badEmail = client.post("/accessory/checkout") {
                header("X-Forwarded-For", "203.0.113.31")
                header("X-Customer-User-Id", "customer-1")
                contentType(ContentType.Application.Json)
                setBody(
                    """{ "lines": [ { "accessoryId": "$encodedAccessoryId", "quantity": 1 } ],
                        "customerName": "A", "customerPhone": "0987654321", "customerEmail": "not-an-email" }""",
                )
            }
            badEmail.status shouldBe HttpStatusCode.UnprocessableEntity

            // No order was created for either rejected payload.
            coVerify(exactly = 0) { orderService.checkout(any(), any()) }
        }
    }

    test("discount-preview with an empty cart returns 422 and performs no discount evaluation (Req 16.4)") {
        val orderService = mockk<AccessoryOrderService>(relaxed = true)
        val discountService = mockk<DiscountCodeService>(relaxed = true)

        testApplication {
            installApp(orderService, discountService)

            val resp = client.post("/accessory/discount/validate") {
                header("X-Forwarded-For", "203.0.113.40")
                header("X-Customer-User-Id", "customer-1")
                contentType(ContentType.Application.Json)
                setBody("""{ "discountCode": "THEFLOB10", "lines": [] }""")
            }
            resp.status shouldBe HttpStatusCode.UnprocessableEntity

            // No catalog derivation and no discount evaluation happened.
            coVerify(exactly = 0) { orderService.deriveCartContext(any()) }
            coVerify(exactly = 0) { discountService.evaluate(any(), any(), any()) }
        }
    }

    test("checkout without an authenticated customer (no X-Customer-User-Id) is 401 and creates no order") {
        val orderService = mockk<AccessoryOrderService>(relaxed = true)
        val discountService = mockk<DiscountCodeService>(relaxed = true)

        testApplication {
            installApp(orderService, discountService)

            val resp = client.post("/accessory/checkout") {
                header("X-Forwarded-For", "203.0.113.50")
                contentType(ContentType.Application.Json)
                setBody(validCheckoutBody())
            }
            resp.status shouldBe HttpStatusCode.Unauthorized

            // The request never reached the domain service.
            coVerify(exactly = 0) { orderService.checkout(any(), any()) }
        }
    }

    test("discount preview without an authenticated customer (no X-Customer-User-Id) is 401") {
        val orderService = mockk<AccessoryOrderService>(relaxed = true)
        val discountService = mockk<DiscountCodeService>(relaxed = true)

        testApplication {
            installApp(orderService, discountService)

            val resp = client.post("/accessory/discount/validate") {
                header("X-Forwarded-For", "203.0.113.51")
                contentType(ContentType.Application.Json)
                setBody("""{ "discountCode": "THEFLOB10", "lines": [] }""")
            }
            resp.status shouldBe HttpStatusCode.Unauthorized

            // Auth gate runs before cart / discount evaluation.
            coVerify(exactly = 0) { orderService.deriveCartContext(any()) }
            coVerify(exactly = 0) { discountService.resolveBestPreview(any(), any(), any()) }
        }
    }
})
