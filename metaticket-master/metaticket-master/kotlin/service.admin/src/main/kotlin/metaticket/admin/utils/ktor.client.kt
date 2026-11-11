package metaticket.admin.utils

import io.ktor.client.request.*
import io.ktor.http.*
import metaticket.admin.configuration.ServiceExplorerConfig

/**
 * @author dongcatchay
 */

fun ServiceExplorerConfig.ServicePeerConfig.buildRequest(
    path: String? = null, extra: HttpRequestBuilder.() -> Unit
): HttpRequestBuilder.() -> Unit {
    val conf = this
    return {
        this.method = HttpMethod.Get
        url(
            host = conf.host,
            port = conf.port,
        )
        if (path != null) url.path(path)
        extra()
    }
}
