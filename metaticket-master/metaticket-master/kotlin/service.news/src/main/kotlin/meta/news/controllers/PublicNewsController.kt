package meta.news.controllers

import meta.news.config.NewsProperties
import meta.news.models.NewsCategoryItem
import meta.news.models.NewsDetail
import meta.news.models.NewsHighlightPage
import meta.news.models.NewsOffsetPage
import meta.news.models.NewsSearchPage
import meta.news.services.NewsRenderService
import meta.news.services.NewsService
import org.springframework.http.CacheControl
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.util.concurrent.TimeUnit

/**
 * Public customer news APIs — paths/DTOs match MetaTicket-Home News_API_Datatypes_Samples.
 */
@RestController
@RequestMapping("/public/news")
class PublicNewsController(
	private val newsService: NewsService,
	private val newsRenderService: NewsRenderService,
	private val newsProperties: NewsProperties,
) {

	/** Proxies WP `?mt_content_styles=1` — theme + active plugin CSS for article HTML. */
	@GetMapping("/content-styles", produces = [MediaType.TEXT_PLAIN_VALUE, "text/css"])
	fun getContentStyles(): ResponseEntity<String> {
		val css = newsService.getContentStyles()
		return ResponseEntity.ok()
			.contentType(MediaType.parseMediaType("text/css;charset=UTF-8"))
			.cacheControl(CacheControl.maxAge(5, TimeUnit.MINUTES).cachePublic())
			.body(css)
	}

	/** Proxies WP `?mt_content_scripts=1` — ordered plugin scripts + localized inline JS. */
	@GetMapping("/content-scripts", produces = [MediaType.APPLICATION_JSON_VALUE])
	fun getContentScripts(): ResponseEntity<String> {
		val json = newsService.getContentScriptsJson()
		return ResponseEntity.ok()
			.contentType(MediaType.APPLICATION_JSON)
			.cacheControl(CacheControl.maxAge(5, TimeUnit.MINUTES).cachePublic())
			.body(json)
	}

	@GetMapping("/category/fetch/all")
	fun getNewsCategoryList(): List<NewsCategoryItem> {
		return newsService.getNewsCategoryList()
	}

	/** Offset list; [excludeSlug] drops the article currently open. */
	@GetMapping("/fetch/all")
	fun getNewsList(
		@RequestParam(defaultValue = "0") offset: Int,
		@RequestParam(defaultValue = "9") size: Int,
		@RequestParam(required = false) categoryCode: String?,
		@RequestParam(defaultValue = "createdAt,desc") sort: String?,
		@RequestParam(required = false) excludeSlug: String?,
	): NewsOffsetPage {
		return newsService.getNewsList(offset, size, categoryCode, sort, excludeSlug)
	}

	/** Global pins 1..3 for “Bài viết nổi bật”. */
	@GetMapping("/fetch/featured")
	fun getFeaturedNews(
		@RequestParam(required = false) excludeSlug: String?,
	): NewsHighlightPage {
		return newsService.getFeaturedNews(excludeSlug)
	}

	/** Related rail: tag overlap then newest same category. */
	@GetMapping("/fetch/related")
	fun getRelatedNews(
		@RequestParam slug: String,
		@RequestParam(defaultValue = "6") size: Int,
		@RequestParam(required = false) excludeSlug: String?,
	): NewsHighlightPage {
		return newsService.getRelatedNews(slug, size, excludeSlug)
	}

	@GetMapping("/fetch/slug")
	fun getNewsBySlug(
		@RequestParam slug: String,
	): NewsDetail {
		return newsService.getNewsBySlug(slug)
	}

	/** Renders a complete HTML page for an article by slug. */
	@GetMapping("/render/{slug}", produces = [MediaType.TEXT_HTML_VALUE])
	fun renderArticle(
		@PathVariable slug: String,
	): ResponseEntity<String> {
		val html = newsRenderService.renderArticle(slug)
		val ttl = newsProperties.renderCacheSeconds
		return ResponseEntity.ok()
			.contentType(MediaType.parseMediaType("text/html;charset=UTF-8"))
			.cacheControl(CacheControl.maxAge(ttl, TimeUnit.SECONDS).cachePublic())
			.body(html)
	}

	@GetMapping("/search")
	fun searchNews(
		@RequestParam keyword: String,
		@RequestParam page: Int,
		@RequestParam size: Int,
		@RequestParam(required = false) excludeSlug: String?,
	): NewsSearchPage {
		return newsService.searchNews(keyword, page, size, excludeSlug)
	}
}
