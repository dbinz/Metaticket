package metaticket.show

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
import metaticket.jobrunr.grpc.JobrunrServiceGrpcKt
import metaticket.show.cloudflare.CloudflareClient
import metaticket.show.controller.CityController
import metaticket.show.controller.ShowController
import metaticket.show.database.service.CityDBService
import metaticket.show.database.service.DatabaseService
import metaticket.show.gateway.JobrunrGateway
import metaticket.show.gateway.TicketGateway
import metaticket.show.metasearch.MetaSearchClient
import metaticket.show.pojo.db.CityDocument
import metaticket.show.pojo.db.EventCategory
import metaticket.show.pojo.db.ShowDocument
import metaticket.show.service.HomepageConfig
import metaticket.show.service.JobExecutorServiceImpl
import metaticket.show.service.ShowServiceImpl
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

const val showColToken = "showCol"
const val cityColToken = "cityCol"
const val eventCategoryColToken = "eventCategoryCol"


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
    single<HomepageConfig> { HomepageConfig(ktorEnv) }
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

    single<MetaSearchClient> { MetaSearchClient(get(), get(), get()) }
    single<CloudflareClient> { CloudflareClient(get(), get()) }
}

val controllerModule = module {

    single<ShowController> { ShowController(get(), get(), get(), get(), get(), get(), get(), get(), get()) }
    single<CityController> { CityController(get(), get(), get()) }
}

val databaseModule = module {

    single<CodecRegistry> {
        CodecRegistries.fromRegistries(
            MongoClientSettings.getDefaultCodecRegistry(),
            CodecRegistries.fromProviders(
                EnumCodecProvider(),
                PojoCodecProvider.builder()
                    .automatic(true)
                    .register("metaticket.show.pojo")
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

    // provide the collection
    single<MongoCollection<ShowDocument>>(qualifier = named(showColToken)) {
        val env : ApplicationEnvironment by inject()
        val db : MongoDatabase by inject()
        val colName = env.config.property("db.showColName").getString()
        val col = db.getCollection(colName, ShowDocument::class.java)

        col
    }

    single<MongoCollection<CityDocument>>(qualifier = named(cityColToken)) {
        val env : ApplicationEnvironment by inject()
        val db : MongoDatabase by inject()
        val colName = env.config.property("db.cityColName").getString()
        val col = db.getCollection(colName, CityDocument::class.java)

        col
    }
    single<MongoCollection<EventCategory>>(qualifier = named(eventCategoryColToken)) {
        val env : ApplicationEnvironment by inject()
        val db : MongoDatabase by inject()
        val colName = env.config.property("db.eventCategoryColName").getString()
        val col = db.getCollection(colName, EventCategory::class.java)

        col
    }
    // provide the database service
    single<DatabaseService> {
        val showCol : MongoCollection<ShowDocument> by inject(qualifier = named(showColToken))
        val eventCategoryCol : MongoCollection<EventCategory> by inject(qualifier = named(eventCategoryColToken))
        DatabaseService(showCol, eventCategoryCol)
    }

    single<CityDBService> {
        val cityCol : MongoCollection<CityDocument> by inject(qualifier = named(cityColToken))
        CityDBService(cityCol)
    }

}

val gatewayModule = module {
    single<JobrunrGateway> { JobrunrGateway(get(), get(), get(), get()) }
    single<TicketGateway> { TicketGateway(get()) }
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
}

val grpcServiceModule = module {
    single<ShowServiceImpl> { ShowServiceImpl(get(), get(), get(), get(), get()) }
    single<JobExecutorServiceImpl> { JobExecutorServiceImpl(get(), get(), get(), get()) }

    single<Server> {
        val config: ApplicationConfig by inject()
        val port: Int = config.property("grpc.server.port").getString().toInt()

        val showService: ShowServiceImpl by inject()
        val jobExecutorService: JobExecutorServiceImpl by inject()

        ServerBuilder.forPort(port)
            .addService(showService)
            .addService(jobExecutorService)
            .build()
    }
}

val startUpModule = module {
    single<StartUp> { StartUp(get(), get(), get()) }
}

class ApplicationDI(ktorEnv: ApplicationEnvironment, monitor: Events) : DIContext(ktorEnv, monitor) {

    val showCtrl : ShowController by inject()
    val cityCtrl : CityController by inject()
    val startUp: StartUp by inject()
}

abstract class DIContext(ktorEnv : ApplicationEnvironment, monitor: Events) : KoinComponent {

    private val koinApp = koinApplication {
        // declare used modules
        modules(configurationModule(ktorEnv, monitor), httpClientModule, controllerModule, databaseModule,
            grpcChannelModule, grpcServiceModule, gatewayModule, startUpModule
        )
    }

    private val koin = koinApp.koin
    override fun getKoin(): Koin {
        return koin
    }
}
