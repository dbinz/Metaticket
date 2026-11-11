package metaticket.qrcode

import io.grpc.Server
import io.ktor.events.*
import io.ktor.server.application.*
import metaticket.common.logger.Logging

/**
 * Manages the startup and shutdown of the gRPC server.
 */
class StartUp constructor(
    private val monitor: Events,
    private val server: Server,
): Logging {
    /**
     *  Start the gRPC server and log the port it's running on.
     *  Also, subscribe to the ApplicationStopPreparing event to gracefully shut down the server when the application stops.
     *  If the server fails to start, log the error and rethrow the exception to stop the application.
     */
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
        } catch (e: Throwable) {
            logger.error("Failed to start gRPC server: ", e)
            // Stop Application when grpc server cannot start
            throw e
        }
    }
}