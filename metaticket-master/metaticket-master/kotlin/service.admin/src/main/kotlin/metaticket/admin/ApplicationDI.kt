package metaticket.admin

import com.fasterxml.jackson.core.util.DefaultIndenter
import com.fasterxml.jackson.core.util.DefaultPrettyPrinter
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.JsonNodeFactory
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.KotlinModule
import com.mongodb.ConnectionString
import com.mongodb.MongoClientSettings
import com.mongodb.kotlin.client.coroutine.MongoClient
import com.mongodb.kotlin.client.coroutine.MongoCollection
import com.mongodb.kotlin.client.coroutine.MongoDatabase
import io.grpc.ManagedChannelBuilder
import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.events.*
import io.ktor.serialization.jackson.*
import io.ktor.server.application.*
import metaticket.admin.configuration.Configuration
import metaticket.admin.configuration.ServiceExplorerConfig
import metaticket.admin.controller.*
import metaticket.admin.database.service.*
import metaticket.admin.gateway.*
import metaticket.admin.peer.OrderClient
import metaticket.admin.pojo.db.*
import metaticket.customer.user.grpc.CustomerUserServiceGrpcKt
import metaticket.order.notification.grpc.OrderServiceGrpcKt
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

const val showSessionDraftColToken = "showSessionDraftCol"
const val showSessionDraftPromotionColToken = "showSessionDraftPromotionCol"
const val showSessionDraftOrderCustomFieldColToken = "showSessionDraftOrderCustomFieldCol"
const val reportFilterColToken = "reportFilterCol"
const val ticketBookingColToken = "ticketBookingCol"

