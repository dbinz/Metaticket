package metaticket.show.gateway

import com.google.protobuf.Empty
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import metaticket.common.logger.Logging
import metaticket.show.datastructure.CalendarRules
import metaticket.ticket.grpc.TicketMessage
import metaticket.ticket.grpc.TicketServiceGrpcKt
import java.util.concurrent.TimeUnit

class TicketGateway(
    private val stub: TicketServiceGrpcKt.TicketServiceCoroutineStub
): Logging {

    private companion object {
        // Bound the price lookup: it runs inside the metasearch push, so a stalled service.ticket must
        // not hang every show edit (partner dashboard edits go through the same push).
        const val PRICE_LOOKUP_DEADLINE_SECONDS = 3L
    }
    suspend fun startTicket(showId: String, sessionId: String): Empty? {
        val requestBuilder = TicketMessage.StartTicketRequest.newBuilder()
            .setShowId(showId)
            .setSessionId(sessionId)
            .build()

        return try {
            withContext(Dispatchers.IO) {
                stub.startTicket(requestBuilder)
            }
        } catch (t: Throwable) {
            logger.error("show {} session {} have exception starting ticket {} ... ", showId, sessionId, requestBuilder, t)
            null
        }
    }

    suspend fun stopTicket(showId: String, sessionId: String): Empty? {
        val requestBuilder = TicketMessage.StopTicketRequest.newBuilder()
            .setShowId(showId)
            .setSessionId(sessionId)
            .build()

        return try {
            withContext(Dispatchers.IO) {
                stub.stopTicket(requestBuilder)
            }
        } catch (t: Throwable) {
            logger.error("show {} session {} have exception stopping ticket {} ... ", showId, sessionId, requestBuilder, t)
            null
        }
    }

    /**
     * Lowest ticket base price across [sessionIds] of a show — the figure behind the storefront
     * "Từ …đ" label.
     *
     * Sessions are queried concurrently: a show can carry many of them and this runs inside the
     * metasearch push, so doing them one after another would stretch a background job that already
     * has its own retry budget. A session whose lookup fails contributes no price instead of failing
     * the whole push — a card without a price is better than a show missing from search.
     *
     * Returns null when no session has a priced ticket (free shows, or tickets not created yet) so
     * the projection can omit the field rather than publish a misleading 0.
     *
     * Calendar sessions ([calendarSessionIds]) hold one ticket per visit date, so only dates from today on
     * count — and the minimum is computed in service.ticket instead of pulling every date's ticket over gRPC.
     */
    suspend fun loadMinBasePrice(showId: String, sessionIds: List<String>, calendarSessionIds: Set<String> = emptySet()): Long? {
        if (sessionIds.isEmpty()) return null

        return coroutineScope {
            sessionIds
                .map { sessionId ->
                    async {
                        if (sessionId in calendarSessionIds) listOfNotNull(loadCalendarMinPrice(showId, sessionId))
                        else loadBasePrices(showId, sessionId)
                    }
                }
                .awaitAll()
                .flatten()
                .filter { it > 0 }
                .minOrNull()
        }
    }

    private suspend fun loadBasePrices(showId: String, sessionId: String): List<Long> {
        val request = TicketMessage.GetTicketByShowAndSessionRequest.newBuilder()
            .setShowId(showId)
            .setSessionId(sessionId)
            .build()

        return try {
            withContext(Dispatchers.IO) {
                stub.withDeadlineAfter(PRICE_LOOKUP_DEADLINE_SECONDS, TimeUnit.SECONDS)
                    .getTicketByShowAndSession(request)
            }.ticketsList.map { it.basePrice }
        } catch (t: Throwable) {
            logger.error("show {} session {} have exception loading ticket prices {} ... ", showId, sessionId, request, t)
            emptyList()
        }
    }

    private suspend fun loadCalendarMinPrice(showId: String, sessionId: String): Long? {
        val request = TicketMessage.GetCalendarMinPriceRequest.newBuilder()
            .setShowId(showId)
            .setSessionId(sessionId)
            .setFromDate(CalendarRules.today().toString())
            .build()

        return try {
            val response = withContext(Dispatchers.IO) {
                stub.withDeadlineAfter(PRICE_LOOKUP_DEADLINE_SECONDS, TimeUnit.SECONDS)
                    .getCalendarMinPrice(request)
            }
            if (response.hasMinPrice()) response.minPrice else null
        } catch (t: Throwable) {
            logger.error("show {} session {} have exception loading calendar min price ... ", showId, sessionId, t)
            null
        }
    }
}
