package metaticket.customer.auth.controller

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.config.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import metaticket.common.logger.Logging
import metaticket.customer.auth.VoteOptions
import metaticket.customer.auth.pojo.VoteRequest
import metaticket.customer.auth.repository.VoteRepository
import metaticket.customer.auth.response.VoteMeResponse
import metaticket.customer.auth.response.VoteOptionResponse
import metaticket.customer.auth.response.VoteOptionsResponse
import metaticket.customer.auth.response.VoteResultItem
import metaticket.customer.auth.response.VoteResultsResponse
import metaticket.customer.auth.response.VoteSubmitResponse
import kotlin.math.roundToLong

class VoteCtrl constructor(
    private val config: ApplicationConfig,
    private val voteRepo: VoteRepository,
) : Logging {
    private val adminSecret = config.property("vote.adminSecret").getString()

    // total = số người đã vote (respondents), KHÔNG phải tổng số lượt chọn — multi-select có thể
    // khiến tổng percent của mọi option vượt 100%, nhưng percent của từng option vẫn phản ánh đúng
    // tỉ lệ respondent đã chọn option đó.
    private fun countPerOption(all: Map<String, String>): Map<String, Long> {
        val counted = all.values.flatMap { it.split(",") }.filter { it.isNotBlank() }.groupingBy { it }.eachCount()
        return VoteOptions.ALL.associate { it.id to (counted[it.id]?.toLong() ?: 0L) }
    }

    private fun percentOf(count: Long, total: Long): Double {
        if (total == 0L) return 0.0
        return (count * 1000.0 / total).roundToLong() / 10.0
    }

    private val statsCacheMutex = Mutex()
    @Volatile private var statsCacheCounts: Map<String, Long>? = null
    @Volatile private var statsCacheTotal: Long = 0
    @Volatile private var statsCacheAt: Long = 0
    private val statsCacheTtlMs = 5_000L

    // Cache chung cho getOptions()/getResults() — 2 route này bị gọi thường xuyên (getOptions không
    // cần login) và cùng cần (counts, total); tránh HGETALL + tính lại mỗi request khi traffic cao,
    // vì đang dùng chung Redis/service với login thật của toàn site. Mutex tránh nhiều request cùng
    // lúc lúc cache hết hạn đều dội xuống Redis (thundering herd).
    private suspend fun getStats(): Pair<Map<String, Long>, Long> {
        val now = System.currentTimeMillis()
        statsCacheCounts?.let { if (now - statsCacheAt < statsCacheTtlMs) return it to statsCacheTotal }

        return statsCacheMutex.withLock {
            val nowLocked = System.currentTimeMillis()
            statsCacheCounts?.let { if (nowLocked - statsCacheAt < statsCacheTtlMs) return@withLock it to statsCacheTotal }

            val all = voteRepo.countAll()
            val counts = countPerOption(all)
            val total = all.size.toLong()
            statsCacheCounts = counts
            statsCacheTotal = total
            statsCacheAt = nowLocked
            counts to total
        }
    }

    suspend fun getOptions(call: ApplicationCall) {
        val (counts, total) = getStats()
        call.respond(
            VoteOptionsResponse(
                multi = VoteOptions.MULTI,
                maxChoicePerVote = VoteOptions.MAX_CHOICE_PER_VOTE,
                totalVotes = total,
                options = VoteOptions.ALL
                    .sortedByDescending { counts[it.id] ?: 0L }
                    .map {
                        VoteOptionResponse(it.id, it.label, it.thumbnailUrl, it.description)
                    },
            )
        )
    }

    suspend fun vote(call: ApplicationCall) {
        val userId = call.request.headers["X-Customer-User-Id"]
            ?: return call.respond(HttpStatusCode.Unauthorized)

        val req = call.receive<VoteRequest>()
        val optionIds = req.optionIds.distinct()

        if (optionIds.isEmpty() || optionIds.any { !VoteOptions.isValid(it) }) {
            return call.respond(HttpStatusCode.BadRequest, "Lựa chọn không hợp lệ")
        }
        if (!VoteOptions.MULTI && optionIds.size != 1) {
            return call.respond(HttpStatusCode.BadRequest, "Chỉ được chọn 1 lựa chọn")
        }
        if (VoteOptions.MULTI && optionIds.size > VoteOptions.MAX_CHOICE_PER_VOTE) {
            return call.respond(HttpStatusCode.BadRequest, "Chỉ được chọn tối đa ${VoteOptions.MAX_CHOICE_PER_VOTE} lựa chọn")
        }

        val saved = voteRepo.trySaveVote(userId, optionIds)
        if (!saved) {
            val existing = voteRepo.findMyVote(userId) ?: optionIds
            return call.respond(HttpStatusCode.Conflict, VoteSubmitResponse("Bạn đã vote rồi", existing))
        }
        call.respond(HttpStatusCode.OK, VoteSubmitResponse("Vote thành công", optionIds))
    }

    suspend fun getMyVote(call: ApplicationCall) {
        val userId = call.request.headers["X-Customer-User-Id"]
            ?: return call.respond(HttpStatusCode.Unauthorized)

        val myOptionIds = voteRepo.findMyVote(userId)
            ?: return call.respond(VoteMeResponse(hasVoted = false))

        call.respond(VoteMeResponse(hasVoted = true, optionIds = myOptionIds))
    }

    suspend fun getResults(call: ApplicationCall) {
        val secret = call.request.headers["X-Admin-Secret"]
        if (secret.isNullOrBlank() || secret != adminSecret) {
            return call.respond(HttpStatusCode.Unauthorized)
        }

        val (counts, total) = getStats()
        // Percent = tỉ lệ trên TỔNG SỐ LƯỢT CHỌN (sum count mọi option), không phải trên số
        // respondent — để phần trăm mọi option cộng lại đúng 100% (kể cả khi multi-select).
        val totalVoteCount = counts.values.sum()
        val results = VoteOptions.ALL.map {
            val count = counts[it.id] ?: 0L
            VoteResultItem(it.id, it.label, count, percentOf(count, totalVoteCount))
        }
        call.respond(VoteResultsResponse(total, VoteOptions.MULTI, results))
    }
}
