package metaticket.accessory.controller

import com.fasterxml.jackson.databind.node.JsonNodeFactory
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
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
import io.mockk.coEvery
import io.mockk.mockk
import metaticket.accessory.database.CatalogDatabaseService
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** Handler contract tests: a missing route is deliberately not interpreted as an empty catalog. */
class AccessorySearchContractTest {
    private val mapper = jacksonObjectMapper()

    private fun controller(catalog: CatalogDatabaseService) = AccessoryController(
        catalogDb = catalog, orderService = mockk(), discountService = mockk(), discountDb = mockk(),
        orderDb = mockk(), reportFilterDb = mockk(), objectMapper = mapper,
        jsonFactory = JsonNodeFactory.instance, showOpenService = mockk(),
    )

    @Test
    fun `no matches returns HTTP 200 and an empty page`() = testApplication {
        val catalog = mockk<CatalogDatabaseService>()
        coEvery { catalog.searchShowsForStorefront("khongtontai", 0, 3) } returns CatalogDatabaseService.AccessoryShowSearchPage(emptyList(), 0)
        coEvery { catalog.minPriceByShowIds(emptyList()) } returns emptyMap()
        val handler = controller(catalog)
        application {
            install(ContentNegotiation) { jackson() }
            routing { get("/public/accessory/show/search") { handler.searchShowsForStorefront(call) } }
        }
        val response = client.get("/public/accessory/show/search") {
            parameter("keyword", "khongtontai"); parameter("page", 0); parameter("size", 3)
        }
        assertEquals(HttpStatusCode.OK, response.status)
        val body = mapper.readTree(response.bodyAsText())
        assertEquals(0, body["content"].size())
        assertEquals(0, body["totalElements"].asInt())
        assertEquals(0, body["totalPages"].asInt())
        assertTrue(body["empty"].asBoolean())
    }

    @Test
    fun `past the last page preserves the real total`() = testApplication {
        val catalog = mockk<CatalogDatabaseService>()
        coEvery { catalog.searchShowsForStorefront("ao", 2, 3) } returns CatalogDatabaseService.AccessoryShowSearchPage(emptyList(), 4)
        coEvery { catalog.minPriceByShowIds(emptyList()) } returns emptyMap()
        val handler = controller(catalog)
        application {
            install(ContentNegotiation) { jackson() }
            routing { get("/public/accessory/show/search") { handler.searchShowsForStorefront(call) } }
        }
        val response = client.get("/public/accessory/show/search") {
            parameter("keyword", "ao"); parameter("page", 2); parameter("size", 3)
        }
        assertEquals(HttpStatusCode.OK, response.status)
        val body = mapper.readTree(response.bodyAsText())
        assertEquals(0, body["content"].size())
        assertEquals(4, body["totalElements"].asInt())
        assertEquals(2, body["totalPages"].asInt())
        assertTrue(body["empty"].asBoolean())
    }

    @Test
    fun `overlong keyword remains a validation error`() = testApplication {
        val handler = controller(mockk())
        application {
            install(ContentNegotiation) { jackson() }
            routing { get("/public/accessory/show/search") { handler.searchShowsForStorefront(call) } }
        }
        val response = client.get("/public/accessory/show/search") { parameter("keyword", "a".repeat(101)) }
        assertEquals(HttpStatusCode.BadRequest, response.status)
    }
}
