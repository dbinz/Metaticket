package meta.metasearch.controllers

import jakarta.servlet.http.HttpServletResponse
import meta.metasearch.dtos.CreateOrUpdateShowRequest
import meta.metasearch.dtos.HighlightShowItem
import meta.metasearch.dtos.ShowListItem
import meta.metasearch.dtos.UpdateSoldTicketRequest
import meta.metasearch.models.CalendarSessions
import meta.metasearch.models.Show
import meta.metasearch.services.ApiKeyAuthenticationService
import meta.metasearch.services.SearchText
import meta.metasearch.services.ShowServiceWithRepository
import org.springframework.data.domain.Page
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.*
import org.springframework.web.server.ResponseStatusException
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId


val NONE_STOPPED_STATUS = listOf("OpeningWait", "Open", "Paused")
val CACHE_TAG_HEADER = "Cache-Tag"
val SHOW_API_TAG = "show-api"

/** Hard cap on sessions accepted in a single write, to bound request-body deserialization memory. */
const val MAX_SESSIONS_PER_SHOW = 5000

/** Length cap for `Show.descriptionText` (HTML-stripped `description`), keeps the search index lean. */
const val DESCRIPTION_TEXT_MAX_CHARS = 2000

@RestController
@RequestMapping("/public/metasearch/shows")
class PublicShowController(val showServiceWithRepository: ShowServiceWithRepository) {

    @Throws(Exception::class)
    @GetMapping("/top-banner")
    @ResponseBody
    fun getTopBanner(
        @RequestParam(defaultValue = "50") size: Int,
        response: HttpServletResponse
    ): Page<ShowListItem> {
        response.setHeader(CACHE_TAG_HEADER, SHOW_API_TAG)
        return showServiceWithRepository.getTopBanner(size)
    }

    @Throws(Exception::class)
    @GetMapping("/suggestion")
    @ResponseBody
    fun searchSuggestions(
        @RequestParam keyword: String,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "10") size: Int,
        @RequestParam(defaultValue = "false") nameOnly: Boolean,
        response: HttpServletResponse
    ): Page<ShowListItem> {
        response.setHeader(CACHE_TAG_HEADER, SHOW_API_TAG)
        return showServiceWithRepository.searchByKeyword(keyword, PageRequest.of(page, size), nameOnly)
    }

    @Throws(Exception::class)
    @GetMapping("/filter")
    @ResponseBody
    fun searchFilteredShows(
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "30") size: Int,
        @RequestParam eventCategoryCode: String,
        @RequestParam(required = false) cities: List<String> = emptyList(),
        @RequestParam(required = false) startTime: Long?,
        @RequestParam(required = false) endTime: Long?,
        @RequestParam(required = false) isFree: Boolean?,
        response: HttpServletResponse
    ): Page<ShowListItem> {
        response.setHeader(CACHE_TAG_HEADER, SHOW_API_TAG)
        val pageable = PageRequest.of(page, size)
        return showServiceWithRepository.searchShowWithOffset(
            pageable,
            eventCategoryCode,
            cities,
            startTime,
            endTime,
            isFree
        )
    }

    /** Homepage / search / event-detail: page 0 prefixes pinOrder 1..3; later pages skip those pins. */
    @Throws(Exception::class)
    @GetMapping("/highlight")
    @ResponseBody
    fun getHighlightShows(
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "30") size: Int,
        @RequestParam(required = false) cities: List<String> = emptyList(),
        response: HttpServletResponse
    ): Page<HighlightShowItem> {
        response.setHeader(CACHE_TAG_HEADER, SHOW_API_TAG)
        val pageable = PageRequest.of(page, size)
        return showServiceWithRepository.getHighlightShows(cities, pageable)
    }
}


@RestController
@RequestMapping("/internal/metasearch/shows")
class InternallyShowController(val showServiceWithRepository: ShowServiceWithRepository, val apiKeyAuthenticationService: ApiKeyAuthenticationService) {
    @Throws(Exception::class)
    @PostMapping("/create-or-update")
    @ResponseBody
    fun createOrUpdate(
            @RequestHeader("X-API-KEY") apiKey: String,
            @RequestBody createOrUpdateShowRequest: CreateOrUpdateShowRequest
    ): Show {
        if (!apiKeyAuthenticationService.isValidApiKey(apiKey)) {
            throw ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid API Key")
        }
        return saveShow(createOrUpdateShowRequest)
    }

    @Throws(Exception::class)
    @PostMapping("/update-sold-ticket")
    @ResponseBody
    fun updateSoldTicket(
            @RequestHeader("X-API-KEY") apiKey: String,
            @RequestBody updateShowTicket: UpdateSoldTicketRequest
    ): Show {
        if (!apiKeyAuthenticationService.isValidApiKey(apiKey)) {
            throw ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid API Key")
        }
        return showServiceWithRepository.updateSoldTicketShow(updateShowTicket.id, updateShowTicket.soldTicket)
    }

