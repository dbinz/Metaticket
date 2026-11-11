package metaticket.editor.policydesc

import com.fasterxml.jackson.core.JsonGenerator
import com.fasterxml.jackson.core.JsonParser
import com.fasterxml.jackson.core.util.DefaultIndenter
import com.fasterxml.jackson.core.util.DefaultPrettyPrinter
import com.fasterxml.jackson.databind.DeserializationContext
import com.fasterxml.jackson.databind.SerializerProvider
import com.fasterxml.jackson.databind.deser.std.StdDeserializer
import com.fasterxml.jackson.databind.module.SimpleModule
import com.fasterxml.jackson.databind.ser.std.StdSerializer
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.KotlinModule
import metaticket.common.logger.Logging
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
import org.slf4j.event.Level
import kotlin.reflect.KClass
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
        allowOrigins { it in listOf("http://localhost", "https://devlocal.metaticket.vn", "*") }
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

private fun Application.configureSerialization() {
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
}

/**
 * Define KClass kotlin module for jackson
 */
private fun KotlinModule.registerKClass(): SimpleModule {
    class KClassSerializer : StdSerializer<KClass<*>>(KClass::class.java) {
        override fun serialize(value: KClass<*>, gen: JsonGenerator, provider: SerializerProvider) {
            gen.writeString(value.qualifiedName)
        }
    }

    class KClassDeserializer : StdDeserializer<KClass<*>>(KClass::class.java) {
        override fun deserialize(p: JsonParser, ctxt: DeserializationContext?): KClass<*> {
            val qualifiedName = p.text
            return Class.forName(qualifiedName).kotlin
        }
    }

    return this
        .addSerializer(KClass::class.java, KClassSerializer())
        .addDeserializer(KClass::class.java, KClassDeserializer())
}
