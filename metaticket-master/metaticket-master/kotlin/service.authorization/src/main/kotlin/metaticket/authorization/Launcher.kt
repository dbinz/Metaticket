package metaticket.authorization

import com.mongodb.client.MongoClients
import metaticket.common.logger.Logging
import org.casbin.jcasbin.main.Enforcer
import org.jim.jcasbin.MongoAdapter

object Launcher: Logging {
    @JvmStatic
    fun main(args: Array<String>) {
        runKtorServer(args)
    }
}
