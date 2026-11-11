package metaticket.editor.seatplanner

import com.mongodb.client.model.Filters
import com.mongodb.client.model.Projections
import com.mongodb.client.model.Updates
import com.mongodb.client.result.UpdateResult
import com.mongodb.kotlin.client.coroutine.MongoCollection
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.withContext
import metaticket.editor.seatplanner.exception.DocumentNotFoundException
import metaticket.editor.seatplanner.pojo.FetchSeatPlanCategoryResult
import metaticket.editor.seatplanner.pojo.PlanItem
import metaticket.editor.seatplanner.pojo.SeatPlanDocument
import org.bson.conversions.Bson
import org.bson.types.ObjectId

class DatabaseService(
    private val col : MongoCollection<SeatPlanDocument>
) {

    suspend fun create(entity : SeatPlanDocument) : String {
        val result = withContext(Dispatchers.IO) { col.insertOne(entity) }
        return result.insertedId.toString()
    }

    suspend fun delete(docId: String): Long {
        val filter = Filters.eq("_id", ObjectId(docId))
        val result = withContext(Dispatchers.IO) { col.deleteOne(filter) }
        return result.deletedCount
    }

    suspend fun loadDocCategoryById(id: String): FetchSeatPlanCategoryResult? {
        val filter = Filters.eq("_id", ObjectId(id))
        val projectionFields= Projections.fields(
            Projections.include(
                "_id",
                SeatPlanDocument::displayName.name,
                SeatPlanDocument::catNames.name,
                SeatPlanDocument::baseImgUrl.name,
            ),
        )
        return withContext(Dispatchers.IO) {
            col.withDocumentClass<FetchSeatPlanCategoryResult>()
                .find(filter)
                .projection(projectionFields)
        }.firstOrNull()
    }

    suspend fun loadAllDocCategory(): List<FetchSeatPlanCategoryResult> {
        val projectionFields= Projections.fields(
            Projections.include(
                SeatPlanDocument::id.name,
                SeatPlanDocument::displayName.name,
                SeatPlanDocument::catNames.name,
                SeatPlanDocument::baseImgUrl.name,
            ),
        )
        return withContext(Dispatchers.IO) {
            col.withDocumentClass<FetchSeatPlanCategoryResult>()
                .find()
                .projection(projectionFields)
        }.toList()
    }

    suspend fun loadDocument(docId: String): SeatPlanDocument? {
        val filter = Filters.eq("_id", ObjectId(docId))
        return withContext(Dispatchers.IO) { col.find(filter)}.firstOrNull()
    }

    suspend fun checkExist(docId : String) : Boolean {
        val filter = Filters.eq("_id", ObjectId(docId))
        return false
    }

    suspend fun replaceItems(docId : String, items : List<PlanItem>) : UpdateResult {
        val filter = Filters.eq("_id", ObjectId(docId))
        val update = Updates.set(SeatPlanDocument::items.name, items)
        return withContext(Dispatchers.IO) { col.updateOne(filter, update) }
    }

    suspend fun saveItems(docId : String, added : List<PlanItem>, updated : List<PlanItem>, removed : List<PlanItem>) {
        loadDocument(docId) ?: throw DocumentNotFoundException(docId)

        val filter = Filters.eq("_id", ObjectId(docId))
        var pull : Bson? = null

        if (updated.isNotEmpty()) {
            val replaceIds = (updated + removed).map { it.id }

            pull = Updates.pull("items", Filters.`in`("id" , replaceIds))

            withContext(Dispatchers.IO) { col.updateOne(filter, pull) }
        }

        val push = Updates.pushEach(SeatPlanDocument::items.name, added + updated)
        withContext(Dispatchers.IO) { col.updateOne(filter, push) }

    }

    suspend fun updateStyle(docId : String, style: String) : UpdateResult {
        val filter = Filters.eq("_id", ObjectId(docId))
        val update = Updates.set(SeatPlanDocument::styleSheet.name, style)
        return withContext(Dispatchers.IO) { col.updateOne(filter, update) }
    }

    suspend fun updateBaseImgUrl(docId: String, url: String): UpdateResult {
        val filter = Filters.eq("_id", ObjectId(docId))
        val update = Updates.set(SeatPlanDocument::baseImgUrl.name, url)
        return withContext(Dispatchers.IO) { col.updateOne(filter, update) }
    }

    suspend fun updateDef(docId : String, def: String) : UpdateResult {
        val filter = Filters.eq("_id", ObjectId(docId))
        val update = Updates.set(SeatPlanDocument::defs.name, def)
        return withContext(Dispatchers.IO) { col.updateOne(filter, update) }
    }

    suspend fun updateName(docId : String, name: String) : UpdateResult {
        val filter = Filters.eq("_id", ObjectId(docId))
        val update = Updates.set(SeatPlanDocument::displayName.name, name)
        return withContext(Dispatchers.IO) { col.updateOne(filter, update) }
    }

    suspend fun updateCategory(docId : String, categories: List<String>) : UpdateResult {
        val filter = Filters.eq("_id", ObjectId(docId))
        val update = Updates.set(SeatPlanDocument::catNames.name, categories)
        return withContext(Dispatchers.IO) { col.updateOne(filter, update) }
    }
}
