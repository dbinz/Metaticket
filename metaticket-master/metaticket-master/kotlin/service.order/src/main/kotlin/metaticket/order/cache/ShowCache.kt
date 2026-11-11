package metaticket.order.cache

import com.google.common.cache.CacheBuilder
import com.google.common.cache.CacheLoader
import com.google.common.cache.LoadingCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import metaticket.common.logger.Logging
import metaticket.order.gateway.ShowGateway
import metaticket.show.datastructure.ShowSessionStatus
import metaticket.show.grpc.ShowMessage
import java.util.*
import java.util.concurrent.TimeUnit
import kotlin.jvm.optionals.getOrNull


/**
 *
 * @author dongcatchay
 */
class ShowCache(
    private val showGateway: ShowGateway
): Logging {


    private val loader: CacheLoader<String, Optional<ShowMessage.GetShowResponse>> = object : CacheLoader<String, Optional<ShowMessage.GetShowResponse>>() {

        @Throws(NoSuchElementException::class)
        override fun load(key: String): Optional<ShowMessage.GetShowResponse> {
            return runBlocking(Dispatchers.IO) {
                val res = showGateway.getShow(key)
                Optional.ofNullable(res)
            }
        }
    }

    private val cache: LoadingCache<String, Optional<ShowMessage.GetShowResponse>> = CacheBuilder.newBuilder()
        .maximumSize(1000)
        .expireAfterWrite(1, TimeUnit.MINUTES)
        .build(loader)

    suspend fun getShowSession(showId: String, sessionId: String): ShowMessage.GetSessionResponse?  = withContext(Dispatchers.IO) {
        return@withContext cache.get(showId).getOrNull()?.sessionsList?.first { sessionId  == it.sessionId }
    }

    suspend fun getShow(showId: String): ShowMessage.GetShowResponse?  = withContext(Dispatchers.IO) {
        return@withContext cache.get(showId).getOrNull()
    }

    suspend fun checkShowSessionInOpen(showId: String, sessionId: String): Boolean? {
        val session = getShowSession(showId, sessionId) ?: kotlin.run {
            return null
        }

        if (ShowSessionStatus.valueOf(session.status) !in listOf(ShowSessionStatus.Open)) {
            return false
        }

        return true
    }
}
