package metaticket.ticket.service

import io.ktor.server.application.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import metaticket.common.coroutine.launch
import metaticket.common.logger.Logging
import metaticket.order.datastructure.OrderStatus
import metaticket.ticket.ReservationExecutionRetryable
import metaticket.ticket.cache.ConfigPerReservationCache
import metaticket.ticket.cache.ShowSessionCache
import metaticket.ticket.calendar.CalendarReservationRules
import metaticket.ticket.calendar.isCalendar
import metaticket.ticket.database.service.TicketCalendarDayDBService
import metaticket.ticket.database.service.TicketCapacityDBService
import metaticket.ticket.database.service.TicketDocDBService
import metaticket.ticket.database.service.TicketReservationDBService
import metaticket.ticket.datastructure.SeatType
import metaticket.ticket.datastructure.TicketReservationStatus
import metaticket.ticket.gateway.JobrunrGateway
import metaticket.ticket.pojo.db.TicketDocument
import metaticket.ticket.pojo.db.TicketReservationDetailsDocument
import metaticket.ticket.pojo.db.TicketReservationDocument
import metaticket.ticket.pojo.db.toCounterUnits
import metaticket.ticket.pojo.request.TicketReservationDetailDTO
import metaticket.ticket.pojo.response.TicketReservationResponse
import metaticket.ticket.pojo.response.toResponse
import metaticket.ticket.processor.ReservationRepository
import metaticket.ticket.redis.RedisAsyncClient
import metaticket.ticket.redis.UpdateRedisFailedConstants
import org.bson.types.ObjectId
import java.time.Instant
import java.util.*

// --- Input Commands ---

data class CreateReservationCommand(
    val showId: String,
    val sessionId: String,
    val customerUserId: String,
    val details: List<TicketReservationDetailDTO>
)

data class ReleaseReservationCommand(
    val reservationId: String,
    val customerUserId: String,
)

data class ReserveReservationCommand(
    val reservationId: String,
    val customerUserId: String,
)

data class NotifyOrderStatusChangedCommand(
    val reservationId: String,
    val orderStatus: String // Keep as String initially, map inside core service
)

data class LockTicketsCommand(
    val showId: String,
    val sessionId: String,
    val operatorId: String,
    val details: List<TicketReservationDetailDTO>,
)

data class UnlockReservationCommand(
    val reservationId: String,
    val operatorId: String,
)

// --- Custom Exception for Core Service ---
// Allows Controller/gRPC layer to map to specific protocol errors

sealed class ReservationException(message: String, cause: Throwable? = null) : RuntimeException(message, cause) {
    class NotFound(message: String) : ReservationException(message)
    class PreconditionFailed(message: String) : ReservationException(message)
    class PermissionDenied(message: String) : ReservationException(message)
    class Conflict(message: String) : ReservationException(message) // e.g., concurrent update failed
    class InternalError(message: String, cause: Throwable? = null) : ReservationException(message, cause)
}

