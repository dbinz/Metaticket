package metaticket.accessory.pojo.request

/**
 * Body for POST `/private/accessory/report-filter/create`.
 * [username] is optional display of who saved the preset.
 */
data class AdminReportFilterCreateRequest(
    val reportName: String? = null,
    val username: String? = null,
    val showId: String? = null,
    val startAt: Long? = null,
    val endAt: Long? = null,
)

/** Body for enable / disable / delete of a saved filter (id = Mongo ObjectId hex). */
data class AdminReportFilterIdRequest(
    val reportId: String? = null,
)
