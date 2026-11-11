package metaticket.order

import io.ktor.server.routing.*
import metaticket.order.controller.FailedEventController
import metaticket.order.controller.NotifyOrderController
import metaticket.order.controller.OrderController
import metaticket.order.controller.PromotionController
import metaticket.order.controller.ReportController
import metaticket.order.datastructure.OrderStatus
import metaticket.order.utils.ValidationUtil.Companion.validateCreateOrderRequest
import metaticket.order.utils.ValidationUtil.Companion.validateCreatePromotionRequest
import metaticket.order.utils.ValidationUtil.Companion.validateLoadOrderByReservationRequest
import metaticket.order.utils.ValidationUtil.Companion.validateOrderId
import metaticket.order.utils.ValidationUtil.Companion.validateUpdateOrderInfoRequest

fun Route.documentRoutes(
    orderCtl: OrderController,
    reportCtrl: ReportController,
    promotionCtrl: PromotionController,
    notifyOrderCtrl: NotifyOrderController,
    failedEventCtrl: FailedEventController,
) {

    route("/private/order/promotion/import") {
        post { promotionCtrl.importFile(call) }
    }

    route("/order/create") {
        post { orderCtl.createOrder(call) }
        validateCreateOrderRequest()
    }
    route("/order/payment/create")  {
        post { orderCtl.createPayment(call) }
        validateOrderId()
    }
    route("/order/update-info") {
        post { orderCtl.updateOrderInfo(call) }
        validateUpdateOrderInfoRequest()
    }
    post("/private/order/fetch/all")             { orderCtl.getOrderByShowAndSession(call) }
    get("/order/fetch")             { orderCtl.getOrderById(call) }
    get("/order/fetch/summary")     { orderCtl.getSummaryOrderById(call) }
    post("/private/order/offline-payment/fetch/summary")     { orderCtl.getOfflinePaymentOrder(call) }
    post("/order/price/tentative")  { orderCtl.calculateTentativePrice(call) }
    post("/order/custom-field/fetch") { orderCtl.fetchCustomFieldTemplateByShowAndSession(call) }
    post("/private/order/custom-field/fetch") { orderCtl.fetchCustomFieldTemplateByShowAndSession(call) }
    post("/private/order/statistic/load") { reportCtrl.loadInvoiceStatistics(call) }
    post("/internal/order/statistic/load") { reportCtrl.loadInvoiceStatistics(call) }
    post("/private/order/report/load") { reportCtrl.loadReport(call) }
    post("/internal/order/report/load") { reportCtrl.loadReport(call) }

    route("/private/order/fetch/reservation") {
        post { orderCtl.loadOrderByReservation(call) }
        validateLoadOrderByReservationRequest()
    }
    route("/private/order/cancel") {
        post { orderCtl.cancelOrder(call) }
        validateOrderId()
    }

    post("/private/order/offline-payment/confirm")     { notifyOrderCtrl.onOfflinePaymentChangeStatus(call, OrderStatus.PaymentSucceed) }
    post("/private/order/offline-payment/cancel")     { notifyOrderCtrl.onOfflinePaymentChangeStatus(call, OrderStatus.Canceled) }
    post("/private/order/plugin/promotion/reload")  { orderCtl.reloadPlugin(call) }
    get("/private/order/plugin/promotions") { promotionCtrl.fetchPromotionsByShowIdAndSessionId(call) }
    route("/private/order/promotion/create")  {
        post { promotionCtrl.createPromotion(call) }
        validateCreatePromotionRequest()
    }

    post("/private/order/notify/resend") { notifyOrderCtrl.resendNotification(call) }

    //create custom field mapping
    post("/private/order/custom/fields/mapping/update") { orderCtl.updateCustomFieldMapping(call) }

    //create promotion
    post("/private/order/promotions/update") { orderCtl.updatePromotion(call) }

    // Failed events management endpoints
    get("/private/order/failed-events") { failedEventCtrl.getFailedEvents(call) }
    get("/private/order/failed-events/{id}") { failedEventCtrl.getFailedEventById(call) }
    post("/private/order/failed-events/{id}/retry") { failedEventCtrl.retryFailedEvent(call) }
    patch("/private/order/failed-events/{id}/disable") { failedEventCtrl.disableFailedEvent(call) }
    get("/private/order/failed-events/stats") { failedEventCtrl.getFailedEventsStats(call) }
}
