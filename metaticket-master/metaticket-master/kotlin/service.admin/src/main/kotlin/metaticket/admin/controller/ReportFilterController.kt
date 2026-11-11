package metaticket.admin.controller

import com.fasterxml.jackson.databind.ObjectMapper
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import metaticket.admin.database.service.ReportFilterDBService
import metaticket.admin.gateway.PaymentGateway
import metaticket.admin.peer.OrderClient
import metaticket.admin.pojo.db.ReportFilterDocument
import metaticket.admin.pojo.db.ReportFilterStatus
import metaticket.admin.pojo.request.*
import metaticket.admin.pojo.response.LoadReportResponse
import metaticket.admin.pojo.response.toResponse
import metaticket.admin.utils.decodeFilter
import metaticket.admin.utils.generateToken
import metaticket.common.logger.Logging
import metaticket.order.request.LoadReportRequest
import java.util.*

/**
 * @author dongcatchay
 */
class ReportFilterController(
    private val db: ReportFilterDBService,
    private val orderClient: OrderClient,
    private val paymentGateway: PaymentGateway,
    private val env: ApplicationEnvironment,
    private val jsonMapper: ObjectMapper,
): Logging {

    private val rfSecretKey: String = env.config.property("secretKey").getString()

    suspend fun createReportFilter(call: ApplicationCall) {
        val req = call.receive<CreateReportFilterRequest>()
        val reportKey = UUID.randomUUID().toString()
        val reportDoc = ReportFilterDocument(
            showId = req.filters.showId,
            sessionId = req.filters.sessionId,
            reportName = req.reportName,
            username = req.username,
            status = ReportFilterStatus.Active,
            filter = generateToken(req.filters, rfSecretKey),
            visitDateFrom = req.filters.visitDateFrom,
            visitDateTo = req.filters.visitDateTo,
            reportKey = reportKey,
        )
        val inserted = db.insertReportFilter(reportDoc) ?: kotlin.run {
            logger.error("insert report filter failed, request {}", call.request)
            return call.respond(HttpStatusCode.InternalServerError, "Lỗi hệ thống")
        }

        logger.info("report filter {} inserted", reportDoc)
        call.respond(HttpStatusCode.OK, reportKey)
    }

    suspend fun deleteReportFilter(call: ApplicationCall) {
        val req = call.receive<DeleteReportFilterRequest>()
        db.removeReportFilter(req.reportId)
        logger.info("report filter {} is removed", req.reportId)
        call.respond(HttpStatusCode.OK, req.reportId)
    }

    suspend fun loadReportFilterList(call: ApplicationCall) {
        val req = call.receive<LoadReportFilterListRequest>()
        val reportFilterList = db.loadReportFilterList(
            showId = req.showId,
            sessionId = req.sessionId,
            username = req.username,
        )
        return call.respond(HttpStatusCode.OK, reportFilterList.map { it.toResponse(rfSecretKey, jsonMapper) })
    }

    suspend fun enableReportFilter(call: ApplicationCall) {
        val req = call.receive<EnableReportFilterRequest>()
        val reportFilterDoc = db.updateReportFilterStatus(
            reportId = req.reportId,
            status = ReportFilterStatus.Active,
        ) ?: kotlin.run {
            logger.error("Enable report filter failed, request {}", call.request)
            return call.respond(HttpStatusCode.NotFound, "không tìm thấy report filter")
        }
        logger.info("report filter {} enabled", reportFilterDoc.id)
        return call.respond(HttpStatusCode.OK, reportFilterDoc.toResponse(rfSecretKey, jsonMapper))
    }

    suspend fun disableReportFilter(call: ApplicationCall) {
        val req = call.receive<DisableReportFilterRequest>()
        val reportFilterDoc = db.updateReportFilterStatus(
            reportId = req.reportId,
            status = ReportFilterStatus.Inactive,
        ) ?: kotlin.run {
            logger.error("Disable report filter failed, request {}", call.request)
            return call.respond(HttpStatusCode.NotFound, "không tìm thấy report filter")
        }
        logger.info("report filter {} disabled", reportFilterDoc.id)
        return call.respond(HttpStatusCode.OK, reportFilterDoc.toResponse(rfSecretKey, jsonMapper))
    }

    suspend fun loadInvoiceStatistics(call: ApplicationCall) {
        val reportKey = call.parameters["reportKey"] ?: kotlin.run {
            return call.respond(HttpStatusCode.BadRequest, "Thiếu report key")
        }

        val reportFilter = db.loadReportFilterByReportKey(reportKey) ?: run {
            return call.respond(HttpStatusCode.BadRequest, "Không tìm thấy report filter")
        }

        if (reportFilter.status === ReportFilterStatus.Inactive) {
            return call.respond(HttpStatusCode.NotFound, "report filter không hoạt động")
        }

        val statistics = orderClient.loadInvoiceStatistics(reportFilter.decodeFilter(rfSecretKey, jsonMapper))
        return call.respond(HttpStatusCode.OK, statistics)
    }

    suspend fun loadReport(call: ApplicationCall) {
        val reportKey = call.parameters["reportKey"] ?: kotlin.run {
            return call.respond(HttpStatusCode.BadRequest, "Missing report key")
        }

        val req = call.receive<LoadReportRequest>()

        val reportFilterDoc = db.loadReportFilterByReportKey(reportKey) ?: run {
            return call.respond(HttpStatusCode.BadRequest, "Not found report filter")
        }

        if (reportFilterDoc.status === ReportFilterStatus.Inactive) {
            return call.respond(HttpStatusCode.NotFound, "report filter không hoạt động")
        }

        val reportFilter = reportFilterDoc.decodeFilter(rfSecretKey, jsonMapper)
        if (
            req.startDate != reportFilter.startDate ||
            req.endDate != reportFilter.endDate ||
            (reportFilter.showId != null && req.showId != reportFilter.showId) ||
            (reportFilter.sessionId != null && req.sessionId != reportFilter.sessionId)
        ) {
            return call.respond(HttpStatusCode.BadRequest, "Report filters không khớp")
        }

        // Route public chỉ cần reportKey: ngày tham quan luôn lấy từ bộ lọc đã lưu, không theo body client gửi
        val orders = orderClient.loadReport(
            req.copy(visitDateFrom = reportFilter.visitDateFrom, visitDateTo = reportFilter.visitDateTo)
        )
        val paymentIds = orders.mapNotNull { it.paymentId }
        val payments = paymentGateway.loadPaymentByIds(paymentIds)

        return call.respond(HttpStatusCode.OK, LoadReportResponse(orders, payments))
    }
}