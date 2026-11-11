package metaticket.customer.auth

import io.ktor.client.*
import io.ktor.server.application.*
import metaticket.common.logger.Logging
import metaticket.common.redis.RedisClusterManager

class StartUp constructor(
    private val monitor: io.ktor.events.Events,
    private val httpClient: HttpClient,
    private val redisClusterManager: RedisClusterManager<String>,
): Logging {
    fun start() {
        try {
            redisClusterManager.connect()
            logger.info("Redis cluster connection established")

            monitor.subscribe(ApplicationStopped) {
                try {
                    redisClusterManager.shutdown()
                    logger.info("Redis cluster connection closed")
                } catch (e: Throwable) {
                    logger.error("Failed to close Redis cluster connection: ", e)
                }
            }
        } catch (t: Throwable) {
            logger.error("Failed to connect to Redis cluster: ${t.message}")
            // Stop Application when connect to Redis failed.
            throw t
        }

        monitor.subscribe(ApplicationStopped){
            try {
                httpClient.close()
                logger.info("Http client closed")
            } catch (e: Throwable) {
                logger.error("Failed to close http client: ", e)
            }
        }
    }

}