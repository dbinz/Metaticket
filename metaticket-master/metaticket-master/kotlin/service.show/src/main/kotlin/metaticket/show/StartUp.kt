package metaticket.show

import io.grpc.Server
import io.ktor.client.*
import io.ktor.events.*
import io.ktor.server.application.*
import metaticket.common.logger.Logging

class StartUp constructor(
    private val monitor: Events,
    private val server: Server,
    private val httpClient: HttpClient,
): Logging {
    fun start() {
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
            server
        } catch (e: Throwable) {
            logger.error("Failed to start gRPC server: ", e)
            // Stop Application when grpc server cannot start
            throw e
        }

        monitor.subscribe(ApplicationStopped) {
            try {
                httpClient.close()
                logger.info("HttpClient closed")
            } catch (e: Throwable) {
                logger.error("Failed to close HttpClient: ", e)
            }
        }
    }
}