package meta.metasearch.services

import meta.metasearch.models.Show
import meta.metasearch.models.ShowSession
import meta.metasearch.repositories.ShowRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.data.domain.PageRequest
import org.springframework.data.elasticsearch.client.elc.ElasticsearchTemplate
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import java.text.Normalizer
import java.util.UUID

/** Analyzer and cross-field semantics require a real, isolated local Elasticsearch index. */
@SpringBootTest(properties = [
    "elasticsearch.host=127.0.0.1",
    "elasticsearch.port=\${METASEARCH_TEST_ES_PORT:49200}",
])
@AutoConfigureMockMvc
class ShowKeywordSearchIntegrationTest {
    companion object {
        // A fresh index is essential: existing indices keep old settings after code changes.
        private val indexName = "it_search_show_keyword_${UUID.randomUUID().toString().replace("-", "")}"

        @JvmStatic
        @DynamicPropertySource
        fun indexProperties(registry: DynamicPropertyRegistry) {
            registry.add("elasticsearch.show.index.name") { indexName }
        }
    }

    @Autowired private lateinit var service: ShowServiceWithRepository
    @Autowired private lateinit var repository: ShowRepository
    @Autowired private lateinit var template: ElasticsearchTemplate
    @Autowired private lateinit var mvc: MockMvc

    @BeforeEach
    fun clean() {
        repository.deleteAll()
    }

    private fun show(id: String, name: String = "Tinh Hoa Bắc Bộ", description: String? = null,
                     venue: String = "", category: String = "", sessionName: String? = null,
                     code: String = "", visible: Boolean = true, sessionVisible: Boolean = true,
                     cityName: String? = null, cityCode: String? = null) = Show(
        id = id, showName = name, descriptionText = description, eventCategory = category,
        eventCategoryCode = "culture", code = code, createdAt = 0, startTime = 2_524_608_000_000,
        showInHome = visible, sessions = listOf(ShowSession(
            id = "session-$id", venue = venue, sessionName = sessionName, isFree = false,
            showInHome = sessionVisible, status = "Open", startTime = 2_524_608_000_000,
            cityName = cityName, cityCode = cityCode,
        )),
    )

    private fun seed(vararg shows: Show) {
        repository.saveAll(shows.toList())
        template.indexOps(Show::class.java).refresh()
    }

    private fun ids(keyword: String): Set<String> = service.searchByKeyword(keyword, PageRequest.of(0, 10))
        .content.map { it.id }.toSet()

    @ParameterizedTest
    @ValueSource(strings = ["tinh hoa", "tinh hoa bac bo", "TINH HOA BẮC BỘ", "tinh hoa bac b", "tinh hoa bắc bộ"])
    fun `show name matches accents case and unfinished words`(keyword: String) {
        seed(show("target"), show("noise", name = "Tinh Hoa Miền Nam"))
        val expected = if (keyword == "tinh hoa") setOf("target", "noise") else setOf("target")
        assertEquals(expected, ids(keyword))
    }

    @Test
    fun `combining Vietnamese marks match in both stored text and query`() {
        seed(show("target", name = Normalizer.normalize("Đêm Bắc Bộ", Normalizer.Form.NFD)))
        assertEquals(setOf("target"), ids("dem bac bo"))
        assertEquals(setOf("target"), ids(Normalizer.normalize("Đêm Bắc Bộ", Normalizer.Form.NFD)))
    }

    @Test
    fun `description folds accents without indexing partial words`() {
        seed(show("target", name = "Sân khấu", description = "Văn hóa đồng bằng"))
        assertEquals(setOf("target"), ids("san khau dong bang"))
        assertEquals(setOf("target"), ids("VĂN HÓA"))
        assertEquals(emptySet<String>(), ids("dong ba"))
    }

    @Test
    fun `AND joins show name with venue category and session name`() {
        seed(show("target", name = "Ánh Dương", venue = "Hồ Tây", category = "Văn hóa", sessionName = "Suất tối"))
        assertEquals(setOf("target"), ids("anh duong ho tay"))
        assertEquals(setOf("target"), ids("anh duong van hoa"))
        assertEquals(setOf("target"), ids("anh duong suat toi"))
        assertEquals(emptySet<String>(), ids("anh duong khongtontai"))
    }

    @Test
    fun `show codes preserve their distinguishing digits`() {
        seed(show("2026", name = "Một", code = "THBB2026"), show("2027", name = "Hai", code = "THBB2027"))
        assertEquals(setOf("2026"), ids("THBB2026"))
        assertEquals(setOf("2027"), ids("thbb2027"))
        assertEquals(setOf("2026", "2027"), ids("THBB202"))
    }

