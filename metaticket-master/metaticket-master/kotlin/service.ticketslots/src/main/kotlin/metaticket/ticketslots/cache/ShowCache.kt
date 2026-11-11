package metaticket.ticketslots.cache

import com.google.common.cache.CacheBuilder
import com.google.common.cache.CacheLoader
import com.google.common.cache.LoadingCache
import kotlinx.coroutines.runBlocking
import metaticket.common.logger.Logging
import metaticket.show.grpc.ShowMessage
import metaticket.ticketslots.gateway.ShowGateway
import java.util.Optional
import java.util.concurrent.TimeUnit
import kotlin.jvm.optionals.getOrNull

/**
 * Service quản lý cache cho thông tin show
 * Cache có TTL 1 ngày sau khi access sẽ clear
 */
class ShowCache constructor(
    private val showGateway: ShowGateway
) : Logging {
    
    // Cache với TTL 1 ngày
    private val showCache: LoadingCache<String, Optional<ShowMessage.GetShowResponse>> = CacheBuilder.newBuilder()
        .expireAfterWrite(1, TimeUnit.MINUTES)
        .maximumSize(10000) // Giới hạn 10k entries
        .recordStats()
        .build(object : CacheLoader<String, Optional<ShowMessage.GetShowResponse>>() {
            override fun load(key: String): Optional<ShowMessage.GetShowResponse> {
                return runBlocking {
                    logger.info("Loading show info from service for sessionId: $key")
                    val res = showGateway.getShowById(key)
                    logger.info("Found show info from sessionId: $res")
                    Optional.ofNullable(res)
                }
            }
        })
    
    /**
     * Lấy thông tin show theo session ID
     * Sẽ load từ cache trước, nếu không có mới gọi show service
     */
    suspend fun getShowById(sessionId: String): ShowMessage.GetShowResponse? {
        return showCache.get(sessionId).getOrNull()
    }

    /**
     * Invalidate cache entry cho session ID cụ thể
     */
    fun invalidateCache(sessionId: String) {
        showCache.invalidate(sessionId)
        logger.info("Invalidated cache for sessionId: $sessionId")
    }
    
    /**
     * Clear toàn bộ cache
     */
    fun clearCache() {
        showCache.invalidateAll()
        logger.info("Cleared all show cache")
    }
    
    /**
     * Lấy thống kê cache
     */
    fun getCacheStats(): String {
        val stats = showCache.stats()
        return "Cache stats - Hit rate: ${String.format("%.2f", stats.hitRate() * 100)}%, " +
                "Hits: ${stats.hitCount()}, Misses: ${stats.missCount()}, " +
                "Load count: ${stats.loadCount()}, Size: ${showCache.size()}"
    }
}