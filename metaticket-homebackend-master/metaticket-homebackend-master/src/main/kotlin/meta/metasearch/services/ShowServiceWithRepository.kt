package meta.metasearch.services

import co.elastic.clients.elasticsearch._types.FieldValue
import co.elastic.clients.elasticsearch._types.SortOptions
import co.elastic.clients.elasticsearch._types.SortOrder
import co.elastic.clients.elasticsearch._types.ScriptSortType
import co.elastic.clients.elasticsearch._types.query_dsl.Operator
import co.elastic.clients.elasticsearch._types.query_dsl.Query
import co.elastic.clients.elasticsearch._types.query_dsl.QueryBuilders
import co.elastic.clients.elasticsearch._types.query_dsl.TermsQueryField
import co.elastic.clients.elasticsearch._types.query_dsl.TextQueryType
import co.elastic.clients.json.JsonData
import meta.metasearch.dtos.HighlightShowItem
import meta.metasearch.dtos.ShowListItem
import meta.metasearch.models.Show
import meta.metasearch.models.ShowSession
import meta.metasearch.repositories.ShowRepository
import org.springframework.data.domain.*
import org.springframework.data.elasticsearch.client.elc.ElasticsearchTemplate
import org.springframework.data.elasticsearch.client.elc.NativeQuery
import org.springframework.data.elasticsearch.core.SearchHitSupport
import org.springframework.data.elasticsearch.core.search
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.web.server.ResponseStatusException
import java.util.stream.Collectors


@Service
class ShowServiceWithRepository(private val showRepository: ShowRepository, private val elasticsearchTemplate: ElasticsearchTemplate) {

    companion object {
        /** Upper bound for `size` on any list endpoint (guards against `size=1_000_000` heap blowups). */
        const val MAX_PAGE_SIZE = 60

        /** Upper bound for `page`. Keeps `from + size` under Elasticsearch's default `max_result_window` (10000). */
        const val MAX_PAGE_NUMBER = 150

        /** Scroll batch size used by [syncAll] so the whole index is never held in memory at once. */
        private const val SYNC_BATCH_SIZE = 500

        /** First highlight page prefixes this many pin-board slots (pinOrder 1..N). Later pages skip them. */
        private const val HIGHLIGHT_PIN_TAKE = 3

        fun clampSize(size: Int): Int = size.coerceIn(1, MAX_PAGE_SIZE)

        fun clampPage(page: Int): Int = page.coerceIn(0, MAX_PAGE_NUMBER)

        fun safePageable(page: Int, size: Int): PageRequest = PageRequest.of(clampPage(page), clampSize(size))
    }

    fun createOrUpdateShowIndex(event: Show): Show {
        return showRepository.save(event)
    }

