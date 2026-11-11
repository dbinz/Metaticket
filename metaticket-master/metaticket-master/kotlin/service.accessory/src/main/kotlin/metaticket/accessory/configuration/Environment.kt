package metaticket.accessory.configuration

/**
 * Process-wide configuration holder for `service.accessory`, populated once at application
 * start-up from HOCON (`application.conf`). Mirrors the `Environment` singleton convention used by
 * the other MetaTicket services (`service.payment`, `service.order`, `service.ticket`, ...).
 *
 * [secretKey] is the shared secret used by the [metaticket.common.checksum.Checksum] utility to
 * encode/decode public-facing order and payment identifiers, exactly as `service.payment` does, so
 * raw MongoDB `ObjectId`s never leave the service (Req 16.1, 16.2).
 */
object Environment {
    var secretKey: String = ""
}
