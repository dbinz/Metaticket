package meta.news.controllers

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import meta.news.client.WordPressClient
import meta.news.config.NewsProperties
import meta.news.services.NewsMapper
import meta.news.services.NewsRenderService
import meta.news.services.NewsService
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter
import org.springframework.test.web.client.MockRestServiceServer
import org.springframework.test.web.client.match.MockRestRequestMatchers.method
import org.springframework.test.web.client.response.MockRestResponseCreators.withStatus
import org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess
import org.springframework.http.HttpMethod
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.web.client.RestClient

/** Exercises the real controller, service and WordPress client; only HTTP transport is stubbed. */
class NewsSearchContractTest {
    private lateinit var transport: MockRestServiceServer
    private lateinit var mvc: MockMvc

    @BeforeEach
    fun setUp() {
        val builder = RestClient.builder().baseUrl("http://wp.test/wp-json/wp/v2")
        transport = MockRestServiceServer.bindTo(builder).build()
        val publicClient = builder.build()
        val wp = WordPressClient(publicClient, publicClient, publicClient)
        val properties = NewsProperties()
        val controller = PublicNewsController(
            NewsService(wp, NewsMapper(properties)), mock(NewsRenderService::class.java), properties,
        )
        mvc = MockMvcBuilders.standaloneSetup(controller)
            .setMessageConverters(MappingJackson2HttpMessageConverter(jacksonObjectMapper())).build()
    }

    @Test
    fun `no matching published articles returns HTTP 200 and an empty page`() {
        transport.expect(method(HttpMethod.GET)).andRespond(
            withSuccess("[]", MediaType.APPLICATION_JSON).header("X-WP-Total", "0").header("X-WP-TotalPages", "0"),
        )
        mvc.perform(get("/public/news/search").param("keyword", "khongtontai").param("page", "0").param("size", "3"))
            .andExpect(status().isOk).andExpect(jsonPath("$.content").isEmpty)
            .andExpect(jsonPath("$.totalElements").value(0)).andExpect(jsonPath("$.totalPages").value(0))
            .andExpect(jsonPath("$.empty").value(true))
        transport.verify()
    }

    @Test
    fun `WordPress out of range page is an empty success`() {
        transport.expect(method(HttpMethod.GET)).andRespond(withStatus(HttpStatus.BAD_REQUEST)
            .contentType(MediaType.APPLICATION_JSON).body("""{"code":"rest_post_invalid_page_number"}"""))
        mvc.perform(get("/public/news/search").param("keyword", "van hoa").param("page", "5").param("size", "3"))
            .andExpect(status().isOk).andExpect(jsonPath("$.content").isEmpty)
            .andExpect(jsonPath("$.empty").value(true)).andExpect(jsonPath("$.number").value(5))
        transport.verify()
    }

    @Test
    fun `WordPress server failure stays an error rather than empty success`() {
        transport.expect(method(HttpMethod.GET)).andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR))
        mvc.perform(get("/public/news/search").param("keyword", "van hoa").param("page", "0").param("size", "3"))
            .andExpect(status().isBadGateway)
        transport.verify()
    }

    @Test
    fun `blank keyword remains a validation error`() {
        mvc.perform(get("/public/news/search").param("keyword", "  ").param("page", "0").param("size", "3"))
            .andExpect(status().isBadRequest)
        transport.verify()
    }
}
