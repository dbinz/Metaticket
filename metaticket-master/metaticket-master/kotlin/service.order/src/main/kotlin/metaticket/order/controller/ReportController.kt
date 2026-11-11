package metaticket.order.controller

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import metaticket.common.logger.Logging
import metaticket.order.database.service.OrderDBService
import metaticket.order.datastructure.response.InvoiceStatisticsByCategory
import metaticket.order.datastructure.response.InvoiceStatisticsBySession
import metaticket.order.datastructure.response.InvoiceStatisticsByShow
import metaticket.order.pojo.response.toResponse
import metaticket.order.request.LoadInvoiceStatisticsRequest
import metaticket.order.request.LoadReportRequest


/**
 *
 * @author dongcatchay
 */
class ReportController(
    private val orderDB: OrderDBService,
): Logging {

    suspend fun loadInvoiceStatistics(call: ApplicationCall) {
        val req = call.receive<LoadInvoiceStatisticsRequest>()
        val order = orderDB.loadOrder(req.startDate, req.endDate, req.status, req.showId, req.sessionId, req.visitDateFrom, req.visitDateTo)

        val list = order.groupBy { it.showId }.map { lsShow ->
            val byShow = InvoiceStatisticsByShow(lsShow.key, 0, 0, 0.0,  0.0, mutableListOf())

            lsShow.value.groupBy { it.sessionId }.forEach { lsSession ->
                val bySession = InvoiceStatisticsBySession(lsSession.key, 0, 0, 0, 0.0f, 0.0, mutableListOf())

                lsSession.value.map { it.tickets }.flatten().groupBy { it.priceCat }.forEach { lsCat ->
                    val byCat = InvoiceStatisticsByCategory(lsCat.key, 0, 0, 0,)

                    lsCat.value.forEach {
                        byCat.basePrice = it.basePrice
                        byCat.totalTicket += it.quantity
                        byCat.totalPrice += it.basePrice * it.quantity
                    }

                    bySession.totalTicket += byCat.totalTicket
                    bySession.totalPrice += byCat.totalPrice
                    bySession.revenue += byCat.totalPrice
                    (bySession.byCategory as MutableList).add(byCat)
                }

                bySession.discount = lsSession.value.sumOf { order -> order.promotionDetails.sumOf { it.priceDiscount } }

                bySession.revenue -= bySession.discount
                //bySession.revenue -= bySession.revenue * bySession.commission

                byShow.totalTicket += bySession.totalTicket
                byShow.totalPrice += bySession.totalPrice
                byShow.discount += bySession.discount
                byShow.revenue += bySession.revenue
                (byShow.bySession as MutableList).add(bySession)
            }
            byShow
        }
        call.respond(HttpStatusCode.OK, list)
    }

    suspend fun loadReport(call: ApplicationCall) {
        val req = call.receive<LoadReportRequest>()
        val orders = orderDB.loadOrder(req.startDate, req.endDate, req.status, req.showId, req.sessionId, req.visitDateFrom, req.visitDateTo)
        return call.respond(HttpStatusCode.OK, orders.map { it.toResponse() })
    }
}
