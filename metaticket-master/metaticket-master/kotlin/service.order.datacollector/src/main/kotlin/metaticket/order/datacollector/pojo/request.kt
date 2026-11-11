package metaticket.order.datacollector.pojo

import metaticket.order.datacollector.pojo.db.ListCustomFieldData
import metaticket.order.pojo.db.CustomFieldData


/**
 *
 * @author dongcatchay
 */

data class ValidateOrderDataCollectedRequest(
    var id: String,
    // Map<catId, List<List<fieldData>> store template for each ticket in category
    val individualTickets: Map<String, List<ArrayList<CustomFieldData>>> = emptyMap(),
    // Map<catId, List<fieldData>> store template for each category, used for all tickets
    val priceCategories: Map<String, List<CustomFieldData>> = emptyMap(),
)

data class SaveOrderDataCollectedRequest(
    var id: String,
    // Map<catId, List<List<fieldData>> store template for each ticket in category
    val individualTickets: Map<String, List<ListCustomFieldData>> = emptyMap(),
    // Map<catId, List<fieldData>> store template for each category, used for all tickets
    val priceCategories: Map<String, List<CustomFieldData>> = emptyMap(),
)
