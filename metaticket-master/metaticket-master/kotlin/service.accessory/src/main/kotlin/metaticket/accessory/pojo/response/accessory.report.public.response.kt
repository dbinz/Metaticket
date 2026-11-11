package metaticket.accessory.pojo.response

/**
 * Public partner-facing sales report (no admin chrome).
 * Served by GET `/public/accessory/report/{reportKey}`.
 */
data class PublicSalesReportResponse(
    val reportKey: String,
    val reportName: String,
    val showId: String?,
    val showCode: String?,
    val showName: String?,
    val startAt: Long?,
    val endAt: Long?,
    val summary: AdminSalesSummaryResponse,
    val byProduct: List<AdminProductSalesRow>,
)
