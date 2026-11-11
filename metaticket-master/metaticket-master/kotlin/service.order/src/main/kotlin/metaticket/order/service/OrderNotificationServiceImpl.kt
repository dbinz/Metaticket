package metaticket.order.service

import com.google.protobuf.Empty
import io.grpc.Status
import io.grpc.StatusException
import metaticket.common.coroutine.launch
import metaticket.common.logger.Logging
import metaticket.order.controller.OrderFailedHandler
import metaticket.order.database.service.OrderDBService
import metaticket.order.datastructure.OrderStatus
import metaticket.order.gateway.JobrunrGateway
import metaticket.order.gateway.NotificationGateway
import metaticket.order.gateway.ReservationGateway
import metaticket.order.kafka.OrderSucceedProducer
import metaticket.order.notification.grpc.OrderMessage
import metaticket.order.notification.grpc.OrderNotificationServiceGrpcKt
import metaticket.order.pocessing.isOrderProcessing
import metaticket.order.pocessing.releaseOrderProcessing
import metaticket.order.pojo.db.OrderDocument
import metaticket.payment.datastructure.PaymentStatus
import java.util.*

class OrderNotificationServiceImpl(
    private val db: OrderDBService,
    private val notificationGateway: NotificationGateway,
    private val orderFailedHandler: OrderFailedHandler,
    private val reservationGateway: ReservationGateway,
    private val jobrunrGateway: JobrunrGateway,
    private val orderSucceedProducer: OrderSucceedProducer,
): OrderNotificationServiceGrpcKt.OrderNotificationServiceCoroutineImplBase(), Logging {
    /**
     * has to wait reservation status is updated before update order status, then return to OK
     */
    override suspend fun notifyPaymentStarted(request: OrderMessage.NotifyPaymentStartedRequest): Empty {
        val orderId = request.orderId

        if (isOrderProcessing(orderId)) {
            logger.info("order {} update to {} but another one is processing", orderId, OrderStatus.PaymentProcessing)
            throw StatusException(Status.FAILED_PRECONDITION.withDescription( "Order processing conflict"))
        }

        try {
            val order = db.updateStatus(orderId, OrderStatus.PaymentProcessing) ?: run {
                logger.error("not found order {}", orderId)
                throw StatusException(Status.NOT_FOUND.withDescription("not found order"))
            }

            launch {
                val reqId = UUID.randomUUID()
                val sendMail = notificationGateway.sendMailOrder(order, reqId)
                if (!sendMail) {
                    logger.error("order {} failed to send mail", order.id)
                    val job = jobrunrGateway.resendEmailOrder(order, reqId)
                    if (!job) {
                        logger.error("order {} failed to schedule job send mail", order.id)
                        // TODO retry or customer will missing email
                    }
                }
            }

            try {
                logger.info("order {} starting payment for reservation {}", order.id, order.reservationId)
                reservationGateway.notifyOrderStatusChanged(order.reservationId, OrderStatus.PaymentProcessing)
            } catch (t: Throwable) {
                logger.error("order {} starting payment, update reservation {} failed... ", order.id, order.reservationId, t)
                throw StatusException(Status.INTERNAL.withDescription(t.message))
            }

            logger.info("order {} updated status to {}", order.id, OrderStatus.PaymentProcessing)
            return Empty.getDefaultInstance()
        } finally {
            releaseOrderProcessing(orderId)
        }
    }

    override suspend fun notifyPaymentStatusChanged(request: OrderMessage.NotifyPaymentStatusChangedRequest): Empty {
        val orderId = request.orderId
        val paymentStatus = request.paymentStatus

        val orderStatus = when(PaymentStatus.valueOf(paymentStatus)) {
            PaymentStatus.Processing -> OrderStatus.PaymentProcessing
            PaymentStatus.Succeed -> OrderStatus.PaymentSucceed
            PaymentStatus.Failed -> OrderStatus.PaymentFailed
            PaymentStatus.Cancelled -> OrderStatus.PaymentCancelled

            else -> {
                logger.error("order {} not handles payment status changed {}", orderId, paymentStatus)
                throw StatusException(Status.INTERNAL.withDescription("Not handle status"))
            }
        }

        if (isOrderProcessing(orderId)) {
            logger.info("order {} update to {} but another one is processing", orderId, orderStatus)
            throw StatusException(Status.FAILED_PRECONDITION.withDescription( "Order processing conflict"))
        }

        try {
            val order = db.loadOrderById(orderId) ?: run {
                logger.error("order {} not found in db", orderId)
                throw StatusException(Status.NOT_FOUND.withDescription("not found order"))
            }

            if (order.status == orderStatus) {
                logger.info("order {} updated status {} already", orderId, orderStatus)
                return Empty.getDefaultInstance()
            }

            if (order.status !in listOf(
                    OrderStatus.PaymentProcessing,
                    OrderStatus.WaitingForPayment,
                    OrderStatus.PaymentPending
                )
            ) {
                logger.info("order {} status was settled {} before receiving {}", orderId, order.status, paymentStatus)
                return Empty.getDefaultInstance()
            }

            order.status = orderStatus

            if (orderStatus in listOf(OrderStatus.PaymentCancelled, OrderStatus.PaymentFailed)) {
                orderFailedHandler.onOrderFailed(order.reservationId, orderId, orderStatus)
                logger.info("order {} received notification that order failed {}", orderId, orderStatus)
                return Empty.getDefaultInstance()
            }

            if (orderStatus == OrderStatus.PaymentSucceed) {
                try {
                    logger.info("order {} notify status changed to {}, reservation {} when update order status", order.id, orderStatus, order.reservationId)
                    reservationGateway.notifyOrderStatusChanged(order.reservationId, orderStatus)
                } catch (t: Throwable) {
                    logger.error("order {} failed to notify status changed to {}, reservation {} when update order status... ", order.id, orderStatus, order.reservationId, t)
                    val job = jobrunrGateway.notifyOrderStatusChanged(order.reservationId, orderStatus)
                    if (!job) {
                        logger.error("order {} failed to notify status changed {}, reservation {} when update order status", order.id, orderStatus, order.reservationId)
                        throw StatusException(Status.INTERNAL.withDescription("cannot send job"))
                    }
                }

                db.updateStatus(orderId, orderStatus) ?: run {
                    logger.error("order {} not found when update status {} in db", orderId, orderStatus)
                    throw StatusException(Status.FAILED_PRECONDITION.withDescription("Failed to update order status"))
                }

                if (order.status == OrderStatus.PaymentSucceed) {
                    orderSucceedProducer.scheduleOrSendOrderSucceedEvent(order)
                }
                scheduleOrSendEmail(order)

                logger.info(
                    "order {} updated to {} when payment status changed to {}",
                    orderId,
                    orderStatus,
                    paymentStatus
                )
                return Empty.getDefaultInstance()

            } else {
                logger.error("order {} failed to update status {}", orderId, orderStatus)
                orderFailedHandler.onOrderFailed(order.reservationId, orderId, orderStatus)
                throw StatusException(Status.FAILED_PRECONDITION.withDescription("Failed to update order status"))
            }
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