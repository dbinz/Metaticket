package meta.news.config

import com.github.benmanes.caffeine.cache.Caffeine
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.cache.CacheManager
import org.springframework.cache.caffeine.CaffeineCacheManager
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.client.SimpleClientHttpRequestFactory
import org.springframework.web.client.RestClient
import org.springframework.web.servlet.config.annotation.CorsRegistry
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer
import java.util.Base64
import java.util.concurrent.TimeUnit

@Configuration
@EnableConfigurationProperties(WordPressProperties::class, NewsProperties::class)
class AppConfig(
	private val wordPressProperties: WordPressProperties,
	private val newsProperties: NewsProperties,
) {

	@Bean
	fun wordPressRestClient(): RestClient {
		val factory = SimpleClientHttpRequestFactory().apply {
			setConnectTimeout(wordPressProperties.connectTimeoutMs.toInt())
			setReadTimeout(wordPressProperties.readTimeoutMs.toInt())
		}
		val base = wordPressProperties.baseUrl.trimEnd('/') + wordPressProperties.restPath
		val builder = RestClient.builder()
			.baseUrl(base)
			.requestFactory(factory)
		val user = wordPressProperties.username.trim()
		val pass = wordPressProperties.applicationPassword.trim()
		if (user.isNotEmpty() && pass.isNotEmpty()) {
			val token = Base64.getEncoder().encodeToString("$user:$pass".toByteArray(Charsets.UTF_8))
			builder.defaultHeader("Authorization", "Basic $token")
		}
		return builder.build()
	}

	/**
	 * Anonymous /wp-json client for public reads — deliberately without the application password, so the
	 * public BFF can only ever see what WP shows a logged-out visitor (published, non-protected content).
	 */
	@Bean
	fun wordPressPublicClient(): RestClient {
		val factory = SimpleClientHttpRequestFactory().apply {
			setConnectTimeout(wordPressProperties.connectTimeoutMs.toInt())
			setReadTimeout(wordPressProperties.readTimeoutMs.toInt())
		}
		return RestClient.builder()
			.baseUrl(wordPressProperties.baseUrl.trimEnd('/') + wordPressProperties.restPath)
			.requestFactory(factory)
			.build()
	}

	/** Root WP client (no /wp-json) — content CSS bundle endpoint. */
	@Bean
	fun wordPressRootClient(): RestClient {
		val factory = SimpleClientHttpRequestFactory().apply {
			setConnectTimeout(wordPressProperties.connectTimeoutMs.toInt())
			setReadTimeout(wordPressProperties.readTimeoutMs.toInt())
		}
		return RestClient.builder()
			.baseUrl(wordPressProperties.baseUrl.trimEnd('/'))
			.requestFactory(factory)
			.build()
	}

	@Bean
	fun cacheManager(): CacheManager {
		val manager = CaffeineCacheManager(
			"articles",
			"article",
			"articleHtml",
			"categories",
			"featured",
			"contentStyles",
			"contentScripts",
		)
		manager.setCaffeine(
			Caffeine.newBuilder()
				.expireAfterWrite(newsProperties.cacheTtlSeconds, TimeUnit.SECONDS)
				.maximumSize(500),
		)
		return manager
	}

	@Bean
	fun corsConfigurer(): WebMvcConfigurer {
		return object : WebMvcConfigurer {
			override fun addCorsMappings(registry: CorsRegistry) {
				// Public BFF only — private/internal must not be browser-callable cross-origin
				registry.addMapping("/public/news/**")
					.allowedOriginPatterns(
						"https://*.metaticket.vn",
						"https://*.metaticket.com.vn",
						"https://admin.devlocal.metaticket.vn",
						"https://devlocal.metaticket.vn",
						"http://localhost:*",
						"http://127.0.0.1:*",
					)
					.allowedMethods("GET", "OPTIONS")
					.allowedHeaders("*")
					.allowCredentials(false)
					.maxAge(3600)
			}
		}
	}
}