    /**
     * [includeRelevanceSort]: for keyword search only. `_score` is inserted right after the
     * future/past split so a better name/field match wins *within* the same tier, instead of the
     * date-based tiebreakers alone deciding order (which ignores how well the show matched at all).
     */
    private fun getCustomSort(includeRelevanceSort: Boolean = false): List<SortOptions> {
        val now = System.currentTimeMillis()

        // 1. Sort by Status (Active first)
        val statusSort = SortOptions.Builder().field { f -> f.field(Show::hasAllSessionsStopped.name).order(SortOrder.Asc) }.build()

        // Show có session vé theo lịch còn bán (ongoingUntil >= now) được coi như đang diễn ra dù ngày đầu đã qua.
        // containsKey: index tạo trước khi có field này không có mapping, đọc thẳng doc['ongoingUntil'] sẽ lỗi.
        val ongoing = "boolean ongoing = doc.containsKey('ongoingUntil') && doc['ongoingUntil'].size() > 0 && doc['ongoingUntil'].value >= params.now; "

        // 2. Sort by Future vs Past (Future first)
        val futureVsPastSort = SortOptions.Builder().script { s ->
            s.type(ScriptSortType.Number)
                .script { sc ->
                    sc.inline { i ->
                        i.source(ongoing + "if (doc['startTime'].value >= params.now || ongoing) { return 0; } else { return 1; }")
                            .params("now", JsonData.of(now))
                    }
                }
                .order(SortOrder.Asc)
        }.build()

        // 2b. Keyword relevance (search only)
        val relevanceSort = SortOptions.Builder().score { s -> s.order(SortOrder.Desc) }.build()

        // 3. Primary Sort Value:
        //    - Future: startTime ASC (Smallest startTime first)
        //    - Past: soldTicket DESC (Largest soldTicket first -> smallest negative value first)
        val primaryValueSort = SortOptions.Builder().script { s ->
            s.type(ScriptSortType.Number)
                .script { sc ->
                    sc.inline { i ->
                        i.source(ongoing + "if (ongoing && doc['startTime'].value < params.now) { return params.now; } " +
                            "if (doc['startTime'].value >= params.now) { return doc['startTime'].value; } else { return -doc['soldTicket'].value; }")
                            .params("now", JsonData.of(now))
                    }
                }
                .order(SortOrder.Asc)
        }.build()

        // 4. Secondary Sort Value for Past:
        //    - Future: 0 (Irrelevant as startTime is unique enough, but keeps it clean)
        //    - Past: startTime DESC (Largest startTime first -> smallest negative value first)
        val secondaryPastSort = SortOptions.Builder().script { s ->
            s.type(ScriptSortType.Number)
                .script { sc ->
                    sc.inline { i ->
                        i.source(ongoing + "if (doc['startTime'].value >= params.now || ongoing) { return 0; } else { return -doc['startTime'].value; }")
                            .params("now", JsonData.of(now))
                    }
                }
                .order(SortOrder.Asc)
        }.build()

        val sorts = mutableListOf(statusSort, futureVsPastSort)
        if (includeRelevanceSort) {
            sorts.add(relevanceSort)
        }
        sorts.add(primaryValueSort)
        sorts.add(secondaryPastSort)
        return sorts
    }

    /** Homepage banner: pinned shows only, ordered by admin pinOrder. Does not fill with unpinned/session rules. */
    fun getTopBanner(pageSize: Int = 10): Page<ShowListItem> {
        val size = clampSize(pageSize)
        val boolQuery = QueryBuilders.bool()
        boolQuery.must { x -> x.term { m -> m.field(Show::pinned.name).value(true) } }
        val searchQuery = NativeQuery.builder()
            .withQuery(Query(boolQuery.build()))
            .withPageable(PageRequest.of(0, MAX_PAGE_SIZE))
            .build()
        val searchHits = elasticsearchTemplate.search<Show>(searchQuery)
        val shows = searchHits.searchHits
            .map { it.content }
            .sortedWith(compareBy(nullsLast()) { it.pinOrder })
            .take(size)
        return toListItemPage(shows, size)
    }

    private fun toListItemPage(shows: List<Show>, size: Int): Page<ShowListItem> {
        val items = shows.map { ShowListItem.from(it) }
        return PageImpl(items, PageRequest.of(0, size), items.size.toLong())
    }

    private fun toListItemPage(page: Page<Show>): Page<ShowListItem> = page.map { ShowListItem.from(it) }

