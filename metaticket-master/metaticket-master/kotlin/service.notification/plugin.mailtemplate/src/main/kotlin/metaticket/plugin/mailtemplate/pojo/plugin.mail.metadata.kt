package metaticket.plugin.mailtemplate.pojo

import kotlin.reflect.KClass

/**
 * @author dongcatchay
 */

data class PluginMailMetaData constructor(
    val endPoint: String,
    val supporter: String,
    val hotline: String,
    val getImage: (suspend (url: String) -> String)? = null,
    val stubProvider: ((KClass<*>) -> Any)? = null
)