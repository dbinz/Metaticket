package metaticket.order.controller

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import kotlinx.coroutines.awaitAll
import metaticket.common.checksum.Checksum
import metaticket.common.coroutine.async
import metaticket.common.coroutine.launch
import metaticket.common.logger.Logging
import metaticket.order.cache.ShowCache
import metaticket.order.configuration.Environment
import metaticket.order.database.service.OrderDBService
import metaticket.order.datastructure.OrderStatus
import metaticket.order.gateway.JobrunrGateway
import metaticket.order.gateway.NotificationGateway
import metaticket.order.gateway.ReservationGateway
import metaticket.order.kafka.OrderSucceedProducer
import metaticket.order.pocessing.isOrderProcessing
import metaticket.order.pocessing.releaseOrderProcessing
import metaticket.order.pojo.db.OrderDocument
import java.util.*

class NotifyOrderController constructor(
    private val db: OrderDBService,
    private val notificationGateway: NotificationGateway,
    private val orderFailedHandler: OrderFailedHandler,
    private val reservationGateway: ReservationGateway,
    private val jobrunrGateway: JobrunrGateway,
    private val orderSucceedProducer: OrderSucceedProducer,
    private val showCache: ShowCache,
) : Logging {

    suspend fun onOfflinePaymentChangeStatus(call: ApplicationCall, orderStatus: OrderStatus) {
        val req = call.receive<Map<String, String>>()

        val orderId = req["orderId"]?.let {
            Checksum.decode(it, Environment.secretKey)
        } ?: run {
            return call.respond(HttpStatusCode.BadRequest, "Không đủ thông tin request")
        }

        if (isOrderProcessing(orderId)) {
            logger.info("order {} update to {} but another one is processing", orderId, orderStatus)
            return call.respond(HttpStatusCode.BadRequest, "Đơn hàng đang được xử lý")
        }

        try {
            var order = db.loadOrderById(orderId) ?: run {
                logger.error("order {} not found in db", orderId)
                return call.respond(HttpStatusCode.NotFound, "Không tìm thấy đơn hàng")
            }

            if (order.status == orderStatus) {
                logger.info("order {} updated status {} already", orderId, orderStatus)
                return call.respond(HttpStatusCode.OK, "Đơn hàng đã cập nhât trạng thái rồi")
            }

            if (order.status !in listOf(
                    OrderStatus.PaymentProcessing,
                    OrderStatus.WaitingForPayment,
                    OrderStatus.PaymentPending
                )
            ) {
                logger.info("order {} status was settled {} before receiving {}", orderId, order.status, orderStatus)
                return call.respond(HttpStatusCode.OK, "Đơn hàng đã được đặt trạng thái")
            }

            // notify to ticket service that order changed
            if (orderStatus in listOf(OrderStatus.Canceled)) {
                orderFailedHandler.onOrderFailed(order.reservationId, orderId, orderStatus)
                logger.info("order {} received notification that order failed {}", orderId, orderStatus)
                return call.respond(HttpStatusCode.OK,"Đơn hàng đã được hủy")
            }

            if (orderStatus == OrderStatus.PaymentSucceed || order.status == OrderStatus.PaymentPending) {
                try {
                    logger.info("order {} notify status changed to {}, reservation {} when update order status", order.id, orderStatus, order.reservationId)
                    reservationGateway.notifyOrderStatusChanged(order.reservationId, orderStatus)
                } catch (t: Throwable) {
                    logger.error(
                        "order {} exception when notify status changed to {}, reservation {} when update order status... ",
                        order.id, orderStatus, order.reservationId, t
                    )
                    return call.respond(HttpStatusCode.InternalServerError, "Cập nhật trạng thái đơn hàng thất bại")
                }

                order = db.updateStatus(orderId, orderStatus) ?: run {
                    logger.error("order {} not found when update status {} in db", orderId, orderStatus)
                    return call.respond(HttpStatusCode.InternalServerError, "Cập nhật trạng thái đơn hàng thất bại")
                }

                if (orderStatus == OrderStatus.PaymentSucceed) {
                    orderSucceedProducer.scheduleOrSendOrderSucceedEvent(order)
                }
                scheduleOrSendEmail(order)

                logger.info("order {} is confirmed", order.id)
                return call.respond(HttpStatusCode.OK, "Đơn hàng đã được xác nhận")

            } else {
                logger.error("order {} failed status {}", orderId, orderStatus)
                orderFailedHandler.onOrderFailed(order.reservationId, orderId, orderStatus)
                return call.respond(HttpStatusCode.InternalServerError, "Cập nhật trạng thái đơn hàng thất bại")
            }
        } finally {
            releaseOrderProcessing(orderId)
        }
    }

    suspend fun resendNotification(call: ApplicationCall) {
        val orderIds = call.receive<List<String>>().map { Checksum.decode(it, Environment.secretKey) }

        val orders = db.loadOrderByIds(orderIds)

        val res = orders.map { order: OrderDocument -> async { // eun async
            logger.info("order {} resending notification for status {}", order.id, order.status)
            val r = notificationGateway.sendMailOrder(order, UUID.randomUUID())
            logger.info("order {} success resend notification for status {}", order.id, order.status)
            Checksum.encode(order.id, Environment.secretKey) to r
        } }.awaitAll()

        val failed = res.filter { it == null || !it.second }
        if (failed.isNotEmpty()) {
            logger.error("failed to resend notification {}", failed)
        }

        call.respond(HttpStatusCode.OK,  res.associate { it!! })
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
