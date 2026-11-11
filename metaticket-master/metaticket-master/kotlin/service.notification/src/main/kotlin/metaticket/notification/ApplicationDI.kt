package metaticket.notification

import com.fasterxml.jackson.core.util.DefaultIndenter
import com.fasterxml.jackson.core.util.DefaultPrettyPrinter
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.JsonNodeFactory
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.KotlinModule
import com.hubspot.jackson.datatype.protobuf.ProtobufModule
import com.mongodb.ConnectionString
import com.mongodb.MongoClientSettings
import com.mongodb.kotlin.client.coroutine.MongoClient
import com.mongodb.kotlin.client.coroutine.MongoCollection
import com.mongodb.kotlin.client.coroutine.MongoDatabase
import io.grpc.ManagedChannelBuilder
import io.grpc.Server
import io.grpc.ServerBuilder
import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.events.*
import io.ktor.serialization.jackson.*
import io.ktor.server.application.*
import io.ktor.server.config.*
import metaticket.notification.cache.ImageCache
import metaticket.notification.cache.JobRequestCache
import metaticket.notification.cache.MailTemplateCache
import metaticket.notification.controller.MailController
import metaticket.notification.database.service.MailTemplateDBService
import metaticket.notification.gateway.ZaloNotificationServiceGateway
import metaticket.notification.mail.client.MailClient
import metaticket.notification.plugin.template.service.PluginMailTemplateService
import metaticket.notification.plugin.template.service.PluginZNSTemplateService
import metaticket.notification.processor.SendMailProcessor
import metaticket.notification.processor.ZaloMessageProcessor
import metaticket.notification.service.JobExecutorServiceImpl
import metaticket.notification.service.MailNotificationServiceImpl
import metaticket.notification.service.ZaloNotificationServiceImpl
import metaticket.plugin.mailtemplate.pojo.db.MailTemplateDocument
import metaticket.qrcode.grpc.QRCodeServiceGrpcKt
import metaticket.ticketslots.grpc.TicketSlotsServiceGrpcKt
import org.bson.codecs.EnumCodecProvider
import org.bson.codecs.configuration.CodecRegistries
import org.bson.codecs.configuration.CodecRegistry
import org.bson.codecs.pojo.PojoCodecProvider
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import org.koin.core.context.GlobalContext.startKoin
import org.koin.core.module.Module
import org.koin.core.qualifier.named
import org.koin.dsl.module
import org.simplejavamail.api.mailer.Mailer
import org.simplejavamail.mailer.MailerBuilder
import java.security.cert.X509Certificate
import javax.net.ssl.X509TrustManager

const val mailTemplateColToken = "mailTemplateCol"

fun configurationModule(ktorEnv: ApplicationEnvironment, monitor: Events): Module = module {
    single<ApplicationEnvironment> { ktorEnv }
    single<Events> { monitor }
    single<ApplicationConfig> { ktorEnv.config }

    single<JsonNodeFactory> { JsonNodeFactory.instance }
    single<ObjectMapper> {
        ObjectMapper().registerModules(
            JavaTimeModule(),
            KotlinModule.Builder().build(),
            ProtobufModule()
        )
    }
}

val mailerModule = module {
    single<Mailer> { MailerBuilder.buildMailer() }
    single<MailClient> { MailClient(get()) }
}

val pluginModule = module {
    single<PluginMailTemplateService> { PluginMailTemplateService() }
    single<PluginZNSTemplateService> { PluginZNSTemplateService() }
}

val trust = object : X509TrustManager {
    override fun checkClientTrusted(p0: Array<out X509Certificate>?, p1: String?) {}

    override fun checkServerTrusted(p0: Array<out X509Certificate>?, p1: String?) {}

    override fun getAcceptedIssuers(): Array<X509Certificate>? = null
}

