package metaticket.payment

import io.ktor.server.routing.*
import metaticket.payment.controller.*

fun Route.documentRoutes(
    paymentController : PaymentController,
    pgAccountController: PGAccountController,
    ninePayGatewayCtrl: NinePayGatewayController,
    vnPayGatewayCtrl: VnPayGatewayController,
    onepayGatewayCtrl: OnePayGatewayController,
) {
    get("/payment/fetch-status") { paymentController.loadPaymentStatus(call) }
    get("/private/payment/fetch") { paymentController.loadPayment(call) }
    post("/private/payment/ids/fetch") { paymentController.loadPaymentByIds(call) }

    // ===== payment gateway return ===
    // ================== 9Pay ===============================
    get("/payment/return/result/ninepay/{pgaCode}/{pD}/{pC}") {
        ninePayGatewayCtrl.ninePayUpdatePaymentStatusFromResult(call)
    }
    get("/payment/return/result/ninepay") {
        ninePayGatewayCtrl.ninePayUpdatePaymentStatusFromResult(call)
    }
    post("/payment/ipn/result/ninepay/{pgaCode}") {
        ninePayGatewayCtrl.ninePayUpdatePaymentStatusFromIPNResult(call)
    }

    // ================== VNPay ===============================
    get("/payment/return/result/vnpay/{pgaCode}/{pD}/{pC}") {
        vnPayGatewayCtrl.vnPayUpdatePaymentStatusFromResult(call)
    }
    get("/payment/return/result/vnpay") {
        vnPayGatewayCtrl.vnPayUpdatePaymentStatusFromResult(call)
    }
    get("/payment/ipn/result/vnpay/{pgaCode}") {
        vnPayGatewayCtrl.vnPayUpdatePaymentStatusFromIPNResult(call)
    }

    // ================== OnePay ===============================
    get("/payment/return/result/onepay/{pgaCode}/{pD}/{pC}") {
        onepayGatewayCtrl.onepayUpdatePaymentStatusFromResult(call)
    }
    get("/payment/return/result/onepay") {
        onepayGatewayCtrl.onepayUpdatePaymentStatusFromResult(call)
    }
    get("/payment/ipn/result/onepay/{pgaCode}") {
        onepayGatewayCtrl.onepayUpdatePaymentStatusFromIPNResult(call)
    }

    post("/payment/start") { paymentController.startPayment(call) }

    // ===== payment gateway account config ===
    get("/private/payment/pg-config/fetch") {
        pgAccountController.loadPGConfig(call)
    }

    route("/private/payment/pg-account/onepay/add") {
        post { pgAccountController.addPGOnePayAccount(call) }
    }

    route("/private/payment/pg-account/ninepay/add") {
        post { pgAccountController.addPGNinePayAccount(call) }
    }

    route("/private/payment/pg-account/vnpay/add") {
        post { pgAccountController.addPGVnPayAccount(call) }
    }

    route("/private/payment/pg-account/onepay/update") {
        post { pgAccountController.updatePGOnePayAccount(call) }
    }

    route("/private/payment/pg-account/ninepay/update") {
        post { pgAccountController.updatePGNinePayAccount(call) }
    }

    route("/private/payment/pg-account/vnpay/update") {
        post { pgAccountController.updatePGVnPayAccount(call) }
    }

    get("/private/payment/pg-account/load") {
        pgAccountController.loadPGAccount(call)
    }

    post("/private/payment/pg-account/remove") {
        pgAccountController.removePGAccount(call)
    }

    post("/private/payment/pg-account/enable") {
        pgAccountController.enablePGAccount(call)
    }

    post("/private/payment/pg-account/disable") {
        pgAccountController.disablePGAccount(call)
    }

    post("/private/payment/pg-account/set-default") {
        pgAccountController.setPGDefaultAccount(call)
    }
}
