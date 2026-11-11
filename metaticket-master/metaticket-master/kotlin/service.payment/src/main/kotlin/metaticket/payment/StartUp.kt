package metaticket.payment

import io.grpc.Server
import io.ktor.client.*
import io.ktor.events.*
import io.ktor.server.application.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import metaticket.common.coroutine.mapLaunch
import metaticket.common.logger.Logging
import metaticket.payment.configuration.PaymentConfig
import metaticket.payment.database.service.PaymentDBService
import metaticket.payment.datastructure.PaymentStatus
import metaticket.payment.gateway.JobrunrGateway
import java.util.*
import java.util.concurrent.ConcurrentHashMap


class StartUp constructor(
    private val monitor: Events,
    private val server: Server,
    private val httpClient: HttpClient,
    private val db: PaymentDBService,
    private val jobrunrGateway: JobrunrGateway,
    private val paymentConfig: PaymentConfig,
) : Logging {

    fun start(applicationScope: CoroutineScope) {
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

        applicationScope.launch { inquiryPaymentStatus() }
        applicationScope.launch { scheduleExpireCreatedPayment() }
    }

    private suspend fun inquiryPaymentStatus() {
        runCatching {
            db.loadPaymentByStatus(listOf(PaymentStatus.Processing)).mapLaunch {
                if (it.paymentDetail != null) {
                    jobrunrGateway.inquiryPaymentStatus(it, it.paymentDetail.gateway)
                }
            }
        }.onFailure {
            logger.error("fail to load Processing payment from db when schedule to inquiryPaymentStatus... ", it)
        }
    }

    private suspend fun scheduleExpireCreatedPayment() {
        val processingPayment = ConcurrentHashMap<String,  Boolean>()
        val listStatus = listOf(PaymentStatus.Created, PaymentStatus.Starting)
        while (true) {
            kotlin.runCatching {
                db.loadPaymentByStatus(listStatus).mapLaunch {
                    try
                    {
                        logger.debug("payment {} process expired", it.id)
                        if (processingPayment.putIfAbsent(it.id, true) == null) {
                            logger.debug("payment {} process putIfAbsent expired", it.id)
                            if (Date().time - it.lastModified.time > paymentConfig.expireInMinute * 60000L) {
                                val notified = jobrunrGateway.notifyPaymentStatusChanged(it, PaymentStatus.Failed)
                                if (notified) {
                                    db.updatePaymentStatus(it.id, PaymentStatus.Failed, listStatus)
                                    logger.info("payment {} has been expired", it.id)
                                } else {
                                    logger.error("payment {} has not been expired cause cannot notified to order service", it.id)
                                }
                            }
                        } else {
                            logger.debug("payment {} is processing expired already", it.id)
                        }
                    } catch (t: Throwable) {
                        logger.error("payment {} fail to expire... ", it.id, t)
                    } finally {
                        processingPayment.remove(it.id)
                    }
                }.joinAll()
            }.onFailure {
                logger.error("fail to load created payment from db when schedule to expire payments... ", it)
            }

            delay(paymentConfig.scanExpireInMinute*60000L)
        }
    }
}