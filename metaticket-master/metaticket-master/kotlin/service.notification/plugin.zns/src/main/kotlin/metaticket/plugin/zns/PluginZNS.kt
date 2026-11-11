package metaticket.plugin.zns

import metaticket.plugin.zns.model.ZaloMessageMetadata

interface PluginZNS {
    suspend fun buildTemplateData(metadata: ZaloMessageMetadata, data: Map<String, String>): Map<String, String>
    suspend fun buildSMSTemplateMsg(metadata: ZaloMessageMetadata, data: Map<String, String>): String
}