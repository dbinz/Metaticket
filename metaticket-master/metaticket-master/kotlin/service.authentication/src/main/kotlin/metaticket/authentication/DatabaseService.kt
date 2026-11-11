package metaticket.authentication

import com.mongodb.client.model.Filters
import com.mongodb.client.model.FindOneAndUpdateOptions
import com.mongodb.client.model.ReturnDocument
import com.mongodb.client.model.Updates
import com.mongodb.kotlin.client.coroutine.MongoCollection
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.withContext
import metaticket.authentication.pojo.db.AuthInformationDocument
import org.bson.types.ObjectId
import java.util.*

class DatabaseService(
    private val authInformationDocument: MongoCollection<AuthInformationDocument>
) {

    suspend fun registerUser(entity : AuthInformationDocument) : String {
        val result = withContext(Dispatchers.IO) { authInformationDocument.insertOne(entity) }
        return result.insertedId!!.toString()
    }

    suspend fun getUserByEmail(email: String): AuthInformationDocument? {
        val filter = Filters.eq(AuthInformationDocument::email.name, email)
        return withContext(Dispatchers.IO) { authInformationDocument.find(filter) }.firstOrNull()
    }

    suspend fun loadUser(userId: String): AuthInformationDocument? {
        val filter = Filters.eq("_id", ObjectId(userId))
        return withContext(Dispatchers.IO) { authInformationDocument.find(filter) }.firstOrNull()
    }

    suspend fun updateUserLoggedIn(userId: String): AuthInformationDocument? {
        val filter = Filters.eq("_id", ObjectId(userId))
        val updates = Updates.combine(
                Updates.set(AuthInformationDocument::loggedIn.name, true),
                Updates.set(AuthInformationDocument::lastModified.name, Date())
        )

        val options = FindOneAndUpdateOptions().returnDocument(ReturnDocument.AFTER)
        return withContext(Dispatchers.IO) { authInformationDocument.findOneAndUpdate(filter, updates, options) }
    }

    suspend fun updateUserLogout(userId: String): AuthInformationDocument? {
        val filter = Filters.eq("_id", ObjectId(userId))
        val updates = Updates.combine(
                Updates.set(AuthInformationDocument::loggedIn.name, false),
                Updates.set(AuthInformationDocument::lastModified.name, Date())
        )

        val options = FindOneAndUpdateOptions().returnDocument(ReturnDocument.AFTER)
        return withContext(Dispatchers.IO) { authInformationDocument.findOneAndUpdate(filter, updates, options) }
    }
}