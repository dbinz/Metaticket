package metaticket.show.cloudflare

import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.application.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import metaticket.common.logger.Logging
import metaticket.show.pojo.db.ShowDocument

class CloudflareClient(
    env: ApplicationEnvironment,
    private val httpClient: HttpClient
) : Logging {
    private val baseUrl = env.config.property("cloudflare.baseUrl").getString()
    private val bearerToken = env.config.property("cloudflare.bearerToken").getString()
    private val zoneId = env.config.property("cloudflare.zoneId").getString()
    private val fileBaseUrl = env.config.property("cloudflare.fileBaseUrl").getString()
    private val tags = try {
        env.config.property("cloudflare.tags").getList()
    } catch (e: Exception) {
        emptyList()
    }

    // Construct the full URL for purging cache
    private val purgeUrl = "${baseUrl}${zoneId}/purge_cache"

    suspend fun purgeCache(show: ShowDocument) {
        val files = mutableListOf<String>()
        files.add("api/public/show/fetch?id=${show.id}")
        files.add("api/public/show/fetch/all")
        files.add("api/public/show/sessions/fetch?showId=${show.id}")

        show.sessions.forEach { session ->
            files.add("api/public/show/session/fetch?showId=${show.id}&sessionId=${session.id}")
        }
        if (files.isEmpty() && tags.isEmpty()) return

        try {
            val response = withContext(Dispatchers.IO) {
                httpClient.post(purgeUrl) {
                    headers {
                        append(HttpHeaders.Authorization, "Bearer $bearerToken")
                        contentType(ContentType.Application.Json)
                    }
                    val body = mutableMapOf<String, Any>()
                    if (files.isNotEmpty()) {
                        body["files"] = files.map { "${fileBaseUrl}$it" }
                    }
                    if (tags.isNotEmpty()) {
                        body["tags"] = tags
                    }
                    setBody(body)
                }
            }

            if (response.status.isSuccess()) {
                logger.info("Successfully purged cache for files: $files and tags: $tags")
            } else {
                logger.error("Failed to purge cache. Status: ${response.status}, Body: ${response.bodyAsText()}")
            }
        } catch (e: Throwable) {
            logger.error("Exception while purging cache for files: $files and tags: $tags", e)
        }
    }
}
