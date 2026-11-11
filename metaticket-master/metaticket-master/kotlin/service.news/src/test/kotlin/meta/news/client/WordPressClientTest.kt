package meta.news.client

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.springframework.http.HttpMethod
import org.springframework.http.MediaType
import org.springframework.test.web.client.MockRestServiceServer
import org.springframework.test.web.client.match.MockRestRequestMatchers.method
import org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess
import org.springframework.util.MultiValueMap
import org.springframework.web.client.RestClient
import org.springframework.web.util.UriComponentsBuilder
import org.springframework.web.util.UriUtils
import java.net.URI

/**
 * Regression for the news search param-injection (keyword `x&status=draft` used to add a second
 * `status` param that WP obeys). Uses real RestClient URI encoding; only the transport is mocked.
 */
class WordPressClientTest {

	private val base = "http://wp.test/wp-json/wp/v2"

	private val adminBuilder = RestClient.builder().baseUrl(base)
	private val publicBuilder = RestClient.builder().baseUrl(base)
	private val adminServer = MockRestServiceServer.bindTo(adminBuilder).build()
	private val publicServer = MockRestServiceServer.bindTo(publicBuilder).build()
	private val client = WordPressClient(
		wordPressRestClient = adminBuilder.build(),
		wordPressPublicClient = publicBuilder.build(),
		wordPressRootClient = RestClient.builder().baseUrl("http://wp.test").build(),
	)

	private fun expectList(server: MockRestServiceServer, captured: MutableList<URI>) {
		server.expect(method(HttpMethod.GET))
			.andExpect { request -> captured += request.uri }
			.andRespond(withSuccess("[]", MediaType.APPLICATION_JSON))
	}

	private fun decodedQuery(uri: URI): MultiValueMap<String, String> {
		val raw = UriComponentsBuilder.fromUri(uri).build(true).queryParams
		return UriComponentsBuilder.newInstance().apply {
			raw.forEach { (name, values) ->
				values.forEach { queryParam(UriUtils.decode(name, Charsets.UTF_8), UriUtils.decode(it, Charsets.UTF_8)) }
			}
		}.build().queryParams
	}

	@ParameterizedTest
	@ValueSource(
		strings = [
			"hello world",
			"a&status=draft",
			"x&status=private&per_page=100",
			"x{y}",
			"c++",
			"a#b",
			"50%",
			"tiếng việt",
		],
	)
	fun `public search keeps the keyword inside the search param`(keyword: String) {
		val captured = mutableListOf<URI>()
		expectList(publicServer, captured)

		client.listPosts(page = 1, perPage = 10, search = keyword)

		publicServer.verify()
		val uri = captured.single()
		assertEquals("/wp-json/wp/v2/posts", uri.rawPath)
		val query = decodedQuery(uri)
		assertEquals(listOf(keyword), query["search"])
		assertEquals(listOf("publish"), query["status"])
		assertEquals(listOf("10"), query["per_page"])
		assertEquals(null, uri.rawFragment)
	}

	@Test
	fun `offset listing is also injection-safe and anonymous`() {
		val captured = mutableListOf<URI>()
		expectList(publicServer, captured)

		client.listPostsOffset(offset = 0, perPage = 6, search = "x&status=future", categoryId = 7)

		publicServer.verify()
		val query = decodedQuery(captured.single())
		assertEquals(listOf("x&status=future"), query["search"])
		assertEquals(listOf("publish"), query["status"])
		assertEquals(listOf("7"), query["categories"])
	}

	@Test
	fun `slug lookup cannot override status`() {
		val captured = mutableListOf<URI>()
		expectList(publicServer, captured)

		client.getPostBySlug("A&status=draft")

		publicServer.verify()
		val query = decodedQuery(captured.single())
		assertEquals(listOf("a&status=draft"), query["slug"])
		assertEquals(listOf("publish"), query["status"])
	}

	@Test
	fun `public categories go through the anonymous client`() {
		val captured = mutableListOf<URI>()
		expectList(publicServer, captured)

		client.listCategories()

		publicServer.verify()
		assertEquals("/wp-json/wp/v2/categories", captured.single().rawPath)
	}

	@Test
	fun `admin listing keeps using the authenticated client`() {
		val captured = mutableListOf<URI>()
		expectList(adminServer, captured)

		client.listPostsAdmin(page = 1, perPage = 20, search = "x&status=publish")

		adminServer.verify()
		val query = decodedQuery(captured.single())
		assertEquals(listOf("any"), query["status"])
		assertEquals(listOf("x&status=publish"), query["search"])
	}
}
