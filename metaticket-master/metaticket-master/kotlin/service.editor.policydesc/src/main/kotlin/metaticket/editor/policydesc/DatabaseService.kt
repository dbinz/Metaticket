package metaticket.editor.policydesc

import com.mongodb.client.model.Filters
import com.mongodb.client.model.Projections
import com.mongodb.kotlin.client.coroutine.MongoCollection
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.withContext
import metaticket.editor.policydesc.pojo.db.policydesc.FetchSeatPlanCategoryResult
import metaticket.editor.policydesc.pojo.db.policydesc.PolicyDescDocument
import org.bson.types.ObjectId

class DatabaseService(val col : MongoCollection<PolicyDescDocument>) {

    suspend fun loadDocument(docId: String): PolicyDescDocument? {
        val filter = Filters.eq("_id", ObjectId(docId))
        return col.find(filter).firstOrNull()
    }

    suspend fun loadDocCategoryById(id: String): FetchSeatPlanCategoryResult? {
        val filter = Filters.eq("_id", ObjectId(id))
        val projectionFields= Projections.fields(
            Projections.include(
                "_id",
                PolicyDescDocument::displayName.name,
                PolicyDescDocument::catNames.name,
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
                PolicyDescDocument::id.name,
                PolicyDescDocument::displayName.name,
                PolicyDescDocument::catNames.name,
            ),
        )
        return withContext(Dispatchers.IO) {
            col.withDocumentClass<FetchSeatPlanCategoryResult>()
                .find()
                .projection(projectionFields)
        }.toList()
    }
}
