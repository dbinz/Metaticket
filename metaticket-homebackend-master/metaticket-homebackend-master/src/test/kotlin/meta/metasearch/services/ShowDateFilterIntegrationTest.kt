package meta.metasearch.services

import meta.metasearch.models.Show
import meta.metasearch.models.ShowSession
import meta.metasearch.repositories.ShowRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.data.domain.PageRequest
import org.springframework.data.elasticsearch.client.elc.ElasticsearchTemplate
import org.springframework.data.elasticsearch.core.query.IndexQueryBuilder
import java.time.LocalDate
import java.time.ZoneId

/**
 * Bộ lọc ngày của `/filter` chạy trên Elasticsearch thật (không mock được ngữ nghĩa nested/includeInParent).
 * Cần ES local, ví dụ `docker run -p 127.0.0.1:49200:9200 -e discovery.type=single-node -e xpack.security.enabled=false
 * docker.elastic.co/elasticsearch/elasticsearch:8.12.0`; host/port ép về localhost để không bao giờ chạm ES staging.
 */
@SpringBootTest(
    properties = [
        "elasticsearch.host=127.0.0.1",
        "elasticsearch.port=\${METASEARCH_TEST_ES_PORT:49200}",
        "elasticsearch.show.index.name=it_show_date_filter",
    ]
)
class ShowDateFilterIntegrationTest {

    @Autowired
    private lateinit var service: ShowServiceWithRepository

    @Autowired
    private lateinit var repository: ShowRepository

    @Autowired
    private lateinit var template: ElasticsearchTemplate

    private val zone = ZoneId.of("Asia/Ho_Chi_Minh")
    private val hour = 3_600_000L

    private fun at(month: Int, day: Int): Long = LocalDate.of(2027, month, day).atStartOfDay(zone).toInstant().toEpochMilli()

    private fun session(id: String, start: Long, end: Long, type: String = "SINGLE") = ShowSession(
        id = id, status = "Open", startTime = start, endTime = end, isFree = false, sessionType = type,
    )

    private fun show(id: String, vararg sessions: ShowSession) = Show(
        id = id, eventCategoryCode = "music", createdAt = 0, sessions = sessions.toList(), startTime = sessions.minOf { it.startTime },
    )

    private fun filterJune(): Set<String> = service
        .searchShowWithOffset(PageRequest.of(0, 60), "music", emptyList(), at(6, 1), at(6, 30) + 24 * hour - 1, null)
        .content.map { it.id }.toSet()

    @BeforeEach
    fun clean() {
        repository.deleteAll()
    }

    @Test
    fun `two single sessions before and after the range do not match across sessions`() {
        repository.save(show("jan-dec", session("a", at(1, 10), at(1, 10) + 3 * hour), session("b", at(12, 10), at(12, 10) + 3 * hour)))

        assertEquals(emptySet<String>(), filterJune())
    }

    @Test
    fun `a calendar session spanning the range matches, a single session inside the range matches`() {
        repository.save(show("calendar", session("c", at(5, 1), at(7, 31), type = "CALENDAR")))
        repository.save(show("inside", session("d", at(6, 15), at(6, 15) + 3 * hour)))
        repository.save(show("outside", session("e", at(8, 1), at(8, 1) + 3 * hour)))

        assertEquals(setOf("calendar", "inside"), filterJune())
    }

    @Test
    fun `sessions without endTime still filter by startTime`() {
        // endTime = 0: giá trị mặc định khi service.show không gửi endTime.
        repository.save(show("zero-in", session("f", at(6, 20), 0)))
        repository.save(show("zero-out", session("g", at(1, 10), 0), session("h", at(12, 10), 0)))
        // Doc cũ không có field endTime trong session.
        template.index(
            IndexQueryBuilder().withId("missing-in").withSource(
                """{"id":"missing-in","eventCategoryCode":"music","createdAt":0,"showInHome":true,"startTime":${at(6, 5)},
                   "sessions":[{"id":"i","status":"Open","startTime":${at(6, 5)},"isFree":false,"showInHome":true}]}"""
            ).build(),
            template.getIndexCoordinatesFor(Show::class.java),
        )
        template.indexOps(Show::class.java).refresh()

        assertEquals(setOf("zero-in", "missing-in"), filterJune())
    }
}
