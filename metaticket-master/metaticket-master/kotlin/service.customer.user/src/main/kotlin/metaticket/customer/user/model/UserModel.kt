package metaticket.customer.user.model

import metaticket.customer.user.grpc.CustomerUserMessage.CustomerUserProto
import org.bson.codecs.pojo.annotations.BsonId
import org.bson.types.ObjectId
import java.util.*

data class CustomerUserPOJO constructor(
    val phone: String,
    val password: String,
    val name: String,
    val email: String,
    val avatar: String? = null,
    @BsonId val _id: ObjectId = ObjectId(),
    val lastModified: Date? = Date(),
    val createdAt: Date? = Date(),
)

data class UserProfile constructor(
    val phone: String,
    val email: String,
    val name: String,
    val avatar: String? = null,
)

fun CustomerUserPOJO.toUserProfile() = UserProfile(
    phone = this.phone,
    email = this.email,
    name = this.name,
    avatar = this.avatar,
)

fun CustomerUserPOJO.toGrpcUser(): CustomerUserProto = CustomerUserProto.newBuilder()
    .setId(_id.toString())
    .setPhone(phone)
    .setName(name)
    .setEmail(email)
    .build()