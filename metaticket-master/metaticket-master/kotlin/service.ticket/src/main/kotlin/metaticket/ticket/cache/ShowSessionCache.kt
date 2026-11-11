package metaticket.ticket.cache

import com.google.common.cache.CacheBuilder
import com.google.common.cache.CacheLoader
import com.google.common.cache.LoadingCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import metaticket.common.logger.Logging
import metaticket.show.datastructure.ShowSessionStatus
import metaticket.show.grpc.ShowMessage
import metaticket.ticket.gateway.ShowGateway
import java.util.*
import java.util.concurrent.TimeUnit
import kotlin.jvm.optionals.getOrNull


/**
 *
 * @author dongcatchay
 */
class ShowSessionCache(
    private val showGateway: ShowGateway
): Logging {

    data class Key(val showId: String, val sessionId: String)

    private val loader: CacheLoader<Key, Optional<ShowMessage.GetSessionResponse>> = object : CacheLoader<Key, Optional<ShowMessage.GetSessionResponse>>() {

        override fun load(key: Key): Optional<ShowMessage.GetSessionResponse> {
            return runBlocking(Dispatchers.IO) {
                val res = showGateway.getShowSession(key.showId, key.sessionId)
                logger.info("Loaded session {}-{} from show service", res?.sessionId, res?.status)
                Optional.ofNullable(res)
            }
        }
    }

    private val cache: LoadingCache<Key, Optional<ShowMessage.GetSessionResponse>> = CacheBuilder.newBuilder()
        .maximumSize(10000)
        .expireAfterWrite(1, TimeUnit.MINUTES)
        .build(loader)

    suspend fun get(showId: String, sessionId: String): ShowMessage.GetSessionResponse?  = withContext(Dispatchers.IO) {
        return@withContext cache.get(Key(showId, sessionId)).getOrNull()
    }

    suspend fun checkShowSessionInOpen(showId: String, sessionId: String): Boolean? {
        val session = get(showId, sessionId) ?: kotlin.run {
            return null
        }

        if (ShowSessionStatus.valueOf(session.status) !in listOf(ShowSessionStatus.Open)) {
            return false
        }

        return true
    }
}
