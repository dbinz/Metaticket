package metaticket.show.metasearch

import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.application.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import metaticket.common.logger.Logging
import metaticket.show.datastructure.SessionType
import metaticket.show.gateway.TicketGateway
import metaticket.show.pojo.db.ShowDocument


/**
 *
 * @author dongcatchay
 */
class MetaSearchClient(
    private val env : ApplicationEnvironment,
    private val client: HttpClient,
    private val ticketGateway: TicketGateway,
): Logging {
    private val baseUrl = env.config.property("metasearch.baseUrl").getString()
    private val createUpdateShowUrl = baseUrl + env.config.property("metasearch.createUpdateShowUrl").getString()
    private val updateSoldTicketUrl = baseUrl + env.config.property("metasearch.updateSoldTicketUrl").getString()
    private val xApiKey = env.config.property("metasearch.xApiKey").getString()

    /**
     * Ticket prices live in service.ticket, not on [ShowDocument], so the "Từ …đ" figure is pulled
     * over gRPC here — at projection time, where every push already passes — rather than duplicating
     * the lookup at each of this method's call sites.
     */
    suspend fun buildCreateUpdateShowRequest(show: ShowDocument): MetaSearchCreateUpdateShowRequest {
        val session = show.sessions.map {
            MetaSearchShowSession(
                id = it.id,
                code = it.code,
                status = it.status,
                sessionName = it.sessionName,
                cityCode = it.city?.code,
                cityName = it.city?.name,
                isFree = it.isFree ?: false,
                showInHome = it.showInHome ?: false,
                venue = it.venue,
                startTime = it.startTime.time,
                endTime = it.endTime.time,
                paymentMethods = it.paymentMethods ?: emptyList(),
                createdAt = it.createdAt.time,
                slug = it.slug ?: "",
                sessionType = it.sessionType ?: SessionType.SINGLE,
            )
        }
        val request = MetaSearchCreateUpdateShowRequest(
            id = show.id,
            showName = show.showName,
            code = show.code,
            status = show.status,
            eventCategoryCode = show.eventCategoryCode,
            eventCategory = show.eventCategory,
            bannerImageUrl = show.bannerImageUrl,
            detailImageUrl = show.detailImageUrl,
            thumbnailUrl = show.thumbnailUrl,
            logoHostUrl = show.logoHostUrl,
            logoShowUrl = show.logoShowUrl,
            description = show.description,
            createdAt = show.createdAt.time,
            pinned = show.pinned,
            pinOrder = show.pinOrder,
            slug = show.slug ?: "",
            minPrice = ticketGateway.loadMinBasePrice(
                show.id,
                show.sessions.map { it.id },
                calendarSessionIds = show.sessions.filter { it.sessionType == SessionType.CALENDAR }.map { it.id }.toSet(),
            ),
            sessions = session
        )
        return request
    }

    suspend fun executeSendCreateUpdateShow(request: MetaSearchCreateUpdateShowRequest): Boolean {
        try {
            val requestBuilder: HttpRequestBuilder.() -> Unit = {
                headers.append("X-API-KEY", xApiKey)
                contentType(ContentType.Application.Json)
                setBody(request)
            }
            logger.info("show {} is sending create-update info to metasearch", request.id)
            val response = withContext(Dispatchers.IO) {
                client.post(createUpdateShowUrl, requestBuilder)
            }
            if (response.status == HttpStatusCode.OK) {
                logger.info("show {} sent create-update info to metasearch success", request.id)
                return true
            } else {
                logger.info("show {} send create-update info to metasearch failed {} {}", request.id, response.status, response.bodyAsText())
                return false
            }
        } catch (t: Throwable) {
            logger.error("show {} have exception sending create-update to metasearch {} ... ", request.id, request, t)
            return false
        }
    }

    suspend fun executeSendUpdateSoldTicket(request: MetaSearchUpdateSoldRequest): Boolean {
        try {
            val requestBuilder: HttpRequestBuilder.() -> Unit = {
                headers.append("X-API-KEY", xApiKey)
                contentType(ContentType.Application.Json)
                setBody(request)
            }
            logger.info("show {} is sending update-sold-ticket {} to metasearch", request.id, request.soldTicket)
            val response = withContext(Dispatchers.IO) {
                client.post(updateSoldTicketUrl, requestBuilder)
            }
            if (response.status == HttpStatusCode.OK) {
                logger.info("show {} sent update-sold-ticket {} to metasearch success", request.id, request.soldTicket)
                return true
            } else {
                logger.info("show {} send update-sold-ticket {} to metasearch failed {} {}", request.id, request.soldTicket, response.status, response.bodyAsText())
                return false
            }
        } catch (t: Throwable) {
            logger.error("show {} have exception sending update-sold-ticket {} to metasearch {} ... ", request.id, request.soldTicket, request, t)
            return false
        }
    }
}