    @Test
    fun `location prefixes and city code filters keep their meaning`() {
        seed(show("target", name = "Mục tiêu", venue = "Hồ Tây", cityName = "Hà Nội", cityCode = "HN2026"),
            show("other-city", name = "Khác", cityCode = "HN2027"))
        assertEquals(setOf("target"), ids("ho ta"))
        assertEquals(setOf("target"), ids("ha no"))
        assertEquals(setOf("target"), ids("HN2026"))
        val filtered = service.searchShowWithOffset(PageRequest.of(0, 10), "culture", listOf("HN2026"), null, null, null)
        assertEquals(listOf("target"), filtered.content.map { it.id })
    }

    @Test
    fun `complete tokens longer than the ngram limit remain searchable`() {
        seed(show("target", code = "ABCDEFGHIJKLMNOPQRSTUV2026"))
        assertEquals(setOf("target"), ids("ABCDEFGHIJKLMNOPQRSTUV2026"))
    }

    @Test
    fun `hidden shows and hidden sessions stay excluded`() {
        seed(show("show-hidden", visible = false), show("session-hidden", sessionVisible = false), show("visible"))
        assertEquals(setOf("visible"), ids("tinh hoa bac bo"))
    }

    @Test
    fun `missing description does not prevent a name match`() {
        seed(show("target"))
        assertEquals(setOf("target"), ids("tinh hoa bac bo"))
    }

    @Test
    fun `HTML style and script content never supplies search terms`() {
        val description = SearchText.plain("<style>.noise { font-family: arial; }</style><script>trackingnoise()</script><p>Văn hóa</p>", 2000)
        seed(show("target", description = description))
        assertEquals(setOf("target"), ids("van hoa"))
        assertEquals(emptySet<String>(), ids("arial"))
        assertEquals(emptySet<String>(), ids("trackingnoise"))
    }

    @Test
    fun `a name match ranks ahead of a description match in the same business tier`() {
        seed(show("name", name = "Bắc Bộ"), show("description", name = "Khác", description = "Bắc Bộ"))
        val result = service.searchByKeyword("bac bo", PageRequest.of(0, 10))
        assertEquals(listOf("name", "description"), result.content.map { it.id })
    }

    @Test
    fun `suggestion with no matches returns HTTP 200 and an empty page`() {
        seed(show("target"))
        mvc.perform(get("/public/metasearch/shows/suggestion").param("keyword", "zzzxqvnonexistent").param("size", "5"))
            .andExpect(status().isOk).andExpect(jsonPath("$.content").isEmpty)
            .andExpect(jsonPath("$.totalElements").value(0)).andExpect(jsonPath("$.totalPages").value(0))
            .andExpect(jsonPath("$.empty").value(true))
    }

    @Test
    fun `a page past the end is empty but preserves the real total`() {
        seed(show("target"))
        mvc.perform(get("/public/metasearch/shows/suggestion").param("keyword", "tinh hoa").param("page", "1").param("size", "5"))
            .andExpect(status().isOk).andExpect(jsonPath("$.content").isEmpty)
            .andExpect(jsonPath("$.totalElements").value(1)).andExpect(jsonPath("$.empty").value(true))
    }

    @Test
    fun `missing keyword is an input error rather than empty success`() {
        mvc.perform(get("/public/metasearch/shows/suggestion")).andExpect(status().isBadRequest)
    }

    @ParameterizedTest
    @ValueSource(strings = ["Đêm nhạc", "DEM NHAC", "dem nha", "  đêm nhạc  "])
    fun `name search excludes matches supplied by other fields`(keyword: String) {
        seed(
            show("target", name = "Đêm nhạc mùa thu"),
            show("description", name = "Hòa nhạc cuối năm", description = "Đêm nhạc đặc biệt"),
            show("category", name = "Giai điệu thanh xuân", category = "Đêm nhạc"),
            show("city", name = "Workshop", cityName = "Đêm nhạc"),
            show("code", name = "Jazz bên sông", code = "LOCAL-DEMO-3", category = "Âm nhạc"),
        )
        val result = service.searchByKeyword(keyword, PageRequest.of(0, 10), nameOnly = true)
        assertEquals(listOf("target"), result.content.map { it.id })
    }

    @Test
    fun `name search endpoint keeps counts and empty results accurate`() {
        seed(show("target", name = "Workshop làm gốm"), show("noise", name = "Khác", description = "Workshop làm gốm"))
        mvc.perform(get("/public/metasearch/shows/suggestion")
            .param("keyword", "workshop lam gom").param("nameOnly", "true"))
            .andExpect(status().isOk).andExpect(jsonPath("$.totalElements").value(1))
            .andExpect(jsonPath("$.content[0].id").value("target"))
        mvc.perform(get("/public/metasearch/shows/suggestion")
            .param("keyword", "khongtontai").param("nameOnly", "true"))
            .andExpect(status().isOk).andExpect(jsonPath("$.totalElements").value(0))
            .andExpect(jsonPath("$.content").isEmpty)
    }
}