    fun searchShowWithOffset(
            pageable: Pageable,
            eventCategoryCode: String,
            cities: List<String>,
            startTime: Long?,
            endTime: Long?,
            isFree: Boolean?
    ): Page<ShowListItem> {
        val customSort = getCustomSort()
        val boolQuery = QueryBuilders.bool()
        boolQuery.must { x -> x.term { m -> m.field(Show::showInHome.name).value(true) } }
        boolQuery.must { x -> x.term { m -> m.field("${Show::sessions.name}.${ShowSession::showInHome.name}").value(true) } }

        boolQuery.must { x -> x.term { m -> m.field(Show::eventCategoryCode.name).value(eventCategoryCode) } }

        // Apply filters conditionally
        if (cities.isNotEmpty()) {
            val lowercaseCities = cities.map { it.lowercase() }
            val venueQueryBuilder = TermsQueryField.Builder()
                    .value(lowercaseCities.stream().map(FieldValue::of).collect(Collectors.toList()))
                    .build()
            boolQuery.must { x -> x.terms { term -> term.field("${Show::sessions.name}.${ShowSession::cityCode.name}").terms(venueQueryBuilder) } }
        }

        if (startTime != null && endTime != null) {
            val sessionStart = "${Show::sessions.name}.${ShowSession::startTime.name}"
            val sessionEnd = "${Show::sessions.name}.${ShowSession::endTime.name}"
            // Suất bắt đầu trong khoảng lọc, hoặc khoảng [startTime, endTime] của suất giao với khoảng lọc — để vé theo
            // lịch kéo dài nhiều tháng vẫn hiện khi lọc một ngày nằm giữa lịch.
            // Bọc nested: hai range start/end phải đúng trên CÙNG một suất; ở doc cha (includeInParent) chúng bị làm phẳng
            // nên suất 10/01 + suất 10/12 sẽ khớp chéo khi lọc tháng 6. Suất thiếu endTime (0/không có) vẫn khớp nhánh đầu.
            boolQuery.must { x ->
                x.nested { n ->
                    n.path(Show::sessions.name).query { q ->
                        q.bool { b ->
                            b.should { s -> s.range { m -> m.field(sessionStart).gte(JsonData.of(startTime)).lte(JsonData.of(endTime)) } }
                            b.should { s ->
                                s.bool { overlap ->
                                    overlap.must { m -> m.range { r -> r.field(sessionStart).lte(JsonData.of(endTime)) } }
                                    overlap.must { m -> m.range { r -> r.field(sessionEnd).gte(JsonData.of(startTime)) } }
                                }
                            }
                            b.minimumShouldMatch("1")
                        }
                    }
                }
            }
        }

        if (isFree != null) {
            boolQuery.must { x -> x.term { m -> m.field("${Show::sessions.name}.${ShowSession::isFree.name}").value(isFree) } }
        }

        // Clamp page/size to keep `from + size` bounded
        val safePageable = safePageable(pageable.pageNumber, pageable.pageSize)

        val searchQuery = NativeQuery.builder()
                .withQuery(Query(boolQuery.build()))
                .withPageable(safePageable)
                .withSort(customSort)
                .build()

        val searchHits = elasticsearchTemplate.search<Show>(searchQuery)
        val page = SearchHitSupport.searchPageFor(searchHits, searchQuery.pageable)
        @Suppress("UNCHECKED_CAST")
        return toListItemPage(SearchHitSupport.unwrapSearchHits(page) as Page<Show>)
    }

