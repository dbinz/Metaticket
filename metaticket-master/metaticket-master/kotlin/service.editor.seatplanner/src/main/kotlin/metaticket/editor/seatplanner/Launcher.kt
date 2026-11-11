package metaticket.editor.seatplanner

import metaticket.common.logger.Logging

object Launcher: Logging {
    @JvmStatic
    fun main(args: Array<String>) {
        runKtorServer(args)
    }
}
