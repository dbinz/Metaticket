package metaticket.payment.controller

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import metaticket.common.logger.Logging
import metaticket.payment.cache.PGAccountCache
import metaticket.payment.configuration.PaymentConfig
import metaticket.payment.database.service.PGAccountConfigDBService
import metaticket.payment.datastructure.PaymentGateway
import metaticket.payment.pojo.db.PGNinePayAccount
import metaticket.payment.pojo.db.PGOnePayAccount
import metaticket.payment.pojo.db.PGVnPayAccount
import metaticket.payment.pojo.request.*
import metaticket.payment.pojo.response.toResponse


/**
 *
 * @author dongcatchay
 */
class PGAccountController(
    private val pgAccountDBS: PGAccountConfigDBService,
    private val pgAccountCache: PGAccountCache,
    private val paymentConfig: PaymentConfig,
): Logging {
    suspend fun addPGOnePayAccount(call: ApplicationCall) {
        val req = call.receive<AddOnePayAccountRequest>()
        val pgAccount = PGOnePayAccount(
            code = req.code,
            gateway = PaymentGateway.OnePay,
            name = req.name,
            description = req.description,
            enabled = req.enabled,
            returnUrl = "${paymentConfig.baseReturnUrl}/onepay/${req.code}",
            ipnUrl = "${paymentConfig.baseIpnUrl}/onepay/${req.code}",
            baseUrl = req.baseUrl,
            merchantId = req.merchantId,
            merchantAccessCode = req.merchantHashCode,
            merchantHashCode = req.merchantHashCode,
            user = req.user,
            password = req.password
        )
        pgAccountDBS.insert(pgAccount) as PGOnePayAccount? ?: run {
            logger.error("failed to add pg onepay account {}", req)
            return call.respond(HttpStatusCode.InternalServerError, "fail to add pg account")
        }

        logger.info("pg onepay account {} added", pgAccount)
        return call.respond(HttpStatusCode.OK, pgAccount.toResponse())
    }

    suspend fun addPGNinePayAccount(call: ApplicationCall) {
        val req = call.receive<AddNinePayAccountRequest>()
        val pgAccount = PGNinePayAccount(
            code = req.code,
            gateway = PaymentGateway.NinePay,
            name = req.name,
            description = req.description,
            enabled = req.enabled,
            returnUrl = "${paymentConfig.baseReturnUrl}/ninepay/${req.code}",
            ipnUrl = "${paymentConfig.baseIpnUrl}/ninepay/${req.code}",
            baseUrl = req.baseUrl,
            merchantKey = req.merchantKey,
            merchantSecretKey = req.merchantSecretKey,
            checksumKey = req.checksumKey
        )
        pgAccountDBS.insert(pgAccount) as PGNinePayAccount? ?: run {
            logger.error("failed to add pg ninepay account {}", req)
            return call.respond(HttpStatusCode.InternalServerError, "fail to add pg account")
        }

        logger.info("pg ninepay account {} added", pgAccount)
        return call.respond(HttpStatusCode.OK, pgAccount.toResponse())
    }

    suspend fun addPGVnPayAccount(call: ApplicationCall) {
        val req = call.receive<AddVnPayAccountRequest>()
        val pgAccount = PGVnPayAccount(
            code = req.code,
            gateway = PaymentGateway.VNPay,
            name = req.name,
            description = req.description,
            enabled = req.enabled,
            returnUrl = "${paymentConfig.baseReturnUrl}/vnpay/${req.code}",
            ipnUrl = "${paymentConfig.baseIpnUrl}/vnpay/${req.code}",
            payUrl = req.payUrl,
            queryUrl = req.queryUrl,
            hashSecret = req.hashSecret,
            tmnCode = req.tmnCode,
            expireInMinute = req.expireInMinute
        )
        pgAccountDBS.insert(pgAccount) as PGVnPayAccount? ?: run {
            logger.error("failed to add pg vnpay account {}", req)
            return call.respond(HttpStatusCode.InternalServerError, "fail to add pg account")
        }

        logger.info("pg vnpay account {} added", pgAccount)
        return call.respond(HttpStatusCode.OK, pgAccount.toResponse())
    }

    suspend fun updatePGOnePayAccount(call: ApplicationCall) {
        val req = call.receive<UpdateOnePayAccountRequest>()
        val updates = listOfNotNull(
            req.name?.let { Pair("name", it) },
            req.description?.let { Pair("description", it) },
            req.baseUrl?.let { Pair("baseUrl", it) },
        )
        val pgAccount = pgAccountDBS.update(req.code, updates)?.pgAccount?.get(req.code) ?: run {
            logger.error("cannot update pg onepay account {}", req)
            return call.respond(HttpStatusCode.InternalServerError, "cannot update pg")
        }

        pgAccountCache.remove(req.code)

        logger.info("updated pg onepay account {}", pgAccount)
        return call.respond(HttpStatusCode.OK, pgAccount.toResponse())
    }

    suspend fun updatePGNinePayAccount(call: ApplicationCall) {
        val req = call.receive<UpdateNinePayAccountRequest>()
        val updates = listOfNotNull(
            req.name?.let { Pair("name", it) },
            req.description?.let { Pair("description", it) },
            req.baseUrl?.let { Pair("baseUrl", it) },
        )
        val pgAccount = pgAccountDBS.update(req.code, updates)?.pgAccount?.get(req.code) ?: run {
            logger.error("cannot update pg ninepay account {}", req)
            return call.respond(HttpStatusCode.InternalServerError, "cannot update pg")
        }

        pgAccountCache.remove(req.code)

        logger.info("updated pg ninepay account {}", pgAccount)
        return call.respond(HttpStatusCode.OK, pgAccount.toResponse())
    }

    suspend fun updatePGVnPayAccount(call: ApplicationCall) {
        val req = call.receive<UpdateVnPayAccountRequest>()
        val updates = listOfNotNull(
            req.name?.let { Pair("name", it) },
            req.description?.let { Pair("description", it) },
            req.payUrl?.let { Pair("payUrl", it) },
            req.queryUrl?.let { Pair("queryUrl", it) },
            req.expireInMinute?.let { Pair("expireInMinute", it) },
        )
        val pgAccount = pgAccountDBS.update(req.code, updates)?.pgAccount?.get(req.code) ?: run {
            logger.error("cannot update pg vnpay account {}", req)
            return call.respond(HttpStatusCode.InternalServerError, "cannot update pg")
        }

        pgAccountCache.remove(req.code)

        logger.info("updated pg vnpay account {}", pgAccount)
        return call.respond(HttpStatusCode.OK, pgAccount.toResponse())
    }

    suspend fun setPGDefaultAccount(call: ApplicationCall) {
        val req = call.receive<CodeOnlyRequest>()

        val pgConfig = pgAccountDBS.updateDefaultAccount(req.code) ?: run {
            logger.error("cannot update default pg account {}", req.code)
            return call.respond(HttpStatusCode.NotFound, "not found pg account")
        }

        pgAccountCache.setDefaultAccount()

        return call.respond(HttpStatusCode.OK, pgConfig.toResponse())
    }

    suspend fun loadPGAccount(call: ApplicationCall) {
        val code = call.parameters["code"] ?: run {
            return call.respond(HttpStatusCode.BadRequest, "missing code")
        }
        val pgAccount = pgAccountCache.get(code) ?: run {
            logger.error("cannot load pg account {}", code)
            return call.respond(HttpStatusCode.NotFound, "not found pg account")
        }
        logger.debug("pg account {}", pgAccount.toResponse())
        return call.respond(HttpStatusCode.OK, pgAccount.toResponse())
    }

    suspend fun removePGAccount(call: ApplicationCall) {
        val req = call.receive<CodeOnlyRequest>()

        if (!pgAccountDBS.remove(req.code)) {
            if (req.code == pgAccountCache.getDefaultAccountCode()) {
                return call.respond(HttpStatusCode.Conflict, "Cannot remove default pg account")
            }
            logger.error("pg account {} cannot remove", req.code)
            return call.respond(HttpStatusCode.InternalServerError, "Cannot remove pg account")
        }

        pgAccountCache.remove(req.code)

        logger.debug("pg account {} removed", req.code)
        return call.respond(HttpStatusCode.OK)
    }

    suspend fun enablePGAccount(call: ApplicationCall) {
        val req = call.receive<CodeOnlyRequest>()

        pgAccountDBS.setEnable(req.code, true) ?: run {
            logger.error("pg account {} cannot enable", req.code)
            return call.respond(HttpStatusCode.InternalServerError, "Cannot enable pg account")
        }

        val pgAccount = pgAccountCache.get(req.code) ?: run {
            logger.error("pg account {} cannot disable, not found from cache", req.code)
            return call.respond(HttpStatusCode.InternalServerError, "Cannot disable pg account")
        }

        pgAccountCache.remove(req.code)

        logger.debug("pg account {} enabled", req.code)
        return call.respond(HttpStatusCode.OK, pgAccount.toResponse())
    }

    suspend fun disablePGAccount(call: ApplicationCall) {
        val req = call.receive<CodeOnlyRequest>()

        pgAccountDBS.setEnable(req.code, false) ?: run {
            logger.error("pg account {} cannot disable, not found", req.code)
            return call.respond(HttpStatusCode.InternalServerError, "Cannot disable pg account")
        }

        val pgAccount = pgAccountCache.get(req.code) ?: run {
            logger.error("pg account {} cannot disable, not found from cache", req.code)
            return call.respond(HttpStatusCode.InternalServerError, "Cannot disable pg account")
        }

        pgAccountCache.remove(req.code)

        logger.debug("pg account {} disabled", req.code)
        return call.respond(HttpStatusCode.OK, pgAccount.toResponse())
    }

    suspend fun loadPGConfig(call: ApplicationCall) {
        val pgConfig = pgAccountDBS.loadConfig() ?: run {
            logger.error("not found pg config, not found")
            return call.respond(HttpStatusCode.NotFound, "not found ap config")
        }

        return call.respond(HttpStatusCode.OK, pgConfig.toResponse())
    }
}
