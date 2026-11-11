package metaticket.plugin.zns

import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import metaticket.common.logger.Logging
import metaticket.plugin.zns.model.ZaloMessageMetadata
import java.util.concurrent.ConcurrentHashMap

class PluginZNSBasic: PluginZNS, Logging {

    private val templatesCache = ConcurrentHashMap<String, String>()
    private val mapper = ObjectMapper().registerKotlinModule()

    init {
        try {
            val stream = this::class.java.classLoader.getResourceAsStream("conf/sms.template.json")
            if (stream != null) {
                val list = mapper.readValue(stream, object: TypeReference<List<Map<String, String>>>() {})
                list.forEach { m -> m.forEach { (k, v) -> templatesCache[k] = v } }
            }
        } catch (t: Throwable) {
            logger.error("Error while loading sms template... ", t)
            throw t
        }
    }

    override suspend fun buildTemplateData(metadata: ZaloMessageMetadata, data: Map<String, String>): Map<String, String> {
        return data
    }

    override suspend fun buildSMSTemplateMsg(metadata: ZaloMessageMetadata, data: Map<String, String>): String {
        var template: String = templatesCache[metadata.template_id] ?: return ""
        data.forEach { (key, value) ->
            template = template.replace("<$key>", value)
        }
        return template
    }
}