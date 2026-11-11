package metaticket.jobrunr

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.JsonNodeFactory
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.KotlinModule
import com.hubspot.jackson.datatype.protobuf.ProtobufModule
import com.mongodb.ConnectionString
import com.mongodb.MongoClientSettings
import com.mongodb.client.MongoClient
import com.mongodb.client.MongoClients
import com.mongodb.client.MongoDatabase
import io.grpc.ManagedChannelBuilder
import io.grpc.Server
import io.grpc.ServerBuilder
import io.ktor.events.*
import io.ktor.server.application.*
import io.ktor.server.config.*
import metaticket.jobrunr.datastructure.JobExecutorServiceName
import metaticket.jobrunr.gateway.*
import metaticket.jobrunr.grpc.JobExecutorServiceGrpcKt
import metaticket.jobrunr.jobrunr.JobrunrActivator
import metaticket.jobrunr.jobrunr.JobrunrBackgroundService
import metaticket.jobrunr.jobrunr.JobrunrRecurrentlyService
import metaticket.jobrunr.service.JobrunrServiceImpl
import org.bson.UuidRepresentation
import org.bson.codecs.EnumCodecProvider
import org.bson.codecs.configuration.CodecRegistries
import org.bson.codecs.configuration.CodecRegistry
import org.bson.codecs.pojo.PojoCodecProvider
import org.jobrunr.configuration.JobRunr
import org.jobrunr.scheduling.JobScheduler
import org.jobrunr.server.BackgroundJobServerConfiguration
import org.jobrunr.storage.nosql.mongo.MongoDBStorageProvider
import org.jobrunr.utils.mapper.jackson.JacksonJsonMapper
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
}

val databaseModule = module {
    single<CodecRegistry> {
        CodecRegistries.fromRegistries(
            MongoClientSettings.getDefaultCodecRegistry(),
            CodecRegistries.fromProviders(
                EnumCodecProvider(),
                PojoCodecProvider.builder()
                    .automatic(true)
                    .build(),
            )
        )
    }

    // provide mongodb
    single<MongoClient> {
        val env: ApplicationEnvironment by inject()
        val connectionString = env.config.property("db.connection").getString()
        val registry: CodecRegistry by inject()
        val settings = MongoClientSettings.builder()
            .applyConnectionString(ConnectionString(connectionString))
            .uuidRepresentation(UuidRepresentation.JAVA_LEGACY)
            .codecRegistry(registry)
            .build()

        MongoClients.create(settings)
    }

    single<MongoDatabase> {
        val env: ApplicationEnvironment by inject()
        val dbName = env.config.property("db.dbName").getString()
        val client: MongoClient by inject()
        client.getDatabase(dbName)
    }
}

val gatewayModule = module {
    single<TicketGateway> { TicketGateway(get(named(JobExecutorServiceName.TicketService))) }
    single<ShowGateway> { ShowGateway(get(named(JobExecutorServiceName.ShowService))) }
    single<OrderGateway> { OrderGateway(get(named(JobExecutorServiceName.OrderService))) }
    single<PaymentGateway> { PaymentGateway(get(named(JobExecutorServiceName.PaymentService))) }
    single<NotificationGateway> { NotificationGateway(get(named(JobExecutorServiceName.NotificationService))) }
    single<AccessoryGateway> { AccessoryGateway(get(named(JobExecutorServiceName.AccessoryService))) }
}


/**
 * Module for gRPC channel and stub.
 */
