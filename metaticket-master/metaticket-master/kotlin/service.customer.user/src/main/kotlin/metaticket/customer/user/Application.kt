package metaticket.customer.user

import com.fasterxml.jackson.core.util.DefaultIndenter
import com.fasterxml.jackson.core.util.DefaultPrettyPrinter
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
import metaticket.common.logger.Logging
import metaticket.customer.user.route.userRoutes
import org.slf4j.event.Level
import kotlin.system.exitProcess

// Initialize logger for the application
val logger = (object : Logging {
    override fun loggerName() = "APPLICATION"
}).logger

/**
 * Starts the Ktor server and handles startup/shutdown logging and error handling.
 *
 * @param args Command-line arguments passed to the application.
 */
fun runKtorServer(args: Array<String>) {
    logger.info("Ktor server starting...")
    try {
        // Start the Ktor server
        EngineMain.main(args)
        logger.info("Ktor server stopped")
    } catch (t: Throwable) {
        // Log error and exit if server startup fails
        logger.error("Ktor server start failed!!!...", t)
        exitProcess(1)
    }
}

/**
 * Configures the Ktor application module with necessary plugins and routes.
 *
 * This function sets up call logging, CORS, compression, authentication, and routes.
 *
 * @receiver The Ktor application instance.
 */
fun Application.module() {

    // Install Call Logging plugin for request/response logging
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

    // Install CORS plugin for cross-origin requests
    install(CORS) {
        allowHost("*", listOf("http", "https"))
        allowOrigins { it in listOf("http://localhost", "https://devlocal.metaticket.vn", "https://admin.devlocal.metaticket.vn", "*") }
        allowMethod(HttpMethod.Get)
        allowMethod(HttpMethod.Post)
        allowCredentials = true
        allowHeader(HttpHeaders.ContentType)
    }

    // Install Compression plugin for response compression
    install(Compression)

    install(ContentNegotiation) {
        jackson {
            setDefaultPrettyPrinter(DefaultPrettyPrinter().apply {
                indentArraysWith(DefaultPrettyPrinter.FixedSpaceIndenter.instance)
                indentObjectsWith(DefaultIndenter("  ", "\n"))
            })
            registerModule(JavaTimeModule())  // support java.time.* types
            registerModule(KotlinModule.Builder().build())
        }
    }

    install(StatusPages) {
        exception<Throwable> { call, cause ->
            logger.error("unknown exception... ", cause)
            call.respondText(text = "500: InternalServerError", status = HttpStatusCode.InternalServerError)
        }
    }

    // Initialize dependency injection context
    val diContext = ApplicationDI(environment, monitor)

    routing {
        userRoutes(diContext.userCtrl)
    }

    diContext.startUp.start()
}