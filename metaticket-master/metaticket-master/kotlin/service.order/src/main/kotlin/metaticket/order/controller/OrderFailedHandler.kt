package metaticket.order.controller

import metaticket.common.coroutine.launch
import metaticket.common.logger.Logging
import metaticket.order.cache.CalculatePromotionClassesCache
import metaticket.order.calculate.promotion.CalculatePromotion
import metaticket.order.calculate.promotion.RevertPromotionInput
import metaticket.order.database.service.OrderDBService
import metaticket.order.database.service.PromotionCodeQuantityDBService
import metaticket.order.database.service.PromotionComboQuantityDBService
import metaticket.order.database.service.PromotionDBService
import metaticket.order.datastructure.OrderStatus
import metaticket.order.gateway.JobrunrGateway
import metaticket.order.gateway.NotificationGateway
import metaticket.order.gateway.ReservationGateway
import metaticket.order.pojo.db.OrderPromotionDetail
import metaticket.order.pojo.db.Promotion
import java.lang.reflect.Constructor
import java.util.*


/**
 *
 * @author dongcatchay
 */
class OrderFailedHandler constructor(
    private val orderDBS: OrderDBService,
    private val promotionDBS: PromotionDBService,
    private val promotionCodeQuantityDBS: PromotionCodeQuantityDBService,
    private val promotionComboQuantityDBS: PromotionComboQuantityDBService,
    private val jobrunrGateway: JobrunrGateway,
    private val notificationGateway: NotificationGateway,
    private val reservationGateway: ReservationGateway,
    private val calculatePromotionClasses: CalculatePromotionClassesCache,
): Logging {

    private fun getPromotionClass(promotion: Promotion): CalculatePromotion? {
        var classInstancePromotion: CalculatePromotion? = calculatePromotionClasses.get(promotion.pluginName)

        if(classInstancePromotion == null) {
            try {
                val clazz = Class.forName(promotion.pluginName)
                val ctor: Constructor<*> = clazz.getConstructor()

                classInstancePromotion = ctor.newInstance() as CalculatePromotion

                calculatePromotionClasses.add(classInstancePromotion)
            } catch (ex: ClassNotFoundException) {
                logger.error("failed to revert promotion cause not found promotion class... ", ex)
            }
        }
        return classInstancePromotion
    }

    suspend fun revertPromotion(promotionPluginConfigId: String, promotionDetails: List<OrderPromotionDetail>) {
        val promotion = promotionDBS.loadPromotion(promotionPluginConfigId)
        if(promotion != null) {
            getPromotionClass(promotion)?.let {
                val result = it.revertPromotionDiscount(
                    RevertPromotionInput(promotionDetails, promotionCodeQuantityDBS, promotionComboQuantityDBS)
                )
            }
        }
    }

    suspend fun onCreateOrderFailed(reservationId: String) {
        try {
            logger.info("create order failed, notify status changed to {}, reservation {}",
                OrderStatus.Failed, reservationId
            )
            reservationGateway.notifyOrderStatusChanged(reservationId, OrderStatus.Failed)
        } catch (t: Throwable) {
            logger.error(
                "create order failed, failed to notify status changed to {}, reservation {}... ",
                OrderStatus.Failed, reservationId, t
            )
            val job = jobrunrGateway.notifyOrderStatusChanged(reservationId, OrderStatus.Failed)
            if (!job) {
                logger.error("create order failed, failed to notify status changed {}, reservation {}",
                    OrderStatus.Failed, reservationId
                )
                // TODO implement logic handle cannot send notification order status change
                return
            }
        }
    }

    suspend fun onOrderFailed(reservationId: String, orderId: String, orderStatus: OrderStatus = OrderStatus.Failed) {
        try {
            val order = orderDBS.loadOrderById(orderId)
            if(order!!.promotionPluginConfigId != null) {
                revertPromotion(order.promotionPluginConfigId!!, order.promotionDetails)
            }

            orderDBS.updateStatus(orderId, orderStatus)?.let {
                launch { // eun async
                    val reqId = UUID.randomUUID()
                    val sendMail = notificationGateway.sendMailOrder(it, reqId)
                    if (!sendMail) {
                        logger.error("order {} failed to send mail", it.id)
                        val job = jobrunrGateway.resendEmailOrder(it, reqId)
                        if (!job) {
                            logger.error("order {} failed to schedule job send mail", it.id)
                            // TODO retry or customer will missing email
                        }
                    }
                }
            }

            try {
                logger.info("order {} notify status changed to {}, reservation {}",
                    orderId, orderStatus, reservationId
                )
                reservationGateway.notifyOrderStatusChanged(reservationId, orderStatus)
            } catch (t: Throwable) {
                logger.error(
                    "order {} failed to notify status changed to {}, reservation {} ... ",
                    orderId, orderStatus, reservationId, t
                )
                val job = jobrunrGateway.notifyOrderStatusChanged(reservationId, orderStatus)
                if (!job) {
                    logger.error(
                        "order {} failed to notify status changed {}, reservation {}",
                        orderId, orderStatus, reservationId
                    )
                    // TODO implement logic handle cannot send notification order status change
                }
            }

        } catch (t: Throwable) {
            logger.error("order {} failed with status {} but cannot handle... ", orderId, orderStatus, t)
        } finally {
        }
    }

    suspend fun onOrderExpired(reservationId: String, orderId: String) {
        try {
            metaticket.order.logger.info("order {} has been expired", orderId)

            val order = orderDBS.loadOrderById(orderId)
            if (order!!.promotionPluginConfigId != null) {
                revertPromotion(order.promotionPluginConfigId!!, order.promotionDetails)
            }
            orderDBS.updateStatus(orderId, OrderStatus.Expired)?.let {
                launch { // eun async
                    val reqId = UUID.randomUUID()
                    val sendMail = notificationGateway.sendMailOrder(it, reqId)
                    if (!sendMail) {
                        logger.error("order {} failed to send mail", it.id)
                        val job = jobrunrGateway.resendEmailOrder(it, reqId)
                        if (!job) {
                            logger.error("order {} failed to schedule job send mail", it.id)
                            // TODO retry or customer will missing email
                        }
                    }
                }
            }

            try {
                logger.info("order {} notify status changed to {}, reservation {}",
                    orderId, OrderStatus.Expired, reservationId
                )
                reservationGateway.notifyOrderStatusChanged(reservationId, OrderStatus.Expired)
            } catch (t: Throwable) {
                logger.error(
                    "order {} failed to notify status changed to {}, reservation {} ...",
                    orderId, OrderStatus.Expired, reservationId, t
                )
                val notify = jobrunrGateway.notifyOrderStatusChanged(reservationId, OrderStatus.Expired)
                if (!notify) {
                    logger.error(
                        "order {} failed to notify status changed {}, reservation {}",
                        orderId, OrderStatus.Expired, reservationId
                    )
                }
            }
        } catch (t: Throwable) {
            logger.error("order {} expired but cannot handle... ", orderId, t)
        } finally {
        }
    }
}
