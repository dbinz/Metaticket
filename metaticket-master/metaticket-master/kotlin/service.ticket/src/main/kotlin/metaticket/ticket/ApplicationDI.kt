package metaticket.ticket

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
import metaticket.common.redis.RedisClusterManager
import metaticket.jobrunr.grpc.JobrunrServiceGrpcKt
import metaticket.show.grpc.ShowServiceGrpcKt
import metaticket.ticket.cache.ConfigPerReservationCache
import metaticket.ticket.cache.ShowSessionCache
import metaticket.ticket.calendar.CalendarTicketService
import metaticket.ticket.controller.CalendarTicketController
import metaticket.ticket.controller.CategoryController
import metaticket.ticket.controller.TicketDocumentController
import metaticket.ticket.controller.TicketReservationController
import metaticket.ticket.database.service.*
import metaticket.ticket.gateway.JobrunrGateway
import metaticket.ticket.gateway.ShowGateway
import metaticket.ticket.pojo.db.*
import metaticket.ticket.processor.ReservationEventDistributor
import metaticket.ticket.processor.ReservationProcessorManager
import metaticket.ticket.processor.ReservationRepository
import metaticket.ticket.redis.RedisAsyncClient
import metaticket.ticket.service.JobExecutorServiceImpl
import metaticket.ticket.service.ReservationCoreService
import metaticket.ticket.service.ReservationServiceImpl
import metaticket.ticket.service.TicketServiceImpl
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

const val ticketDocColToken = "ticketDocCol"
const val ticketCapacityColToken = "ticketCapacityCol"
const val reservationColToken = "reservationCol"
const val configPerReservationColToken = "configPerReservationCol"
const val categoryMappingColToken = "categoryMappingCol"
const val calendarDayColToken = "calendarDayCol"

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

val controllerModule = module {
    single<TicketDocumentController> { TicketDocumentController(get(), get(), get(), get(), get(), get()) }
    single<TicketReservationController> { TicketReservationController(get(), get(), get(), get()) }
    single<CategoryController> { CategoryController(get(), get(), get(), get(), get()) }
    single<CalendarTicketController> { CalendarTicketController(get()) }
}

val processorModule = module {
    single<ReservationEventDistributor> { ReservationEventDistributor(get()) }
    single<ReservationRepository> { ReservationRepository() }
    single<ReservationProcessorManager> { ReservationProcessorManager(get(), get(), get(), get(), get(), get(), get()) }
    single<ReservationExecutionRetryable> { ReservationExecutionRetryable(get(), get(), get(), get(), get(), get()) }
}

val redisModule = module {
    // provide the redis client
    single<RedisClusterManager<Any>> {
        val env : ApplicationEnvironment by inject()
        val redisNodes = env.config.property("redis.cluster.nodes").getList()
        RedisClusterManager(redisNodes)
    }
    single<RedisAsyncClient> {
        RedisAsyncClient(get(), get())
    }
}

val cacheModule = module {
    single<ShowSessionCache> { ShowSessionCache(get()) }
    single<ConfigPerReservationCache> { ConfigPerReservationCache(get()) }
}

