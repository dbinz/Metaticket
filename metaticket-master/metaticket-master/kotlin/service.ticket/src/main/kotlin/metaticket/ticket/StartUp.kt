package metaticket.ticket

import com.fasterxml.jackson.databind.ObjectMapper
import io.grpc.Server
import io.ktor.events.*
import io.ktor.server.application.*
import metaticket.common.logger.Logging
import kotlinx.coroutines.runBlocking
import metaticket.common.redis.codec.RedisCustomValueCodec
import metaticket.ticket.database.service.TicketCalendarDayDBService
import metaticket.ticket.database.service.TicketDocDBService
import metaticket.ticket.processor.ReservationProcessorManager
import metaticket.ticket.redis.RedisAsyncClient

class StartUp(
    private val monitor: Events,
    private val server: Server,
    private val redisClient: RedisAsyncClient,
    private val reservationProcessorManager: ReservationProcessorManager,
    private val jsonMapper: ObjectMapper,
    private val ticketDocDB: TicketDocDBService,
    private val calendarDayDB: TicketCalendarDayDBService,
): Logging {
    fun start() {
        // Unique index là thứ chặn sinh trùng ngày/vé theo lịch. Lỗi tạo index không được làm sập luồng bán vé thường,
        // nên chỉ log lỗi; khi đó hai lần sinh lịch đồng thời có thể tạo trùng.
        try {
            runBlocking {
                ticketDocDB.ensureCalendarIndexes()
                calendarDayDB.ensureIndexes()
            }
            logger.info("Calendar ticket indexes ensured")
        } catch (t: Throwable) {
            logger.error("Failed to ensure calendar ticket indexes", t)
        }

        try {
            redisClient.start()
            logger.info("Redis cluster connection established")

            monitor.subscribe(ApplicationStopped) {
                try {
                    redisClient.shutdown()
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
            reservationProcessorManager.start()
            logger.info("ReservationProcessorManager started")
            monitor.subscribe(ApplicationStopped) {
                try {
                    reservationProcessorManager.stopBlocking()
                } catch (e: Throwable) {
                    logger.error("Failed to stop ReservationProcessorManager: ", e)
                }
            }
        } catch (e: Throwable) {
            logger.error("Failed to start reservation processor: ", e)
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