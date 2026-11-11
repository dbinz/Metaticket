package metaticket.admin.configuration

/**
 * @author dongcatchay
 */
data class ServiceExplorerConfig constructor(
    val orderS: ServicePeerConfig,
) {
    data class ServicePeerConfig(
        val sid: String,
        val host: String,
        val port: Int
    )

}
