package metaticket.ticket.redis

import com.fasterxml.jackson.databind.ObjectMapper
import io.lettuce.core.ScriptOutputType
import io.lettuce.core.cluster.api.async.RedisClusterAsyncCommands
import metaticket.common.redis.RedisClusterManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.future.await
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import metaticket.common.logger.Logging
import metaticket.common.redis.codec.RedisCustomValueCodec
import metaticket.ticket.datastructure.TicketReservationStatus
import metaticket.ticket.pojo.db.TicketReservationDocument


/**
 *
 * @author dongcatchay
 */
class RedisAsyncClient constructor(
    private val redisClusterManager: RedisClusterManager<Any>,
    private val jsonMapper: ObjectMapper
): Logging {
    private val async: RedisClusterAsyncCommands<String, Any>
        get() = redisClusterManager.asyncCommands

    private lateinit var addTicketRemSha: String
    private lateinit var updateResSha: String

    fun start() = runBlocking {
        redisClusterManager.connect(RedisCustomValueCodec(jsonMapper))
        logger.info("Redis cluster connection established")
        addTicketRemSha = withContext(Dispatchers.IO) {
            val path = "lua/update.ticket.remaining.script.lua"
            val fileScript = javaClass.classLoader.getResource(path)!!
            val script = fileScript.readText()
            async.scriptLoad(script).await()
        }

        updateResSha = withContext(Dispatchers.IO) {
            val path = "lua/update.reservation.status.script.lua"
            val fileScript = javaClass.classLoader.getResource(path)!!
            val script = fileScript.readText()
            async.scriptLoad(script).await()
        }
    }

    /**
     * Shutdown this client and close all open connections
     */
    fun shutdown() {
        redisClusterManager.shutdown()
    }

    private fun prefixTicketKey(key: String) : String {
        return "ticket.$key"
    }

    private fun prefixReservationKey(key: String) : String {
        return "reservation.$key"
    }

    suspend fun getTicketRemCap(ticketId: String): Int? {
        val res =  withContext(Dispatchers.IO) {
            async.get(prefixTicketKey(ticketId))
        }.await() ?: return null
        return res as Int
    }

    suspend fun setTicketRemCap(ticketId: String, quantity: Int): Boolean {
        return withContext(Dispatchers.IO) {
            async.set(prefixTicketKey(ticketId), quantity)
        }.await() === "OK"
    }

    suspend fun setNxTicketRemCap(ticketId: String, quantity: Int): Boolean {
        return withContext(Dispatchers.IO) {
            async.setnx(prefixTicketKey(ticketId), quantity)
        }.await()
    }

    /**
     * Increase or decrease ticket remaining capacity, only can update when remaining after that >= 0
     *
     * @param ticketId the ticket doc id.
     * @param quantity the value.
     * @return Int remaining capacity if update was executed correctly,
     * if not exist return -999
     * if failed return -1
     */
    suspend fun addTicketRemCap(ticketId:String, quantity: Int): Int {
        return withContext(Dispatchers.IO) {
            async.evalsha<Int>(addTicketRemSha, ScriptOutputType.INTEGER, arrayOf(prefixTicketKey(ticketId)), quantity)
        }.await()
    }

    suspend fun removeTicketRemCap(vararg ticketIds: String): Long {
        return withContext(Dispatchers.IO) {
            async.del(*ticketIds.map { prefixTicketKey(it) }.toTypedArray())
        }.await()
    }

    suspend fun updateReservationStatus(resId: String, oldStatus: TicketReservationStatus, newStatus: TicketReservationStatus): Int {
        return withContext(Dispatchers.IO) {
            async.evalsha<Int>(updateResSha, ScriptOutputType.INTEGER, arrayOf(prefixReservationKey(resId)), oldStatus, newStatus)
        }.await()
    }

    suspend fun setReservation(reservation: TicketReservationDocument): Boolean {
        return withContext(Dispatchers.IO) {
            async.set(prefixReservationKey(reservation.id), reservation)
        }.await() === "OK"
    }

    suspend fun setNxReservation(reservation: TicketReservationDocument): Boolean {
        return withContext(Dispatchers.IO) {
            async.setnx(prefixReservationKey(reservation.id), reservation)
        }.await()
    }

    suspend fun getReservation(reservationIds: String): TicketReservationDocument? {
        val res = withContext(Dispatchers.IO) {
            async.get(prefixReservationKey(reservationIds))
        }.await() ?: return null
        return jsonMapper.convertValue(res, TicketReservationDocument::class.java)
    }

    suspend fun removeReservation(vararg reservationIds: String): Long {
        return withContext(Dispatchers.IO) {
            async.del(*reservationIds.map { prefixReservationKey(it) }.toTypedArray())
        }.await()
    }
}
