package metaticket.customer.auth.repository

import io.ktor.server.config.*
import io.lettuce.core.cluster.api.async.RedisClusterAsyncCommands
import kotlinx.coroutines.future.await
import metaticket.common.logger.Logging
import metaticket.common.redis.RedisClusterManager

/**
 * Repository lưu vote — dùng kết nối Redis RIÊNG, độc lập hoàn toàn với RedisClusterManager dùng
 * chung cho token login (UserTokenRepository), để tính năng vote không ảnh hưởng/phụ thuộc vào cấu
 * hình cluster vốn có (không sửa StartUp.kt hay module Redis dùng chung).
 *
 * Chỉ trỏ vào ĐÚNG 1 node cố định (config `vote.redisNode`), không dùng danh sách nhiều node — vì
 * cluster Redis thật tại 10.0.1.12:43790-43792 hiện KHÔNG ghép mesh với nhau (xác nhận qua
 * CLUSTER NODES: mỗi node tự báo sở hữu toàn bộ slot theo góc nhìn riêng), khiến request bị định
 * tuyến không nhất quán giữa các node và phá vỡ "1 tài khoản/1 vote" (HSETNX chỉ atomic trong 1
 * node). Dùng 1 node cố định loại bỏ hoàn toàn nguy cơ này.
 */
class VoteRepository constructor(config: ApplicationConfig) : Logging {
    private val redisManager = RedisClusterManager<String>(listOf(config.property("vote.redisNode").getString()))

    init {
        redisManager.connect()
    }

    private val asyncCommands: RedisClusterAsyncCommands<String, String>
        get() = redisManager.asyncCommands

    private val voteKey = "customer_vote:responses"

    suspend fun trySaveVote(userId: String, optionIds: List<String>): Boolean {
        return try {
            asyncCommands.hsetnx(voteKey, userId, optionIds.joinToString(",")).await()
        } catch (e: Throwable) {
            logger.error("Error saving vote for user $userId: ${e.message}")
            false
        }
    }

    suspend fun findMyVote(userId: String): List<String>? {
        return asyncCommands.hget(voteKey, userId).await()?.split(",")?.filter { it.isNotBlank() }
    }

    /** userId -> "optionId1,optionId2" (raw), dùng để tổng hợp count/percent ở controller. */
    suspend fun countAll(): Map<String, String> {
        return asyncCommands.hgetall(voteKey).await()
    }
}
