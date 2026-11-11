package meta.metasearch.repositories

import meta.metasearch.models.Show
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.elasticsearch.repository.ElasticsearchRepository
import org.springframework.stereotype.Repository

@Repository
interface ShowRepository : ElasticsearchRepository<Show, String>