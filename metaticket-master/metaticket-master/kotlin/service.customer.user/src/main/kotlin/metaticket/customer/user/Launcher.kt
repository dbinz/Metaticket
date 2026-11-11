package metaticket.customer.user

import metaticket.common.logger.Logging
import metaticket.customer.user.grpc.*

object Launcher: Logging {
    @JvmStatic
    fun main(args: Array<String>) {
        runKtorServer(args)
    }
}
