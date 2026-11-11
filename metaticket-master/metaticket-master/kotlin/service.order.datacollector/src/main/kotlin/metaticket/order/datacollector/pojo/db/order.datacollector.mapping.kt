package metaticket.order.datacollector.pojo.db

import metaticket.order.datacollector.OrderDataCollectedStatus
import metaticket.order.pojo.db.CustomFieldData
import metaticket.order.pojo.db.CustomFieldTmpl
import org.bson.BsonType
import org.bson.codecs.pojo.annotations.BsonId
import org.bson.codecs.pojo.annotations.BsonRepresentation
import org.bson.types.ObjectId
import java.util.*

/**
 *
 * @author dongcatchay
 */
data class OrderDataCollectorMapping(
    @BsonId @BsonRepresentation(BsonType.OBJECT_ID)
    val id: String = ObjectId().toHexString(),
    @BsonRepresentation(BsonType.OBJECT_ID)
    val showId: String,
    @BsonRepresentation(BsonType.OBJECT_ID)
    val sessionId: String,

    val eventName: String ? = null,
    val faviconUrl: String ? = null,

    // Map<catId, List<field>> store template for each ticket in category
    val individualTickets: Map<String, List<CustomFieldTmpl>> = emptyMap(),
    // Map<catId, List<field>> store template for each category, used for all tickets
    val priceCategories: Map<String, List<CustomFieldTmpl>> = emptyMap(),

    val version: Int = 1,
    val createdAt: Date = Date(),
    val lastModified: Date = Date(),
)

data class TicketReservationDetail constructor(
    var ticketId : String,
    var quantity : Int,     // requested capacity
    var priceCat : String,   // price category
    var sid : String,   // seat id / sone id
    var capacityId: String?
)


data class OrderDataCollector(
    @BsonId @BsonRepresentation(BsonType.OBJECT_ID)
    val id: String,
    @BsonRepresentation(BsonType.OBJECT_ID)
    val showId: String,
    @BsonRepresentation(BsonType.OBJECT_ID)
    val sessionId: String,
    @BsonRepresentation(BsonType.OBJECT_ID)
    val resId: String,

    val tickets: List<TicketReservationDetail>,

    @BsonRepresentation(BsonType.OBJECT_ID)
    val dataCollectedId: String? = null,

    val dataCollectedStatus: OrderDataCollectedStatus = OrderDataCollectedStatus.EMPTY,

    val version: Int = 1,
    val createdAt: Date = Date(),
    val lastModified: Date = Date(),
)

class ListCustomFieldData: ArrayList<CustomFieldData>()

data class OrderDataCollected(
    @BsonId @BsonRepresentation(BsonType.OBJECT_ID)
    val id: String,
    @BsonRepresentation(BsonType.OBJECT_ID)
    val showId: String,
    @BsonRepresentation(BsonType.OBJECT_ID)
    val sessionId: String,
    @BsonRepresentation(BsonType.OBJECT_ID)
    val resId: String,

    val status: OrderDataCollectedStatus = OrderDataCollectedStatus.EMPTY,

    // Map<catId, List<List<fieldData>> store template for each ticket in category
    val individualTickets: Map<String, List<ListCustomFieldData>> = emptyMap(),
    // Map<catId, List<fieldData>> store template for each category, used for all tickets
    val priceCategories: Map<String, List<CustomFieldData>> = emptyMap(),

    val version: Int = 1,
    val createdAt: Date = Date(),
    val lastModified: Date = Date(),
)
