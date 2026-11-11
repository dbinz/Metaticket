package metaticket.accessory.pojo.response

import metaticket.accessory.pojo.db.AccessoryReportFilterDocument
import metaticket.accessory.pojo.db.AccessoryReportFilterCriteria

/** Filter criteria on admin create / list / load-by-key responses. */
data class AdminReportFilterCriteriaResponse(
    val showId: String?,
    val startAt: Long?,
    val endAt: Long?,
)

/** Admin Filters list/detail row. */
data class AdminReportFilterItem(
    val id: String,
    val reportKey: String,
    val reportName: String,
    val username: String,
    val status: String,
    val showId: String?,
    val filter: AdminReportFilterCriteriaResponse,
    val createdAt: Long,
)

/** Maps a persisted preset to the admin DTO. */
fun AccessoryReportFilterDocument.toAdminItem() = AdminReportFilterItem(
    id = id,
    reportKey = reportKey,
    reportName = reportName,
    username = username,
    status = status.name,
    showId = showId,
    filter = filter.toAdminResponse(),
    createdAt = createdAt.time,
)

private fun AccessoryReportFilterCriteria.toAdminResponse() = AdminReportFilterCriteriaResponse(
    showId = showId,
    startAt = startAt,
    endAt = endAt,
)
