package metaticket.order.service

import metaticket.common.coroutine.launch
import metaticket.common.logger.Logging
import metaticket.jobrunr.grpc.JobExecutorServiceGrpcKt
import metaticket.jobrunr.grpc.JobrunrMessage
import metaticket.jobrunr.grpc.JobrunrMessage.*
import metaticket.order.controller.OrderFailedHandler
import metaticket.order.database.service.OrderDBService
import metaticket.order.datastructure.OrderStatus
import metaticket.order.datastructure.jobrunr.OrderJobType
import metaticket.order.gateway.JobrunrGateway
import metaticket.order.gateway.NotificationGateway
import metaticket.order.gateway.ReservationGateway
import metaticket.order.kafka.OrderSucceedProducer
import metaticket.order.pocessing.isOrderProcessing
import metaticket.order.pocessing.releaseOrderProcessing
import metaticket.order.pojo.db.OrderDocument
import java.util.*

class JobExecutorServiceImpl constructor(
    private val db: OrderDBService,
    private val jobrunrGateway: JobrunrGateway,
    private val reservationGateway: ReservationGateway,
    private val notificationGateway: NotificationGateway,
    private val orderFailedHandler: OrderFailedHandler,
    private val orderSucceedProducer: OrderSucceedProducer,
): JobExecutorServiceGrpcKt.JobExecutorServiceCoroutineImplBase(), Logging {
    fun JobExecutionResponse(jobId: String, nextAction: JobrunrMessage.JobNextAction): JobrunrMessage.JobExecutionResponse {
        return JobrunrMessage.JobExecutionResponse.newBuilder()
            .setJobId(jobId)
            .setNextAction(nextAction)
            .build()
    }

    override suspend fun executeJob(request: JobExecutionRequest): JobExecutionResponse {
        val orderStatus = when (OrderJobType.valueOf(request.jobName)) {
            OrderJobType.NotifyPaymentProcessing -> OrderStatus.PaymentProcessing
            OrderJobType.NotifyPaymentSucceed -> OrderStatus.PaymentSucceed
            OrderJobType.NotifyPaymentFailed -> OrderStatus.PaymentFailed
            OrderJobType.NotifyPaymentCancelled -> OrderStatus.PaymentCancelled
            else -> {
                logger.error("Not handle job {}", request.jobName)
                return JobExecutionResponse(request.jobId, JobNextAction.STOP)
            }
        }
        val orderId = request.jobDesc

        if (isOrderProcessing(orderId)) {
            logger.info("order {} update to {} but another one is processing when execute job {}", orderId, orderStatus, request)
            return JobExecutionResponse(request.jobId, JobNextAction.CONTINUE)
        }

        try {
            val order = db.loadOrderById(orderId) ?: run {
                logger.info("order {} not found when execute job {}", orderId, request)
                return JobExecutionResponse(request.jobId, JobNextAction.STOP)
            }

            if (order.status == orderStatus) {
                logger.info(
                    "order {} status was updated already to {} after executing job {}",
                    orderId,
                    orderStatus,
                    request
                )
                return JobExecutionResponse(request.jobId, JobNextAction.STOP)
            }

            if (order.status !in listOf(
                    OrderStatus.PaymentProcessing,
                    OrderStatus.WaitingForPayment,
                    OrderStatus.PaymentPending
                )
            ) {
                logger.info("order {} status was settled {} before executing job {}", orderId, order.status, request)
                return JobExecutionResponse(request.jobId, JobNextAction.STOP)
            }

            order.status = orderStatus

            if (orderStatus == OrderStatus.PaymentSucceed || order.status == OrderStatus.PaymentPending) {
                // notify to ticket service that order changed
                try {
                    logger.info("order {} notify status changed to {}, reservation {} when execute jobrunr",
                        orderId, orderStatus, order.reservationId
                    )
                    reservationGateway.notifyOrderStatusChanged(order.reservationId, orderStatus)
                } catch (t: Throwable) {
                    logger.error(
                        "order {} failed to notify status changed to {}, reservation {} when execute jobrunr",
                        orderId, orderStatus, order.reservationId
                    )
                    val job = jobrunrGateway.notifyOrderStatusChanged(order.reservationId, orderStatus)
                    if (!job) {
                        logger.error(
                            "order {} failed to notify status changed {} when execute job {}",
                            orderId,
                            orderStatus,
                            request
                        )
                        return JobExecutionResponse(request.jobId, JobNextAction.CONTINUE)
                    }
                }

                db.updateStatus(orderId, orderStatus) ?: run {
                    logger.info(
                        "order {} not found when update status {} when execute job {}",
                        orderId,
                        orderStatus,
                        request
                    )
                    return JobExecutionResponse(request.jobId, JobNextAction.STOP)
                }

                if (orderStatus == OrderStatus.PaymentSucceed) {
                    orderSucceedProducer.scheduleOrSendOrderSucceedEvent(order)
                }
                scheduleOrSendEmail(order)

            } else {
                orderFailedHandler.onOrderFailed(order.reservationId, orderId, orderStatus)
            }

            logger.info("order {} status was updated to {} after execute job {}", orderId, orderStatus, request)
            return JobExecutionResponse(request.jobId, JobNextAction.STOP)
        } finally {
            releaseOrderProcessing(orderId)
        }
    }

    private suspend fun scheduleOrSendEmail(orderDoc: OrderDocument) {
        launch { // Run async
            val reqId = UUID.randomUUID()
            val jobScheduled = jobrunrGateway.resendEmailOrder(orderDoc, reqId)
            if (!jobScheduled) {
                logger.warn("Failed to schedule email job for order {}, attempting direct send.", orderDoc.id)
                val sent = notificationGateway.sendMailOrder(orderDoc, reqId)
                if (!sent) {
                    logger.error("CRITICAL: Failed to schedule AND send email for order {}. Req ID: {}", orderDoc.id, reqId)
                    // TODO: Implement more robust retry/alerting mechanism
                }
            } else {
                logger.info("Scheduled email job for order {}. Req ID: {}", orderDoc.id, reqId)
            }
        }
    }
}