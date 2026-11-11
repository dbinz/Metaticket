package metaticket.order.datacollector

import metaticket.common.logger.Logging

object Launcher : Logging {
    @JvmStatic
    fun main(args: Array<String>) {
        runKtorServer(args)
    }
}
