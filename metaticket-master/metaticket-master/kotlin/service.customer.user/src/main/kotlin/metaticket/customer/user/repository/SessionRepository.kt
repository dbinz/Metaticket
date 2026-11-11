package metaticket.customer.user.repository

import io.lettuce.core.cluster.api.async.RedisClusterAsyncCommands
import kotlinx.coroutines.future.await
import metaticket.common.logger.Logging
import metaticket.common.redis.RedisClusterManager
import metaticket.customer.user.model.SessionStatus
import metaticket.customer.user.model.SessionType


class SessionRepository constructor(
    private val redisClusterManager: RedisClusterManager<String>,
): Logging {
    // Get asyncCommands from redisConnection to execute asynchronous commands
    private val asyncCommands: RedisClusterAsyncCommands<String, String>
        get() = redisClusterManager.asyncCommands

    suspend fun save(type: SessionType, id: String, data: String, ttl: Long): String? {
        return try {
            val key = "$type:$id"
            asyncCommands.setex(key, ttl, data).await()
        } catch (e: Throwable) {
            // Log error if an exception occurs
            logger.error("Error saving [{}] session [{}] : ", type, id, e)
            // Return null if error occurs
            null
        }
    }

    suspend fun findSession(type: SessionType, id: String): String? {
        val key = "$type:$id"
        return asyncCommands.get(key).await()
    }

    suspend fun deleteSessionId(type: SessionType, id: String): Long? {
        return try {
            val key = "$type:$id"
            asyncCommands.del(key).await()
        } catch (e: Throwable) {
            // Log error if an exception occurs
            logger.error("Error deleting [{}] session {} : ", type, id, e)
            // Return null if error occurs
            null
        }
    }
}