// Inject all necessary dependencies
class ReservationCoreService(
    private val env: ApplicationEnvironment,
    private val ticketResDB: TicketReservationDBService,
    private val redisClient: RedisAsyncClient,
    private val resExeRetryable: ReservationExecutionRetryable,
    private val showSessionCache: ShowSessionCache,
    private val ticketDocDB: TicketDocDBService,
    private val configPerResCache: ConfigPerReservationCache,
    private val jobrunrGateway: JobrunrGateway,
    private val reservationReps: ReservationRepository,
    private val ticketCapacityDB: TicketCapacityDBService,
    private val calendarDayDB: TicketCalendarDayDBService,
) : Logging {

    private val defaultExpiredInS = env.config.property("jrunr.expireReservation.delayInSecond").getString().toLong()

    suspend fun getReservation(reservationId: String): TicketReservationResponse? {
        val reservation = findReservationById(reservationId) ?: return null
        return reservation.toResponse()
    }

    suspend fun createReservation(command: CreateReservationCommand): String {
        // 1. Validate Show/Session Open
        validateShowSessionOpen(command.showId, command.sessionId, command.customerUserId, "createReservation")

        // 2. Basic Quantity Validation
        validateTicketQuantities(command.details, command.customerUserId)

        // 3. Enrich Details (Modifies the input command details), then calendar-ticket rules
        val tickets = enrichReservationDetails(command.details, command.customerUserId)
        applyCalendarRules(command, tickets)

        // 4. Validate Limits (using enriched details)
        validateCreatingReservationLimits(command.showId, command.sessionId, command.details, command.customerUserId)

        // 5. Generate ID
        val resId = ObjectId().toHexString()

        // 6. Determine & Schedule Expiration
        val expireInS = determineExpiration(command.showId, command.sessionId)
        if (expireInS > -1L) {
            scheduleExpirationOrFail(resId, expireInS, command.customerUserId)
        }

        // 7. Build Document
        val reservation = buildNewReservationDocument(resId, command, expireInS)

        // 8. Store in Redis
        val added = redisClient.setReservation(reservation)
        if (!added) {
            logger.error("cusUser [{}] createReservation: Failed to set reservation {} in Redis", command.customerUserId, resId)
            throw ReservationException.InternalError("Lỗi hệ thống khi tạo reservation (Redis set failed)")
        }

        logger.info("cusUser [{}] createReservation: Reservation {} created successfully", command.customerUserId, resId)
        return resId
    }

    suspend fun releaseReservation(command: ReleaseReservationCommand) {
        val reservation = findReservationById(command.reservationId) ?: run {
            logger.info("cusUser [{}] releaseReservation: Reservation {} not found, considering released.", command.customerUserId, command.reservationId)
            return // If not found, it's effectively released
        }

        // Validate user permission
        validateUserPermission(reservation, command.customerUserId, command.customerUserId, "releaseReservation")

        // Validate status allows release
        val allowedStatuses = listOf(
            TicketReservationStatus.Reserved,
            TicketReservationStatus.Reserving,
            TicketReservationStatus.Created
        )
        validateReservationStatus(reservation, allowedStatuses, command.customerUserId, "releaseReservation")

        logger.info("cusUser [{}] releaseReservation: Releasing reservation {}", command.customerUserId, command.reservationId)

        // Delegate the actual release logic
        try {
            resExeRetryable.releaseReservation(command.reservationId)
        } catch (e: Throwable) {
            logger.error("cusUser [{}] releaseReservation: Error during retryable release for {}", command.customerUserId, command.reservationId, e)
            // Decide if this should throw. If retryable handles it, maybe just log.
            // For now, let's rethrow as internal error.
            throw ReservationException.InternalError("Lỗi hệ thống khi hủy reservation (release failed)", e)
        }

        logger.info("cusUser [{}] releaseReservation: Reservation {} release process initiated", command.customerUserId, command.reservationId)
    }

    suspend fun reserveReservation(command: ReserveReservationCommand): String {
        val resId = command.reservationId

        // Fetch reservation - MUST be in Redis for this operation
        val reservation = getReservationFromRedisOrThrow(resId, command.customerUserId, "reserveReservation")

        // Validate user permission
        validateUserPermission(reservation, command.customerUserId, command.customerUserId, "reserveReservation")

        // Validate show/session is open
        validateShowSessionOpen(reservation.showId, reservation.sessionId, command.customerUserId, "reserveReservation")

        // Attempt to update status in Redis (atomically)
        val updatedCode = redisClient.updateReservationStatus(resId,
            TicketReservationStatus.Created,
            TicketReservationStatus.Reserving
        )

        if (updatedCode == UpdateRedisFailedConstants.NOT_EXIST) {
            throw ReservationException.PreconditionFailed("Reservation $resId không đúng trạng thái")
        }
        if (updatedCode == UpdateRedisFailedConstants.FAILED) {
            val currentState = redisClient.getReservation(resId)?.status ?: "UNKNOWN (removed?)"
            logger.error("cusUser [{}] reserveReservation: Failed to update reservation {} from {} to {}. Current state: {}", command.customerUserId, resId,
                TicketReservationStatus.Created,
                TicketReservationStatus.Reserving, currentState)
            throw ReservationException.PreconditionFailed("Không thể đặt chỗ reservation (trạng thái không hợp lệ: $currentState)")
        }

        // Schedule the background processing task
        launch { reservationReps.put(resId) } // Fire-and-forget

        logger.info("cusUser [{}] reserveReservation: Reservation {} status updated to {} and scheduled for processing", command.customerUserId, resId,
            TicketReservationStatus.Reserving
        )
        return resId
    }

    suspend fun notifyOrderStatusChanged(command: NotifyOrderStatusChangedCommand) {
        val resId = command.reservationId
        val orderStatusStr = command.orderStatus
        val newResStatus = mapOrderStatusToReservationStatus(orderStatusStr) // Can throw PreconditionFailed

        // Use combined Redis -> DB fetch
        val reservation = findReservationById(resId)
            ?: throw ReservationException.NotFound("Reservation $resId not found for status update")

        val oldResStatus = reservation.status

        // Specific validation for state transitions
        validateStatusTransition(oldResStatus, newResStatus, resId, orderStatusStr) // Can throw PreconditionFailed

        // Perform the update (Redis, DB, potential capacity release)
        updateReservationStatusAndRelated(resId, oldResStatus, newResStatus, reservation) // Can throw InternalError, Conflict
    }

    /**
     * Lock tickets so they cannot be sold (no expiry). Capacity is taken first and the Locked document is
     * written last, so a failed lock never leaves a document behind that could later "release" capacity it
     * never took.
     */
    suspend fun lockTickets(command: LockTicketsCommand): TicketReservationDocument {
        validateTicketQuantities(command.details, command.operatorId)
        val details = buildLockDetails(command)
        val resId = ObjectId().toHexString()
        val taken = mutableListOf<TakenCapacity>()

        try {
            // Vé theo lịch có sức chứa chung cả ngày: khóa trừ cả bộ đếm của ngày.
            details.toCounterUnits().forEach { taken += takeCapacity(resId, it, command.operatorId) }
            commitReservedCapInDb(taken)

            val reservation = TicketReservationDocument(
                id = resId,
                showId = command.showId,
                sessionId = command.sessionId,
                details = details,
                status = TicketReservationStatus.Locked,
                // Admin screens read the operator from this field; dedicated lock-owner fields come later.
                customerUserId = command.operatorId,
                expireInS = -1,
            )
            insertLockDocument(reservation)
            logger.info("optUser [{}] lockTickets: reservation {} locked {}", command.operatorId, resId, details)
            return reservation
        } catch (e: Throwable) {
            // Not enough tickets is a normal outcome (e.g. a customer got the seat first): no stack trace, no ERROR alert.
            if (e is ReservationException && e !is ReservationException.InternalError) {
                logger.warn("optUser [{}] lockTickets: reservation {} rejected ({}), rolling back {}", command.operatorId, resId, e.message, taken)
            } else {
                logger.error("optUser [{}] lockTickets: reservation {} failed, rolling back {}", command.operatorId, resId, taken, e)
            }
            withContext(NonCancellable) { rollbackTakenCapacity(resId, taken) }
            throw when (e) {
                is ReservationException, is CancellationException -> e
                else -> ReservationException.InternalError("Lỗi hệ thống khi khóa vé", e)
            }
        }
    }

    /**
     * Unlock a Locked reservation and give its capacity back. The released details come from the document
     * returned by the compare-and-set, not from an earlier read, so concurrent unlocks cannot release twice.
     */
    suspend fun unlockReservation(command: UnlockReservationCommand) {
        if (!ObjectId.isValid(command.reservationId)) {
            throw ReservationException.NotFound("Không tìm thấy reservation")
        }
        val locked = ticketResDB.updateStatus(
            command.reservationId, TicketReservationStatus.Locked, TicketReservationStatus.Unlocked, before = true
        ) ?: run {
            val current = ticketResDB.loadReservationById(command.reservationId)
                ?: throw ReservationException.NotFound("Không tìm thấy reservation")
            logger.warn("optUser [{}] unlockReservation: reservation {} is {}, not Locked", command.operatorId, current.id, current.status)
            throw ReservationException.PreconditionFailed("Không thể mở khóa vé đang ở trạng thái ${current.status}")
        }

        resExeRetryable.releaseRemainingCapOnRedis(locked.id, locked.details)
        resExeRetryable.releaseReservedCapInDb(locked.id, locked.details)
        logger.info("optUser [{}] unlockReservation: reservation {} unlocked {}", command.operatorId, locked.id, locked.details)
    }

    private data class TakenCapacity(
        val detail: TicketReservationDetailsDocument,
        var onRedis: Boolean,
        var inDb: Boolean,
    )

    /**
     * Merge duplicated ticket ids (order creation rejects duplicates) and check every ticket belongs to the
     * requested show/session: the lock is stored under the body's show/session, so a foreign ticket would
     * otherwise be locked under the wrong session.
     */
    private suspend fun buildLockDetails(command: LockTicketsCommand): List<TicketReservationDetailsDocument> {
        val quantities = command.details.groupBy { it.ticketId }.mapValues { (_, d) -> d.sumOf { it.quantity } }
        if (quantities.keys.any { !ObjectId.isValid(it) }) {
            throw ReservationException.NotFound("Không tìm thấy vé")
        }
        val tickets = ticketDocDB.loadTicketDocByIds(quantities.keys.toList()).associateBy { it.id }
        val dayCapacityIds = loadDayCapacityIds(command.sessionId, tickets.values)

        return quantities.map { (ticketId, quantity) ->
            val ticket = tickets[ticketId] ?: throw ReservationException.NotFound("Không tìm thấy vé")
            if (ticket.showId != command.showId || ticket.sessionId != command.sessionId) {
                logger.warn("optUser [{}] lockTickets: ticket {} belongs to {}/{}, not {}/{}", command.operatorId,
                    ticketId, ticket.showId, ticket.sessionId, command.showId, command.sessionId)
                throw ReservationException.PreconditionFailed("Vé ${ticket.sid} không thuộc suất diễn đã chọn")
            }
            if (ticket.seatType == SeatType.Seat && quantity > 1) {
                throw ReservationException.PreconditionFailed("Ghế ${ticket.sid} chỉ khóa được số lượng 1")
            }
            TicketReservationDetailsDocument(
                ticketId = ticket.id,
                capacityId = ticket.capacityId,
                quantity = quantity,
                priceCat = ticket.priceCat,
                sid = ticket.sid,
                dayCapacityId = ticket.calendarDate?.let { dayCapacityIds[it] },
            )
        }
    }

    /** Sức chứa chung theo ngày của các vé theo lịch trong [tickets], key là calendarDate. */
    private suspend fun loadDayCapacityIds(sessionId: String, tickets: Collection<TicketDocument>): Map<String, String> =
        tickets.mapNotNull { it.calendarDate }.distinct()
            .mapNotNull { date -> calendarDayDB.loadDay(sessionId, date)?.capacityId?.let { date to it } }
            .toMap()

    /**
     * Take capacity for one detail. Redis is the gate while the session is open; when its counter does not
     * exist (session not opened yet, or already stopped) the conditional Mongo update is the gate instead.
     */
    private suspend fun takeCapacity(resId: String, detail: TicketReservationDetailsDocument, operatorId: String): TakenCapacity {
        val key = detail.capacityId ?: detail.ticketId
        val rem = redisClient.addTicketRemCap(key, -detail.quantity)
        if (rem >= 0) return TakenCapacity(detail, onRedis = true, inDb = false)
        if (rem != UpdateRedisFailedConstants.NOT_EXIST) {
            throw ReservationException.Conflict("Vé ${detail.sid} không còn đủ số lượng để khóa")
        }

        val capacityId = detail.capacityId
        val takenInDb = if (capacityId != null) {
            ticketCapacityDB.tryAddReservedCap(capacityId, detail.quantity)
        } else {
            ticketDocDB.tryAddReservedCap(detail.ticketId, detail.quantity)
        }
        if (!takenInDb) {
            throw ReservationException.Conflict("Vé ${detail.sid} không còn đủ số lượng để khóa")
        }
        val taken = TakenCapacity(detail, onRedis = false, inDb = true)

        // startTicket may have loaded the counter from Mongo while we updated it, and we cannot tell whether it
        // read before or after our update. Take it on Redis too: worst case these tickets stay unsellable until
        // the session restarts (safe), instead of being sold twice.
        if (redisClient.getTicketRemCap(key) != null) {
            logger.warn("optUser [{}] lockTickets: reservation {} counter {} appeared during a pre-open lock, also taking it on redis",
                operatorId, resId, key)
            taken.onRedis = redisClient.addTicketRemCap(key, -detail.quantity) >= 0
        }
        return taken
    }

    /** Mirror capacity taken on Redis into Mongo, with the same unconditional update the customer flow uses. */
    private suspend fun commitReservedCapInDb(taken: List<TakenCapacity>) {
        val pending = taken.filter { it.onRedis && !it.inDb }
        val (capacities, tickets) = pending.partition { it.detail.capacityId != null }

        if (tickets.isNotEmpty()) {
            if (!ticketDocDB.addReservedCap(tickets.map { it.detail.ticketId to it.detail.quantity })) {
                throw ReservationException.InternalError("Không cập nhật được số lượng vé")
            }
            tickets.forEach { it.inDb = true }
        }
        if (capacities.isNotEmpty()) {
            val pairs = capacities.mapNotNull { t -> t.detail.capacityId?.let { it to t.detail.quantity } }
            if (!ticketCapacityDB.addReservedCap(pairs)) {
                throw ReservationException.InternalError("Không cập nhật được số lượng vé")
            }
            capacities.forEach { it.inDb = true }
        }
    }

    /**
     * If the insert errors out, re-check before failing: the write may have landed with its acknowledgement
     * lost, and rolling back capacity under an existing lock would oversell.
     */
    private suspend fun insertLockDocument(reservation: TicketReservationDocument) {
        val inserted = try {
            ticketResDB.insertReservation(reservation) != null
        } catch (e: Throwable) {
            if (e is CancellationException) throw e
            logger.error("lockTickets: insert reservation {} failed, checking whether it was written", reservation.id, e)
            ticketResDB.loadReservationById(reservation.id) != null
        }
        if (!inserted) throw ReservationException.InternalError("Không lưu được thông tin khóa vé")
    }

    private suspend fun rollbackTakenCapacity(resId: String, taken: List<TakenCapacity>) {
        val onRedis = taken.filter { it.onRedis }.map { it.detail }
        val inDb = taken.filter { it.inDb }.map { it.detail }
        if (onRedis.isNotEmpty()) resExeRetryable.releaseRemainingCapOnRedis(resId, onRedis)
        if (inDb.isNotEmpty()) resExeRetryable.releaseReservedCapInDb(resId, inDb)
    }


    // --- Private Helper Methods (Copied and adapted from ReservationServiceImpl) ---
    // Make sure these helpers throw ReservationException instead of StatusException

    private suspend fun findReservationById(resId: String): TicketReservationDocument? {
        return redisClient.getReservation(resId) ?: ticketResDB.loadReservationById(resId)
    }

    private suspend fun getReservationFromRedisOrThrow(resId: String, opUserId: String?, operation: String): TicketReservationDocument {
        return redisClient.getReservation(resId) ?: run {
            logger.error("optUser [{}] {}: Reservation {} not found in Redis", opUserId, operation, resId)
            throw ReservationException.NotFound("Reservation $resId không tồn tại hoặc đã hết hạn")
        }
    }

    private suspend fun validateShowSessionOpen(showId: String, sessionId: String, opUserId: String?, operation: String) {
        val isOpen = showSessionCache.checkShowSessionInOpen(showId, sessionId) ?: run {
            logger.error("optUser [{}] {}: Show {} Session {} not found in cache", opUserId, operation, showId, sessionId)
            throw ReservationException.NotFound("Không tải được thông tin suất diễn ($showId / $sessionId)")
        }
        if (!isOpen) {
            logger.warn("optUser [{}] {}: Show {} Session {} is not open for reservations", opUserId, operation, showId, sessionId)
            throw ReservationException.PreconditionFailed("Suất diễn ($showId / $sessionId) hiện không mở bán")
        }
    }

    private fun validateUserPermission(reservation: TicketReservationDocument, requestingUserId: String, opUserId: String?, operation: String) {
        if (reservation.customerUserId != requestingUserId) {
            logger.warn("optUser [{}] {}: Permission denied for reservation {}. Requesting user {} does not match owner {}",
                opUserId, operation, reservation.id, requestingUserId, reservation.customerUserId)
            throw ReservationException.PermissionDenied("Không có quyền thực hiện thao tác trên reservation này")
        }
    }

    private fun validateReservationStatus(reservation: TicketReservationDocument, allowedStatuses: List<TicketReservationStatus>, opUserId: String, operation: String) {
        if (reservation.status !in allowedStatuses) {
            logger.error("optUser [{}] {}: Invalid status ({}) for reservation {}. Allowed: {}",
                opUserId, operation, reservation.status, reservation.id, allowedStatuses)
            throw ReservationException.PreconditionFailed("Thao tác không hợp lệ với trạng thái hiện tại (${reservation.status}) của reservation")
        }
    }

    private fun validateTicketQuantities(details: List<TicketReservationDetailDTO>, opUserId: String?) {
        if (details.isEmpty()) {
            throw ReservationException.PreconditionFailed("Yêu cầu phải có ít nhất một loại vé")
        }
        if (details.any { it.quantity <= 0 }) {
            throw ReservationException.PreconditionFailed("Số lượng vé yêu cầu phải lớn hơn 0")
        }
    }

    /**
     * Fill capacity/sid/priceCat from the stored ticket (modifies the input list) and return the tickets. Values sent
     * by the client are overwritten, never trusted: a foreign capacityId would make the processor decrement another
     * counter instead of this ticket's.
     */
    private suspend fun enrichReservationDetails(details: List<TicketReservationDetailDTO>, opUserId: String?): List<TicketDocument> {
        val ids = details.map { it.ticketId }.filter { ObjectId.isValid(it) }.distinct()
        val tickets = ticketDocDB.loadTicketDocByIds(ids).associateBy { it.id }
        return details.map { detail ->
            val ticket = tickets[detail.ticketId] ?: run {
                logger.error("optUser [{}] enrichReservationDetails: Ticket document {} not found {} {}", opUserId, detail.ticketId, detail.priceCat, detail.sid)
                throw ReservationException.NotFound("Không tìm thấy thông tin vé ID: ${detail.priceCat} ${detail.sid}")
            }
            detail.capacityId = ticket.capacityId
            detail.sid = ticket.sid
            detail.priceCat = ticket.priceCat
            detail.dayCapacityId = null
            ticket
        }
    }

    /**
     * Vé theo lịch: mọi vé cùng một ngày của đúng session, ngày còn bán (mở, chưa qua, trong cửa sổ bán, chưa quá giờ
     * cutoff) và gắn sức chứa chung của ngày để processor trừ cả hai bộ đếm. Vé lịch không được giữ chỗ qua session
     * thường.
     */
    private suspend fun applyCalendarRules(command: CreateReservationCommand, tickets: List<TicketDocument>) {
        val session = showSessionCache.get(command.showId, command.sessionId)
            // Không chèn id thô của client vào thông báo trả về (surrogate lẻ làm Ktor encode thất bại → 500).
            ?: throw ReservationException.NotFound("Không tải được thông tin suất diễn")
        if (!session.isCalendar()) {
            if (tickets.any { it.calendarDate != null || it.showId != command.showId || it.sessionId != command.sessionId }) {
                throw ReservationException.PreconditionFailed("Vé không thuộc suất đã chọn")
            }
            return
        }

        val date = when (val result = CalendarReservationRules.resolveDate(command.showId, command.sessionId, tickets)) {
            is CalendarReservationRules.DateResult.Ok -> result.date
            is CalendarReservationRules.DateResult.Rejected -> throw ReservationException.PreconditionFailed(result.reason)
        }
        val day = calendarDayDB.loadDay(command.sessionId, date)
        CalendarReservationRules.unsellableReason(day, session, Instant.now())?.let {
            logger.warn("cusUser [{}] createReservation: calendar date {} rejected: {}", command.customerUserId, date, it)
            throw ReservationException.PreconditionFailed(it)
        }
        command.details.forEach { it.dayCapacityId = day?.capacityId }
    }

    private suspend fun validateCreatingReservationLimits(showId: String, sessionId: String, details: List<TicketReservationDetailDTO>, opUserId: String?) {
        val config = configPerResCache.get(showId, sessionId) ?: return // No config, no limits

        val totalQuantity = details.sumOf { it.quantity }

        // Max Limits
        config.maxTicket?.total?.let { max ->
            if (totalQuantity > max) throw ReservationException.PreconditionFailed("Tổng số lượng vé không được vượt quá $max")
        }
        config.maxTicket?.perCat?.forEach { (cat, max) ->
            val count = details.filter { it.priceCat == cat }.sumOf { it.quantity }
            if (count > max) throw ReservationException.PreconditionFailed("Hạng vé '$cat': số lượng không được vượt quá $max")
        }
        config.maxTicket?.perZone?.forEach { (zone, max) ->
            val count = details.filter { it.sid == zone }.sumOf { it.quantity }
            if (count > max) throw ReservationException.PreconditionFailed("Khu vực '$zone': số lượng không được vượt quá $max")
        }

        // Min Limits
        config.minTicket?.total?.let { min ->
            if (totalQuantity < min) throw ReservationException.PreconditionFailed("Tổng số lượng vé phải ít nhất là $min")
        }
        config.minTicket?.perCat?.forEach { (cat, min) ->
            val count = details.filter { it.priceCat == cat }.sumOf { it.quantity }
            if (count > 0 && count < min) throw ReservationException.PreconditionFailed("Hạng vé '$cat': số lượng phải ít nhất là $min (nếu chọn)")
        }
        config.minTicket?.perZone?.forEach { (zone, min) ->
            val count = details.filter { it.sid == zone }.sumOf { it.quantity }
            if (count > 0 && count < min) throw ReservationException.PreconditionFailed("Khu vực '$zone': số lượng phải ít nhất là $min (nếu chọn)")
        }
    }

    private suspend fun determineExpiration(showId: String, sessionId: String): Long {
        val configuredExpire = configPerResCache.get(showId, sessionId)?.expireInS
        return when (configuredExpire) {
            null -> defaultExpiredInS
            -1L -> -1L
            else -> configuredExpire
        }
    }

    private suspend fun scheduleExpirationOrFail(resId: String, expireInS: Long, opUserId: String?) {
        val scheduled = jobrunrGateway.scheduleExpireReservation(resId, expireInS)
        if (!scheduled) {
            logger.error("optUser [{}] scheduleExpirationOrFail: Failed to schedule expiration job for reservation {}", opUserId, resId)
            throw ReservationException.InternalError("Lỗi hệ thống khi lên lịch hủy tự động")
        }
        logger.info("optUser [{}] scheduleExpirationOrFail: Scheduled expiration job for reservation {} in {} seconds", opUserId, resId, expireInS)
    }

    private fun buildNewReservationDocument(resId: String, command: CreateReservationCommand, expireInS: Long): TicketReservationDocument {
        val detailsDocs = command.details.map {
            TicketReservationDetailsDocument(
                ticketId = it.ticketId,
                capacityId = it.capacityId, // Assumes enriched
                quantity = it.quantity,
                priceCat = it.priceCat,     // Assumes enriched
                sid = it.sid,               // Assumes enriched
                dayCapacityId = it.dayCapacityId,
            )
        }
        return TicketReservationDocument(
            id = resId,
            showId = command.showId,
            sessionId = command.sessionId,
            details = detailsDocs,
            status = TicketReservationStatus.Created,
            customerUserId = command.customerUserId,
            expireInS = expireInS
        )
    }

    private fun isReservationStuck(reservation: TicketReservationDocument): Boolean {
        val stuckThresholdMillis = 2 * reservation.expireInS * 1000
        return (Date().time - reservation.lastModified.time) > stuckThresholdMillis
    }

    private fun mapOrderStatusToReservationStatus(orderStatus: String): TicketReservationStatus {
        return try {
            when (OrderStatus.valueOf(orderStatus)) {
                OrderStatus.PaymentProcessing, OrderStatus.PaymentPending -> TicketReservationStatus.OrderPaying
                OrderStatus.PaymentSucceed -> TicketReservationStatus.OrderSuccessful
                OrderStatus.PaymentCancelled, OrderStatus.Canceled -> TicketReservationStatus.OrderCancelled
                OrderStatus.PaymentFailed, OrderStatus.Failed, OrderStatus.Expired -> TicketReservationStatus.OrderFailed
                else -> {
                    logger.error("Unhandled OrderStatus mapping: {}", orderStatus)
                    throw ReservationException.InternalError("Internal error: Unhandled order status '$orderStatus'")
                }
            }
        } catch (e: IllegalArgumentException) {
            logger.error("Invalid OrderStatus received: {}", orderStatus, e)
            throw ReservationException.PreconditionFailed("Invalid order status value received: '$orderStatus'")
        }
    }

    private fun validateStatusTransition(oldResStatus: TicketReservationStatus, newResStatus: TicketReservationStatus, resId: String, orderStatus: String) {
        if (oldResStatus == newResStatus) {
            logger.info("Reservation {} status is already {}. Idempotent update.", resId, newResStatus)
            // Decide if action is still needed. For now, allow proceeding.
            // If no action needed, could return early here.
        }

        val validSourceStatuses = listOf(
            TicketReservationStatus.Reserved,
            TicketReservationStatus.OrderPaying,
            TicketReservationStatus.Locked,
            TicketReservationStatus.OrderSuccessful
        )
        if (oldResStatus !in validSourceStatuses) {
            logger.error("Reservation {} cannot be updated from status {} based on order status '{}'. Invalid source.", resId, oldResStatus, orderStatus)
            throw ReservationException.PreconditionFailed("Reservation $resId is in state '$oldResStatus', cannot process order update.")
        }

        val allowedTransitions: List<TicketReservationStatus> = when (oldResStatus) {
            TicketReservationStatus.Reserved -> listOf(
                TicketReservationStatus.OrderPaying,
                TicketReservationStatus.OrderFailed,
                TicketReservationStatus.OrderCancelled
            )
            TicketReservationStatus.OrderPaying -> listOf(
                TicketReservationStatus.OrderSuccessful,
                TicketReservationStatus.OrderFailed,
                TicketReservationStatus.OrderCancelled
            )
            TicketReservationStatus.Locked -> listOf(TicketReservationStatus.OrderCancelled) // Review if this is correct
            TicketReservationStatus.OrderSuccessful -> listOf(TicketReservationStatus.OrderCancelled) // Review if this is correct
            else -> emptyList() // Should be caught by validSourceStatuses check
        }

        if (newResStatus !in allowedTransitions && oldResStatus != newResStatus) { // Allow idempotent case implicitly
            logger.error("Reservation {} transition from {} to {} is not allowed based on order status '{}'. Allowed: {}", resId, oldResStatus, newResStatus, orderStatus, allowedTransitions)
            throw ReservationException.PreconditionFailed("Reservation $resId: Invalid status transition from '$oldResStatus' to '$newResStatus'")
        }
    }

    private suspend fun updateReservationStatusAndRelated(resId: String, oldResStatus: TicketReservationStatus, newResStatus: TicketReservationStatus, reservation: TicketReservationDocument) {
        val updateRedisSuccessful = try {
            redisClient.updateReservationStatus(resId, oldResStatus, newResStatus)
        } catch (t: Throwable) {
            logger.error("Reservation {} failed to update status from {} to {} in Redis", resId, oldResStatus, newResStatus, t)
            throw ReservationException.InternalError("Lỗi hệ thống khi cập nhật trạng thái reservation (Redis)", t)
        }
        if (updateRedisSuccessful == UpdateRedisFailedConstants.NOT_EXIST) {
            logger.info("Reservation {} database status update to {} scheduled.", resId, newResStatus)
        }
        val currentState = (redisClient.getReservation(resId) ?: ticketResDB.loadReservationById(resId)) ?.status ?: "UNKNOWN (removed?)"
        if (updateRedisSuccessful == UpdateRedisFailedConstants.FAILED) {
            logger.error("Reservation {} failed CAS update from {} to {} in Redis. Current state: {}", resId, oldResStatus, newResStatus, currentState)
            throw ReservationException.Conflict("Reservation $resId state changed concurrently. Update aborted.")
        }

        logger.info("Reservation {} status updated from {} to {} in Redis successfully.", resId, oldResStatus, newResStatus)

        // Trigger side effects
        if (newResStatus in listOf(TicketReservationStatus.OrderCancelled, TicketReservationStatus.OrderFailed)) {
            logger.info("Reservation {} reached terminal state {}. Initiating capacity release and reservation cleanup.", resId, newResStatus)
            launch { resExeRetryable.releaseRemainingCapOnRedis(resId, reservation.details) }
            launch { resExeRetryable.releaseReservedCapInDb(resId, reservation.details) }
            launch { resExeRetryable.removeReservationOnRedis(resId) }
        } else if (newResStatus == TicketReservationStatus.OrderSuccessful) {
            logger.info("Reservation {} reached successful state. Cleaning up reservation from Redis.", resId)
            launch { resExeRetryable.removeReservationOnRedis(resId) }
        }

        // Update DB asynchronously
        resExeRetryable.updateReservationStatusDb(resId, oldResStatus, newResStatus)
        logger.info("Reservation {} database status update to {} scheduled.", resId, newResStatus)
    }
}