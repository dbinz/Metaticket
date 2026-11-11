package metaticket.ticketslots

import io.grpc.Server
import io.ktor.client.HttpClient
import io.ktor.server.application.*
import metaticket.common.logger.Logging
import metaticket.common.redis.RedisClusterManager
import metaticket.ticketslots.kafka.*
import metaticket.ticketslots.dlq.service.QRCodeEventProducerRetryService
import metaticket.ticketslots.dlq.service.QRCodeEventConsumerRetryService
import metaticket.ticketslots.dlq.service.OrderSucceedEventRetryService

/**
 * Manages the startup and shutdown of the gRPC server and Kafka consumers/producers.
 */
class StartUp constructor(
    private val orderSucceedConsumer: OrderSucceedConsumer,
    private val generateTicketSlotQRCodeProducer: GenerateTicketSlotQRCodeProducer,
    private val generateTicketSlotQRCodeConsumer: GenerateTicketSlotQRCodeConsumer,
    private val server: Server,
    private val monitor: io.ktor.events.Events,
    private val httpClient: HttpClient,
    private val redisClusterManager: RedisClusterManager<String>,
    private val qrCodeEventProducerRetryService: QRCodeEventProducerRetryService,
    private val qrCodeEventConsumerRetryService: QRCodeEventConsumerRetryService,
    private val orderSucceedRetryService: OrderSucceedEventRetryService,
): Logging {

    /**
     * Starts the gRPC server and Kafka consumers, and subscribes to application stop events for graceful shutdown.
     */
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

        try {
            orderSucceedConsumer.start()
            logger.info("orderSucceedConsumer started")
            monitor.subscribe(ApplicationStopPreparing) {
                try {
                    orderSucceedConsumer.stop()
                } catch (e: Throwable) {
                    logger.error("Failed to stop orderSucceedConsumer: ", e)
                }
            }
        } catch (t: Throwable) {
            logger.error("Failed to start orderSucceedConsumer: ", t)
            throw t
        }

        try {
            generateTicketSlotQRCodeConsumer.start()
            logger.info("generateTicketSlotQRCodeConsumer started")
            try {
                monitor.subscribe(ApplicationStopPreparing) {
                    generateTicketSlotQRCodeConsumer.stop()
                }
            } catch (e: Throwable) {
                logger.error("Failed to stop generateTicketSlotQRCodeConsumer: ", e)
            }
        } catch (t: Throwable) {
            logger.error("Failed to start generateTicketSlotQRCodeConsumer: ", t)
            throw t
        }



        try {
            qrCodeEventProducerRetryService.initialize()
            qrCodeEventProducerRetryService.start()
            logger.info("qrCodeEventProducerRetryService started")
            monitor.subscribe(ApplicationStopPreparing) {
                try {
                    qrCodeEventProducerRetryService.stop()
                } catch (e: Throwable) {
                    logger.error("Failed to stop qrCodeEventProducerRetryService: ", e)
                }
            }
        } catch (t: Throwable) {
            logger.error("Failed to start qrCodeEventProducerRetryService: ", t)
            throw t
        }

        try {
            qrCodeEventConsumerRetryService.initialize()
            qrCodeEventConsumerRetryService.start()
            logger.info("qrCodeEventConsumerRetryService started")
            monitor.subscribe(ApplicationStopPreparing) {
                try {
                    qrCodeEventConsumerRetryService.stop()
                } catch (e: Throwable) {
                    logger.error("Failed to stop qrCodeEventConsumerRetryService: ", e)
                }
            }
        } catch (t: Throwable) {
            logger.error("Failed to start qrCodeEventConsumerRetryService: ", t)
            throw t
        }

        try {
            orderSucceedRetryService.initialize()
            orderSucceedRetryService.start()
            logger.info("orderSucceedRetryService started")
            monitor.subscribe(ApplicationStopPreparing) {
                try {
                    orderSucceedRetryService.stop()
                } catch (e: Throwable) {
                    logger.error("Failed to stop orderSucceedRetryService: ", e)
                }
            }
        } catch (t: Throwable) {
            logger.error("Failed to start orderSucceedRetryService: ", t)
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

        monitor.subscribe(ApplicationStopPreparing) {

            try {
                generateTicketSlotQRCodeProducer.stop()
            } catch (e: Throwable) {
                logger.error("Failed to stop generateTicketSlotQRCodeProducer: ", e)
            }

        }
    }
}