package metaticket.order

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
import io.ktor.events.*
import io.ktor.server.application.*
import io.ktor.server.config.*
import metaticket.jobrunr.grpc.JobrunrServiceGrpcKt
import metaticket.notification.mail.grpc.MailNotificationServiceGrpcKt
import metaticket.order.cache.CalculatePromotionClassesCache
import metaticket.order.cache.ShowCache
import metaticket.order.configuration.Configuration
import metaticket.order.configuration.OrderConfig
import metaticket.order.controller.*
import metaticket.order.database.service.*
import metaticket.order.datacollector.grpc.OrderDataCollectorServiceGrpcKt
import metaticket.order.gateway.*
import metaticket.order.kafka.OrderSucceedProducer
import metaticket.order.pojo.db.*
import metaticket.order.service.JobExecutorServiceImpl
import metaticket.order.service.OrderNotificationServiceImpl
import metaticket.order.service.OrderServiceImpl
import metaticket.order.service.OrderSucceedRetryService
import metaticket.order.storage.FailedOrderSucceedEventStorage
import metaticket.order.storage.FailedOrderSucceedEventStorageImpl
import metaticket.payment.grpc.PaymentServiceGrpcKt
import metaticket.show.grpc.ShowServiceGrpcKt
import metaticket.ticket.grpc.ReservationServiceGrpcKt
import metaticket.ticket.grpc.TicketServiceGrpcKt
import org.bson.codecs.EnumCodecProvider
import org.bson.codecs.configuration.CodecRegistries
import org.bson.codecs.configuration.CodecRegistry
import org.bson.codecs.pojo.PojoCodecProvider
import org.koin.core.Koin
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import org.koin.core.qualifier.named
import org.koin.dsl.koinApplication
import org.koin.dsl.module
import java.io.File

const val orderColToken = "mailPluginTmplCol"
const val promotionColToken = "promotionCol"
const val promotionCodeQuantityColToken = "promotionCodeQuantityCol"
const val promotionComboQuantityColToken = "promotionComboQuantityCol"
const val customFieldMappingColToken = "customFieldMappingCol"
const val failedOrderSucceedEventColToken = "failedOrderSucceedEventCol"

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

    single<OrderConfig> {
        val config: Configuration by inject()
        config.orderConfig
    }
}

val cacheModule = module {
    single<ShowCache> { ShowCache(get()) }
    single<CalculatePromotionClassesCache> { CalculatePromotionClassesCache() }
}

val controllerModule = module {
    single<OrderController> { OrderController(get(), get(), get(), get(), get(), get(), get(), get(), get(), get(), get(), get(), get(), get(), get(), get()) }
    single<ReportController> { ReportController(get()) }
    single<PromotionController> { PromotionController(get(), get(), get(), get()) }
    single<NotifyOrderController> { NotifyOrderController(get(), get(), get(), get(), get(), get(), get()) }
    single<FailedEventController> { FailedEventController(get(), get()) }
    single<OrderFailedHandler> { OrderFailedHandler(get(), get(), get(), get(), get(), get(), get(), get()) }
    single<StartUp> { StartUp(get(), get(), get(), get(), get(), get(), get()) }
    single<OrderCreationService> { OrderCreationService(get(), get(), get(), get(), get(), get(), get(), get(), get(), get(), get(), get(), get(), get(), get(), get()) }
}