    @Throws(Exception::class)
    @PostMapping("/sync-data")
    fun syncData(): Map<String, Int> {
        val total = showServiceWithRepository.syncAll { buildShowIndex(it) }
        return mapOf("synced" to total)
    }

    /** Re-derives the denormalized/index fields of a show without persisting it. */
    private fun buildShowIndex(show: Show): Show {
        // Filter sessions according to your rules
        val nonStoppedSessions = show.sessions.filter { session ->
            session.status in NONE_STOPPED_STATUS
        }

        val startTime = nonStoppedSessions.minByOrNull { it.startTime }?.startTime ?: Instant.now().toEpochMilli()
        val instant = Instant.ofEpochMilli(startTime)
        val localDateTime = LocalDateTime.ofInstant(instant, ZoneId.systemDefault())
        val month = localDateTime.monthValue
        val year = localDateTime.year
        val day  = localDateTime.dayOfMonth

        return Show(
            id = show.id,
            showName = show.showName,
            code = show.code,
            status = show.status,
            showInHome = show.sessions.any { it.showInHome },
            hasAllSessionsStopped = show.sessions.all { it.status !in NONE_STOPPED_STATUS },
            eventCategory = show.eventCategory,
            eventCategoryCode = show.eventCategoryCode,
            detailImageUrl = show.detailImageUrl,
            thumbnailUrl = show.thumbnailUrl,
            bannerImageUrl = show.bannerImageUrl,
            logoShowUrl = show.logoShowUrl,
            logoHostUrl = show.logoHostUrl,
            createdAt = show.createdAt,
            description = show.description,
            descriptionText = SearchText.plain(show.description, DESCRIPTION_TEXT_MAX_CHARS),
            ranking = show.ranking,
            pinned = show.pinned,
            pinOrder = show.pinOrder,
            soldTicket = show.soldTicket,
            minPrice = show.minPrice,
            slug = show.slug,
            sessions = show.sessions,
            startYear = year,
            startMonth = month,
            startDay = day,
            startTime = startTime,
            ongoingUntil = CalendarSessions.ongoingUntil(show.sessions, NONE_STOPPED_STATUS),
        )
    }

    private fun saveShow(request: CreateOrUpdateShowRequest): Show {
        if (request.sessions.size > MAX_SESSIONS_PER_SHOW) {
            throw ResponseStatusException(
                HttpStatus.PAYLOAD_TOO_LARGE,
                "Too many sessions: ${request.sessions.size} (max $MAX_SESSIONS_PER_SHOW)"
            )
        }
        // Filter sessions according to your rules
        val nonStoppedSessions = request.sessions.filter { session ->
            session.status in NONE_STOPPED_STATUS
        }

        val startTime = nonStoppedSessions.minByOrNull { it.startTime }?.startTime ?: Instant.now().toEpochMilli()
        val instant = Instant.ofEpochMilli(startTime)
        val localDateTime = LocalDateTime.ofInstant(instant, ZoneId.systemDefault())
        val month = localDateTime.monthValue
        val year = localDateTime.year
        val day  = localDateTime.dayOfMonth

        val showUpdate = Show(
            id = request.id,
            showName = request.showName,
            code = request.code,
            status = request.status,
            showInHome = request.sessions.any { it.showInHome },
            hasAllSessionsStopped = request.sessions.all { it.status !in NONE_STOPPED_STATUS },
            eventCategory = request.eventCategory,
            eventCategoryCode = request.eventCategoryCode,
            detailImageUrl = request.detailImageUrl,
            thumbnailUrl = request.thumbnailUrl,
            bannerImageUrl = request.bannerImageUrl,
            logoShowUrl = request.logoShowUrl,
            logoHostUrl = request.logoHostUrl,
            createdAt = request.createdAt,
            description = request.description,
            descriptionText = SearchText.plain(request.description, DESCRIPTION_TEXT_MAX_CHARS),
            ranking = request.ranking,
            pinned = request.pinned,
            pinOrder = request.pinOrder,
            soldTicket = request.soldTicket,
            minPrice = request.minPrice,
            slug = request.slug,
            sessions = request.sessions,
            startYear = year,
            startMonth = month,
            startDay = day,
            startTime = startTime,
            ongoingUntil = CalendarSessions.ongoingUntil(request.sessions, NONE_STOPPED_STATUS),
        )
        return showServiceWithRepository.createOrUpdateShowIndex(showUpdate)
    }
}

