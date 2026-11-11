package metaticket.customer.auth.repository

import io.lettuce.core.cluster.api.async.RedisClusterAsyncCommands
import kotlinx.coroutines.future.await
import metaticket.common.logger.Logging
import metaticket.common.redis.RedisClusterManager

/**
 * Repository for managing customer user tokens in Redis.
 *
 * This class provides asynchronous methods to save, retrieve, and delete user tokens
 * associated with user IDs, utilizing Redis for efficient storage and retrieval.
 *
 * **Note:** This repository supports only one active token per user at a time.
 * If a new token is saved for a user, the previous token is overwritten.
 *
 * @param redisConnection The active, stateful connection to the Redis Cluster.
 *
 * @author dongcatchay
 */
class UserTokenRepository constructor(private val redisClusterManager: RedisClusterManager<String>): Logging {
    // Get asyncCommands from redisConnection to execute asynchronous commands
    private val asyncCommands: RedisClusterAsyncCommands<String, String>
        get() = redisClusterManager.asyncCommands

    // Prefix for Redis keys to distinguish user tokens
    private val userTokenPrefix = "customer_user_token:"
    private val userRefreshTokenPrefix = "customer_user_refresh_token:"

    /**
     * Saves a user token associated with a user ID in Redis with a specified time-to-live (TTL).
     *
     * **Note:** Only one token is active per user. Saving a new token will overwrite the old one.
     *
     * @param userId The ID of the user.
     * @param token The JWT token to be saved.
     * @param ttl The time-to-live (TTL) of the token in seconds.
     * @return A string indicating the result of the save operation (e.g., "OK"), or null if an error occurred.
     */
    suspend fun saveToken(userId: String, token: String, ttl: Long): String? {
        return try {
            val key = "$userTokenPrefix$userId"
            asyncCommands.setex(key, ttl, token).await()
        } catch (e: Throwable) {
            // Log error if an exception occurs
            logger.error("Error saving token for user $userId: ${e.message}")
            // Return null if error occurs
            null
        }
    }

    suspend fun saveRefreshToken(userId: String, token: String, ttl: Long): String? {
        return try {
            val key = "$userRefreshTokenPrefix$userId"
            asyncCommands.setex(key, ttl, token).await()
        } catch (e: Throwable) {
            // Log error if an exception occurs
            logger.error("Error saving refresh token for user $userId: ${e.message}")
            // Return null if error occurs
            null
        }
    }

    /**
     * Retrieves a user token associated with a user ID from Redis.
     *
     * @param userId The ID of the user.
     * @return The JWT token associated with the user ID, or null if not found or an error occurred.
     */
    suspend fun findToken(userId: String): String? {
        val key = "$userTokenPrefix$userId"
        return asyncCommands.get(key).await()
    }

    suspend fun findRefreshToken(userId: String): String? {
        val key = "$userRefreshTokenPrefix$userId"
        return asyncCommands.get(key).await()
    }

    /**
     * Deletes a user token associated with a user ID from Redis.
     *
     * @param userId The ID of the user.
     * @return The number of keys that were removed (1 if successful, 0 if key did not exist), or null if an error occurred.
     */
    suspend fun deleteToken(userId: String): Long? {
        return try {
            val key = "$userTokenPrefix$userId"
            asyncCommands.del(key).await()
        } catch (e: Throwable) {
            // Log error if an exception occurs
            logger.error("Error deleting token for user $userId: ${e.message}")
            // Return null if error occurs
            null
        }
    }

    suspend fun deleteRefreshToken(userId: String): Long? {
        return try {
            val key = "$userTokenPrefix$userId"
            asyncCommands.del(key).await()
        } catch (e: Throwable) {
            // Log error if an exception occurs
            logger.error("Error deleting refresh token for user $userId: ${e.message}")
            // Return null if error occurs
            null
        }
    }
}