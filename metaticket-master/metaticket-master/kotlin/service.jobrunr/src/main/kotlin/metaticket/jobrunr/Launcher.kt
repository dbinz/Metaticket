package metaticket.jobrunr

import metaticket.common.logger.Logging

object Launcher: Logging {
    @JvmStatic
    fun main(args: Array<String>) {
        runKtorServer(args)
    }
}
