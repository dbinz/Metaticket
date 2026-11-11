package metaticket.order.datacollector

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
import metaticket.order.datacollector.controller.OrderDataCollectorController
import metaticket.order.datacollector.database.service.OrderDataCollectedDBService
import metaticket.order.datacollector.database.service.OrderDataCollectorDBService
import metaticket.order.datacollector.database.service.OrderDataCollectorMappingDBService
import metaticket.order.datacollector.gateway.ReservationGateway
import metaticket.order.datacollector.pojo.db.OrderDataCollected
import metaticket.order.datacollector.pojo.db.OrderDataCollector
import metaticket.order.datacollector.pojo.db.OrderDataCollectorMapping
import metaticket.order.datacollector.service.OrderDataCollectorServiceImpl
import metaticket.ticket.grpc.ReservationServiceGrpcKt
import org.bson.codecs.CollectionCodecProvider
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

const val datacollectorMappingColToken = "datacollectorMappingCol"
const val datacollectorColToken = "datacollectorCol"
const val datacollectedColToken = "datacollectedCol"

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
    single<OrderDataCollectorController> { OrderDataCollectorController(get(), get(), get(), get(), get(), get()) }
}

val databaseModule = module {

    single<CodecRegistry> {
        CodecRegistries.fromRegistries(
            MongoClientSettings.getDefaultCodecRegistry(),
            CodecRegistries.fromProviders(
                EnumCodecProvider(),
                CollectionCodecProvider(),
                PojoCodecProvider.builder()
                    .automatic(true)
                    .register("metaticket.order.pojo.db", "metaticket.order.datacollector.pojo.db")
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
    single<MongoCollection<OrderDataCollectorMapping>>(qualifier = named(datacollectorMappingColToken)) {
        val env: ApplicationEnvironment by inject()
        val db: MongoDatabase by inject()
        val colName = env.config.property("db.orderDataCollectorMappingColName").getString()
        db.getCollection(colName, OrderDataCollectorMapping::class.java)
    }

    single<OrderDataCollectorMappingDBService> {
        val col: MongoCollection<OrderDataCollectorMapping> by inject(qualifier = named(datacollectorMappingColToken))
        OrderDataCollectorMappingDBService(col)
    }

    single<MongoCollection<OrderDataCollector>>(qualifier = named(datacollectorColToken)) {
        val env: ApplicationEnvironment by inject()
        val db: MongoDatabase by inject()
        val colName = env.config.property("db.orderDataCollectorColName").getString()
        db.getCollection(colName, OrderDataCollector::class.java)
    }

    single<OrderDataCollectorDBService> {
        val col: MongoCollection<OrderDataCollector> by inject(qualifier = named(datacollectorColToken))
        OrderDataCollectorDBService(col)
    }

    single<MongoCollection<OrderDataCollected>>(qualifier = named(datacollectedColToken)) {
        val env: ApplicationEnvironment by inject()
        val db: MongoDatabase by inject()
        val colName = env.config.property("db.orderDataCollectedColName").getString()
        db.getCollection(colName, OrderDataCollected::class.java)
    }

    single<OrderDataCollectedDBService> {
        val col: MongoCollection<OrderDataCollected> by inject(qualifier = named(datacollectedColToken))
        OrderDataCollectedDBService(col)
    }
}

/**
 * Module for gRPC channel and stub.
 */
val grpcChannelModule = module {
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
}

val gatewayModule = module {
    single<ReservationGateway> { ReservationGateway(get()) }
}

val grpcServiceModule = module {
    single<OrderDataCollectorServiceImpl> {
        OrderDataCollectorServiceImpl(get(), get())
    }

    single<Server> {
        val config: ApplicationConfig by inject()
        val port: Int = config.property("grpc.server.port").getString().toInt()

        val orderDataCollectorService: OrderDataCollectorServiceImpl by inject()

        ServerBuilder.forPort(port)
            .addService(orderDataCollectorService)
            .build()
    }
}

val startUpModule = module {
    single<StartUp> { StartUp(get(), get()) }
}

class ApplicationDI(ktorEnv: ApplicationEnvironment, monitor: Events) : DIContext(ktorEnv, monitor) {

    val documentCtrl: OrderDataCollectorController by inject()
    val startUp: StartUp by inject()
}

abstract class DIContext(ktorEnv: ApplicationEnvironment, monitor: Events) : KoinComponent {

    private val koinApp = koinApplication {
        // declare used modules
        modules(configurationModule(ktorEnv, monitor),
            controllerModule, databaseModule, grpcChannelModule, gatewayModule, grpcServiceModule, startUpModule
        )
    }

    private val koin = koinApp.koin
    override fun getKoin(): Koin {
        return koin
    }
}