    fun searchByKeyword(
            keyword: String, pageable: Pageable,
            nameOnly: Boolean = false,
    ): Page<ShowListItem> {
        val customSort = getCustomSort(includeRelevanceSort = true)
        val boolQuery = QueryBuilders.bool()

        val lowerKeyword = keyword.trim().lowercase()
        // Fields to search, boosted (`^N`) toward the most identifying ones so `_score` (see
        // getCustomSort) actually distinguishes a close match from a tangential one. `status` and
        // `eventCategoryCode` were dropped — they're internal enum-like values (e.g. "active"),
        // not something a user searches for, and matching them pulled in unrelated shows.
        val fieldsToSearch = listOf(
            "${Show::showName.name}^5",
            "${Show::code.name}^4",
            Show::eventCategory.name,
            "${Show::sessions.name}.${ShowSession::venue.name}^2",
            "${Show::sessions.name}.${ShowSession::sessionName.name}",
            "${Show::sessions.name}.${ShowSession::cityName.name}^2",
            "${Show::sessions.name}.${ShowSession::cityCode.name}",
            // HTML-stripped plain text of `description` (see Show.descriptionText). Needs the ES
            // mapping's search_analyzer updated and the index backfilled via /sync-data first;
            // until then this field is empty/absent on existing docs and simply contributes nothing.
            "${Show::descriptionText.name}^0.5",
        )
        boolQuery.must { x -> x.term { m -> m.field(Show::showInHome.name).value(true) } }
        boolQuery.must { x -> x.term { m -> m.field("${Show::sessions.name}.${ShowSession::showInHome.name}").value(true) } }
        // AND (not the multiMatch default OR): every token in the keyword must match somewhere,
        // otherwise the edge-ngram autocomplete analyzer (min_gram 1) turns any short query into an
        // almost-unfiltered match over the whole catalog.
        // cross_fields (not best_fields): groups fields by search analyzer and ANDs within the
        // group, so a keyword whose words are split across fields (e.g. show name + venue city)
        // still matches — best_fields requires every word to land in a single field.
        boolQuery.must { x ->
            if (nameOnly) {
                // The storefront name search must not pick up words from descriptions or cities.
                // Keep the existing folded search analyzer and indexed prefixes for Vietnamese typing.
                x.match { m ->
                    m.field(Show::showName.name).query(lowerKeyword).operator(Operator.And)
                }
            } else {
                x.multiMatch { m ->
                    m.fields(fieldsToSearch).query(lowerKeyword).operator(Operator.And).type(TextQueryType.CrossFields)
                }
            }
        }

        // Clamp page/size to keep `from + size` bounded
        val safePageable = safePageable(pageable.pageNumber, pageable.pageSize)

        val searchQuery = NativeQuery.builder()
                .withQuery(Query(boolQuery.build()))
                .withPageable(safePageable)
                .withSort(customSort)
                .build()

        val searchHits = elasticsearchTemplate.search<Show>(searchQuery)
        val page = SearchHitSupport.searchPageFor(searchHits, searchQuery.pageable)
        @Suppress("UNCHECKED_CAST")
        return toListItemPage(SearchHitSupport.unwrapSearchHits(page) as Page<Show>)
    }

    /**
     * Highlight grid for homepage, search, and event-detail.
     * Virtual list = pinOrder 1..[HIGHLIGHT_PIN_TAKE] (board order) then the usual highlight sort,
     * excluding those pin slots so they are not repeated on later pages.
     */
    fun getHighlightShows(cities: List<String>, pageable: PageRequest): Page<HighlightShowItem> {
        val safePageable = safePageable(pageable.pageNumber, pageable.pageSize)
        val page = safePageable.pageNumber
        val size = safePageable.pageSize
        val prefix = loadHighlightPinPrefix(HIGHLIGHT_PIN_TAKE)
        val tailQuery = highlightTailQuery(cities)
        val tailTotal = elasticsearchTemplate.count(
            NativeQuery.builder().withQuery(tailQuery).build(),
            Show::class.java,
        )
        val total = prefix.size + tailTotal
        val start = page.toLong() * size
        if (start >= total) {
            return PageImpl(emptyList(), safePageable, total)
        }

        val shows = mutableListOf<Show>()
        if (start < prefix.size) {
            shows.addAll(prefix.drop(start.toInt()).take(size))
        }
        val remaining = size - shows.size
        if (remaining > 0) {
            val tailFrom = (start - prefix.size).coerceAtLeast(0).toInt()
            shows.addAll(searchHighlightTail(tailQuery, tailFrom, remaining))
        }
        return PageImpl(shows.map { HighlightShowItem.from(it) }, safePageable, total)
    }

    /** Pin-board shows occupying slots 1..[take], in pinOrder. Not filtered by showInHome. */
    private fun loadHighlightPinPrefix(take: Int): List<Show> {
        val boolQuery = QueryBuilders.bool()
        boolQuery.must { x -> x.term { m -> m.field(Show::pinned.name).value(true) } }
        val searchQuery = NativeQuery.builder()
            .withQuery(Query(boolQuery.build()))
            .withPageable(PageRequest.of(0, MAX_PAGE_SIZE))
            .build()
        val searchHits = elasticsearchTemplate.search<Show>(searchQuery)
        return searchHits.searchHits
            .map { it.content }
            .filter { val order = it.pinOrder; order != null && order in 1..take }
            .sortedBy { it.pinOrder }
            .take(take)
    }

