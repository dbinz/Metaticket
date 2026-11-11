package metaticket.order.datacollector.pojo

import metaticket.order.datacollector.OrderDataCollectedStatus
import metaticket.order.datacollector.pojo.db.ListCustomFieldData
import metaticket.order.pojo.db.CustomFieldData
import metaticket.order.pojo.db.CustomFieldTmpl


/**
 *
 * @author dongcatchay
 */


data class OrderDataCollectorTemplateResponse(
    val id: String,
    val showId: String,
    val sessionId: String,
    val resId: String,

    val eventName: String ? = null,
    val faviconUrl: String ? = null,

    // Map<catId, List<List<field>>> store template for each ticket in category
    val individualTickets: Map<String, List<List<CustomFieldTmpl>>> = emptyMap(),
    // Map<catId, List<field>> store template for each category, used for all tickets
    val priceCategories: Map<String, List<CustomFieldTmpl>> = emptyMap(),

    val dataCollectedId: String? = null,
)

data class OrderDataCollectedResponse(
    val id: String,
    val showId: String,
    val sessionId: String,
    val resId: String,
    val status: OrderDataCollectedStatus,

    // Map<catId, List<List<fieldData>> store template for each ticket in category
    val individualTickets: Map<String, List<List<CustomFieldData>>> = emptyMap(),
    // Map<catId, List<fieldData>> store template for each category, used for all tickets
    val priceCategories: Map<String, List<CustomFieldData>> = emptyMap(),
)
