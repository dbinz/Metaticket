package metaticket.customer.user

import com.mongodb.ConnectionString
import com.mongodb.MongoClientSettings
import com.mongodb.client.model.IndexOptions
import com.mongodb.client.model.Indexes
import io.grpc.ManagedChannelBuilder
import io.grpc.Server
import io.grpc.ServerBuilder
import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.events.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.config.*
import kotlinx.coroutines.runBlocking
import metaticket.common.redis.RedisClusterManager
import metaticket.customer.user.controller.UserCtrl
import metaticket.customer.user.gateway.CaptchaGateway
import metaticket.customer.user.gateway.NotificationGateway
import metaticket.customer.user.model.CustomerUserPOJO
import metaticket.customer.user.model.MailOTPConfig
import metaticket.customer.user.model.SessionConfig
import metaticket.customer.user.model.SessionType
import metaticket.customer.user.model.ZNSConfig
import metaticket.customer.user.repository.SessionRepository
import metaticket.customer.user.repository.UserRepository
import metaticket.customer.user.service.CustomerUserServiceImpl
import metaticket.notification.mail.grpc.MailNotificationServiceGrpcKt
import metaticket.notification.zns.grpc.ZaloNotificationServiceGrpcKt
import org.bson.Document
import org.koin.core.Koin
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import org.koin.core.module.Module
import org.koin.core.qualifier.named
import org.koin.dsl.koinApplication
import org.koin.dsl.module
import org.litote.kmongo.coroutine.CoroutineCollection
import org.litote.kmongo.coroutine.CoroutineDatabase
import org.litote.kmongo.coroutine.coroutine
import org.litote.kmongo.reactivestreams.KMongo

/**
 * Module providing application environment configuration.
 *
 * This module provides the Ktor [ApplicationEnvironment] as a singleton.
 *
 * @param ktorEnv The application environment.
 */
fun configurationModule(ktorEnv: ApplicationEnvironment, monitor: Events): Module = module {
    single<ApplicationEnvironment> { ktorEnv }
    single<ApplicationConfig> { ktorEnv.config }
    single<Events> { monitor }
    single<SessionConfig> {
        val config: ApplicationConfig = get()
        val expiredTimeInS = config.property("session.expiredTimeInS").getString().toLong()
        val maxOtpAttempts = config.property("session.maxOtpAttempts").getString().toInt()
        SessionConfig(expiredTimeInS, maxOtpAttempts)
    }

    single<Map<SessionType, ZNSConfig>>(named("znsConfig")) {
        val config: ApplicationConfig = get()
        val znsRegisterUserPlugin = config.property("zns.registerUser.plugin").getString()
        val znsRegisterUserTemplateId = config.property("zns.registerUser.templateId").getString()
        val znsRegisterUserFailover = config.property("zns.registerUser.failover").getString().toBoolean()
        val znsResetPassPlugin = config.property("zns.resetPassword.plugin").getString()
        val znsResetPassTemplateId = config.property("zns.resetPassword.templateId").getString()
        val znsResetPassFailover = config.property("zns.resetPassword.failover").getString().toBoolean()

        mapOf(
            SessionType.REGISTERED_USER to ZNSConfig(znsRegisterUserPlugin, znsRegisterUserTemplateId, znsRegisterUserFailover),
            SessionType.RESET_PASSWORD to ZNSConfig(znsResetPassPlugin, znsResetPassTemplateId, znsResetPassFailover),
        )
    }

    single<Map<SessionType, MailOTPConfig>>(named("mailConfig")) {
        val config: ApplicationConfig = get()
        val mailValidatePlugin = config.property("mail.validateMail.plugin").getString()
        val mailValidateTemplateId = config.property("mail.validateMail.templateId").getString()

        mapOf(
            SessionType.REGISTERED_USER to MailOTPConfig(mailValidatePlugin, mailValidateTemplateId),
            SessionType.UPDATE_PROFILE to MailOTPConfig(mailValidatePlugin, mailValidateTemplateId),
        )
    }
}

val httpClientModule = module {
    single<HttpClient> {
        HttpClient(CIO) {
            install(ContentNegotiation) {
                json()
            }
        }
    }
}

