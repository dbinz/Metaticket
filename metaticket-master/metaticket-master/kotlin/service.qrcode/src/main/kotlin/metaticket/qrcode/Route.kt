package metaticket.qrcode

import io.ktor.server.application.*
import io.ktor.server.routing.*
import metaticket.qrcode.controller.QRCodeController

fun Route.documentRoutes(
    qrcodeCtrl: QRCodeController
) {
    get("/private/qrcode/generate-link-scanner") {
        qrcodeCtrl.generateLinkScanner(call)
    }

    post("/private/qrcode/order/fetch") {
        qrcodeCtrl.fetchOrderQRCode(call)
    }

    post("/private/qrcode/order/disable") {
        qrcodeCtrl.disableOrderQrCode(call)
    }

    post("/private/qrcode/order/enable") {
        qrcodeCtrl.enableOrderQrCode(call)
    }

    get("/public/qrcode/verify-scanner") {
        qrcodeCtrl.verifyLinkScanner(call)
    }

    post("/public/qrcode/scan") {
        qrcodeCtrl.scanCode(call)
    }
}