val databaseModule = module {

    single<CodecRegistry> {
        CodecRegistries.fromRegistries(
            MongoClientSettings.getDefaultCodecRegistry(),
            CodecRegistries.fromProviders(
                EnumCodecProvider(),
                PojoCodecProvider.builder()
                    .automatic(true)
                    .register("metaticket.ticket.pojo.db")
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
    single<MongoCollection<TicketDocument>>(qualifier = named(ticketDocColToken)) {
        val env : ApplicationEnvironment by inject()
        val db : MongoDatabase by inject()
        val colName = env.config.property("db.colTicketDoc").getString()
        db.getCollection(colName, TicketDocument::class.java)
    }
    single<MongoCollection<TicketCapacityDocument>>(qualifier = named(ticketCapacityColToken)) {
        val env : ApplicationEnvironment by inject()
        val db : MongoDatabase by inject()
        val colName = env.config.property("db.colTicketCapacity").getString()
        db.getCollection(colName, TicketCapacityDocument::class.java)
    }
    single<MongoCollection<TicketReservationDocument>>(qualifier = named(reservationColToken)) {
        val env : ApplicationEnvironment by inject()
        val db : MongoDatabase by inject()
        val colName = env.config.property("db.colTicketReservation").getString()
        db.getCollection(colName, TicketReservationDocument::class.java)
    }
    single<MongoCollection<ConfigPerReservationDocument>>(qualifier = named(configPerReservationColToken)) {
        val env : ApplicationEnvironment by inject()
        val db : MongoDatabase by inject()
        val colName = env.config.property("db.colConfigPerReservation").getString()
        db.getCollection(colName, ConfigPerReservationDocument::class.java)
    }

    // provide the database service
    single<TicketDocDBService> {
        val col : MongoCollection<TicketDocument> by inject(qualifier = named(ticketDocColToken))
        TicketDocDBService(col)
    }
    single<TicketCapacityDBService> {
        val col : MongoCollection<TicketCapacityDocument> by inject(qualifier = named(ticketCapacityColToken))
        TicketCapacityDBService(col)
    }
    single<TicketReservationDBService> {
        val col : MongoCollection<TicketReservationDocument> by inject(qualifier = named(reservationColToken))
        TicketReservationDBService(col)
    }
    single<ConfigPerReservationDBService> {
        val col : MongoCollection<ConfigPerReservationDocument> by inject(qualifier = named(configPerReservationColToken))
        ConfigPerReservationDBService(col)
    }
    single<MongoCollection<CategoryMappingDocument>>(qualifier = named(categoryMappingColToken)) {
        val env: ApplicationEnvironment by inject()
        val db: MongoDatabase by inject()
        val colName = env.config.property("db.categoryMappingColName").getString()
        val col = db.getCollection(colName, CategoryMappingDocument::class.java)
        col
    }
    single<CategoryMappingDBService> {
        val col : MongoCollection<CategoryMappingDocument> by inject(qualifier = named(categoryMappingColToken))
        CategoryMappingDBService(col)
    }
    single<MongoCollection<TicketCalendarDayDocument>>(qualifier = named(calendarDayColToken)) {
        val env: ApplicationEnvironment by inject()
        val db: MongoDatabase by inject()
        // Key mới, có mặc định: theo lệ deploy conf sửa tay trên server, không bắt buộc thêm key khi nâng cấp.
        val colName = env.config.propertyOrNull("db.colTicketCalendarDay")?.getString() ?: "ticket-calendar-day"
        db.getCollection(colName, TicketCalendarDayDocument::class.java)
    }
    single<TicketCalendarDayDBService> {
        val col : MongoCollection<TicketCalendarDayDocument> by inject(qualifier = named(calendarDayColToken))
        TicketCalendarDayDBService(col)
    }
}

val gatewayModule = module {
    single<JobrunrGateway> { JobrunrGateway(get(), get(), get()) }
    single<ShowGateway> { ShowGateway(get()) }
}

/**
 * Module for gRPC channel and stub.
 */
val grpcChannelModule = module {
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
}

val grpcServiceModule = module {
    single<TicketServiceImpl> { TicketServiceImpl(get(), get(), get(), get()) }
    single<JobExecutorServiceImpl> { JobExecutorServiceImpl(get(), get(), get(), get()) }
    single<ReservationServiceImpl> { ReservationServiceImpl(get()) }

    single<Server> {
        val config: ApplicationConfig by inject()
        val port: Int = config.property("grpc.server.port").getString().toInt()

        val ticketService: TicketServiceImpl by inject()
        val jobExecutorService: JobExecutorServiceImpl by inject()
        val reservationService: ReservationServiceImpl by inject()

        ServerBuilder.forPort(port)
            .addService(ticketService)
            .addService(jobExecutorService)
            .addService(reservationService)
            .build()
    }
}

val serviceModule = module {
    single<ReservationCoreService> { ReservationCoreService(get(), get(), get(), get(), get(), get(), get(), get(), get(), get(), get()) }
    single<CalendarTicketService> { CalendarTicketService(get(), get(), get(), get(), get(), get()) }
}

val startUpModule = module {
    single<StartUp> { StartUp(get(), get(), get(), get(), get(), get(), get()) }
}

class ApplicationDI(ktorEnv: ApplicationEnvironment, monitor: Events) : DIContext(ktorEnv, monitor) {
    val documentCtrl: TicketDocumentController by inject()
    val ticketReservationCtrl: TicketReservationController by inject()
    val categoryCtrl : CategoryController by inject()
    val calendarCtrl : CalendarTicketController by inject()
    val startUp by inject<StartUp>()
}

abstract class DIContext(ktorEnv : ApplicationEnvironment, monitor: Events) : KoinComponent {

    private val koinApp = koinApplication {
        // declare used modules
        modules(configurationModule(ktorEnv, monitor), serviceModule, grpcServiceModule, cacheModule, processorModule,
            controllerModule, redisModule, databaseModule, grpcChannelModule, gatewayModule, startUpModule
        )
    }

    private val koin = koinApp.koin
    override fun getKoin(): Koin {
        return koin
    }
}
