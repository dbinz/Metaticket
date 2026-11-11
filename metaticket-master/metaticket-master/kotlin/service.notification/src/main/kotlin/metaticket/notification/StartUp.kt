package metaticket.notification

import io.grpc.Server
import io.ktor.client.*
import io.ktor.events.*
import io.ktor.server.application.*
import metaticket.common.logger.Logging
import metaticket.notification.plugin.template.service.PluginMailTemplateService
import metaticket.notification.plugin.template.service.PluginZNSTemplateService
import metaticket.notification.processor.SendMailProcessor
import metaticket.notification.processor.ZaloMessageProcessor

/**
 * This class is responsible for starting and stopping the application's components,
 * including the gRPC server, HTTP client, and message processors.
 */
class StartUp constructor(
    private val monitor: Events,
    private val server: Server,
    private val httpClient: HttpClient,
    private val sendMailProcessor: SendMailProcessor,
    private val zaloMessageProcessor: ZaloMessageProcessor,
    private val pluginMailTemplateService: PluginMailTemplateService,
    private val pluginZNSTemplateService: PluginZNSTemplateService,
    ): Logging {

    /**
     * Starts all the application components and subscribes to the ApplicationStopPreparing event
     * to ensure graceful shutdown.
     */
    fun start() {
        try {
            pluginMailTemplateService.loadJar()
            sendMailProcessor.start()
            logger.info("send mail processor started")

            monitor.subscribe(ApplicationStopped) {
                try {
                    sendMailProcessor.stop()
                    logger.info("send mail processor stopped")
                } catch (e: Throwable) {
                    logger.error("Failed to stop send mail processor: ", e)
                }
            }
        } catch (t: Throwable) {
            logger.error("fail to start send mail processor ...", t)
            throw t
        }

        try {
            pluginZNSTemplateService.loadJar()
            zaloMessageProcessor.start()
            logger.info("zalo message processor started")

            monitor.subscribe(ApplicationStopped) {
                try {
                    zaloMessageProcessor.stop()
                    logger.info("zalo message processor stopped")
                } catch (e: Throwable) {
                    logger.error("Failed to stop zalo message processor: ", e)
                }
            }
        } catch (t: Throwable) {
            logger.error("fail to start zalo message processor ...", t)
            throw t
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

        monitor.subscribe(ApplicationStopped) {
            try {
                httpClient.close()
                logger.info("httpClient closed")
            } catch (e: Throwable) {
                logger.error("Failed to close httpClient: ", e)
            }
        }
    }
}