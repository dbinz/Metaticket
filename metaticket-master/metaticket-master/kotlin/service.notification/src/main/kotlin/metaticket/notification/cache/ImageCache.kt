package metaticket.notification.cache

import com.google.common.cache.CacheBuilder
import com.google.common.cache.CacheLoader
import com.google.common.cache.LoadingCache
import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.util.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import metaticket.common.logger.Logging
import java.util.concurrent.TimeUnit


/**
 *
 * @author dongcatchay
 */
class ImageCache(
    private val client: HttpClient,
): Logging {

    private val loader: CacheLoader<String, ByteArray> = object : CacheLoader<String, ByteArray>() {

        override fun load(key: String): ByteArray {
            return runBlocking {
                withContext(Dispatchers.IO) {
                    client.get(Url(key)).readRawBytes()
                }
            }
        }
    }

    private val cache: LoadingCache<String, ByteArray> = CacheBuilder.newBuilder()
        .maximumSize(100)
        .expireAfterAccess(1, TimeUnit.HOURS)
        .build(loader)

    suspend fun get(id: String): String  = withContext(Dispatchers.IO) {
        return@withContext cache.get(id).encodeBase64()
    }

    suspend fun remove(id: String) {
        cache.invalidate(id)
    }
}
