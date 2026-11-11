package metaticket.customer.user.repository

import com.mongodb.client.model.Updates
import metaticket.customer.user.model.CustomerUserPOJO
import org.bson.types.ObjectId
import org.litote.kmongo.coroutine.CoroutineCollection
import org.litote.kmongo.eq
import java.util.*

class UserRepository constructor(private val collection: CoroutineCollection<CustomerUserPOJO>) {
    suspend fun findByPhone(phone: String): CustomerUserPOJO? =
        collection.findOne(CustomerUserPOJO::phone eq phone)

    suspend fun findByEmail(email: String): CustomerUserPOJO? =
        collection.findOne(CustomerUserPOJO::email eq email)

    suspend fun findById(id: String): CustomerUserPOJO? =
        collection.findOneById(ObjectId(id))

    suspend fun save(user: CustomerUserPOJO) =
        user.also { collection.insertOne(user) }

    suspend fun updatePassword(phone: String, newPassword: String): CustomerUserPOJO? {
        val filter = CustomerUserPOJO::phone eq phone
        return collection.findOneAndUpdate(filter, Updates.combine(
            Updates.set(CustomerUserPOJO::password.name, newPassword),
            Updates.set(CustomerUserPOJO::lastModified.name, Date())
        ))
    }

    suspend fun updateProfile(id: String, name: String, email: String): CustomerUserPOJO? {
        val filter = CustomerUserPOJO::_id eq ObjectId(id)
        return collection.findOneAndUpdate(
            filter,
            Updates.combine(
                Updates.set(CustomerUserPOJO::name.name, name),
                Updates.set(CustomerUserPOJO::email.name, email),
                Updates.set(CustomerUserPOJO::lastModified.name, Date())
            )
        )
    }
}