package metaticket.ticketslots

import io.ktor.server.routing.*
import metaticket.ticketslots.controller.TicketSlotsCtrl

import metaticket.ticketslots.controller.AdminQRCodeProducerEventController
import metaticket.ticketslots.controller.AdminQRCodeConsumerEventController
import metaticket.ticketslots.controller.AdminOrderSucceedEventController
import metaticket.ticketslots.pojo.db.TicketSlotOptStatus
import metaticket.ticketslots.pojo.db.TicketSlotStatus

fun Route.ticketSlotsRoutes(ticketSlotsCtrl: TicketSlotsCtrl, adminQRCodeProducerEventCtrl: AdminQRCodeProducerEventController, adminQRCodeConsumerEventCtrl: AdminQRCodeConsumerEventController, adminOrderSucceedEventCtrl: AdminOrderSucceedEventController) {
    get    ("/ticket-slots/fetch-by-owner") { ticketSlotsCtrl.fetchTicketSlotsByOwner(call) }
    get    ("/ticket-slots/get-qrcode") { ticketSlotsCtrl.getQrCode(call) }

    get     ("/ticket-slots/captcha-setting") { ticketSlotsCtrl.getCaptchaSetting(call) }
    post    ("/ticket-slots/transfer/init-session") { ticketSlotsCtrl.initSessionId(call) }
    post    ("/ticket-slots/transfer/send-otp") { ticketSlotsCtrl.sendTransferOTP(call) }
    post    ("/ticket-slots/transfer/resend-otp") { ticketSlotsCtrl.resendTransferOTP(call) }
    post    ("/ticket-slots/transfer/verify-otp") { ticketSlotsCtrl.verifyTransferOTP(call) }
    post    ("/ticket-slots/transfer") { ticketSlotsCtrl.transferTicketSlot(call) }

    // Private admin endpoints
    post    ("/private/ticket-slots/fetch-by-show") { ticketSlotsCtrl.fetchTicketSlotsByShow(call) }
    post    ("/private/ticket-slots/open-selected-slots") { ticketSlotsCtrl.updateSelectedTicketSlotsOptStatus(call, TicketSlotOptStatus.OPENED) }
    post    ("/private/ticket-slots/close-selected-slots") { ticketSlotsCtrl.updateSelectedTicketSlotsOptStatus(call, TicketSlotOptStatus.CLOSED) }



    // Admin QR Code Producer event management endpoints
    get     ("/private/ticket-slots/qrcode-producer-events/failed") { adminQRCodeProducerEventCtrl.getFailedEvents(call) }
    post    ("/private/ticket-slots/qrcode-producer-events/{eventId}/retry") { adminQRCodeProducerEventCtrl.retryEvent(call) }
    put     ("/private/ticket-slots/qrcode-producer-events/{eventId}/disable") { adminQRCodeProducerEventCtrl.disableEvent(call) }
    put     ("/private/ticket-slots/qrcode-producer-events/{eventId}/archive") { adminQRCodeProducerEventCtrl.archiveEvent(call) }

    // Admin QR Code Consumer event management endpoints
    get     ("/private/ticket-slots/qrcode-consumer-events/failed") { adminQRCodeConsumerEventCtrl.getFailedEvents(call) }
    post    ("/private/ticket-slots/qrcode-consumer-events/{eventId}/retry") { adminQRCodeConsumerEventCtrl.retryEvent(call) }
    put     ("/private/ticket-slots/qrcode-consumer-events/{eventId}/disable") { adminQRCodeConsumerEventCtrl.disableEvent(call) }
    put     ("/private/ticket-slots/qrcode-consumer-events/{eventId}/archive") { adminQRCodeConsumerEventCtrl.archiveEvent(call) }

    // Admin Order Succeed event management endpoints
    get     ("/private/ticket-slots/order-succeed-events/failed") { adminOrderSucceedEventCtrl.getFailedEvents(call) }
    get     ("/private/ticket-slots/order-succeed-events/failed/{id}") { adminOrderSucceedEventCtrl.getFailedEventById(call) }
    post    ("/private/ticket-slots/order-succeed-events/failed/{id}/retry") { adminOrderSucceedEventCtrl.retryFailedEvent(call) }
    patch   ("/private/ticket-slots/order-succeed-events/failed/{id}/disable") { adminOrderSucceedEventCtrl.disableFailedEvent(call) }
    patch   ("/private/ticket-slots/order-succeed-events/failed/{id}/enable") { adminOrderSucceedEventCtrl.enableFailedEvent(call) }
    get     ("/private/ticket-slots/order-succeed-events/failed/stats") { adminOrderSucceedEventCtrl.getFailedEventsStats(call) }
    post    ("/private/ticket-slots/order-succeed-events/retry-service/run") { adminOrderSucceedEventCtrl.runRetryService(call) }

}