    /** Same highlight filters as before, minus shows already used as the pin prefix. */
    private fun highlightTailQuery(cities: List<String>): Query {
        val boolQuery = QueryBuilders.bool()
        boolQuery.must { x -> x.term { m -> m.field(Show::showInHome.name).value(true) } }
        boolQuery.must { x -> x.term { m -> m.field("${Show::sessions.name}.${ShowSession::showInHome.name}").value(true) } }
        boolQuery.mustNot { x ->
            x.range { m -> m.field(Show::pinOrder.name).lte(JsonData.of(HIGHLIGHT_PIN_TAKE)) }
        }
        if (cities.isNotEmpty()) {
            val lowercaseCities = cities.map { it.lowercase() }
            val venueQueryBuilder = TermsQueryField.Builder()
                .value(lowercaseCities.stream().map(FieldValue::of).collect(Collectors.toList()))
                .build()
            boolQuery.must { x ->
                x.terms { term ->
                    term.field("${Show::sessions.name}.${ShowSession::cityCode.name}").terms(venueQueryBuilder)
                }
            }
        }
        return Query(boolQuery.build())
    }

    /** Slice of the highlight tail at [from] (not an ES page index). */
    private fun searchHighlightTail(tailQuery: Query, from: Int, size: Int): List<Show> {
        val searchQuery = NativeQuery.builder()
            .withQuery(tailQuery)
            .withPageable(OffsetLimit(from, size))
            .withSort(getCustomSort())
            .build()
        val searchHits = elasticsearchTemplate.search<Show>(searchQuery)
        return searchHits.searchHits.map { it.content }
    }

    fun updateSoldTicketShow(id: String, soldTicket: Long): Show {
        val show = showRepository.findById(id).orElseThrow {
            ResponseStatusException(HttpStatus.NOT_FOUND, "Show not found")
        }
        show.soldTicket = soldTicket
        return showRepository.save(show)
    }

    /**
     * Re-derives and re-saves every indexed show in bounded batches.
     *
     * Uses a scroll-backed stream ([ElasticsearchTemplate.searchForStream]) so the whole index is
     * never materialized in memory (unlike the old `findAll().toList()` which loaded everything at
     * once and was a prime OOM source). The scroll runs against a point-in-time snapshot, so the
     * in-place re-saves below do not disturb iteration. Returns the number of shows processed.
     */
    fun syncAll(transform: (Show) -> Show): Int {
        val query = NativeQuery.builder()
            .withQuery(Query.of { q -> q.matchAll { it } })
            .withPageable(PageRequest.of(0, SYNC_BATCH_SIZE))
            .build()

        var total = 0
        val batch = ArrayList<Show>(SYNC_BATCH_SIZE)
        elasticsearchTemplate.searchForStream(query, Show::class.java).use { stream ->
            stream.forEach { hit ->
                batch.add(transform(hit.content))
                if (batch.size >= SYNC_BATCH_SIZE) {
                    showRepository.saveAll(batch)
                    total += batch.size
                    batch.clear()
                }
            }
        }
        if (batch.isNotEmpty()) {
            showRepository.saveAll(batch)
            total += batch.size
        }
        return total
    }

}

/** Pageable whose offset is not required to be a multiple of [limit] (pin-prefix + tail paging). */
private class OffsetLimit(private val offset: Int, private val limit: Int) : Pageable {
    override fun getPageNumber(): Int = if (limit == 0) 0 else offset / limit
    override fun getPageSize(): Int = limit
    override fun getOffset(): Long = offset.toLong()
    override fun getSort(): Sort = Sort.unsorted()
    override fun next(): Pageable = OffsetLimit(offset + limit, limit)
    override fun previousOrFirst(): Pageable =
        if (hasPrevious()) OffsetLimit((offset - limit).coerceAtLeast(0), limit) else this
    override fun first(): Pageable = OffsetLimit(0, limit)
    override fun withPage(pageNumber: Int): Pageable = OffsetLimit(pageNumber * limit, limit)
    override fun hasPrevious(): Boolean = offset > 0
}
