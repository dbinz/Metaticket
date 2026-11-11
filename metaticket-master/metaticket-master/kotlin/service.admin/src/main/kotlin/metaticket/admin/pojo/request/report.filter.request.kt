package metaticket.admin.pojo.request

import metaticket.order.request.LoadInvoiceStatisticsRequest


data class CreateReportFilterRequest(
    val reportName: String,
    val username: String,
    val filters: LoadInvoiceStatisticsRequest,
)

data class LoadReportFilterListRequest(
    val showId: String? = null,
    val sessionId: String? = null,
    val username: String? = null,
)

data class DeleteReportFilterRequest(
    val reportId: String
)

data class EnableReportFilterRequest(
    val reportId: String
)

data class DisableReportFilterRequest(
    val reportId: String
)