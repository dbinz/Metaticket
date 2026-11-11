package metaticket.ticketslots.repository

import io.lettuce.core.cluster.api.async.RedisClusterAsyncCommands
import kotlinx.coroutines.future.await
import metaticket.common.logger.Logging
import metaticket.common.redis.RedisClusterManager
import metaticket.ticketslots.model.SessionStatus
import metaticket.ticketslots.model.ZNSType


class SessionRepository constructor(
    private val redisClusterManager: RedisClusterManager<String>,
): Logging {
    // Get asyncCommands from redisConnection to execute asynchronous commands
    private val asyncCommands: RedisClusterAsyncCommands<String, String>
        get() = redisClusterManager.asyncCommands

    suspend fun save(type: ZNSType, id: String, status: SessionStatus, ttl: Long? = null, extraInfo: String? = null): String? {
        return try {
            val key = "$type:$id"
            val value: String = extraInfo?.let { "${status.name}:$it" } ?: status.name
            if (ttl != null) asyncCommands.setex(key, ttl, value).await()
            else asyncCommands.set(key, value).await()
        } catch (e: Throwable) {
            // Log error if an exception occurs
            logger.error("Error saving [{}] session [{}] to [{}] : ", type, id, status, e)
            // Return null if error occurs
            null
        }
    }

    suspend fun findSession(type: ZNSType, id: String): String? {
        val key = "$type:$id"
        return asyncCommands.get(key).await()
    }

    suspend fun deleteSessionId(type: ZNSType, id: String): Long? {
        return try {
            val key = "$type:$id"
            asyncCommands.del(key).await()
        } catch (e: Throwable) {
            // Log error if an exception occurs
            logger.error("Error deleting {} session {} : ", type, id, e)
            // Return null if error occurs
            null
        }
    }
}