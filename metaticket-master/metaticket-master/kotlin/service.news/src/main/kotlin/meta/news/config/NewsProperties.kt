package meta.news.config

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "wordpress")
data class WordPressProperties(
    var baseUrl: String = "http://localhost:8081",
    var restPath: String = "/wp-json/wp/v2",
    var connectTimeoutMs: Long = 3000,
    var readTimeoutMs: Long = 10000,
    var username: String = "",
    var applicationPassword: String = "",
)

@ConfigurationProperties(prefix = "news")
data class NewsProperties(
    var publicBaseUrl: String = "https://devlocal.metaticket.vn/news",
    var mediaBaseUrl: String = "https://news.devlocal.metaticket.vn",
    var frontendBaseUrl: String = "https://devlocal.metaticket.vn",
    var renderCacheSeconds: Long = 300,
    var cacheTtlSeconds: Long = 60,
)
