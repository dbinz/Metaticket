package metaticket.ticket.controller

import io.ktor.http.*
import io.ktor.server.application.*
import io.mockk.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import metaticket.ticket.calendar.CalendarTestFixtures.SESSION_ID
import metaticket.ticket.calendar.CalendarTestFixtures.SHOW_ID
import metaticket.ticket.calendar.CalendarTicketService
import org.junit.jupiter.api.Test
import kotlin.test.assertFailsWith

/** Xử lý lỗi chung của controller vé lịch: hủy coroutine phải lan ra ngoài, lỗi khác mới thành 500. */
class CalendarTicketControllerTest {

    private val service = mockk<CalendarTicketService>()
    private val controller = CalendarTicketController(service)
    private val call = mockk<ApplicationCall>(relaxed = true).also {
        every { it.parameters } returns parametersOf(
            "showId" to listOf(SHOW_ID), "sessionId" to listOf(SESSION_ID), "month" to listOf("2026-11"),
        )
    }

    @Test
    fun `cancellation is rethrown instead of being answered as 500`() = runTest {
        coEvery { service.publicMonth(SHOW_ID, SESSION_ID, "2026-11", any()) } throws CancellationException("client gone")

        assertFailsWith<CancellationException> { controller.publicMonth(call) }
        coVerify(exactly = 0) { call.respond(any(), any()) }
    }

    @Test
    fun `other failures are still answered with 500`() = runTest {
        coEvery { service.publicMonth(SHOW_ID, SESSION_ID, "2026-11", any()) } throws IllegalStateException("db down")

        controller.publicMonth(call)

        verify { call.response.status(HttpStatusCode.InternalServerError) }
        coVerify { call.respond("Lỗi hệ thống", any()) }
    }
}
