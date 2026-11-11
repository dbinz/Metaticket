package metaticket.customer.user

import io.grpc.Server
import io.ktor.client.HttpClient
import io.ktor.events.*
import io.ktor.server.application.*
import metaticket.common.logger.Logging
import metaticket.common.redis.RedisClusterManager

class StartUp constructor(
    private val monitor: Events,
    private val server: Server,
    private val httpClient: HttpClient,
    private val redisManager: RedisClusterManager<String>,
): Logging {
    fun start() {
        monitor.subscribe(ApplicationStopPreparing) {
            try {
                httpClient.close()
                logger.info("HttpClient closed.")
            } catch (e: Throwable) {
                logger.error("Failed to close HttpClient: ", e)
            }
        }
        try {
            redisManager.connect()
            logger.info("Redis cluster connection established.")

            monitor.subscribe(ApplicationStopped) {
                try {
                    redisManager.shutdown()
                    logger.info("Redis cluster connection closed.")
                } catch (e: Throwable) {
                    logger.error("Failed to close Redis connection: ", e)
                }
            }
        } catch (e: Throwable) {
            logger.error("Failed to connect to Redis cluster: ${e.message}")
            // Stop Application when connect to Redis failed.
            throw e
        }
        try {
            server.start()
            logger.info("gRPC server started on port ${server.port}")

            monitor.subscribe(ApplicationStopPreparing) {
                try {
                    server.shutdown()
                    logger.info("gRPC server stopped")
                } catch (e: Throwable) {
                    logger.error("Failed to stop gRPC server: ", e)
                }
            }
        } catch (e: Throwable) {
            logger.error("Failed to start gRPC server: ", e)
            // Stop Application when grpc server cannot start
            throw e
        }
    }
}