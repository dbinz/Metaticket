package metaticket.order.datastructure.validation

import com.fasterxml.jackson.databind.ObjectMapper
import metaticket.common.logger.Logging
import metaticket.order.datastructure.CustomFieldType
import metaticket.order.pojo.db.CustomFieldData
import metaticket.order.pojo.db.CustomFieldTmpl
import java.util.*

open class ValidationCustomField constructor() : Logging {
    open suspend fun isCustomFieldsValid(
        customFieldsTmpl: List<CustomFieldTmpl>,
        customFieldsData: List<CustomFieldData>,
        jsonMapper: ObjectMapper,
    ): Boolean {
        var isValidAll = true
        for(customFieldTmpl in customFieldsTmpl) {
            for(customFieldData in customFieldsData) {
                if(customFieldData.id == customFieldTmpl.id) {
                    isValidAll = isCustomFieldValid(customFieldTmpl, customFieldData.value, jsonMapper)
                    if(!isValidAll) {
                        logger.error("custom field invalid data {}, template {}", customFieldData.value, customFieldTmpl)
                        break
                    }
                }
            }
        }
        return isValidAll
    }

    open suspend fun isCustomFieldValid(customField: CustomFieldTmpl, value: String?, jsonMapper: ObjectMapper): Boolean {
        when(customField.type) {
            CustomFieldType.Number -> {
                customField.constraints?.let {
                    val numberConstraints = jsonMapper.readValue(it, NumberConstraints::class.java)

                    if(numberConstraints.require == true && value == null) {
                        return false
                    }

                    if(value != null) {
                        try {
                            val numberValue = value.toInt()
                            numberConstraints.max?.let { numberMax ->
                                if(numberValue > numberMax) {
                                    return false
                                }
                            }
                            numberConstraints.min?.let { numberMin ->
                                if(numberValue < numberMin) {
                                    return false
                                }
                            }
                            return true
                        } catch (t: Throwable) {
                            return false
                        }
                    } else {
                        return true
                    }
                } ?: kotlin.run {
                    if(value != null) {
                        try {
                            value.toInt()
                            return true
                        } catch (t: Throwable) {
                            return false
                        }
                    } else {
                        return true
                    }
                }
            }
            CustomFieldType.String -> {
                customField.constraints?.let {
                    val stringConstraints = jsonMapper.readValue(it, StringConstraints::class.java)

                    if(stringConstraints.require != true) {
                        return true
                    }

                    if(value == null) {
                        return false
                    }

                    stringConstraints.regex?.let { regexString ->
                        val regex = Regex(regexString)
                        if(!regex.matches(value)) {
                            return false
                        }
                    }
                    stringConstraints.minLength?.let {minLength ->
                        if(value.trim().length < minLength) {
                            return false
                        }
                    }
                    stringConstraints.maxLength?.let {maxLength ->
                        if(value.trim().length > maxLength) {
                            return false
                        }
                    }
                }
                return true

            }
            CustomFieldType.Date -> {
                customField.constraints?.let {
                    val dateConstraints = jsonMapper.readValue(it, DateTimeConstraints::class.java)
                    if(dateConstraints.require == true && value == null) {
                        return false
                    } else if(value != null) {
                        try {
                            val date = Date(value.toLong())
                            dateConstraints.maxDate?.let { dateConstraintMaxDate ->
                                val maxDate = Date(dateConstraintMaxDate)
                                if(maxDate.before(date)) {
                                    return false
                                }
                            }

                            dateConstraints.minDate?.let { dateConstraintMinDate ->
                                val minDate = Date(dateConstraintMinDate)
                                if(minDate.after(date)) {
                                    return false
                                }
                            }
                        } catch (t: Throwable) {
                            return false
                        }
                    }

                }
                return true
            }
            CustomFieldType.List -> {
                customField.constraints?.let {
                    val listConstraints = jsonMapper.readValue(it, ListConstraints::class.java)

                    val optionExists = listConstraints.options.any { o -> o.value == value }

                    if (optionExists) {
                        return true
                    }

                    if (listConstraints.require && value.isNullOrBlank()) return false
                }
                return true
            }
            CustomFieldType.Boolean -> {
                value.toBoolean()
                return true
            }
            else -> {

            }
        }

        return false
    }
}
