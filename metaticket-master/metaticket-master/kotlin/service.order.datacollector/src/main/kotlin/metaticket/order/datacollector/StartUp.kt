package metaticket.order.datacollector

import io.grpc.Server
import io.ktor.events.Events
import metaticket.common.logger.Logging

class StartUp constructor(
    private val monitor: Events,
    private val server: Server,
): Logging {
    fun start() {
        try {
            server.start()
            logger.info("gRPC server started on port ${server.port}")

            monitor.subscribe(io.ktor.server.application.ApplicationStopPreparing) {
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
    }
}