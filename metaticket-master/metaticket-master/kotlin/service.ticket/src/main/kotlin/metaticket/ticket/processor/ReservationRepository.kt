package metaticket.ticket.processor

import kotlinx.coroutines.channels.Channel

/**
 * In memory implementation of reservation repository
 */
class ReservationRepository {

    private val db = Channel<String>(Channel.UNLIMITED)

    /** Puts a reservation into the repository */
    suspend fun put(reservation: String) {
        db.send(reservation)
    }

    /** Pulls a reservation from the repository */
    suspend fun pull(): String {
        return db.receive()
    }
}
