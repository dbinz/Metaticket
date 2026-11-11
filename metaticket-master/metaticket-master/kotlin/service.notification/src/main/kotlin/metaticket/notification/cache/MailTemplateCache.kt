package metaticket.notification.cache

import com.google.common.cache.CacheBuilder
import com.google.common.cache.CacheLoader
import com.google.common.cache.LoadingCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import metaticket.common.logger.Logging
import metaticket.notification.database.service.MailTemplateDBService
import metaticket.plugin.mailtemplate.pojo.db.MailTemplateDocument
import java.util.*
import java.util.concurrent.TimeUnit
import kotlin.jvm.optionals.getOrNull


/**
 *
 * @author dongcatchay
 */
class MailTemplateCache(
    private val dbService: MailTemplateDBService
): Logging {

    private val loader: CacheLoader<String, Optional<MailTemplateDocument>> = object : CacheLoader<String, Optional<MailTemplateDocument>>() {

        @Throws(NoSuchElementException::class)
        override fun load(key: String): Optional<MailTemplateDocument> {
            return runBlocking(Dispatchers.IO) {
                logger.info("loading mail template {} from db", key)
                val res = dbService.findById(key)
                logger.info("load mail template {} from db", key)
                Optional.ofNullable(res)
            }
        }
    }

    private val cache: LoadingCache<String, Optional<MailTemplateDocument>> = CacheBuilder.newBuilder()
        .maximumSize(1000)
        .expireAfterAccess(1, TimeUnit.MINUTES)
        .build(loader)

    suspend fun get(id: String): MailTemplateDocument?  = withContext(Dispatchers.IO) {
        return@withContext cache.get(id).getOrNull()
    }

    suspend fun remove(id: String) {
        cache.invalidate(id)
    }
}