val databaseModule = module {

    single<CodecRegistry> {
        CodecRegistries.fromRegistries(
            MongoClientSettings.getDefaultCodecRegistry(),
            CodecRegistries.fromProviders(
                EnumCodecProvider(),
                PojoCodecProvider.builder()
                    .automatic(true)
                    .register("metaticket.order.pojo.db")
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
    single<MongoCollection<OrderDocument>>(qualifier = named(orderColToken)) {
        val env: ApplicationEnvironment by inject()
        val db: MongoDatabase by inject()
        val colName = env.config.property("db.orderColName").getString()
        db.getCollection(colName, OrderDocument::class.java)
    }

    single<MongoCollection<Promotion>>(qualifier = named(promotionColToken)) {
        val env: ApplicationEnvironment by inject()
        val db: MongoDatabase by inject()
        val colName = env.config.property("db.promotionColName").getString()
        db.getCollection(colName, Promotion::class.java)
    }

    single<MongoCollection<PromotionCodeQuantity>>(qualifier = named(promotionCodeQuantityColToken)) {
        val env: ApplicationEnvironment by inject()
        val db: MongoDatabase by inject()
        val colName = env.config.property("db.promotionCodeQuantityColName").getString()
        db.getCollection(colName, PromotionCodeQuantity::class.java)
    }

    single<MongoCollection<PromotionComboQuantity>>(qualifier = named(promotionComboQuantityColToken)) {
        val env: ApplicationEnvironment by inject()
        val db: MongoDatabase by inject()
        val colName = env.config.property("db.promotionComboQuantityColName").getString()
        db.getCollection(colName, PromotionComboQuantity::class.java)
    }

    single<MongoCollection<OrderCustomFieldMapping>>(qualifier = named(customFieldMappingColToken)) {
        val env: ApplicationEnvironment by inject()
        val db: MongoDatabase by inject()
        val colName = env.config.property("db.customFieldMappingColName").getString()
        db.getCollection(colName, OrderCustomFieldMapping::class.java)
    }

    single<MongoCollection<FailedOrderSucceedEventDoc>>(qualifier = named(failedOrderSucceedEventColToken)) {
        val env: ApplicationEnvironment by inject()
        val db: MongoDatabase by inject()
        val colName = env.config.property("db.failedOrderSucceedEventColName").getString()
        db.getCollection(colName, FailedOrderSucceedEventDoc::class.java)
    }

    // provide the database service
    single<OrderDBService> {
        val orderCol: MongoCollection<OrderDocument> by inject(qualifier = named(orderColToken))
        OrderDBService(orderCol)
    }

    single<PromotionDBService> {
        val promotionCol: MongoCollection<Promotion> by inject(qualifier = named(promotionColToken))
        PromotionDBService(promotionCol)
    }

    single<PromotionCodeQuantityDBService> {
        val promotionCodeQuantityCol: MongoCollection<PromotionCodeQuantity> by inject(qualifier = named(promotionCodeQuantityColToken))
        PromotionCodeQuantityDBService(promotionCodeQuantityCol)
    }

    single<PromotionComboQuantityDBService> {
        val promotionComboQuantityCol: MongoCollection<PromotionComboQuantity> by inject(qualifier = named(promotionComboQuantityColToken))
        PromotionComboQuantityDBService(promotionComboQuantityCol)
    }

    single<OrderCustomFieldMappingDBService> {
        val col: MongoCollection<OrderCustomFieldMapping> by inject(qualifier = named(customFieldMappingColToken))
        OrderCustomFieldMappingDBService(col)
    }

    single<FailedOrderSucceedEventStorage> {
        val col: MongoCollection<FailedOrderSucceedEventDoc> by inject(qualifier = named(failedOrderSucceedEventColToken))
        FailedOrderSucceedEventStorageImpl(col)
    }
}

/**
 * Module for gRPC channel and stub.
 */
val grpcChannelModule = module {
    single<TicketServiceGrpcKt.TicketServiceCoroutineStub> {
        val environment: ApplicationEnvironment by inject()
        val host = environment.config.property("grpc.channel.ticketS.host").getString()
        val port = environment.config.property("grpc.channel.ticketS.port").getString().toInt()
        val channel = ManagedChannelBuilder.forAddress(host, port)
            .usePlaintext()
            //.useTransportSecurity() // Consider using TLS in production
            .build()
        TicketServiceGrpcKt.TicketServiceCoroutineStub(channel)
    }

    single<ReservationServiceGrpcKt.ReservationServiceCoroutineStub> {
        val environment: ApplicationEnvironment by inject()
        val host = environment.config.property("grpc.channel.ticketS.host").getString()
        val port = environment.config.property("grpc.channel.ticketS.port").getString().toInt()
        val channel = ManagedChannelBuilder.forAddress(host, port)
            .usePlaintext()
            //.useTransportSecurity() // Consider using TLS in production
            .build()
        ReservationServiceGrpcKt.ReservationServiceCoroutineStub(channel)
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

    single<ShowServiceGrpcKt.ShowServiceCoroutineStub> {
        val environment: ApplicationEnvironment by inject()
        val host = environment.config.property("grpc.channel.showS.host").getString()
        val port = environment.config.property("grpc.channel.showS.port").getString().toInt()
        val channel = ManagedChannelBuilder.forAddress(host, port)
            .usePlaintext()
            //.useTransportSecurity() // Consider using TLS in production
            .build()
        ShowServiceGrpcKt.ShowServiceCoroutineStub(channel)
    }

    single<PaymentServiceGrpcKt.PaymentServiceCoroutineStub> {
        val environment: ApplicationEnvironment by inject()
        val host = environment.config.property("grpc.channel.paymentS.host").getString()
        val port = environment.config.property("grpc.channel.paymentS.port").getString().toInt()
        val channel = ManagedChannelBuilder.forAddress(host, port)
            .usePlaintext()
            //.useTransportSecurity() // Consider using TLS in production
            .build()
        PaymentServiceGrpcKt.PaymentServiceCoroutineStub(channel)
    }

    single<OrderDataCollectorServiceGrpcKt.OrderDataCollectorServiceCoroutineStub> {
        val environment: ApplicationEnvironment by inject()
        val host = environment.config.property("grpc.channel.orderDataCollectorS.host").getString()
        val port = environment.config.property("grpc.channel.orderDataCollectorS.port").getString().toInt()
        val channel = ManagedChannelBuilder.forAddress(host, port)
            .usePlaintext()
            //.useTransportSecurity() // Consider using TLS in production
            .build()
        OrderDataCollectorServiceGrpcKt.OrderDataCollectorServiceCoroutineStub(channel)
    }
}

val gatewayModule = module {
    single<TicketGateway> { TicketGateway(get()) }
    single<ReservationGateway> { ReservationGateway(get()) }
    single<JobrunrGateway> { JobrunrGateway(get(), get(), get(), get(), get()) }
    single<ShowGateway> { ShowGateway(get()) }
    single<NotificationGateway> { NotificationGateway(get(), get(), get()) }
    single<PaymentGateway> { PaymentGateway(get()) }
    single<OrderDataCollectorGateway> { OrderDataCollectorGateway(get()) }
}

val grpcServiceModule = module {
    single<JobExecutorServiceImpl> {
        JobExecutorServiceImpl(get(), get(), get(), get(), get(), get())
    }

    single<OrderNotificationServiceImpl> {
        OrderNotificationServiceImpl(get(), get(), get(), get(), get(), get())
    }

    single<OrderServiceImpl> {
        OrderServiceImpl(get(), get(), get(), get(), get(), get(), get(), get(), get())
    }

    single<OrderSucceedRetryService> {
        OrderSucceedRetryService(get(), get())
    }

    single<Server> {
        val config: ApplicationConfig by inject()
        val port: Int = config.property("grpc.server.port").getString().toInt()

        val jobExecutorService: JobExecutorServiceImpl by inject()
        val orderService: OrderServiceImpl by inject()
        val orderNotificationService: OrderNotificationServiceImpl by inject()

        ServerBuilder.forPort(port)
            .addService(jobExecutorService)
            .addService(orderService)
            .addService(orderNotificationService)
            .build()
    }
}

val kafkaModule = module {
    single<OrderSucceedProducer> { OrderSucceedProducer(get(), get(), get(), get()) }
}

val startUpModule = module {
    single<StartUp> { StartUp(get(), get(), get(), get(), get(), get(), get()) }
}

class ApplicationDI(ktorEnv: ApplicationEnvironment, monitor: Events) : DIContext(ktorEnv, monitor) {

    val documentCtrl: OrderController by inject()
    val promotionCtrl: PromotionController by inject()
    val reportCtrl: ReportController by inject()
    val notifyOrderCtrl: NotifyOrderController by inject()
    val failedEventCtrl: FailedEventController by inject()
    val startUp: StartUp by inject()
}

abstract class DIContext(ktorEnv: ApplicationEnvironment, monitor: Events) : KoinComponent {

    private val koinApp = koinApplication {
        // declare used modules
        modules(configurationModule(ktorEnv, monitor), grpcChannelModule, controllerModule, databaseModule,
            cacheModule, gatewayModule, grpcServiceModule, kafkaModule, startUpModule
            )
    }

    private val koin = koinApp.koin
    override fun getKoin(): Koin {
        return koin
    }
}
