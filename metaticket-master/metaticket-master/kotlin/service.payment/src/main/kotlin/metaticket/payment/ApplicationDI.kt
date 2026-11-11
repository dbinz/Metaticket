package metaticket.payment

import com.fasterxml.jackson.core.util.DefaultIndenter
import com.fasterxml.jackson.core.util.DefaultPrettyPrinter
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.JsonNodeFactory
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.KotlinModule
import com.google.common.reflect.ClassPath
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
import metaticket.jobrunr.grpc.JobrunrServiceGrpcKt
import metaticket.order.notification.grpc.OrderNotificationServiceGrpcKt
import metaticket.payment.cache.PGAccountCache
import metaticket.payment.configuration.Configuration
import metaticket.payment.configuration.PaymentConfig
import metaticket.payment.controller.*
import metaticket.payment.datastructure.OrderType
import metaticket.payment.database.service.PGAccountConfigDBService
import metaticket.payment.database.service.PaymentDBService
import metaticket.payment.gateway.JobrunrGateway
import metaticket.payment.gateway.OrderNotificationGateway
import metaticket.payment.gateway.worker.NinePayPaymentGatewayWorker
import metaticket.payment.gateway.worker.OnePayPaymentGatewayWorker
import metaticket.payment.gateway.worker.VnPayPaymentGatewayWorker
import metaticket.payment.pojo.db.PaymentDocument
import metaticket.payment.pojo.db.PaymentGatewayConfig
import metaticket.payment.service.JobExecutorServiceImpl
import metaticket.payment.service.PaymentServiceImpl
import org.bson.UuidRepresentation
import org.bson.codecs.CollectionCodecProvider
import org.bson.codecs.EnumCodecProvider
import org.bson.codecs.IterableCodecProvider
import org.bson.codecs.configuration.CodecRegistries
import org.bson.codecs.configuration.CodecRegistry
import org.bson.codecs.pojo.PojoCodecProvider
import org.bson.codecs.pojo.annotations.BsonDiscriminator
import org.koin.core.Koin
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import org.koin.core.qualifier.named
import org.koin.dsl.koinApplication
import org.koin.dsl.module
import java.io.File
import java.util.stream.Collectors

const val paymentColToken = "paymentCol"
const val pgAccountConfigColToken = "pgAccountConfigCol"
const val orderNotificationStubToken = "orderNotificationStub"
const val accessoryNotificationStubToken = "accessoryNotificationStub"

fun configurationModule(ktorEnv: ApplicationEnvironment, monitor: Events) = module {
    single<ApplicationEnvironment> { ktorEnv }
    single<ApplicationConfig> { ktorEnv.config }
    single<Events> { monitor }
    single<JsonNodeFactory> { JsonNodeFactory.instance }
    single<ObjectMapper> {
        ObjectMapper().registerModules(
            JavaTimeModule(),
            KotlinModule.Builder().build(),
            ProtobufModule()
        )
    }

    single<Configuration> {
        val jsonMapper: ObjectMapper by inject()
        jsonMapper.readValue(File("./conf/config.json"), Configuration::class.java)
    }

    single<PaymentConfig> {
        val config: Configuration by inject()
        config.payment
    }
}

