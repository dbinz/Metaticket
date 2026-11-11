package metaticket.accessory.database

import com.mongodb.client.model.Filters
import com.mongodb.client.model.Indexes
import com.mongodb.client.model.IndexOptions
import com.mongodb.client.model.Sorts
import com.mongodb.client.model.Updates
import com.mongodb.kotlin.client.coroutine.MongoCollection
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import metaticket.accessory.logger
import metaticket.accessory.pojo.db.AccessoryReportFilterDocument
import metaticket.accessory.pojo.db.AccessoryReportFilterStatus
import org.bson.types.ObjectId
import java.util.Date

/**
 * Owns `accessory-report-filter` presets used by admin Filters / shareable statistic links.
 */
class AccessoryReportFilterDatabaseService(
    private val col: MongoCollection<AccessoryReportFilterDocument>,
) {
    init {
        CoroutineScope(Dispatchers.Unconfined).launch {
            val keyIdx = col.createIndex(
                Indexes.ascending(AccessoryReportFilterDocument::reportKey.name),
                IndexOptions().unique(true),
            )
            logger.info("created accessory-report-filter reportKey index {}", keyIdx)
            val showIdx = col.createIndex(
                Indexes.descending(AccessoryReportFilterDocument::createdAt.name),
            )
            logger.info("created accessory-report-filter createdAt index {}", showIdx)
        }
    }

    /** Inserts a new preset and returns its id, or null on failure. */
    suspend fun insert(doc: AccessoryReportFilterDocument): String? = withContext(Dispatchers.IO) {
        col.insertOne(doc)
        doc.id
    }

    /** Loads one preset by shareable [reportKey], or null. */
    suspend fun findByReportKey(reportKey: String): AccessoryReportFilterDocument? = withContext(Dispatchers.IO) {
        if (reportKey.isBlank()) return@withContext null
        col.find(Filters.eq(AccessoryReportFilterDocument::reportKey.name, reportKey.trim())).firstOrNull()
    }

    /** Lists presets, optionally scoped to one show (newest first). */
    suspend fun list(showId: String?): List<AccessoryReportFilterDocument> = withContext(Dispatchers.IO) {
        val filter = if (!showId.isNullOrBlank() && ObjectId.isValid(showId)) {
            Filters.eq(AccessoryReportFilterDocument::showId.name, ObjectId(showId))
        } else {
            Filters.empty()
        }
        col.find(filter).sort(Sorts.descending(AccessoryReportFilterDocument::createdAt.name)).toList()
    }

    /** Updates Active/Inactive; returns updated doc or null if missing. */
    suspend fun updateStatus(id: String, status: AccessoryReportFilterStatus): AccessoryReportFilterDocument? =
        withContext(Dispatchers.IO) {
            if (!ObjectId.isValid(id)) return@withContext null
            col.findOneAndUpdate(
                Filters.eq("_id", ObjectId(id)),
                Updates.combine(
                    Updates.set(AccessoryReportFilterDocument::status.name, status),
                    Updates.set(AccessoryReportFilterDocument::lastUpdated.name, Date()),
                    Updates.inc(AccessoryReportFilterDocument::version.name, 1),
                ),
            )
            col.find(Filters.eq("_id", ObjectId(id))).firstOrNull()
        }

    /** Hard-deletes a preset by id. */
    suspend fun delete(id: String): Boolean = withContext(Dispatchers.IO) {
        if (!ObjectId.isValid(id)) return@withContext false
        col.deleteOne(Filters.eq("_id", ObjectId(id))).deletedCount == 1L
    }
}
