package metaticket.authentication

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import com.fasterxml.jackson.core.util.DefaultIndenter
import com.fasterxml.jackson.core.util.DefaultPrettyPrinter
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
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
import io.ktor.server.sessions.*
import metaticket.authentication.pojo.session.UserSession
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

    val diContext = ApplicationDI(environment)

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

    configureSession()

    configureSecurity()

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
}

private fun Application.configureStatusPages() {
    install(StatusPages) {
//        status(HttpStatusCode.NotFound) { call, status ->
//            call.respondText(text = "404: Page Not Found", status = status)
//        }
        exception<Throwable> { call, cause ->
            logger.error("unknown exception... ", cause)
            call.respondText(text = "500: InternalServerError", status = HttpStatusCode.InternalServerError)
        }
    }
}

private fun Application.configureRouting(di : ApplicationDI) {
    routing {
        documentRoutes(di.documentCtrl)
    }
}

private fun Application.configureSession() {
    val sessionMaxAge = environment.config.property("jwt.expiration_time").getString().toLong()

    install(Sessions) {
        cookie<UserSession>("user_session") {
            cookie.path = "/"
            cookie.maxAgeInSeconds = sessionMaxAge / 1000 // Convert to seconds
            // Chỉ định serializer sử dụng Jackson
            serializer = object : SessionSerializer<UserSession> {
                private val objectMapper = ObjectMapper().apply {
                    registerModule(JavaTimeModule())
                    registerModule(KotlinModule.Builder().build())
                }

                override fun deserialize(text: String): UserSession {
                    return objectMapper.readValue(text, UserSession::class.java)
                }

                override fun serialize(session: UserSession): String {
                    return objectMapper.writeValueAsString(session)
                }
            }
        }
    }
}

private fun Application.configureSecurity() {
    val secret = environment.config.property("jwt.secret").getString()
    val issuer = environment.config.property("jwt.issuer").getString()
    val audience = environment.config.property("jwt.audience").getString()
    val realm = environment.config.property("jwt.realm").getString()
    install(Authentication) {

        jwt("jwt-auth") {
            verifier(JWT
                    .require(Algorithm.HMAC256(secret))
                    .withAudience(audience)
                    .withIssuer(issuer)
                    .build())
            validate { credential ->
                if (credential.payload.getClaim("email") != null && credential.payload.getClaim("email").asString().isNotEmpty()) {
                    JWTPrincipal(credential.payload)
                } else {
                    null
                }
            }
            challenge { defaultScheme, realm ->
                call.response.headers.append(HttpHeaders.WWWAuthenticate, "Bearer")
                call.respond(HttpStatusCode.Unauthorized, "Token is not valid or has expired")
            }
        }
        session<UserSession>("auth-session") {
            validate { session ->
                if(session.token.isNotEmpty()) {
                    session
                } else {
                    null
                }
            }
            challenge {
                call.respond(HttpStatusCode.Unauthorized, "Session invalid")
            }
        }
    }
}

private fun Application.configureSerialization() {
    install(ContentNegotiation) {
        jackson {
            configure(SerializationFeature.INDENT_OUTPUT, true)
            setDefaultPrettyPrinter(DefaultPrettyPrinter().apply {
                indentArraysWith(DefaultPrettyPrinter.FixedSpaceIndenter.instance)
                indentObjectsWith(DefaultIndenter("  ", "\n"))
            })
            registerModule(JavaTimeModule())  // support java.time.* types
            registerModule(KotlinModule.Builder().build())
        }
    }
}