val httpClientModule = module {
    single<HttpClient> {
        HttpClient(CIO) {
            expectSuccess = false
            engine {
                https {
                    trustManager = trust
                }
            }
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
    }
}

val grpcChannelModule = module {
    single<QRCodeServiceGrpcKt.QRCodeServiceCoroutineStub> {
        val environment: ApplicationEnvironment by inject()
        val host = environment.config.property("grpc.channel.qrcodeS.host").getString()
        val port = environment.config.property("grpc.channel.qrcodeS.port").getString().toInt()
        val channel = ManagedChannelBuilder.forAddress(host, port)
            .usePlaintext()
            //.useTransportSecurity() // Consider using TLS in production
            .build()
        QRCodeServiceGrpcKt.QRCodeServiceCoroutineStub(channel)
    }
    single<TicketSlotsServiceGrpcKt.TicketSlotsServiceCoroutineStub> {
        val environment: ApplicationEnvironment by inject()
        val host = environment.config.property("grpc.channel.ticketSlotS.host").getString()
        val port = environment.config.property("grpc.channel.ticketSlotS.port").getString().toInt()
        val channel = ManagedChannelBuilder.forAddress(host, port)
            .usePlaintext()
            //.useTransportSecurity() // Consider using TLS in production
            .build()
        TicketSlotsServiceGrpcKt.TicketSlotsServiceCoroutineStub(channel)
    }
}

val grpcServiceModule = module {

    single<ZaloNotificationServiceImpl> { ZaloNotificationServiceImpl(get()) }
    single<JobExecutorServiceImpl> { JobExecutorServiceImpl(get(), get(), get()) }
    single<MailNotificationServiceImpl> { MailNotificationServiceImpl(get(), get()) }

    single<Server> {
        val config: ApplicationConfig by inject()
        val port: Int = config.property("grpc.server.port").getString().toInt()

        val zns: ZaloNotificationServiceImpl = get()
        val jobrunrService: JobExecutorServiceImpl = get()
        val mailService: MailNotificationServiceImpl = get()

        ServerBuilder.forPort(port)
            .addService(zns)
            .addService(jobrunrService)
            .addService(mailService)
            .build()
    }
}

val controllerModule = module {
    single<MailController> { MailController(get(), get(), get()) }
}

val cacheModule = module {
    single<JobRequestCache> { JobRequestCache() }
    single<MailTemplateCache> { MailTemplateCache(get()) }
    single<ImageCache> { ImageCache(get()) }
}

val processorModule = module {
    single<SendMailProcessor> {
        SendMailProcessor(get(), get(), get(), get(), get())
    }

    single<ZaloMessageProcessor> {
        ZaloMessageProcessor(get(), get(), get())
    }
}

val gatewayModule = module {
    single<ZaloNotificationServiceGateway> { ZaloNotificationServiceGateway(get(), get()) }
}

val databaseModule = module {
    single<CodecRegistry> {
        CodecRegistries.fromRegistries(
            MongoClientSettings.getDefaultCodecRegistry(),
            CodecRegistries.fromProviders(
                EnumCodecProvider(),
                PojoCodecProvider.builder()
                    .automatic(true)
                    .register("metaticket.plugin.mailtemplate.pojo.db")
                    .build(),
            )
        )
    }

    // provide mongo db
    single<MongoDatabase> {
        val env: ApplicationEnvironment by inject()
        val connectionString = env.config.property("db.connection").getString()
        val dbName = env.config.property("db.dbName").getString()
        val registry: CodecRegistry by inject()
        val settings = MongoClientSettings.builder().applyConnectionString(ConnectionString(connectionString))
            .codecRegistry(registry).build()

        val client = MongoClient.create(settings)
        client.getDatabase(dbName)
    }

    // provide the collection
    single<MongoCollection<MailTemplateDocument>>(qualifier = named(mailTemplateColToken)) {
        val env: ApplicationEnvironment by inject()
        val db: MongoDatabase by inject()
        val colName = env.config.property("db.mailTemplateColName").getString()
        db.getCollection(colName, MailTemplateDocument::class.java)
    }

    // provide the database service
    single<MailTemplateDBService> {
        val col: MongoCollection<MailTemplateDocument> by inject(qualifier = named(mailTemplateColToken))
        MailTemplateDBService(col)
    }
}

val startUpModule = module {
    single<StartUp> { StartUp(get(), get(), get(), get(), get(), get(), get()) }
}

val appModules = listOf(
    pluginModule, processorModule, httpClientModule, gatewayModule,
    databaseModule, controllerModule, cacheModule, mailerModule,
    grpcServiceModule, grpcChannelModule, startUpModule
)

class ApplicationDI(ktorEnv: ApplicationEnvironment, monitor: Events) : KoinComponent {
    // KoinComponent sẽ tự động sử dụng GlobalContext đã được khởi tạo

    // inject sẽ hoạt động dựa trên Koin context toàn cục
    val startUp: StartUp by inject()
    val mailCtrl: MailController by inject()

    companion object {
        private var koinInitialized = false
        private val koinInitLock = Any()

        fun initialize(ktorEnv: ApplicationEnvironment, monitor: Events) {
            synchronized(koinInitLock) {
                if (!koinInitialized) {
                    startKoin {
                        // logger(SLF4JLogger()) // Tùy chọn: Thêm Koin logger nếu cần
                        // Khai báo các module
                        modules(configurationModule(ktorEnv, monitor)) // Module cần tham số
                        modules(appModules) // Các module còn lại
                    }
                    koinInitialized = true
                }
            }
        }
    }

    init {
        // Đảm bảo Koin được khởi tạo khi ApplicationDI được tạo
        initialize(ktorEnv, monitor)
    }

    // Không cần override getKoin() nữa, KoinComponent sẽ dùng GlobalContext.
    // override fun getKoin(): Koin = GlobalContext.get().koin
}

//abstract class DIContext(ktorEnv: ApplicationEnvironment, monitor: Events) : KoinComponent {
//
//    private val koinApp = koinApplication {
//        // declare used modules
//        modules(
//            configurationModule(ktorEnv, monitor), pluginModule, processorModule, httpClientModule, gatewayModule,
//            databaseModule, controllerModule, cacheModule, mailerModule, grpcServiceModule, grpcChannelModule, startUpModule
//        )
//    }
//
//    private val koin = koinApp.koin
//    override fun getKoin(): Koin {
//        return koin
//    }
//}
