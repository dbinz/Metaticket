package metaticket.order.validation

import com.fasterxml.jackson.databind.ObjectMapper
import metaticket.order.datacollector.OrderDataCollectedStatus
import metaticket.order.datastructure.CustomFieldType
import metaticket.order.datastructure.validation.CustomFieldConstraints
import metaticket.order.datastructure.validation.ValidationCustomField
import metaticket.order.gateway.OrderDataCollectorGateway
import metaticket.order.pojo.db.CustomFieldTmpl

class OrderValidationCustomField constructor(): ValidationCustomField() {
    suspend fun isCustomFieldValid(
        customField: CustomFieldTmpl, value: String?, jsonMapper: ObjectMapper, gateway: OrderDataCollectorGateway
    ): Boolean {
        if(customField.type == CustomFieldType.DataCollector) {
            customField.constraints?.let {
                val dataCollectorConstraints = jsonMapper.readValue(it, CustomFieldConstraints::class.java)
                if(dataCollectorConstraints.require == true && value == null) {
                    return false
                } else if(dataCollectorConstraints.require == true){
                    val status = gateway.checkOrderDataCollectorIsValid(value!!)
                    logger.error(
                        "data collector status {}, is valid: {}",
                        status, status == OrderDataCollectedStatus.VALID
                    )
                    return status == OrderDataCollectedStatus.VALID
                }
            }
            return true
        } else {
            return super.isCustomFieldValid(customField, value, jsonMapper)
        }

    }
}
