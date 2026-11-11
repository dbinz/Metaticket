package meta.news.client

import org.slf4j.LoggerFactory
import org.springframework.core.ParameterizedTypeReference
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClient
import org.springframework.web.client.RestClientResponseException
import org.springframework.web.server.ResponseStatusException
import org.springframework.web.util.UriBuilder
import java.net.URI

data class WpPageResult<T>(
	val items: List<T>,
	val total: Long,
	val totalPages: Int,
)

data class WpSort(
	val orderBy: String = "date",
	val order: String = "desc",
)

/**
 * Two REST clients on purpose: [wordPressPublicClient] (anonymous) serves every public read, so WP itself
 * refuses drafts/private/future posts and password-protected content even if a query param ever gets
 * smuggled in; [wordPressRestClient] (application password of the service user) is kept for the admin
 * pin board / editor flows that must see non-published posts or write.
 */
@Component
class WordPressClient(
	private val wordPressRestClient: RestClient,
	private val wordPressPublicClient: RestClient,
	private val wordPressRootClient: RestClient,
) {
	private val log = LoggerFactory.getLogger(javaClass)

	private val embed = "wp:featuredmedia,wp:term,author"

	/** Theme + active-plugin CSS bundle from mu-plugin `?mt_content_styles=1`. */
	fun fetchContentStyles(): String {
		return try {
			wordPressRootClient.get()
				.uri("/?mt_content_styles=1")
				.accept(MediaType.parseMediaType("text/css"), MediaType.TEXT_PLAIN, MediaType.ALL)
				.retrieve()
				.body(String::class.java)
				?: ""
		} catch (ex: RestClientResponseException) {
			log.error("WordPress content-styles failed status={} body={}", ex.statusCode.value(), ex.responseBodyAsString.take(200))
			throw ResponseStatusException(
				HttpStatus.BAD_GATEWAY,
				"WordPress content-styles failed (${ex.statusCode.value()})",
				ex,
			)
		}
	}

	/** Active-plugin (+ jQuery) script manifest from mu-plugin `?mt_content_scripts=1`. */
	fun fetchContentScriptsJson(): String {
		return try {
			wordPressRootClient.get()
				.uri("/?mt_content_scripts=1")
				.accept(MediaType.APPLICATION_JSON)
				.retrieve()
				.body(String::class.java)
				?: """{"scripts":[],"inline":[]}"""
		} catch (ex: RestClientResponseException) {
			log.error("WordPress content-scripts failed status={} body={}", ex.statusCode.value(), ex.responseBodyAsString.take(200))
			throw ResponseStatusException(
				HttpStatus.BAD_GATEWAY,
				"WordPress content-scripts failed (${ex.statusCode.value()})",
				ex,
			)
		}
	}

	/** Public list with WP-native offset (FE getNewsList). */
	fun listPostsOffset(
		offset: Int,
		perPage: Int,
		categoryId: Long? = null,
		search: String? = null,
		sort: WpSort = WpSort(),
	): WpPageResult<WpPost> {
		val query = listOf(
			"offset" to offset.coerceAtLeast(0),
			"per_page" to perPage.coerceIn(1, 100),
			"status" to "publish",
			"_embed" to embed,
			"orderby" to sort.orderBy,
			"order" to sort.order,
		) + searchAndCategory(search, categoryId)
		return getList(wordPressPublicClient, "/posts", query, object : ParameterizedTypeReference<List<WpPost>>() {})
	}

	fun listPosts(
		page: Int,
		perPage: Int,
		search: String? = null,
		categoryId: Long? = null,
		sort: WpSort = WpSort(),
	): WpPageResult<WpPost> {
		val query = listOf(
			"page" to page.coerceAtLeast(1),
			"per_page" to perPage.coerceIn(1, 100),
			"status" to "publish",
			"_embed" to embed,
			"orderby" to sort.orderBy,
			"order" to sort.order,
		) + searchAndCategory(search, categoryId)
		return getList(wordPressPublicClient, "/posts", query, object : ParameterizedTypeReference<List<WpPost>>() {})
	}

	/** Walk WP pages so pin ranking can see the full published set (news volume is small). */
	fun listAllPublished(categoryId: Long? = null): List<WpPost> {
		val all = mutableListOf<WpPost>()
		var page = 1
		while (page <= 20) {
			val result = listPosts(
				page = page,
				perPage = 100,
				categoryId = categoryId,
				sort = WpSort("modified", "desc"),
			)
			all += result.items
			if (result.items.isEmpty() || page >= result.totalPages) break
			page += 1
		}
		return all
	}

	/** All statuses so the pin board can still show a draft that was pinned then unpublished. */
	fun listAllAdmin(): List<WpPost> {
		val all = mutableListOf<WpPost>()
		var page = 1
		while (page <= 20) {
			val result = listPostsAdmin(page = page, perPage = 100)
			all += result.items
			if (result.items.isEmpty() || page >= result.totalPages) break
			page += 1
		}
		return all
	}

	fun listPostsAdmin(
		page: Int,
		perPage: Int,
		search: String? = null,
		categoryId: Long? = null,
	): WpPageResult<WpPost> {
		val query = listOf(
			"page" to page.coerceAtLeast(1),
			"per_page" to perPage.coerceIn(1, 100),
			"status" to "any",
			"_embed" to embed,
			"orderby" to "modified",
			"order" to "desc",
		) + searchAndCategory(search, categoryId)
		return getList(wordPressRestClient, "/posts", query, object : ParameterizedTypeReference<List<WpPost>>() {})
	}

	fun getPostBySlug(slug: String): WpPost? {
		val query = listOf(
			"slug" to normalizeSlug(slug),
			"status" to "publish",
			"_embed" to embed,
		)
		val result = getList(wordPressPublicClient, "/posts", query, object : ParameterizedTypeReference<List<WpPost>>() {})
		return result.items.firstOrNull()
	}

	fun getPostById(id: Long): WpPost? {
		return try {
			wordPressRestClient.get()
				.uri("/posts/$id?_embed=$embed")
				.retrieve()
				.body(WpPost::class.java)
		} catch (ex: RestClientResponseException) {
			if (ex.statusCode.value() == 404) null else throw ResponseStatusException(
				HttpStatus.BAD_GATEWAY,
				"WordPress request failed (${ex.statusCode.value()})",
				ex,
			)
		}
	}

	fun createPost(body: WpPostWrite): WpPost {
		return wordPressRestClient.post()
			.uri("/posts?_embed=$embed")
			.contentType(MediaType.APPLICATION_JSON)
			.body(body)
			.retrieve()
			.body(WpPost::class.java)!!
	}

	fun updatePost(id: Long, body: WpPostWrite): WpPost {
		return wordPressRestClient.put()
			.uri("/posts/$id?_embed=$embed")
			.contentType(MediaType.APPLICATION_JSON)
			.body(body)
			.retrieve()
			.body(WpPost::class.java)!!
	}

	/** PATCH-like PUT of REST extra fields only (`mt_pin_order`, `mt_category_pins`). */
	fun updatePostFields(id: Long, fields: Any): WpPost {
		return wordPressRestClient.put()
			.uri("/posts/$id?_embed=$embed")
			.contentType(MediaType.APPLICATION_JSON)
			.body(fields)
			.retrieve()
			.body(WpPost::class.java)!!
	}

	fun updateFeaturedPin(id: Long, pinOrder: Int?): WpPost =
		updatePostFields(id, WpFeaturedPinWrite(pinOrder = pinOrder))

	fun updateCategoryPins(id: Long, pins: Map<String, Int>): WpPost =
		updatePostFields(id, WpCategoryPinsWrite(categoryPins = pins))

	fun listCategories(): List<WpCategory> {
		val query = listOf("per_page" to 100, "hide_empty" to true)
		return getList(wordPressPublicClient, "/categories", query, object : ParameterizedTypeReference<List<WpCategory>>() {}).items
	}

	fun listCategoriesAdmin(): List<WpCategory> {
		val query = listOf("per_page" to 100, "hide_empty" to false)
		return getList(wordPressRestClient, "/categories", query, object : ParameterizedTypeReference<List<WpCategory>>() {}).items
	}

	/** Lowercase, trim, collapse spaces to hyphens for FE raw slug input. */
	fun normalizeSlug(raw: String): String {
		return raw.trim()
			.lowercase()
			.replace(Regex("\\s+"), "-")
			.replace(Regex("-+"), "-")
			.trim('-')
	}

	private fun searchAndCategory(search: String?, categoryId: Long?): List<Pair<String, Any>> {
		val extra = mutableListOf<Pair<String, Any>>()
		if (!search.isNullOrBlank()) extra += "search" to search.trim()
		if (categoryId != null && categoryId > 0) extra += "categories" to categoryId
		return extra
	}

	/**
	 * Every query value is bound as a URI template variable, which RestClient encodes strictly — so `&`, `=`,
	 * `+`, `#` or `{}` in user input (search keyword, slug) stay inside their own param. Never concatenate
	 * values into a URI string and hand that to `uri(String)`: it is re-parsed as a template and a keyword
	 * like `x&status=draft` turns into a second `status` param that WP (PHP: last value wins) obeys.
	 */
	private fun <T> getList(
		client: RestClient,
		path: String,
		query: List<Pair<String, Any>>,
		type: ParameterizedTypeReference<List<T>>,
	): WpPageResult<T> {
		return try {
			val response = client.get()
				.uri { builder -> buildUri(builder, path, query) }
				.retrieve()
				.toEntity(type)

			val items = response.body ?: emptyList()
			val headers = response.headers
			val total = headerLong(headers, "X-WP-Total")
			val totalPagesHeader = headerLong(headers, "X-WP-TotalPages").toInt()
			WpPageResult(
				items = items,
				total = total,
				totalPages = when {
					totalPagesHeader > 0 -> totalPagesHeader
					total == 0L -> 0
					else -> 1
				},
			)
		} catch (ex: RestClientResponseException) {
			// WP returns 400 rest_post_invalid_page_number when page is past the last page
			if (ex.statusCode.value() == 404 || isInvalidPageNumber(ex)) {
				WpPageResult(emptyList(), 0, 0)
			} else {
				log.error("WordPress request failed path={} params={} status={} body={}", path, query.map { it.first }, ex.statusCode.value(), ex.responseBodyAsString)
				throw ResponseStatusException(
					HttpStatus.BAD_GATEWAY,
					"WordPress request failed (${ex.statusCode.value()}): ${ex.responseBodyAsString.take(200)}",
					ex,
				)
			}
		}
	}

	private fun buildUri(builder: UriBuilder, path: String, query: List<Pair<String, Any>>): URI {
		builder.path(path)
		val values = HashMap<String, Any>()
		query.forEachIndexed { index, (name, value) ->
			builder.queryParam(name, "{q$index}")
			values["q$index"] = value
		}
		return builder.build(values)
	}

	/** Treat out-of-range pagination as an empty page instead of failing the BFF. */
	private fun isInvalidPageNumber(ex: RestClientResponseException): Boolean {
		if (ex.statusCode.value() != 400) return false
		return ex.responseBodyAsString.contains("rest_post_invalid_page_number")
	}

	private fun headerLong(headers: HttpHeaders, name: String): Long {
		return headers.getFirst(name)?.toLongOrNull() ?: 0L
	}
}
