package metaticket.plugin.zns.model

import kotlinx.coroutines.CompletableDeferred
import kotlinx.serialization.Serializable

data class ZaloMessageMetadata constructor(
    val ref_id: String,
    val plugin: String,
    val template_id: String,
    val phone: String,
    val failover: Boolean,
)

data class ZaloMessageRequest constructor(
    val metadata: ZaloMessageMetadata,
    val data: Map<String, String>,
    val respond: CompletableDeferred<ZaloMessageResponse>
)

data class ZaloMessageResponse constructor(
    val code: Int,
    val message: String,
    val data: String? = null,
)