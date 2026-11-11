package meta.news.controllers

import org.springframework.beans.factory.annotation.Value
import org.springframework.cache.CacheManager
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.server.ResponseStatusException

/**
 * Cache invalidation for WordPress publish webhook.
 * Not published on nginx edge — call http://127.0.0.1:8082 only, with shared secret.
 */
@RestController
@RequestMapping("/internal/news")
class InternalNewsController(
	private val cacheManager: CacheManager,
	@Value("\${news.internal-api-key:}") private val internalApiKey: String,
) {

	@PostMapping("/cache/invalidate")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	fun invalidateCache(
		@RequestHeader(name = "X-News-Internal-Key", required = false) key: String?,
	) {
		val expected = internalApiKey.trim()
		if (expected.isEmpty() || key.isNullOrBlank() || key != expected) {
			throw ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid internal key")
		}
		cacheManager.cacheNames.forEach { name ->
			cacheManager.getCache(name)?.clear()
		}
	}
}
