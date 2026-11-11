package metaticket.common.logger

import org.slf4j.Logger
import org.slf4j.LoggerFactory


/**
 * Define logger for each class, just inherit this class then use this logger to write log
 *
 * @property logger Logger
 *
 * @author dongcatchay
 */
interface Logging {
    val logger: Logger
        get() = LoggerFactory.getLogger(loggerName())

    fun loggerName(): String {
        return this.javaClass.name
    }
}
