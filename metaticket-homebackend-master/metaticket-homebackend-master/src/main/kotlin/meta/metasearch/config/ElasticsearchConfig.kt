package meta.metasearch.config

import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.ComponentScan
import org.springframework.context.annotation.Configuration
import org.springframework.data.elasticsearch.client.ClientConfiguration
import org.springframework.data.elasticsearch.client.elc.ElasticsearchConfiguration
import org.springframework.data.elasticsearch.repository.config.EnableElasticsearchRepositories
import java.time.Duration


@Configuration
@EnableElasticsearchRepositories
@ComponentScan
class ElasticsearchConfig(
        @Value("\${elasticsearch.host}") private val esHost: String,
        @Value("\${elasticsearch.port}") private val esPort: Int
) : ElasticsearchConfiguration() {
    private val logger = LoggerFactory.getLogger(ElasticsearchConfig::class.java)

    override fun clientConfiguration(): ClientConfiguration {
        logger.info("Connecting to Elasticsearch at $esHost:$esPort")
        return ClientConfiguration.builder()
                .connectedTo("$esHost:$esPort")
                // Bound how long a request can tie up a worker thread. Without these, a slow/hung ES
                // lets requests (and their buffered responses) pile up in heap until OOM.
                .withConnectTimeout(Duration.ofSeconds(5))
                .withSocketTimeout(Duration.ofSeconds(30))
                .build()
    }
}