package metaticket.payment.cache

import com.google.common.cache.CacheBuilder
import com.google.common.cache.CacheLoader
import com.google.common.cache.LoadingCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import metaticket.common.logger.Logging
import metaticket.payment.database.service.PGAccountConfigDBService
import metaticket.payment.pojo.db.PGAccount
import java.util.*
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import kotlin.jvm.optionals.getOrNull


/**
 *
 * @author dongcatchay
 */
class PGAccountCache(
    private val dbService: PGAccountConfigDBService
): Logging {

    private val defaultAccountCode: AtomicReference<String?> = AtomicReference(null)

    init {
        runBlocking {
            setDefaultAccount()
        }
    }

    private val loader: CacheLoader<String, Optional<PGAccount>> = object : CacheLoader<String, Optional<PGAccount>>() {
        @Throws(NoSuchElementException::class)
        override fun load(key: String): Optional<PGAccount> {
            return runBlocking(Dispatchers.IO) {
                val res = dbService.load(key)
                logger.info("load pgAccount {} from db {}", key, res)
                Optional.ofNullable(res?.pgAccount?.get(key))
            }
        }
    }

    private val cache: LoadingCache<String, Optional<PGAccount>> = CacheBuilder.newBuilder()
        .maximumSize(1000)
        .expireAfterAccess(1, TimeUnit.MINUTES)
        .build(loader)

    suspend fun get(code: String): PGAccount?  = withContext(Dispatchers.IO) {
        return@withContext cache.get(code).getOrNull()
    }

    suspend fun getDefaultAccountCode(): String? = withContext(Dispatchers.IO) {
        return@withContext defaultAccountCode.get() ?: setDefaultAccount()
    }

    suspend fun setDefaultAccount(): String {
        val defaultAccountCode = dbService.loadDefaultAccountCode()
        if (defaultAccountCode.isNullOrBlank()) {
            logger.error("There is no default PG account")
            throw RuntimeException("There is no default PG account")
        }
        this.defaultAccountCode.set(defaultAccountCode)
        logger.info("set default Account Code: $defaultAccountCode")

        return defaultAccountCode
    }

    fun remove(code: String) {
        cache.invalidate(code)
    }
}
