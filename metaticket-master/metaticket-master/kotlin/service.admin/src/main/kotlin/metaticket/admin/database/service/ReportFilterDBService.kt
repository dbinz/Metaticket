package metaticket.admin.database.service

import com.mongodb.client.model.Filters
import com.mongodb.client.model.FindOneAndUpdateOptions
import com.mongodb.client.model.ReturnDocument
import com.mongodb.client.model.Updates
import com.mongodb.kotlin.client.coroutine.MongoCollection
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.withContext
import metaticket.admin.pojo.db.ReportFilterDocument
import metaticket.admin.pojo.db.ReportFilterStatus
import org.bson.types.ObjectId
import java.util.*

/**
 * @author dongcatchay
 */
class ReportFilterDBService(
    private val col: MongoCollection<ReportFilterDocument>,
) {
    suspend fun insertReportFilter(doc: ReportFilterDocument): String? {
        return withContext(Dispatchers.IO) { col.insertOne(doc) }.insertedId?.toString()
    }

    suspend fun removeReportFilter(id: String) {
        val filters = Filters.eq("_id", ObjectId(id))
        withContext(Dispatchers.IO) { col.deleteOne(filters) }
    }

    suspend fun loadReportFilterByReportKey(reportKey: String): ReportFilterDocument? {
        val filters = Filters.eq("reportKey", reportKey)
        return withContext(Dispatchers.IO) { col.find(filters) }.firstOrNull()
    }

    suspend fun loadReportFilterList(
        showId: String? = null,
        sessionId: String? = null,
        username: String? = null,
    ): List<ReportFilterDocument> {
        val f = listOfNotNull(
            showId?.let { Filters.eq("showId", ObjectId(it)) },
            sessionId?.let { Filters.eq("sessionId", ObjectId(it)) },
            username?.let { Filters.eq("username", it) },
        )
        if (f.isEmpty()) return withContext(Dispatchers.IO) { col.find() }.toList()

        return withContext(Dispatchers.IO) { col.find(Filters.and(f)) }.toList()
    }

    suspend fun updateReportFilterStatus(reportId: String, status: ReportFilterStatus): ReportFilterDocument? {
        val filters = Filters.eq("_id", ObjectId(reportId))
        val updates = Updates.combine(listOfNotNull(
            Updates.set(ReportFilterDocument::status.name, status),
            Updates.set(ReportFilterDocument::lastUpdated.name, Date()),
            Updates.inc(ReportFilterDocument::version.name, 1),
        ))
        val option = FindOneAndUpdateOptions().returnDocument(ReturnDocument.AFTER)
        return withContext(Dispatchers.IO) { col.findOneAndUpdate(filters, updates, option) }
    }
}