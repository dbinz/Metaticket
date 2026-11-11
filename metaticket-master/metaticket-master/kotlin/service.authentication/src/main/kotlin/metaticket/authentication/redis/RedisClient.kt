package metaticket.authentication.redis

import com.google.common.cache.Cache
import com.google.common.cache.CacheBuilder
import io.ktor.server.application.ApplicationEnvironment
import java.util.concurrent.TimeUnit

// Data class to store access and refresh tokens together
data class UserAuthTokens(val accessToken: String, val refreshToken: String)

class RedisClient(private val ktorEnv: ApplicationEnvironment,) {
    private val jwtExpirationTime: Long = ktorEnv.config.property("jwt.expiration_time").getString().toLong()
    // Cache: Key is userId, Value is UserAuthTokens
    private val userSessionCache: Cache<String, UserAuthTokens> = CacheBuilder.newBuilder()
        .maximumSize(100_000) // Maximum number of sessions to store
        .expireAfterWrite(jwtExpirationTime, TimeUnit.MILLISECONDS) // Tokens expire
//         .expireAfterAccess(1, TimeUnit.HOURS) // Alternative: Tokens expire 1 hour after last access
        .build()

    /**
     * Saves or updates the user's access and refresh tokens.
     * The userId is used as the key.
     *
     * @param userId The unique identifier for the user.
     * @param accessToken The access token to be stored.
     * @param refreshToken The refresh token to be stored.
     */
    fun saveUserTokens(userId: String, accessToken: String, refreshToken: String) {
        val tokens = UserAuthTokens(accessToken, refreshToken)
        userSessionCache.put(userId, tokens)
    }

    /**
     * Retrieves the user's access and refresh tokens based on their userId.
     *
     * @param userId The unique identifier for the user.
     * @return UserAuthTokens containing the access and refresh tokens, or null if not found.
     */
    fun getUserTokens(userId: String): UserAuthTokens? {
        return userSessionCache.getIfPresent(userId)
    }

    /**
     * Removes the user's tokens from the cache based on their userId.
     *
     * @param userId The unique identifier for the user whose tokens are to be removed.
     */
    fun removeUserTokens(userId: String) {
        userSessionCache.invalidate(userId)
    }

    /**
     * Checks if tokens exist for a given userId.
     *
     * @param userId The unique identifier for the user.
     * @return true if tokens exist for the user, false otherwise.
     */
    fun doUserTokensExist(userId: String): Boolean {
        return userSessionCache.getIfPresent(userId) != null
    }

    /**
     * Checks if a given access token is currently active for any user.
     * Note: This operation can be expensive as it iterates through the cache values.
     *
     * @param token The access token to check.
     * @return true if the access token is found in any user's session, false otherwise.
     */
    fun isAccessTokenActive(token: String): Boolean {
        // Guava Cache doesn't provide an efficient way to search by value.
        // For simulation, we iterate. In a real scenario, you might need a secondary index or actual Redis.
        return userSessionCache.asMap().values.any { it.accessToken == token }
    }

    /**
     * Removes a user's session based on their access token.
     * Useful if you need to invalidate a session using only the access token.
     * Assumes access tokens are unique across all stored sessions.
     * Note: This operation can be expensive.
     *
     * @param token The access token of the session to be removed.
     * @return true if a session associated with the access token was found and removed, false otherwise.
     */
    fun removeSessionByAccessToken(token: String): Boolean {
        val userIdToRemove = userSessionCache.asMap().entries
            .find { it.value.accessToken == token }
            ?.key

        return if (userIdToRemove != null) {
            userSessionCache.invalidate(userIdToRemove)
            true // Indicate that an entry was invalidated
        } else {
            false // Indicate that no matching entry was found
        }
    }

    /**
     * Clears all entries from the cache.
     * Useful for cleanup or resetting in test environments.
     */
    fun clearAllTokens() {
        userSessionCache.invalidateAll()
    }
}