val httpClientModule = module {
    single<HttpClient> {
        HttpClient(CIO) {
            expectSuccess = false
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

val controllerModule = module {
    single<OnePayGatewayController> { OnePayGatewayController(get(), get(), get(), get(), get(), get(), get()) }
    single<NinePayGatewayController> { NinePayGatewayController(get(), get(), get(), get(), get(), get(), get()) }
    single<VnPayGatewayController> { VnPayGatewayController(get(), get(), get(), get(), get(), get(), get()) }
    single<PGAccountController> { PGAccountController(get(), get(), get()) }
    single<StartUp> { StartUp(get(), get(), get(), get(), get(), get()) }
    single<PaymentController> {
        PaymentController(get(), get(), get(), get(), get(), get(), get(), get())
    }
}

val workerModule = module {
    single<OnePayPaymentGatewayWorker> { OnePayPaymentGatewayWorker(get(), get()) }
    single<NinePayPaymentGatewayWorker> { NinePayPaymentGatewayWorker(get(), get()) }
    single<VnPayPaymentGatewayWorker> { VnPayPaymentGatewayWorker(get()) }
}

val cacheModule = module {
    single<PGAccountCache> { PGAccountCache(get()) }
}

val databaseModule = module {

    @Throws(Throwable::class)
    fun loadAllBsonDiscriminatorClasses(packages: List<String>): List<Class<*>> {
        return try {
            ClassPath.from(javaClass.classLoader)
                .allClasses.stream()
                .filter { c: ClassPath.ClassInfo -> packages.contains(c.packageName) }
                .map { c: ClassPath.ClassInfo -> c.load() }
                .filter {  it.isAnnotationPresent(BsonDiscriminator::class.java) }
                .collect(Collectors.toList())
        } catch (t: Throwable) {
            logger.error("Load BsonDiscriminator exception... ", t)
            throw t
        }
    }

    single {
        CodecRegistries.fromRegistries(
                MongoClientSettings.getDefaultCodecRegistry(),
                CodecRegistries.fromProviders(
                        EnumCodecProvider(),
                        CollectionCodecProvider(),
                        IterableCodecProvider(),
                        PojoCodecProvider.builder()
                                .automatic(true)
                                .register("metaticket.payment.pojo.db")
                                .register(*loadAllBsonDiscriminatorClasses(listOf(
                                    "metaticket.payment.pojo.db"
                                )).toTypedArray())
                                .build(),
                )
        )
    }

    // provide mongodb
    single<MongoDatabase> {
        val env: ApplicationEnvironment by inject()
        val connectionString = env.config.property("db.connection").getString()
        val dbName = env.config.property("db.dbName").getString()
        val registry: CodecRegistry by inject()
        val settings = MongoClientSettings.builder()
            .uuidRepresentation(UuidRepresentation.STANDARD)
            .applyConnectionString(ConnectionString(connectionString))
            .codecRegistry(registry).build()

        val client = MongoClient.create(settings)
        client.getDatabase(dbName)
    }

    // provide the collection
    single<MongoCollection<PaymentDocument>>(qualifier = named(paymentColToken)) {
        val env: ApplicationEnvironment by inject()
        val db: MongoDatabase by inject()
        val colName = env.config.property("db.paymentColName").getString()
        db.getCollection(colName, PaymentDocument::class.java)
    }

    single<MongoCollection<PaymentGatewayConfig>>(qualifier = named(pgAccountConfigColToken)) {
        val env: ApplicationEnvironment by inject()
        val db: MongoDatabase by inject()
        val colName = env.config.property("db.pgConfigColName").getString()
        db.getCollection(colName, PaymentGatewayConfig::class.java)
    }

    // provide the database service
    single<PaymentDBService> {
        val col: MongoCollection<PaymentDocument> by inject(qualifier = named(paymentColToken))
        PaymentDBService(col)
    }

    single<PGAccountConfigDBService> {
        val col: MongoCollection<PaymentGatewayConfig> by inject(qualifier = named(pgAccountConfigColToken))
        PGAccountConfigDBService(col)
    }
}

val gatewayModule = module {
    single<JobrunrGateway> { JobrunrGateway(get(), get()) }
    single<OrderNotificationGateway> {
        // Additive orderType-keyed routing (Req 8.1, 8.2): TICKET -> service.order (unchanged),
        // ACCESSORY -> the accessory-side OrderNotificationService impl.
        val orderStub: OrderNotificationServiceGrpcKt.OrderNotificationServiceCoroutineStub =
            get(qualifier = named(orderNotificationStubToken))
        val accessoryStub: OrderNotificationServiceGrpcKt.OrderNotificationServiceCoroutineStub =
            get(qualifier = named(accessoryNotificationStubToken))
        OrderNotificationGateway(
            mapOf(
                OrderType.TICKET to orderStub,
                OrderType.ACCESSORY to accessoryStub,
            )
        )
    }
}

/**
 * Module for gRPC channel and stub.
 */
val grpcChannelModule = module {
    single<OrderNotificationServiceGrpcKt.OrderNotificationServiceCoroutineStub>(qualifier = named(orderNotificationStubToken)) {
        val environment: ApplicationEnvironment by inject()
        val host = environment.config.property("grpc.channel.orderS.host").getString()
        val port = environment.config.property("grpc.channel.orderS.port").getString().toInt()
        val channel = ManagedChannelBuilder.forAddress(host, port)
            .usePlaintext()
            //.useTransportSecurity() // Consider using TLS in production
            .build()
        OrderNotificationServiceGrpcKt.OrderNotificationServiceCoroutineStub(channel)
    }

    // ACCESSORY callback target: the accessory-side OrderNotificationService impl (Req 8.2).
    single<OrderNotificationServiceGrpcKt.OrderNotificationServiceCoroutineStub>(qualifier = named(accessoryNotificationStubToken)) {
        val environment: ApplicationEnvironment by inject()
        val host = environment.config.property("grpc.channel.accessoryS.host").getString()
        val port = environment.config.property("grpc.channel.accessoryS.port").getString().toInt()
        val channel = ManagedChannelBuilder.forAddress(host, port)
            .usePlaintext()
            //.useTransportSecurity() // Consider using TLS in production
            .build()
        OrderNotificationServiceGrpcKt.OrderNotificationServiceCoroutineStub(channel)
    }

    single<JobrunrServiceGrpcKt.JobrunrServiceCoroutineStub> {
        val environment: ApplicationEnvironment by inject()
        val host = environment.config.property("grpc.channel.jobrunrS.host").getString()
        val port = environment.config.property("grpc.channel.jobrunrS.port").getString().toInt()
        val channel = ManagedChannelBuilder.forAddress(host, port)
            .usePlaintext()
            //.useTransportSecurity() // Consider using TLS in production
            .build()
        JobrunrServiceGrpcKt.JobrunrServiceCoroutineStub(channel)
    }
}

val grpcServiceModule = module {
    single<JobExecutorServiceImpl> {
        JobExecutorServiceImpl(get(), get(), get(), get(), get(), get(), get(), get(), get())
    }

    single<PaymentServiceImpl> {
        PaymentServiceImpl(get(), get())
    }

    single<Server> {
        val config: ApplicationConfig by inject()
        val port: Int = config.property("grpc.server.port").getString().toInt()

        val jobExecutorService: JobExecutorServiceImpl by inject()
        val paymentService: PaymentServiceImpl by inject()

        ServerBuilder.forPort(port)
            .addService(jobExecutorService)
            .addService(paymentService)
            .build()
    }
}

val startUpModule = module {
    single<StartUp> { StartUp(get(), get(), get(), get(), get(), get()) }
}

class ApplicationDI(ktorEnv: ApplicationEnvironment, monitor: Events) : DIContext(ktorEnv, monitor) {

    val paymentCtrl: PaymentController by inject()
    val pgAccountCtrl: PGAccountController by inject()
    val ninePayGatewayCtrl: NinePayGatewayController by inject()
    val vnPayGatewayCtrl: VnPayGatewayController by inject()
    val onepayGatewayCtrl: OnePayGatewayController by inject()
    val startUp: StartUp by inject()
}

abstract class DIContext(ktorEnv: ApplicationEnvironment, monitor: Events) : KoinComponent {

    private val koinApp = koinApplication {
        // declare used modules
        modules(
            configurationModule(ktorEnv, monitor), httpClientModule, controllerModule, databaseModule, workerModule,
            cacheModule, grpcServiceModule, grpcChannelModule, gatewayModule, startUpModule
        )
    }

    private val koin = koinApp.koin
    override fun getKoin(): Koin {
        return koin
    }
}
