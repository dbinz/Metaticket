package metaticket.qrcode

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
import metaticket.order.notification.grpc.OrderServiceGrpcKt
import metaticket.qrcode.controller.QRCodeController
import metaticket.qrcode.db.DatabaseService
import metaticket.qrcode.db.pojo.QRCodeDocument
import metaticket.qrcode.gateway.OrderGateway
import metaticket.qrcode.gateway.TicketSlotsGateway
import metaticket.qrcode.service.QRCodeServiceImpl
import metaticket.ticketslots.grpc.TicketSlotsServiceGrpcKt
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

const val qrcodeColToken = "qrcodeCol"

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
    single<QRCodeController> { QRCodeController(get(), get(), get(), get()) }
}

val databaseModule = module {

    single<CodecRegistry> {
        CodecRegistries.fromRegistries(
            MongoClientSettings.getDefaultCodecRegistry(),
            CodecRegistries.fromProviders(
                EnumCodecProvider(),
                PojoCodecProvider.builder()
                    .automatic(true)
                    .register("metaticket.qrcode.db.pojo")
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
        val settings = MongoClientSettings.builder()
            .applyConnectionString(ConnectionString(connectionString))
            .codecRegistry(registry)
            .build()

        val client = MongoClient.create(settings)
        client.getDatabase(dbName)
    }

    // provide the collection
    single<MongoCollection<QRCodeDocument>>(qualifier = named(qrcodeColToken)) {
        val env: ApplicationEnvironment by inject()
        val db: MongoDatabase by inject()
        val colName = env.config.property("db.qrcodeColName").getString()
        val col = db.getCollection(colName, QRCodeDocument::class.java)

        col
    }

    // provide the database service
    single<DatabaseService> {
        val col: MongoCollection<QRCodeDocument> by inject(qualifier = named(qrcodeColToken))
        DatabaseService(col)
    }
}

/**
 * Module for gRPC channel and stub.
 */
val grpcChannelModule = module {
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
    single<TicketSlotsGateway> { TicketSlotsGateway(get()) }
    single<OrderGateway> { OrderGateway(get()) }
}

val grpcServiceModule = module {
    single<QRCodeServiceImpl> {
        QRCodeServiceImpl(get())
    }

    single<Server> {
        val config: ApplicationConfig by inject()
        val port: Int = config.property("grpc.server.port").getString().toInt()

        val qrCodeService: QRCodeServiceImpl by inject()

        ServerBuilder.forPort(port)
            .addService(qrCodeService)
            .build()
    }
}

val startUpModule = module {
    single<StartUp> { StartUp(get(), get()) }
}

class ApplicationDI(ktorEnv: ApplicationEnvironment, monitor: Events) : DIContext(ktorEnv, monitor) {
    val startUp: StartUp by inject()
    val qrcodeCtrl: QRCodeController by inject()
}

abstract class DIContext(ktorEnv: ApplicationEnvironment, monitor: Events) : KoinComponent {

    private val koinApp = koinApplication {
        // declare used modules
        modules(
            configurationModule(ktorEnv, monitor),
            controllerModule, databaseModule, grpcServiceModule, grpcChannelModule, gatewayModule, startUpModule
        )
    }

    private val koin = koinApp.koin
    override fun getKoin(): Koin {
        return koin
    }
}