val databaseModule = module {
    single<CoroutineDatabase> {
        val config: ApplicationConfig = get()
        val connectionString = config.property("mongodb.url").getString()
        val databaseName = config.property("mongodb.database").getString()
        val settings = MongoClientSettings.builder().
            applyConnectionString(ConnectionString(connectionString)).
            build()
        KMongo.createClient(settings).coroutine.getDatabase(databaseName)
    }

    single<CoroutineCollection<CustomerUserPOJO>> {
        val database = get<CoroutineDatabase>()
        val col = database.getCollection<CustomerUserPOJO>("customer.user")

        // Runs the index creation logic in a blocking coroutine.
        runBlocking { // Using runBlocking because Koin initializes singletons synchronously and createIndex is a suspend function.
            val phoneIndexName = "${CustomerUserPOJO::phone.name}_1"

            // Retrieves the list of existing indexes in the collection.
            val existingIndexes = col.listIndexes<Document>().toList()

            val phoneIndexExists = existingIndexes.any { it.getString("name") == phoneIndexName }

            // Creates the phone index if it doesn't exist.
            if (!phoneIndexExists) {
                // Creates a unique ascending index on the phone field.
                col.createIndex(Indexes.ascending(CustomerUserPOJO::phone.name), IndexOptions().unique(true))
                metaticket.customer.user.logger.info("Created phone index: $phoneIndexName")
            }

            val emailIndexName = "${CustomerUserPOJO::email.name}_1"
            val emailIndexExists = existingIndexes.any { it.getString("name") == emailIndexName }
            if (!emailIndexExists) {
                // Creates a unique ascending index on the phone field.
                col.createIndex(Indexes.ascending(CustomerUserPOJO::email.name), IndexOptions().unique(false))
                metaticket.customer.user.logger.info("Created email index: $emailIndexName")
            }
        }
        col
    }

    single<UserRepository> {
        UserRepository(get())
    }
}

val redisClusterModule = module {
    single<RedisClusterManager<String>> {
        val config: ApplicationConfig by inject()
        val redisNodes = config.property("redis.cluster.nodes").getList()
        RedisClusterManager(redisNodes)
    }

    single<SessionRepository> {
        SessionRepository(get())
    }
}

val grpcServiceModule = module {
    single<CustomerUserServiceImpl> {
        CustomerUserServiceImpl(get(), get())
    }

    single<Server> {
        val config: ApplicationConfig by inject()
        val port: Int = config.property("grpc.server.port").getString().toInt()
        val userService: CustomerUserServiceImpl by inject()

        ServerBuilder.forPort(port)
            .addService(userService)
            .build()
    }
}

val gatewayModule = module {
    single<CaptchaGateway> { CaptchaGateway(get(), get()) }
    single<NotificationGateway> { NotificationGateway(get(), get()) }
}

val grpcChannelModule = module {
    single<ZaloNotificationServiceGrpcKt.ZaloNotificationServiceCoroutineStub> {
        val environment: ApplicationEnvironment by inject()
        val host = environment.config.property("grpc.channel.notificationS.host").getString()
        val port = environment.config.property("grpc.channel.notificationS.port").getString().toInt()
        val channel = ManagedChannelBuilder.forAddress(host, port)
            .usePlaintext()
            //.useTransportSecurity() // Consider using TLS in production
            .build()
        ZaloNotificationServiceGrpcKt.ZaloNotificationServiceCoroutineStub(channel)
    }
    single<MailNotificationServiceGrpcKt.MailNotificationServiceCoroutineStub> {
        val environment: ApplicationEnvironment by inject()
        val host = environment.config.property("grpc.channel.notificationS.host").getString()
        val port = environment.config.property("grpc.channel.notificationS.port").getString().toInt()
        val channel = ManagedChannelBuilder.forAddress(host, port)
            .usePlaintext()
            //.useTransportSecurity() // Consider using TLS in production
            .build()
        MailNotificationServiceGrpcKt.MailNotificationServiceCoroutineStub(channel)
    }
}

val controllersModule = module {
    single<UserCtrl> {
        UserCtrl(get(), get(), get(), get(), get(), get(named("znsConfig")), get(named("mailConfig")), get())
    }
}

val startUpModule = module {
    single<StartUp> { StartUp(get(), get(), get(), get()) }
}

class ApplicationDI(ktorEnv: ApplicationEnvironment, monitor: Events) : DIContext(ktorEnv, monitor) {
    val userCtrl by inject<UserCtrl>()
    val startUp by inject<StartUp>()
}

/**
 * Abstract base class for dependency injection contexts.
 *
 * This class initializes a Koin application and provides access to the Koin instance.
 *
 * @param ktorEnv The application environment.
 */
abstract class DIContext(ktorEnv: ApplicationEnvironment, monitor: Events) : KoinComponent {

    private val koinApp = koinApplication {
        // Declare used modules
        modules(
            configurationModule(ktorEnv, monitor), gatewayModule, databaseModule, httpClientModule, redisClusterModule,
            grpcServiceModule, controllersModule, grpcChannelModule, startUpModule
        )
    }

    private val koin: Koin = koinApp.koin

    override fun getKoin(): Koin {
        return koin
    }
}