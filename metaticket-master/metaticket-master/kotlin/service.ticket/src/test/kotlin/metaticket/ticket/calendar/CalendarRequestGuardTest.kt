package metaticket.ticket.calendar

import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import io.mockk.*
import kotlinx.coroutines.test.runTest
import metaticket.ticket.cache.ShowSessionCache
import metaticket.ticket.calendar.CalendarTestFixtures.SESSION_ID
import metaticket.ticket.calendar.CalendarTestFixtures.SHOW_ID
import metaticket.ticket.calendar.CalendarTestFixtures.calendarSession
import metaticket.ticket.calendar.CalendarTestFixtures.today
import metaticket.ticket.database.service.CategoryMappingDBService
import metaticket.ticket.database.service.TicketCalendarDayDBService
import metaticket.ticket.database.service.TicketCapacityDBService
import metaticket.ticket.database.service.TicketDocDBService
import metaticket.ticket.pojo.db.CategoryItemDocument
import metaticket.ticket.pojo.db.CategoryMappingDocument
import metaticket.ticket.pojo.request.BulkUpdateCalendarRequest
import metaticket.ticket.pojo.request.DeleteCalendarRequest
import metaticket.ticket.pojo.request.GenerateCalendarRequest
import metaticket.ticket.redis.RedisAsyncClient
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.test.assertFailsWith

/**
 * Chặn input xấu của các API lịch bán thành 400 có thông báo trả được, thay vì 500: null lọt qua Jackson (tham số
 * generic, phần tử list), giá vượt trần, và input thô của client trong thông báo lỗi (surrogate UTF-16 lẻ làm Ktor encode
 * text/plain thất bại). Request đi qua đúng ObjectMapper kiểu của Application (Kotlin + JavaTime).
 */
class CalendarRequestGuardTest {

    private val mapper = jacksonObjectMapper().registerModule(JavaTimeModule())
    private val sessionCache = mockk<ShowSessionCache>()
    private val ticketDocDB = mockk<TicketDocDBService>()
    private val categoryDB = mockk<CategoryMappingDBService>()
    private val service = CalendarTicketService(
        sessionCache, ticketDocDB, mockk<TicketCapacityDBService>(), mockk<TicketCalendarDayDBService>(), categoryDB,
        mockk<RedisAsyncClient>(),
    )
    private val from = today.plusDays(10).toString()
    private val to = today.plusDays(20).toString()
    private val loneSurrogate = "\ud83d"

    init {
        coEvery { sessionCache.get(SHOW_ID, SESSION_ID) } returns calendarSession()
    }

    private fun generateJson(
        type: String = """{"category":"STD","price":{"default":100000},"quota":{"default":10}}""",
        types: String = "[$type]",
        daysOfWeek: String = "[1,2,3,4,5,6,7]",
        extra: String = "",
    ) = """{"showId":"$SHOW_ID","sessionId":"$SESSION_ID","fromDate":"$from","toDate":"$to",
        |"daysOfWeek":$daysOfWeek,"types":$types$extra}""".trimMargin()

    private fun scopeJson(extra: String) =
        """{"showId":"$SHOW_ID","sessionId":"$SESSION_ID","fromDate":"$from","toDate":"$to"$extra}"""

    private suspend fun generate(json: String) = service.generate(mapper.readValue<GenerateCalendarRequest>(json), "op")

    private suspend fun assertBadRequest(block: suspend () -> Unit): CalendarException {
        val error = assertFailsWith<CalendarException> { block() }
        assertEquals(400, error.status)
        return error
    }

    /** Đúng điều Ktor làm khi trả text/plain: encode UTF-8 với REPORT — chuỗi encode không được thì 500. */
    private fun assertEncodableReply(error: CalendarException) {
        val message = error.message.orEmpty()
        assertTrue(Charsets.UTF_8.newEncoder().canEncode(message), "thông báo không encode UTF-8 được: $message")
        assertTrue(message.length <= 200, "thông báo dài ${message.length} ký tự")
    }

    @Test
    fun `null weekday price is a bad request, not a NullPointerException`() = runTest {
        assertBadRequest {
            generate(generateJson(type = """{"category":"STD","price":{"default":1,"byDayOfWeek":{"3":null}},"quota":{"default":10}}"""))
        }
    }

    @Test
    fun `null default quota is a bad request`() = runTest {
        assertBadRequest {
            generate(generateJson(type = """{"category":"STD","price":{"default":1},"quota":{"default":null}}"""))
        }
    }

    @Test
    fun `null default daily capacity is a bad request`() = runTest {
        assertBadRequest { generate(generateJson(extra = ""","dailyCapacity":{"default":null}""")) }
    }

