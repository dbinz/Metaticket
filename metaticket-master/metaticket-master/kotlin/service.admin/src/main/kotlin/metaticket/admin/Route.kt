package metaticket.admin

import io.ktor.server.routing.*
import metaticket.admin.controller.*

fun Route.documentRoutes(
    sessionDraftCtrl: ShowSessionDraftController,
    sessionDraftPromotionCtrl: ShowSessionDraftPromotionController,
    sessionDraftOrderCustomFieldController: ShowSessionDraftOrderCustomFieldController,
) {

    post("/private/admin/show/session/draft/create") {
        sessionDraftCtrl.createSessionDraft(call)
    }

    post("/private/admin/show/session/draft/update") {
        sessionDraftCtrl.updateSessionDraft(call)
    }

    post("/private/admin/show/session/draft/update/payment") {
        sessionDraftCtrl.updateSessionDraftPayment(call)
    }

    post("/private/admin/show/session/draft/update/seatplan") {
        sessionDraftCtrl.updateSessionDraftSeatPlan(call)
    }

    post("/private/admin/show/session/draft/update/promotion") {
        sessionDraftPromotionCtrl.updateSessionDraftPromotion(call)
    }

    post("/private/admin/show/session/draft/update/mail/templates") {
        sessionDraftCtrl.updateSessionDraftMailTemplates(call)
    }

    post("/private/admin/show/session/draft/update/mail/templates") {
        sessionDraftCtrl.updateSessionDraftMailTemplates(call)
    }

    post("/private/admin/show/session/draft/create/promotion") {
        sessionDraftPromotionCtrl.createSessionDraftPromotion(call)
    }

    get("/private/admin/show/session/draft/promotions/fetch") {
        sessionDraftPromotionCtrl.loadSessionDraftPromotions(call)
    }

    get("/private/admin/show/sessions/draft/fetch") {
        sessionDraftCtrl.loadShowSessionsDraft(call)
    }

    get("/private/admin/show/session/draft/order/custom/fields/fetch") {
        sessionDraftOrderCustomFieldController.loadSessionDraftOrderCustomFields(call)
    }

    post("/private/admin/show/session/draft/create/order/custom/fields") {
        sessionDraftOrderCustomFieldController.createSessionDraftOrderCustomField(call)
    }

    post("/private/admin/show/session/draft/update/order/custom/fields") {
        sessionDraftOrderCustomFieldController.updateSessionDraftOrderCustomField(call)
    }

    post("/private/admin/show/session/draft/session/create") {
        sessionDraftCtrl.createShowSessionFromSessionDraft(call)
    }

    post("/private/admin/show/session/draft/delete") {
        sessionDraftCtrl.deleteSessionDraft(call)
    }
}

fun Route.reportFilterRoutes(
    reportFilterCtrl: ReportFilterController
) {
    get("/public/admin/report-filter/load-invoice-statistics/{reportKey}") {
        reportFilterCtrl.loadInvoiceStatistics(call)
    }

    post("/public/admin/report-filter/load-report/{reportKey}") {
        reportFilterCtrl.loadReport(call)
    }

    post("/private/admin/report-filter/create") {
        reportFilterCtrl.createReportFilter(call)
    }

    post("/private/admin/report-filter/remove") {
        reportFilterCtrl.deleteReportFilter(call)
    }

    post("/private/admin/report-filter/list") {
        reportFilterCtrl.loadReportFilterList(call)
    }

    post("/private/admin/report-filter/disable") {
        reportFilterCtrl.disableReportFilter(call)
    }

    post("/private/admin/report-filter/enable") {
        reportFilterCtrl.enableReportFilter(call)
    }
}

fun Route.reportGovRoutes(
    reportGovCtrl: ReportGovController
) {
    post("/admin/report-gov") {
        reportGovCtrl.getReportGov(call)
    }
}

fun Route.ticketBookingRoutes(
    ctrl: TicketBookingController
) {
    post("/private/admin/booking-ticket") {
        ctrl.bookingTicketFromFile(call)
    }
    get("/private/admin/booking-ticket/fetch") {
        ctrl.fetchBookingTicketByShowAndSession(call)
    }

}