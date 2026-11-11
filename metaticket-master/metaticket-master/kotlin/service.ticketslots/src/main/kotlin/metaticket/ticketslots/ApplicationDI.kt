package metaticket.ticketslots

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
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.config.*
import metaticket.common.redis.RedisClusterManager
import metaticket.customer.user.grpc.CustomerUserServiceGrpcKt.CustomerUserServiceCoroutineStub
import metaticket.notification.zns.grpc.ZaloNotificationServiceGrpcKt
import metaticket.order.notification.grpc.OrderServiceGrpcKt
import metaticket.show.grpc.ShowServiceGrpcKt
import metaticket.ticketslots.cache.ShowCache
import metaticket.ticketslots.controller.AdminOrderSucceedEventController
import metaticket.ticketslots.controller.AdminQRCodeProducerEventController
import metaticket.ticketslots.controller.AdminQRCodeConsumerEventController
import metaticket.ticketslots.controller.TicketSlotsCtrl
import metaticket.ticketslots.dbservice.TicketSlotDatabaseService
import metaticket.ticketslots.gateway.CaptchaGateway
import metaticket.ticketslots.gateway.CustomerUserGateway
import metaticket.ticketslots.gateway.NotificationGateway
import metaticket.ticketslots.gateway.ShowGateway
import metaticket.ticketslots.kafka.GenerateTicketSlotQRCodeConsumer
import metaticket.ticketslots.kafka.GenerateTicketSlotQRCodeProducer
import metaticket.ticketslots.kafka.OrderSucceedConsumer
import metaticket.ticketslots.model.ZNSConfig
import metaticket.ticketslots.model.ZNSType
import metaticket.ticketslots.pojo.db.QRCodeEventConsumerDLQDoc
import metaticket.ticketslots.pojo.db.OrderSucceedEventConsumerDLQDoc
import metaticket.ticketslots.pojo.db.QRCodeEventProducerDLQDoc
import metaticket.ticketslots.pojo.db.TicketSlotDoc
import metaticket.ticketslots.processor.GenerateTicketSlotQRCodeProcessor
import metaticket.ticketslots.processor.OrderSucceedProcessor
import metaticket.ticketslots.repository.SessionRepository
import metaticket.ticketslots.dlq.service.OrderSucceedEventRetryService
import metaticket.ticketslots.dlq.service.QRCodeEventConsumerRetryService
import metaticket.ticketslots.dlq.service.QRCodeEventProducerRetryService
import metaticket.ticketslots.dlq.storage.OrderSucceedEventConsumerDLQStorage
import metaticket.ticketslots.dlq.storage.QRCodeEventConsumerDLQStorage
import metaticket.ticketslots.dlq.storage.QRCodeEventProducerDLQStorage
import metaticket.ticketslots.gateway.OrderGateway
import metaticket.ticketslots.service.TicketSlotsServiceImpl
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
    single<Map<ZNSType, ZNSConfig>> {
        val config: ApplicationConfig = get()
        val znsTransferTicketSlotPlugin = config.property("zns.transferTicketSlot.plugin").getString()
        val znsTransferTicketSlotTemplateId = config.property("zns.transferTicketSlot.templateId").getString()
        val znsTransferTicketSlotExpiredTime = config.property("zns.transferTicketSlot.expiredTimeInS").getString().toLong()
        val znsTransferTicketSlotFailover = config.property("zns.transferTicketSlot.failover").getString().toBoolean()
        val znsTransferTicketMaxOtpAttempts = config.property("zns.transferTicketSlot.maxOtpAttempts").getString().toInt()
        val znsNotifyTicketSlotPlugin = config.property("zns.notifyTicketSlot.plugin").getString()
        val znsNotifyTicketSlotTemplateId = config.property("zns.notifyTicketSlot.templateId").getString()
        val znsNotifyTicketSlotFailover = config.property("zns.notifyTicketSlot.failover").getString().toBoolean()

        mapOf(
            ZNSType.transferTicketSlot to ZNSConfig(znsTransferTicketSlotPlugin, znsTransferTicketSlotTemplateId, znsTransferTicketSlotFailover, znsTransferTicketSlotExpiredTime, znsTransferTicketMaxOtpAttempts),
            ZNSType.notifyTicketSlot to ZNSConfig(znsNotifyTicketSlotPlugin, znsNotifyTicketSlotTemplateId, znsNotifyTicketSlotFailover),
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

    single<CaptchaGateway> { CaptchaGateway(get(), get()) }
}

val controllerModule = module {
    single<TicketSlotsCtrl> {
        TicketSlotsCtrl(
            db = get(),
            config = get(),
            captchaClient = get(),
            notificationGateway = get(),
            znsConfig = get(),
            sessionRepo = get(),
            userGateway = get(),
            generateTicketSlotQRCodeProducer = get(),
            qrCodeProducerDLQStorage = get(),
            showCacheService = get()
        )
    }
    single<AdminQRCodeProducerEventController> {
        AdminQRCodeProducerEventController(
            qrCodeProducerDLQStorage = get(),
            producerRetryService = get()
        )
    }
    single<AdminQRCodeConsumerEventController> {
        AdminQRCodeConsumerEventController(
            qrCodeConsumerDLQStorage = get(),
            consumerRetryService = get()
        )
    }
    single<AdminOrderSucceedEventController> { AdminOrderSucceedEventController(get(), get(), get()) }
}

val processorModule = module {
    single<GenerateTicketSlotQRCodeProcessor> { GenerateTicketSlotQRCodeProcessor(get()) }
    single<OrderSucceedProcessor> { OrderSucceedProcessor(get(), get()) }
}

val cacheModule = module {
    single<ShowCache> { ShowCache(get()) }
}

val databaseModule = module {

    single<CodecRegistry> {
        CodecRegistries.fromRegistries(
            MongoClientSettings.getDefaultCodecRegistry(),
            CodecRegistries.fromProviders(
                EnumCodecProvider(),
                PojoCodecProvider.builder()
                    .automatic(true)
                    .register("metaticket.ticketslots.pojo.db")
                    .build(),
            )
        )
    }

    // provide mongodb
    single<MongoDatabase> {
        val env : ApplicationEnvironment by inject()
        val connectionString = env.config.property("db.connection").getString()
        val dbName = env.config.property("db.dbName").getString()
        val registry : CodecRegistry by inject()
        val settings = MongoClientSettings.builder().
                            applyConnectionString(ConnectionString(connectionString)).
                            codecRegistry(registry).
                            build()

        val client = MongoClient.create(settings)
        client.getDatabase(dbName)
    }

    // provide the collection
    single<MongoCollection<TicketSlotDoc>>(qualifier = named(TicketSlotDoc::class.simpleName!!)) {
        val db : MongoDatabase by inject()
        val col = db.getCollection("ticket_slots", TicketSlotDoc::class.java)
        col
    }

    single<MongoCollection<OrderSucceedEventConsumerDLQDoc>>(qualifier = named(OrderSucceedEventConsumerDLQDoc::class.simpleName!!)) {
        val db : MongoDatabase by inject()
        val col = db.getCollection("ticket_slot_order_succeed_event_consumer_dlq", OrderSucceedEventConsumerDLQDoc::class.java)
        col
    }

    single<MongoCollection<QRCodeEventProducerDLQDoc>>(qualifier = named(QRCodeEventProducerDLQDoc::class.simpleName!!)) {
        val db : MongoDatabase by inject()
        val col = db.getCollection("ticket_slot_qrcode_event_producer_dlq", QRCodeEventProducerDLQDoc::class.java)
        col
    }

    single<MongoCollection<QRCodeEventConsumerDLQDoc>>(qualifier = named(QRCodeEventConsumerDLQDoc::class.simpleName!!)) {
        val db : MongoDatabase by inject()
        val col = db.getCollection("ticket_slot_qrcode_event_consumer_dlq", QRCodeEventConsumerDLQDoc::class.java)
        col
    }

    single<TicketSlotDatabaseService> {
        val col : MongoCollection<TicketSlotDoc> by inject(qualifier = named(TicketSlotDoc::class.simpleName!!))
        TicketSlotDatabaseService(col)
    }

    single<QRCodeEventProducerDLQStorage> {
        val col : MongoCollection<QRCodeEventProducerDLQDoc> by inject(qualifier = named(QRCodeEventProducerDLQDoc::class.simpleName!!))
        QRCodeEventProducerDLQStorage(col)
    }

    single<QRCodeEventConsumerDLQStorage> {
        val col : MongoCollection<QRCodeEventConsumerDLQDoc> by inject(qualifier = named(QRCodeEventConsumerDLQDoc::class.simpleName!!))
        QRCodeEventConsumerDLQStorage(col)
    }

    single<OrderSucceedEventConsumerDLQStorage> {
        val col : MongoCollection<OrderSucceedEventConsumerDLQDoc> by inject(qualifier = named(OrderSucceedEventConsumerDLQDoc::class.simpleName!!))
        OrderSucceedEventConsumerDLQStorage(col)
    }
}

val kafkaModule = module {
    single<GenerateTicketSlotQRCodeConsumer> {
        GenerateTicketSlotQRCodeConsumer(
            config = get(),
            objectMapper = get(),
            processor = get(),
            qrCodeConsumerDLQStorage = get()
        )
    }
    single<OrderSucceedConsumer> { OrderSucceedConsumer(get(), get(), get(), get()) }

    single<GenerateTicketSlotQRCodeProducer> { GenerateTicketSlotQRCodeProducer(get(), get()) }
}

val grpcServiceModule = module {
    single<TicketSlotsServiceImpl> { TicketSlotsServiceImpl(get(), get()) }

    single<Server> {
        val config: ApplicationConfig by inject()
        val port: Int = config.property("grpc.server.port").getString().toInt()

        val ticketService: TicketSlotsServiceImpl by inject()

        ServerBuilder.forPort(port)
            .addService(ticketService)
            .build()
    }
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

    single<CustomerUserServiceCoroutineStub> {
        val environment: ApplicationEnvironment by inject()
        val host = environment.config.property("grpc.channel.userS.host").getString()
        val port = environment.config.property("grpc.channel.userS.port").getString().toInt()
        val channel = ManagedChannelBuilder.forAddress(host, port)
            .usePlaintext()
            //.useTransportSecurity() // Consider using TLS in production
            .build()
        CustomerUserServiceCoroutineStub(channel)
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

    single<OrderServiceGrpcKt.OrderServiceCoroutineStub> {
        val environment: ApplicationEnvironment by inject()
        val host = environment.config.property("grpc.channel.orderS.host").getString()
        val port = environment.config.property("grpc.channel.orderS.port").getString().toInt()
        val channel = ManagedChannelBuilder.forAddress(host, port)
            .usePlaintext()
            //.useTransportSecurity() // Consider using TLS in production
            .build()
        OrderServiceGrpcKt.OrderServiceCoroutineStub(channel)
    }
}

val gatewayModule = module {
    single<NotificationGateway> { NotificationGateway(get()) }
    single<CustomerUserGateway> { CustomerUserGateway(get()) }
    single<ShowGateway> { ShowGateway(get()) }
    single<OrderGateway> { OrderGateway(get()) }
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

val repositoryModule = module {
    single<SessionRepository> { SessionRepository(get()) }
}

val retryServiceModule = module {
    single<QRCodeEventProducerRetryService> {
        QRCodeEventProducerRetryService(
            qrCodeProducerDLQStorageImpl = get(),
            qrCodeProducer = get(),
        )
    }

    single<QRCodeEventConsumerRetryService> {
        QRCodeEventConsumerRetryService(
            qrCodeConsumerDLQStorageImpl = get(),
            qrCodeProcessor = get(),
        )
    }

    single<OrderSucceedEventRetryService> {
        OrderSucceedEventRetryService(
            storageImpl = get(),
            processor = get()
        )
    }
}

val startUpModule = module {
    single<StartUp> { StartUp(get(), get(), get(), get(), get(), get(), get(), get(), get(), get()) }
}

class ApplicationDI(ktorEnv: ApplicationEnvironment, monitor: Events) : DIContext(ktorEnv, monitor) {
    val startUp by inject<StartUp>()
    val ticketSlotsCtrl by inject<TicketSlotsCtrl>()
    val adminQRCodeProducerEventCtrl by inject<AdminQRCodeProducerEventController>()
    val adminQRCodeConsumerEventCtrl by inject<AdminQRCodeConsumerEventController>()
    val adminOrderSucceedEventCtrl by inject<AdminOrderSucceedEventController>()
}

abstract class DIContext(ktorEnv : ApplicationEnvironment, monitor: Events) : KoinComponent {

    private val koinApp = koinApplication {
        // declare used modules
        modules(configurationModule(ktorEnv, monitor), grpcServiceModule, processorModule, cacheModule,
            controllerModule, databaseModule, kafkaModule, startUpModule, repositoryModule, httpClientModule,
            grpcChannelModule, gatewayModule, redisClusterModule, retryServiceModule
        )
    }

    private val koin = koinApp.koin
    override fun getKoin(): Koin {
        return koin
    }
}
