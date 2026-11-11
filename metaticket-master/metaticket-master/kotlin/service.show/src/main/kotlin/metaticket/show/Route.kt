package metaticket.show

import io.ktor.server.routing.*
import metaticket.show.controller.CityController
import metaticket.show.controller.ShowController

fun Route.documentRoutes(showCtrl : ShowController, cityCtrl: CityController) {
    get("/public/show/fetch") { showCtrl.fetchShowById(call) }
    get("/public/show/fetch/slug") { showCtrl.fetchShowBySlug(call) }
    get("/public/show/fetch/all") { showCtrl.fetchAllShow(call) }
    get("/public/show/session/fetch") { showCtrl.loadSession(call) }
    get("/public/show/event-category/fetch") { showCtrl.loadEventCategory(call) }

    get("/public/show/sessions/fetch") { showCtrl.loadShowSessions(call) }

    //city
    get("/public/show/city/fetch/all") { cityCtrl.fetchAllCities(call) }
    get("/public/show/city/fetch") { cityCtrl.fetchCity(call) }

    get("/private/show/sync-metasearch") { showCtrl.syncShowDataToMetasearch(call) }
    post("/private/show/session/link") { showCtrl.generateLink(call) }
    post("/private/show/detail/link") { showCtrl.generateShowDetailLink(call) }
    post("/private/show/create") { showCtrl.createShow(call) }
    post("/private/show/update") { showCtrl.updateShow(call) }
    get("/private/show/pin/banner") { showCtrl.getBannerPins(call) }
    post("/private/show/pin/banner/refresh") { showCtrl.refreshBannerPins(call) }
    post("/private/show/pin/banner/confirm") { showCtrl.confirmBannerPins(call) }
    get("/private/show/pin/candidates") { showCtrl.searchPinCandidates(call) }
    post("/private/show/upload/image") { showCtrl.uploadImage(call) }
    post("/private/show/session/update") { showCtrl.updateSession(call) }
    post("/private/show/session/update/payment") { showCtrl.updateSessionPayment(call) }
    post("/private/show/session/update/mail/templates") { showCtrl.updateSessionMailTemplate(call) }
    post("/private/show/session/update/seatplan") { showCtrl.updateSessionSeatPlan(call) }
    post("/private/show/session/approval/request") { showCtrl.requestApproveSession(call) }
    post("/private/show/session/approval/approve") { showCtrl.approveSession(call) }
    post("/private/show/session/approval/reject") { showCtrl.rejectSession(call) }
    post("/private/show/session/sale/start") { showCtrl.startSession(call) }
    post("/private/show/session/sale/pause") { showCtrl.pauseSession(call) }
    post("/private/show/session/sale/stop") { showCtrl.stopSession(call) }
    post("/private/show/session/terminate") { showCtrl.terminateSession(call) }
    post("/private/show/session/sale/mode/change") { showCtrl.changeSessionSaleMode(call) }
}
