package metaticket.ticket.cache

import com.google.common.cache.CacheBuilder
import com.google.common.cache.CacheLoader
import com.google.common.cache.LoadingCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import metaticket.common.logger.Logging
import metaticket.ticket.database.service.ConfigPerReservationDBService
import metaticket.ticket.pojo.db.ConfigPerReservationDocument
import java.util.*
import java.util.concurrent.TimeUnit
import kotlin.jvm.optionals.getOrNull


/**
 *
 * @author dongcatchay
 */
class ConfigPerReservationCache(
    private val configPerResDB: ConfigPerReservationDBService
): Logging {

    data class Key(val showId: String, val sessionId: String)

    private val loader: CacheLoader<Key, Optional<ConfigPerReservationDocument>> = object : CacheLoader<Key, Optional<ConfigPerReservationDocument>>() {

        @Throws(NoSuchElementException::class)
        override fun load(key: Key): Optional<ConfigPerReservationDocument> {
            return runBlocking(Dispatchers.IO) {
                val res = configPerResDB.loadConfigByShowAndSession(key.showId, key.sessionId)
                logger.info("load ticket per reservation from db {}", res)
                Optional.ofNullable(res)
            }
        }
    }

    private val cache: LoadingCache<Key, Optional<ConfigPerReservationDocument>> = CacheBuilder.newBuilder()
        .maximumSize(1000)
        .expireAfterAccess(1, TimeUnit.MINUTES)
        .build(loader)

    suspend fun get(showId: String, sessionId: String): ConfigPerReservationDocument?  = withContext(Dispatchers.IO) {
        return@withContext cache.get(Key(showId, sessionId)).getOrNull()
    }
}
