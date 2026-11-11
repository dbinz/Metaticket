package metaticket.accessory.pojo.db

import org.bson.BsonType
import org.bson.codecs.pojo.annotations.BsonId
import org.bson.codecs.pojo.annotations.BsonRepresentation
import org.bson.types.ObjectId
import java.util.Date

/** Active presets can be opened via shareable `reportKey` link. */
enum class AccessoryReportFilterStatus { Active, Inactive }

/**
 * Saved query criteria for accessory sales statistics (admin "Filters" / shareable report link).
 * Dates are epoch millis; null means unbounded.
 */
data class AccessoryReportFilterCriteria(
    @BsonRepresentation(BsonType.OBJECT_ID)
    val showId: String? = null,
    val startAt: Long? = null,
    val endAt: Long? = null,
)

/**
 * Persisted accessory report filter preset (collection `accessory-report-filter`).
 * Share link uses [reportKey] (UUID), not the Mongo id.
 */
data class AccessoryReportFilterDocument(
    @BsonId @BsonRepresentation(BsonType.OBJECT_ID)
    var id: String = ObjectId().toHexString(),
    val reportKey: String,
    val reportName: String,
    val username: String = "",
    var status: AccessoryReportFilterStatus = AccessoryReportFilterStatus.Active,
    @BsonRepresentation(BsonType.OBJECT_ID)
    val showId: String? = null,
    val filter: AccessoryReportFilterCriteria,
    val version: Int = 1,
    var lastUpdated: Date = Date(),
    val createdAt: Date = Date(),
)
