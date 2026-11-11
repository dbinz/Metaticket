package metaticket.admin

import io.grpc.Server
import io.ktor.server.application.*
import metaticket.common.logger.Logging

class StartUp constructor(
    private val monitor: io.ktor.events.Events,
    private val httpClient: io.ktor.client.HttpClient,
): Logging {
    fun start() {
        monitor.subscribe(ApplicationStopped) {
            try {
                httpClient.close()
                logger.info("http client closed")
            } catch (e: Throwable) {
                logger.error("Failed to close http client: ", e)
            }
        }
    }
}