    @Test
    fun `null elements in the generate lists are a bad request`() = runTest {
        assertBadRequest { generate(generateJson(types = "[null]")) }
        assertBadRequest { generate(generateJson(daysOfWeek = "[1,null]")) }
        assertBadRequest { generate(generateJson(extra = ""","excludeDates":[null]""")) }
    }

    @Test
    fun `null elements in the bulk update and delete scopes are a bad request`() = runTest {
        assertBadRequest { service.bulkUpdate(mapper.readValue<BulkUpdateCalendarRequest>(scopeJson(""","dates":[null],"price":1""")), "op") }
        assertBadRequest { service.bulkUpdate(mapper.readValue<BulkUpdateCalendarRequest>(scopeJson(""","daysOfWeek":[null],"price":1""")), "op") }
        assertBadRequest { service.delete(mapper.readValue<DeleteCalendarRequest>(scopeJson(""","dates":[null]""")), "op") }
    }

    @Test
    fun `price above the ceiling is rejected on generate and on bulk update`() = runTest {
        val over = CalendarPlanner.MAX_PRICE + 1
        assertBadRequest {
            generate(generateJson(type = """{"category":"STD","price":{"default":$over},"quota":{"default":10}}"""))
        }
        assertBadRequest {
            generate(generateJson(type = """{"category":"STD","price":{"default":1,"byDayOfWeek":{"3":${Long.MAX_VALUE}}},"quota":{"default":10}}"""))
        }
        assertBadRequest { service.bulkUpdate(mapper.readValue<BulkUpdateCalendarRequest>(scopeJson(""","price":$over""")), "op") }
    }

    @Test
    fun `price at the ceiling is accepted`() = runTest {
        coEvery { categoryDB.findByShowAndSession(SHOW_ID, SESSION_ID) } returns CategoryMappingDocument(
            showId = SHOW_ID, sessionId = SESSION_ID, kindMap = "PolicyConfig",
            catMapping = mapOf("STD" to CategoryItemDocument("STD", "Vé STD", 1, null, null, null)),
        )
        // Không cần đi hết luồng sinh: tới bước đọc DB nghĩa là kiểm tra đầu vào đã cho qua.
        coEvery { ticketDocDB.loadCalendarTickets(SHOW_ID, SESSION_ID, any(), any(), null) } throws IllegalStateException("validated")

        val error = assertFailsWith<IllegalStateException> {
            generate(generateJson(type = """{"category":"STD","price":{"default":${CalendarPlanner.MAX_PRICE}},"quota":{"default":10}}"""))
        }
        assertEquals("validated", error.message)
    }

    @Test
    fun `unknown category with a lone surrogate gives a reply Ktor can encode`() = runTest {
        coEvery { categoryDB.findByShowAndSession(SHOW_ID, SESSION_ID) } returns null
        val json = generateJson(type = """{"category":"\ud83d","price":{"default":1},"quota":{"default":10}}""")
        assertEquals(loneSurrogate, mapper.readValue<GenerateCalendarRequest>(json).types.single().category)

        assertEncodableReply(assertBadRequest { generate(json) })
    }

    @Test
    fun `duplicated or invalid categories with a lone surrogate give a reply Ktor can encode`() = runTest {
        val type = """{"category":"\ud83d","price":{"default":1},"quota":{"default":10}}"""
        assertEncodableReply(assertBadRequest { generate(generateJson(types = "[$type,$type]")) })
        val negative = """{"category":"\ud83d","price":{"default":-1},"quota":{"default":10}}"""
        assertEncodableReply(assertBadRequest { generate(generateJson(type = negative)) })
    }

    @Test
    fun `very long category is cut in the reply`() = runTest {
        coEvery { categoryDB.findByShowAndSession(SHOW_ID, SESSION_ID) } returns null
        val category = "A".repeat(10_000)

        assertEncodableReply(assertBadRequest {
            generate(generateJson(type = """{"category":"$category","price":{"default":1},"quota":{"default":10}}"""))
        })
    }

    @Test
    fun `invalid date or month with a lone surrogate gives a reply Ktor can encode`() = runTest {
        val json = """{"showId":"$SHOW_ID","sessionId":"$SESSION_ID","fromDate":"\ud83d","toDate":"$to","price":1}"""
        assertEncodableReply(assertBadRequest { service.bulkUpdate(mapper.readValue<BulkUpdateCalendarRequest>(json), "op") })
        assertEncodableReply(assertBadRequest { service.adminMonth(SHOW_ID, SESSION_ID, loneSurrogate) })
    }
}
