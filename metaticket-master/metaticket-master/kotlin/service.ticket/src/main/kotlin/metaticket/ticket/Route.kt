package metaticket.ticket

import io.ktor.server.routing.*
import metaticket.ticket.controller.CalendarTicketController
import metaticket.ticket.controller.CategoryController
import metaticket.ticket.controller.TicketDocumentController
import metaticket.ticket.controller.TicketReservationController

fun Route.ticketDocumentRoutes(ticketDocCtrl: TicketDocumentController) {
    route("/ticket/doc/fetch"){
        post    { ticketDocCtrl.loadTicketDocByIdsCheckSession(call) }
        get     { ticketDocCtrl.loadTicketDocByIdCheckSession(call) }
    }
    route("/private/ticket/doc/fetch"){
        post    { ticketDocCtrl.loadTicketDocByIds(call) }
        get     { ticketDocCtrl.loadTicketDocById(call) }
    }

    // POST METHOD IS FOR BACKWARD COMPATIBLE
    // WITH FRONTEND AND ADMIN, need to be updated
    post    ("/ticket/doc/fetch-all")   { ticketDocCtrl.loadTicketDocByShowIdAndSessionIdCheckSession(call) }
    post    ("/private/ticket/doc/fetch-all")   { ticketDocCtrl.loadTicketDocByShowIdAndSessionId(call) }
    post    ("/ticket/doc/fetch-time")  { ticketDocCtrl.loadTicketDocByTime(call) }

    /**
     * GET METHODS for Homepage to support proper caching
     */
    get    ("/ticket/doc/fetch-all")   { ticketDocCtrl.versionedLoadTicketDocByShowIdAndSessionIdCheckSession(call) }
    get    ("/ticket/doc/fetch-time")  { ticketDocCtrl.versionedLoadTicketDocByTime(call) }

    post    ("/private/ticket/doc/create")      { ticketDocCtrl.createTicketDoc(call) }
    post    ("/private/ticket/doc/update")      { ticketDocCtrl.updateTicketDoc(call) }
    post    ("/private/ticket/doc/update-slot")      { ticketDocCtrl.updateTicketSlot(call) }
    get     ("/private/ticket/doc/count/by-show") { ticketDocCtrl.countTicketOfShow(call) }
    post    ("/private/ticket/doc/delete-by-cat") { ticketDocCtrl.deleteTicketByCat(call) }
}

fun Route.reservationRoutes(ticketReservationCtrl: TicketReservationController) {
    post    ("/ticket/reservation/create")  { ticketReservationCtrl.createTicketReservation(call) }
    get     ("/ticket/reservation/reserve") { ticketReservationCtrl.reserveTicket(call) }
    get     ("/ticket/reservation/release") { ticketReservationCtrl.releaseReservation(call) }
    get   ("/ticket/reservation/fetch")   { ticketReservationCtrl.fetchSingleTicketReservation(call) }

    get   ("/private/ticket/reservation/fetch")   { ticketReservationCtrl.fetchSingleTicketReservationPrivate(call) }
    post    ("/private/ticket/reservation/fetch")    { ticketReservationCtrl.fetchListTicketReservation(call) }
    post    ("/private/ticket/reservation/fetch-by-ticket") { ticketReservationCtrl.fetchByTicket(call) }
    get     ("/private/ticket/reservation/unlock") { ticketReservationCtrl.unlockReservation(call) }
    post    ("/private/ticket/reservation/lock-ticket")  { ticketReservationCtrl.lockTicket(call) }
    post    ("/private/ticket/reservation/release-expired")  { ticketReservationCtrl.releaseExpiredReservation(call) }
}

fun Route.calendarRoutes(calendarCtrl: CalendarTicketController) {
    get("/ticket/calendar/month") { calendarCtrl.publicMonth(call) }

    get("/private/ticket/calendar/month") { calendarCtrl.adminMonth(call) }
    post("/private/ticket/calendar/generate") { calendarCtrl.generate(call) }
    post("/private/ticket/calendar/bulk-update") { calendarCtrl.bulkUpdate(call) }
    post("/private/ticket/calendar/delete") { calendarCtrl.delete(call) }
}

fun Route.categoryRoutes(categoryCtrl: CategoryController) {
    post("/private/ticket/category/mapping/create") { categoryCtrl.createCategoryMapping(call) }
    post("/private/ticket/category/mapping/add-update") { categoryCtrl.addOrUpdateCategoryMapping(call) }
    post("/private/ticket/category/mapping/delete") { categoryCtrl.deleteCategoryMapping(call) }
    get("/private/ticket/category/mapping/load-by-show-session") { categoryCtrl.loadCategoryMappingByShowAndSession(call) }
}