fun configurationModule(ktorEnv: ApplicationEnvironment, monitor: Events) = module {
    single<ApplicationEnvironment> {  ktorEnv }
    single<Events> {  monitor }
    single<JsonNodeFactory> { JsonNodeFactory.instance }
    single<ObjectMapper> { ObjectMapper().registerModule(KotlinModule.Builder().build()) }

    single<Configuration> {
        val jsonMapper: ObjectMapper by inject()
        jsonMapper.readValue(File("./conf/config.json"), Configuration::class.java)
    }

    single<ServiceExplorerConfig> {
        val config: Configuration by inject()
        config.se
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

    single<OrderClient> { OrderClient(get(), get()) }
}

val controllerModule = module {
    single<ShowSessionDraftController> { ShowSessionDraftController(get(), get(), get(), get(), get(), get()) }
    single<ShowSessionDraftPromotionController> { ShowSessionDraftPromotionController(get(), get(), get()) }
    single<ShowSessionDraftOrderCustomFieldController> { ShowSessionDraftOrderCustomFieldController(get(), get(), get()) }
    single<ReportFilterController> { ReportFilterController(get(), get(), get(), get(), get()) }
    single<ReportGovController> { ReportGovController(get(), get()) }
    single<TicketBookingController> { TicketBookingController(get(), get(), get(), get(), get(),get()) }
}

val databaseModule = module {

    single<CodecRegistry> {
        CodecRegistries.fromRegistries(
            MongoClientSettings.getDefaultCodecRegistry(),
            CodecRegistries.fromProviders(
                EnumCodecProvider(),
                PojoCodecProvider.builder()
                    .automatic(true)
                    .register("metaticket.admin.pojo.db")
                    .build(),
            )
        )
    }

    // provide mongo db
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

    single<MongoCollection<ShowSessionDraftDocument>>(qualifier = named(showSessionDraftColToken)) {
        val env : ApplicationEnvironment by inject()
        val db : MongoDatabase by inject()
        val colName = env.config.property("db.showSessionDraftColName").getString()
        db.getCollection(colName, ShowSessionDraftDocument::class.java)
    }

    single<MongoCollection<ShowSessionDraftPromotionDocument>>(qualifier = named(showSessionDraftPromotionColToken)) {
        val env : ApplicationEnvironment by inject()
        val db : MongoDatabase by inject()
        val colName = env.config.property("db.showSessionDraftPromotionColName").getString()
        db.getCollection(colName, ShowSessionDraftPromotionDocument::class.java)
    }

    single<MongoCollection<ShowSessionDraftOrderCustomFieldDocument>>(qualifier = named(showSessionDraftOrderCustomFieldColToken)) {
        val env : ApplicationEnvironment by inject()
        val db : MongoDatabase by inject()
        val colName = env.config.property("db.showSessionDraftOrderCustomFieldColName").getString()
        db.getCollection(colName, ShowSessionDraftOrderCustomFieldDocument::class.java)
    }

    single<MongoCollection<ReportFilterDocument>>(qualifier = named(reportFilterColToken)) {
        val env : ApplicationEnvironment by inject()
        val db : MongoDatabase by inject()
        val colName = env.config.property("db.reportFilterColName").getString()
        db.getCollection(colName, ReportFilterDocument::class.java)
    }

    single<MongoCollection<TicketBookingDocument>>(qualifier = named(ticketBookingColToken)) {
        val env : ApplicationEnvironment by inject()
        val db : MongoDatabase by inject()
        val colName = env.config.property("db.ticketBookingColName").getString()
        db.getCollection(colName, TicketBookingDocument::class.java)
    }

    single<ShowSessionDraftDBService> {
        val showSessionDraftCol : MongoCollection<ShowSessionDraftDocument> by inject(qualifier = named(showSessionDraftColToken))
        ShowSessionDraftDBService(showSessionDraftCol)
    }

    single<ShowSessionDraftPromotionDBService> {
        val showSessionDraftPromotionCol : MongoCollection<ShowSessionDraftPromotionDocument> by inject(qualifier = named(showSessionDraftPromotionColToken))
        ShowSessionDraftPromotionDBService(showSessionDraftPromotionCol)
    }

    single<ShowSessionDraftOrderCustomFieldDBService> {
        val showSessionDraftOrderCustomFieldCol : MongoCollection<ShowSessionDraftOrderCustomFieldDocument> by inject(qualifier = named(showSessionDraftOrderCustomFieldColToken))
        ShowSessionDraftOrderCustomFieldDBService(showSessionDraftOrderCustomFieldCol)
    }

    single<ReportFilterDBService> {
        val col : MongoCollection<ReportFilterDocument> by inject(qualifier = named(reportFilterColToken))
        ReportFilterDBService(col)
    }

    single<TicketBookingDBService> {
        val col : MongoCollection<TicketBookingDocument> by inject(qualifier = named(ticketBookingColToken))
        TicketBookingDBService(col)
    }
}

val gatewayModule = module {
    single<ShowGateway> { ShowGateway(get()) }
    single<TicketGateway> { TicketGateway(get()) }
    single<ReservationGateway> { ReservationGateway(get()) }
    single<PaymentGateway> { PaymentGateway(get()) }
    single<OrderGateway> { OrderGateway(get()) }
    single<CustomerUserGateway> { CustomerUserGateway(get()) }

}

val grpcChannelModule = module {
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

    single<CustomerUserServiceGrpcKt.CustomerUserServiceCoroutineStub> {
        val environment: ApplicationEnvironment by inject()
        val host = environment.config.property("grpc.channel.userS.host").getString()
        val port = environment.config.property("grpc.channel.userS.port").getString().toInt()
        val channel = ManagedChannelBuilder.forAddress(host, port)
            .usePlaintext()
            //.useTransportSecurity() // Consider using TLS in production
            .build()
        CustomerUserServiceGrpcKt.CustomerUserServiceCoroutineStub(channel)
    }
}

val startUpModule = module {
    single<StartUp> { StartUp(get(), get()) }
}


class ApplicationDI(ktorEnv: ApplicationEnvironment, monitor: Events) : DIContext(ktorEnv, monitor) {
    val startUp: StartUp by inject()
    val sessionDraftCtrl : ShowSessionDraftController by inject()
    val sessionDraftPromotionCtrl : ShowSessionDraftPromotionController by inject()
    val sessionDraftOrderCustomFieldCtrl : ShowSessionDraftOrderCustomFieldController by inject()
    val reportFilterCtrl : ReportFilterController by inject()
    val reportGovCtrl : ReportGovController by inject()
    val ticketBookingCtrl : TicketBookingController by inject()
}

abstract class DIContext(ktorEnv : ApplicationEnvironment, monitor: Events) : KoinComponent {

    private val koinApp = koinApplication {
        // declare used modules
        modules(configurationModule(ktorEnv, monitor), grpcChannelModule, gatewayModule, httpClientModule, controllerModule, databaseModule, startUpModule)
    }

    private val koin = koinApp.koin
    override fun getKoin(): Koin {
        return koin
    }
}
