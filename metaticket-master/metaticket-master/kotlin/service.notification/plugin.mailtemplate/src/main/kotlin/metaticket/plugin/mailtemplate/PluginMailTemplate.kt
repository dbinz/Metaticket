package metaticket.plugin.mailtemplate

import metaticket.plugin.mailtemplate.pojo.PluginMailMetaData
import metaticket.plugin.mailtemplate.pojo.db.MailTemplateDocument
import java.util.HashMap


/**
 *
 * @author dongcatchay
 */
interface PluginMailTemplate {
    suspend fun buildSubject(data: Any): String
    suspend fun buildBody(metaData: PluginMailMetaData, data: Any, document: MailTemplateDocument): String
    suspend fun buildAttachment(
        metaData: PluginMailMetaData,
        data: Any,
        document: MailTemplateDocument
    ): Map<String, Pair<String, ByteArray>> {
        val attachments = HashMap<String, Pair<String, ByteArray>>()
        document.attachments?.files?.forEach {
            attachments[it.filename] = Pair(it.type, it.data.data)
        }
        return attachments
    }
}
