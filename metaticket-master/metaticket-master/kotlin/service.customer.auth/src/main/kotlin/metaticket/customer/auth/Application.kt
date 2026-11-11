package metaticket.customer.auth

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import com.fasterxml.jackson.core.util.DefaultIndenter
import com.fasterxml.jackson.core.util.DefaultPrettyPrinter
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.KotlinModule
import io.ktor.http.*
import io.ktor.serialization.jackson.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
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
import metaticket.customer.auth.route.authRoutes
import metaticket.customer.auth.route.voteRoutes
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
        allowOrigins {
            it in listOf(
                "https://devlocal.metaticket.vn",
                "https://admin.devlocal.metaticket.vn",
                // Admin portal thật — nơi vote-results.html sẽ được mở khi deploy staging/prod.
                "https://admin.staging.metaticket.vn",
                "https://admin.metaticket.com.vn",
                "*",
            )
        }
        allowMethod(HttpMethod.Get)
        allowMethod(HttpMethod.Post)
        allowCredentials = true
        allowHeader(HttpHeaders.ContentType)
        // TODO TẠM THỜI: header dùng bởi vote-results.html — xoá cùng lúc với route/tính năng vote.
        allowHeader("X-Admin-Secret")
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

    // Install Authentication plugin for JWT authentication
    install(Authentication) {
        jwt("auth-jwt") {
            // Configure JWT verifier
            verifier(
                JWT
                    .require(Algorithm.HMAC256(diContext.jwtUtil.secret))
                    .withAudience(diContext.jwtUtil.audience)
                    .withIssuer(diContext.jwtUtil.issuer)
                    .build()
            )
            // Configure JWT validation
            validate { credential ->
                if (
                    credential.payload.getClaim("phone").asString().isNotBlank() &&
                    credential.payload.getClaim("userId").asString().isNotBlank()
                ) {
                    JWTPrincipal(credential.payload)
                } else {
                    null
                }
            }
            // Configure challenge for unauthorized requests
            challenge { _, _ ->
                call.respond(HttpStatusCode.Unauthorized, "Token is not valid or has expired")
            }
        }
    }

    routing {
        // Configure authentication routes using the dependency injection context
        authRoutes(diContext.authCtrl)
        voteRoutes(diContext.voteCtrl)
    }

    diContext.startUp.start()
}