val grpcChannelModule = module {
    // Order Service Stub
    single<JobExecutorServiceGrpcKt.JobExecutorServiceCoroutineStub>(named(JobExecutorServiceName.OrderService)) {
        val environment: ApplicationEnvironment by inject()
        val host = environment.config.property("grpc.channel.orderS.host").getString()
        val port = environment.config.property("grpc.channel.orderS.port").getString().toInt()
        val channel = ManagedChannelBuilder.forAddress(host, port)
            .usePlaintext()
            .build()
        JobExecutorServiceGrpcKt.JobExecutorServiceCoroutineStub(channel)
    }

    // Notification Service Stub
    single<JobExecutorServiceGrpcKt.JobExecutorServiceCoroutineStub>(named(JobExecutorServiceName.NotificationService)) {
        val environment: ApplicationEnvironment by inject()
        val host = environment.config.property("grpc.channel.notificationS.host").getString()
        val port = environment.config.property("grpc.channel.notificationS.port").getString().toInt()
        val channel = ManagedChannelBuilder.forAddress(host, port)
            .usePlaintext()
            .build()
        JobExecutorServiceGrpcKt.JobExecutorServiceCoroutineStub(channel)
    }

    // Payment Service Stub
    single<JobExecutorServiceGrpcKt.JobExecutorServiceCoroutineStub>(named(JobExecutorServiceName.PaymentService)) {
        val environment: ApplicationEnvironment by inject()
        val host = environment.config.property("grpc.channel.paymentS.host").getString()
        val port = environment.config.property("grpc.channel.paymentS.port").getString().toInt()
        val channel = ManagedChannelBuilder.forAddress(host, port)
            .usePlaintext()
            .build()
        JobExecutorServiceGrpcKt.JobExecutorServiceCoroutineStub(channel)
    }

    // Show Service Stub
    single<JobExecutorServiceGrpcKt.JobExecutorServiceCoroutineStub>(named(JobExecutorServiceName.ShowService)) {
        val environment: ApplicationEnvironment by inject()
        val host = environment.config.property("grpc.channel.showS.host").getString()
        val port = environment.config.property("grpc.channel.showS.port").getString().toInt()
        val channel = ManagedChannelBuilder.forAddress(host, port)
            .usePlaintext()
            .build()
        JobExecutorServiceGrpcKt.JobExecutorServiceCoroutineStub(channel)
    }

    // Ticket Service Stub
    single<JobExecutorServiceGrpcKt.JobExecutorServiceCoroutineStub>(named(JobExecutorServiceName.TicketService)) {
        val environment: ApplicationEnvironment by inject()
        val host = environment.config.property("grpc.channel.ticketS.host").getString()
        val port = environment.config.property("grpc.channel.ticketS.port").getString().toInt()
        val channel = ManagedChannelBuilder.forAddress(host, port)
            .usePlaintext()
            .build()
        JobExecutorServiceGrpcKt.JobExecutorServiceCoroutineStub(channel)
    }

    // Accessory Service Stub (hosts the accessory-side expiry-sweep executor)
    single<JobExecutorServiceGrpcKt.JobExecutorServiceCoroutineStub>(named(JobExecutorServiceName.AccessoryService)) {
        val environment: ApplicationEnvironment by inject()
        val host = environment.config.property("grpc.channel.accessoryS.host").getString()
        val port = environment.config.property("grpc.channel.accessoryS.port").getString().toInt()
        val channel = ManagedChannelBuilder.forAddress(host, port)
            .usePlaintext()
            .build()
        JobExecutorServiceGrpcKt.JobExecutorServiceCoroutineStub(channel)
    }
}

val grpcServiceModule = module {
    single<JobrunrServiceImpl> {
        JobrunrServiceImpl()
    }

    single<Server> {
        val config: ApplicationConfig by inject()
        val port: Int = config.property("grpc.server.port").getString().toInt()
        val jobrunrService: JobrunrServiceImpl by inject()

        ServerBuilder.forPort(port)
            .addService(jobrunrService)
            .build()
    }
}

val controllerModule = module {

    single<JobScheduler> {
        val env: ApplicationEnvironment by inject()
        val dashboardPort = env.config.property("dashboard.port").getString().toInt()

        val jobActivator = JobrunrActivator(get(), get())
        val objectMapper : ObjectMapper by inject()

        val jobServerConfiguration = BackgroundJobServerConfiguration
            .usingStandardBackgroundJobServerConfiguration()
            .andPollIntervalInSeconds(5)

        val storageProvider: MongoDBStorageProvider by inject()

        val runr = JobRunr.configure()
            .useJsonMapper(JacksonJsonMapper(objectMapper))
            .useJobActivator(jobActivator)
            .useStorageProvider(storageProvider)
            .useBackgroundJobServer(jobServerConfiguration)
            .useDashboard(dashboardPort)
            .initialize()
            .jobScheduler

        runr
    }
}

val jobrunrModule = module {

    single<MongoDBStorageProvider> {
        val env: ApplicationEnvironment by inject()
        val dbName = env.config.property("db.dbName").getString()
        val mongoClient: MongoClient by inject()

        MongoDBStorageProvider(mongoClient, dbName)
    }

    single<JobrunrBackgroundService> { JobrunrBackgroundService(get(), get(), get(), get(), get(), get(), get()) }
    single<JobrunrRecurrentlyService> { JobrunrRecurrentlyService() }
}

val startUpModule = module {
    single<StartUp> { StartUp(get(), get()) }
}

class ApplicationDI(ktorEnv: ApplicationEnvironment, monitor: Events) : DIContext(ktorEnv, monitor) {

    val jobScheduler: JobScheduler by inject()
    val startUp: StartUp by inject()
}

abstract class DIContext(ktorEnv : ApplicationEnvironment, monitor: Events) : KoinComponent {

    private val koinApp = koinApplication {
        // declare used modules
        modules(configurationModule(ktorEnv, monitor), databaseModule, gatewayModule, controllerModule,
            jobrunrModule, grpcChannelModule, grpcServiceModule, startUpModule
        )
    }

    private val koin = koinApp.koin
    override fun getKoin(): Koin {
        return koin
    }
}