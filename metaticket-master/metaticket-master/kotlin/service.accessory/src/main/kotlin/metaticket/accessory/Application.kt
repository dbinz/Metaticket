package metaticket.accessory

import com.fasterxml.jackson.core.util.DefaultIndenter
import com.fasterxml.jackson.core.util.DefaultPrettyPrinter
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.KotlinModule
import io.ktor.http.*
import io.ktor.serialization.jackson.*
import io.ktor.server.application.*
import io.ktor.server.netty.*
import io.ktor.server.plugins.calllogging.*
import io.ktor.server.plugins.compression.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.plugins.cors.routing.*
import io.ktor.server.plugins.statuspages.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import metaticket.accessory.configuration.Environment
import metaticket.accessory.pojo.request.CheckoutLimits
import metaticket.accessory.security.configureRateLimiting
import metaticket.common.logger.Logging
import org.slf4j.event.Level
import kotlin.system.exitProcess

val logger = (object : Logging {
    override fun loggerName() = "APPLICATION"
}).logger

fun runKtorServer(args: Array<String>) {
    logger.info("Ktor server starting...")
    try {
        EngineMain.main(args)
        logger.info("Ktor server stopped")
    } catch (t: Throwable) {
        logger.error("Ktor server start failed!!!...", t)
        exitProcess(1)
    }
}

fun Application.module() {

    configureEnvironment()

    val diContext = ApplicationDI(environment, monitor)

    configureRateLimiting()

    install(CORS) {
        allowHost("*", listOf("http", "https"))
        allowOrigins { it in listOf("http://localhost", "https://devlocal.metaticket.vn", "https://admin.devlocal.metaticket.vn", "*") }
        allowMethod(HttpMethod.Get)
        allowMethod(HttpMethod.Post)
        allowMethod(HttpMethod.Options)
        allowCredentials = true
        allowHeader(HttpHeaders.ContentType)
    }

    install(Compression)

    configureRouting(diContext)

    configureStatusPages()

    configureSerialization()

    install(CallLogging) {
        level = Level.DEBUG
        format { call ->
            val status = call.response.status()
            val httpMethod = call.request.httpMethod.value
            val userAgent = call.request.headers["User-Agent"]
            val params = call.request.queryParameters.formUrlEncode()
            val path = call.request.path()
            "$status, $httpMethod $path, $userAgent, $params"
        }
    }

    diContext.startUp.start()
}

private fun Application.configureEnvironment() {
    val cfg = environment.config
    Environment.secretKey = cfg.property("secretKey").getString()

    // Checkout line/quantity bounds — HOCON-configurable via `accessory.checkout.*`, falling back to
    // the built-in defaults when a key is absent or malformed.
    CheckoutLimits.minCheckoutLines =
        cfg.propertyOrNull("accessory.checkout.minLines")?.getString()?.toIntOrNull() ?: CheckoutLimits.minCheckoutLines
    CheckoutLimits.maxCheckoutLines =
        cfg.propertyOrNull("accessory.checkout.maxLines")?.getString()?.toIntOrNull() ?: CheckoutLimits.maxCheckoutLines
    CheckoutLimits.minLineQuantity =
        cfg.propertyOrNull("accessory.checkout.minLineQuantity")?.getString()?.toIntOrNull() ?: CheckoutLimits.minLineQuantity
    CheckoutLimits.maxLineQuantity =
        cfg.propertyOrNull("accessory.checkout.maxLineQuantity")?.getString()?.toIntOrNull() ?: CheckoutLimits.maxLineQuantity
}

private fun Application.configureStatusPages() {
    install(StatusPages) {
        exception<Throwable> { call, cause ->
            logger.error("unknown exception... ", cause)
            call.respondText(text = "500: InternalServerError", status = HttpStatusCode.InternalServerError)
        }
    }
}

private fun Application.configureRouting(di: ApplicationDI) {
    routing {
        accessoryRoutes(di)
    }
}

private fun Application.configureSerialization() {
    install(ContentNegotiation) {
        jackson {
            setDefaultPrettyPrinter(DefaultPrettyPrinter().apply {
                indentArraysWith(DefaultPrettyPrinter.FixedSpaceIndenter.instance)
                indentObjectsWith(DefaultIndenter("  ", "\n"))
            })
            registerModule(JavaTimeModule())  // support java.time.* types
            registerModule(KotlinModule.Builder().build())
            // Tolerate unknown/extra JSON fields on request bodies instead of failing the whole
            // request (a client sending an extra field must not turn into a "Malformed payload").
            configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
        }
    }
}
