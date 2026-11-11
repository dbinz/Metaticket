package metaticket.admin.pojo.response

import com.fasterxml.jackson.databind.ObjectMapper
import metaticket.admin.pojo.db.ReportFilterDocument
import metaticket.admin.pojo.db.ReportFilterStatus
import metaticket.admin.utils.decodeFilter
import metaticket.order.datastructure.response.OrderResponse
import metaticket.order.request.LoadInvoiceStatisticsRequest
import metaticket.payment.response.PaymentResponse

data class ReportFilterResponse constructor(
    val id: String,
    val reportKey: String,
    val reportName: String,
    val username: String,
    val status: ReportFilterStatus,
    val filter: LoadInvoiceStatisticsRequest,
    val createdAt: Long,
)

data class LoadReportResponse constructor(
    val orders: List<OrderResponse>,
    val payments: List<PaymentResponse>
)

fun ReportFilterDocument.toResponse(secret: String, jsonMapper: ObjectMapper): ReportFilterResponse {
    return ReportFilterResponse(
        id = this.id,
        reportKey = reportKey,
        username = username,
        reportName = reportName,
        status = status,
        filter = decodeFilter(secret, jsonMapper),
        createdAt = createdAt.time,
    )
}