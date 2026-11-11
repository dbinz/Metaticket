package metaticket.order.pojo.db

import metaticket.order.datastructure.CustomFieldType


/**
 *
 * @author dongcatchay
 */

data class CustomFieldTmpl constructor(
    val id: String,
    val type: CustomFieldType,
    val displayName: String? = null,
    val constraints: String? = null,
    val note: String? = null
)

data class CustomFieldData constructor(
    val id: String,
    val name: String,
    val value: String? = null,
)
