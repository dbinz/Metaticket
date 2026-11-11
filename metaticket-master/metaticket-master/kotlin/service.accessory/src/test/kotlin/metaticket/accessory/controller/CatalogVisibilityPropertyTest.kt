package metaticket.accessory.controller

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.JsonNodeFactory
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.KotlinModule
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.jackson.jackson
import io.ktor.server.application.install
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import io.ktor.server.testing.testApplication
import io.kotest.property.Arb
import io.kotest.property.arbitrary.arbitrary
import io.kotest.property.arbitrary.boolean
import io.kotest.property.arbitrary.element
import io.kotest.property.arbitrary.int
import io.kotest.property.checkAll
import io.mockk.coEvery
import io.mockk.mockk
import metaticket.accessory.configuration.Environment
import metaticket.accessory.database.CatalogDatabaseService
import metaticket.accessory.pojo.db.AccessoryDocument
import metaticket.accessory.pojo.db.AccessoryShowDocument
import metaticket.accessory.pojo.db.AccessoryShowStatus
import metaticket.accessory.security.PublicId
import metaticket.accessory.service.AccessoryOrderService
import metaticket.accessory.service.DiscountCodeService
import org.bson.types.ObjectId

/**
 * Property 11: Catalog visibility (task 12.5).
 *
 * **Validates: Requirements 1.1, 1.4**
 *
 * ∀ accessory returned by a public listing: `active == true`. Inactive accessories are never
 * exposed through the storefront read endpoints. Waiting / Closed shows are listed on `/san-pham`
 * so detail fetch is allowed for every status.
 *
 * The test drives the **real** [AccessoryController] read handler (`fetchShowById`) and the
 * **real** response DTO mappers through the Ktor test host. The
 * [CatalogDatabaseService] collaborator is mocked with an in-memory catalog holding a *mixed* set of
 * `Open`/`Closed` shows and `active`/inactive accessories; its query methods honor their documented
 * filtering contract (`loadShowById` ⇒ any status,
 * `loadActiveAccessories` ⇒ `active` only). The property then asserts that across many random mixed
 * catalogs the storefront endpoints surface **only** `active` accessories,
 * and that the response payloads never carry an inactive accessory — catching any
 * regression where a handler called an unfiltered query or a mapper leaked a hidden entity.
 */
class CatalogVisibilityPropertyTest : FunSpec({

    // PublicId.encode/decode (used by the mappers and the query params) needs a 16-char AES key.
    Environment.secretKey = "0123456789abcdef"

    val parseMapper: ObjectMapper = ObjectMapper()
        .registerModule(KotlinModule.Builder().build())
        .registerModule(JavaTimeModule())

    // A show with a random visibility status.
    val showArb: Arb<AccessoryShowDocument> = arbitrary {
        AccessoryShowDocument(
            id = ObjectId().toHexString(),
            code = "SH-${Arb.int(0..1_000_000).bind()}",
            name = "Show",
            status = Arb.element(AccessoryShowStatus.Open, AccessoryShowStatus.Waiting, AccessoryShowStatus.Closed).bind(),
        )
    }

    fun accessoryArb(showId: String): Arb<AccessoryDocument> = arbitrary {
        AccessoryDocument(
            id = ObjectId().toHexString(),
            accessoryShowId = showId,
            sku = "SKU-${Arb.int(0..1_000_000).bind()}",
            name = "Accessory",
            price = 100_000,
            stock = 10,
            active = Arb.boolean().bind(),
        )
    }

    // A mixed catalog: 1..5 shows (each Open/Closed), each with 0..4 accessories (each active/inactive).
    val catalogArb: Arb<Pair<List<AccessoryShowDocument>, List<AccessoryDocument>>> = arbitrary {
        val showCount = Arb.int(1..5).bind()
        val shows = (0 until showCount).map { showArb.bind() }
        val accessories = mutableListOf<AccessoryDocument>()
        for (s in shows) {
            val accCount = Arb.int(0..4).bind()
            repeat(accCount) { accessories += accessoryArb(s.id).bind() }
        }
        shows to accessories
    }

    test("public read endpoints surface only active accessories") {
        // Mutable in-memory catalog the mock reads on each call; varied per property iteration.
        var curShows: List<AccessoryShowDocument> = emptyList()
        var curAccessories: List<AccessoryDocument> = emptyList()

        val catalogDb = mockk<CatalogDatabaseService>()
        // Honor the documented filtering contract of each query (the real Mongo-backed behavior).
        coEvery { catalogDb.loadShowById(any()) } answers {
            val raw = firstArg<String>()
            curShows.firstOrNull { it.id == raw }
        }
        coEvery { catalogDb.loadActiveAccessories(any()) } answers {
            val showId = firstArg<String>()
            curAccessories.filter { it.accessoryShowId == showId && it.active }
        }

        val controller = AccessoryController(
            catalogDb = catalogDb,
            orderService = mockk<AccessoryOrderService>(relaxed = true),
            discountService = mockk<DiscountCodeService>(relaxed = true),
            discountDb = mockk(relaxed = true),
            orderDb = mockk(relaxed = true),
            reportFilterDb = mockk(relaxed = true),
            objectMapper = parseMapper,
            jsonFactory = JsonNodeFactory.instance,
            showOpenService = mockk(relaxed = true),
        )

        testApplication {
            application {
                install(ContentNegotiation) {
                    jackson {
                        registerModule(KotlinModule.Builder().build())
                        registerModule(JavaTimeModule())
                    }
                }
                routing {
                    get("/public/accessory/show/fetch") { controller.fetchShowById(call) }
                }
            }

            checkAll(100, catalogArb) { (shows, accessories) ->
                curShows = shows
                curAccessories = accessories

                // Single-show fetch: missing ⇒ 404; present ⇒ 200 with only active accessories.
                for (s in shows) {
                    val encoded = PublicId.encode(s.id)
                    val resp = client.get("/public/accessory/show/fetch") { parameter("id", encoded) }

                    resp.status shouldBe HttpStatusCode.OK
                    val node = parseMapper.readTree(resp.bodyAsText())
                    val surfacedAccIds =
                        node.get("accessories").mapNotNull { PublicId.decode(it.get("id").asText()) }.toSet()

                    val activeIds = accessories
                        .filter { it.accessoryShowId == s.id && it.active }.map { it.id }.toSet()
                    val inactiveIds = accessories
                        .filter { it.accessoryShowId == s.id && !it.active }.map { it.id }.toSet()

                    surfacedAccIds shouldBe activeIds
                    surfacedAccIds.intersect(inactiveIds).isEmpty() shouldBe true
                }
            }
        }
    }
})
