package metaticket.accessory

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
import metaticket.accessory.controller.AccessoryController
import metaticket.accessory.database.AccessoryReportFilterDatabaseService
import metaticket.accessory.database.CatalogDatabaseService
import metaticket.accessory.database.DiscountDatabaseService
import metaticket.accessory.database.OrderDatabaseService
import metaticket.accessory.database.ReconciliationDatabaseService
import metaticket.accessory.gateway.AccessoryMailGateway
import metaticket.accessory.gateway.JobrunrGateway
import metaticket.accessory.gateway.PaymentGateway
import metaticket.accessory.service.AccessoryJobExecutorServiceImpl
import metaticket.accessory.service.AccessoryOrderService
import metaticket.accessory.service.AccessoryOrderServiceImpl
import metaticket.accessory.service.AccessoryShowOpenService
import metaticket.accessory.service.DiscountCodeService
import metaticket.accessory.service.DiscountCodeServiceImpl
import metaticket.accessory.service.ExpirySweepService
import metaticket.accessory.service.OrderCodeService
import metaticket.accessory.service.OrderNotificationServiceImpl
import metaticket.accessory.service.OrderStateMachine
import metaticket.accessory.pojo.db.AccessoryDocument
import metaticket.accessory.pojo.db.AccessoryOrderDocument
import metaticket.accessory.pojo.db.AccessoryReportFilterDocument
import metaticket.accessory.pojo.db.AccessoryShowDocument
import metaticket.accessory.pojo.db.DiscountCodeDocument
import metaticket.accessory.pojo.db.OrderSeqDocument
import metaticket.accessory.pojo.db.ReconciliationEntry
import metaticket.jobrunr.grpc.JobrunrServiceGrpcKt
import metaticket.notification.mail.grpc.MailNotificationServiceGrpcKt
import metaticket.payment.grpc.PaymentServiceGrpcKt
import org.bson.Document
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

// -----------------------------------------------------------------------------
// Collection accessors (metaticket_accessory database)
// -----------------------------------------------------------------------------
const val accessoryShowColName = "accessory-show"
const val accessoryColName = "accessory"
const val accessoryOrderColName = "accessory-order"
const val accessoryDiscountCodeColName = "accessory-discount-code"
const val accessoryOrderSeqColName = "accessory-order-seq"
const val accessoryReconciliationColName = "accessory-reconciliation"
const val accessoryReportFilterColName = "accessory-report-filter"

const val accessoryShowColToken = "accessoryShowCol"
const val accessoryColToken = "accessoryCol"
const val accessoryOrderColToken = "accessoryOrderCol"
const val accessoryDiscountCodeColToken = "accessoryDiscountCodeCol"
const val accessoryOrderSeqColToken = "accessoryOrderSeqCol"
const val accessoryReconciliationColToken = "accessoryReconciliationCol"
const val accessoryReportFilterColToken = "accessoryReportFilterCol"

// Typed (POJO-mapped) collection accessors for the catalog models.
const val accessoryShowTypedColToken = "accessoryShowTypedCol"
const val accessoryTypedColToken = "accessoryTypedCol"
const val accessoryOrderTypedColToken = "accessoryOrderTypedCol"
const val accessoryDiscountCodeTypedColToken = "accessoryDiscountCodeTypedCol"
const val accessoryOrderSeqTypedColToken = "accessoryOrderSeqTypedCol"
const val accessoryReconciliationTypedColToken = "accessoryReconciliationTypedCol"
const val accessoryReportFilterTypedColToken = "accessoryReportFilterTypedCol"

fun configurationModule(ktorEnv: ApplicationEnvironment, monitor: Events) = module {
    single<ApplicationEnvironment> { ktorEnv }
    single<ApplicationConfig> { ktorEnv.config }
    single<Events> { monitor }
    single<JsonNodeFactory> { JsonNodeFactory.instance }
    single<ObjectMapper> {
        ObjectMapper().registerModules(
            JavaTimeModule(),
            KotlinModule.Builder().build(),
            // Serialize the SendMailAccessoryOrderRequest proto into the JobRunr jobDesc for the
            // durable email fallback (mirrors service.order / service.notification).
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
                    .register("metaticket.accessory.pojo")
                    .build(),
            )
        )
    }

    // provide the metaticket_accessory database
    single<MongoDatabase> {
        val env: ApplicationEnvironment by inject()
        val connectionString = env.config.property("mongodb.connectionString").getString()
        val dbName = env.config.property("mongodb.dbName").getString()
        val registry: CodecRegistry by inject()
        val settings = MongoClientSettings.builder()
            .applyConnectionString(ConnectionString(connectionString))
            .codecRegistry(registry)
            .build()

        val client = MongoClient.create(settings)
        client.getDatabase(dbName)
    }

    // collection accessors
    single<MongoCollection<Document>>(qualifier = named(accessoryShowColToken)) {
        val db: MongoDatabase by inject()
        db.getCollection(accessoryShowColName, Document::class.java)
    }
    single<MongoCollection<Document>>(qualifier = named(accessoryColToken)) {
        val db: MongoDatabase by inject()
        db.getCollection(accessoryColName, Document::class.java)
    }
    single<MongoCollection<Document>>(qualifier = named(accessoryOrderColToken)) {
        val db: MongoDatabase by inject()
        db.getCollection(accessoryOrderColName, Document::class.java)
    }
    single<MongoCollection<Document>>(qualifier = named(accessoryDiscountCodeColToken)) {
        val db: MongoDatabase by inject()
        db.getCollection(accessoryDiscountCodeColName, Document::class.java)
    }
    single<MongoCollection<Document>>(qualifier = named(accessoryOrderSeqColToken)) {
        val db: MongoDatabase by inject()
        db.getCollection(accessoryOrderSeqColName, Document::class.java)
    }
    single<MongoCollection<Document>>(qualifier = named(accessoryReconciliationColToken)) {
        val db: MongoDatabase by inject()
        db.getCollection(accessoryReconciliationColName, Document::class.java)
    }

    // typed (POJO-mapped) catalog collection accessors
    single<MongoCollection<AccessoryShowDocument>>(qualifier = named(accessoryShowTypedColToken)) {
        val db: MongoDatabase by inject()
        db.getCollection(accessoryShowColName, AccessoryShowDocument::class.java)
    }
    single<MongoCollection<AccessoryDocument>>(qualifier = named(accessoryTypedColToken)) {
        val db: MongoDatabase by inject()
        db.getCollection(accessoryColName, AccessoryDocument::class.java)
    }
    single<MongoCollection<AccessoryOrderDocument>>(qualifier = named(accessoryOrderTypedColToken)) {
        val db: MongoDatabase by inject()
        db.getCollection(accessoryOrderColName, AccessoryOrderDocument::class.java)
    }
    single<MongoCollection<DiscountCodeDocument>>(qualifier = named(accessoryDiscountCodeTypedColToken)) {
        val db: MongoDatabase by inject()
        db.getCollection(accessoryDiscountCodeColName, DiscountCodeDocument::class.java)
    }
    single<MongoCollection<OrderSeqDocument>>(qualifier = named(accessoryOrderSeqTypedColToken)) {
        val db: MongoDatabase by inject()
        db.getCollection(accessoryOrderSeqColName, OrderSeqDocument::class.java)
    }
    single<MongoCollection<ReconciliationEntry>>(qualifier = named(accessoryReconciliationTypedColToken)) {
        val db: MongoDatabase by inject()
        db.getCollection(accessoryReconciliationColName, ReconciliationEntry::class.java)
    }
    single<MongoCollection<AccessoryReportFilterDocument>>(qualifier = named(accessoryReportFilterTypedColToken)) {
        val db: MongoDatabase by inject()
        db.getCollection(accessoryReportFilterColName, AccessoryReportFilterDocument::class.java)
    }

    // catalog database service (declares catalog indexes on instantiation)
    single<CatalogDatabaseService> {
        CatalogDatabaseService(
            get(qualifier = named(accessoryShowTypedColToken)),
            get(qualifier = named(accessoryTypedColToken)),
        )
    }

    // order database service (declares the unique accessory-order code index on instantiation)
    single<OrderDatabaseService> {
        OrderDatabaseService(
            get(qualifier = named(accessoryOrderTypedColToken)),
        )
    }

    // discount database service (declares the uppercase-canonical unique discount-code index)
    single<DiscountDatabaseService> {
        DiscountDatabaseService(
            get(qualifier = named(accessoryDiscountCodeTypedColToken)),
        )
    }

    // reconciliation database service (append-only anomaly log; used by payment-status handling)
    single<ReconciliationDatabaseService> {
        ReconciliationDatabaseService(
            get(qualifier = named(accessoryReconciliationTypedColToken)),
        )
    }

    single<AccessoryReportFilterDatabaseService> {
        AccessoryReportFilterDatabaseService(
            get(qualifier = named(accessoryReportFilterTypedColToken)),
        )
    }
}

/**
 * gRPC client channels/stubs for the reused services (task 16.2).
 *
 * - [PaymentServiceGrpcKt.PaymentServiceCoroutineStub] dials `service.payment.CreatePayment`
 *   (additive `orderType = ACCESSORY`, Req 7.1) at `grpc.channel.payment`.
 * - [MailNotificationServiceGrpcKt.MailNotificationServiceCoroutineStub] dials the dedicated
 *   `SendMailAccessoryOrder` RPC (Req 9.4) at `grpc.channel.notification`.
 *
 * All channels use plaintext transport, matching the platform convention (behind the private
 * network / nginx). Each stub is a Koin singleton so the underlying channel is shared.
 */
val grpcChannelModule = module {
    single<PaymentServiceGrpcKt.PaymentServiceCoroutineStub> {
        val env: ApplicationEnvironment by inject()
        val host = env.config.property("grpc.channel.payment.host").getString()
        val port = env.config.property("grpc.channel.payment.port").getString().toInt()
        val channel = ManagedChannelBuilder.forAddress(host, port).usePlaintext().build()
        PaymentServiceGrpcKt.PaymentServiceCoroutineStub(channel)
    }

    single<MailNotificationServiceGrpcKt.MailNotificationServiceCoroutineStub> {
        val env: ApplicationEnvironment by inject()
        val host = env.config.property("grpc.channel.notification.host").getString()
        val port = env.config.property("grpc.channel.notification.port").getString().toInt()
        val channel = ManagedChannelBuilder.forAddress(host, port).usePlaintext().build()
        MailNotificationServiceGrpcKt.MailNotificationServiceCoroutineStub(channel)
    }

    // Dials service.jobrunr.ScheduleJob for the durable confirmation-email fallback (Req 9.6).
    single<JobrunrServiceGrpcKt.JobrunrServiceCoroutineStub> {
        val env: ApplicationEnvironment by inject()
        val host = env.config.property("grpc.channel.jobrunr.host").getString()
        val port = env.config.property("grpc.channel.jobrunr.port").getString().toInt()
        val channel = ManagedChannelBuilder.forAddress(host, port).usePlaintext().build()
        JobrunrServiceGrpcKt.JobrunrServiceCoroutineStub(channel)
    }
}

/**
 * Outbound gateways (accessory-side gRPC clients) built on top of [grpcChannelModule] (task 16.2).
 */
val gatewayModule = module {
    single<PaymentGateway> { PaymentGateway(get()) }
    single<AccessoryMailGateway> { AccessoryMailGateway(get()) }
    single<JobrunrGateway> {
        JobrunrGateway(
            get<ApplicationEnvironment>(),
            get<ObjectMapper>(),
            get<AccessoryMailGateway>(),
            get<JobrunrServiceGrpcKt.JobrunrServiceCoroutineStub>(),
        )
    }
}

/**
 * Domain services reachable from the HTTP and gRPC layers (task 16.2 completes the graph).
 *
 * [DiscountCodeService], [OrderStateMachine], [OrderCodeService], [ExpirySweepService], and the full
 * [AccessoryOrderService] (with its payment/notification gRPC-client deps) are all registered here.
 */
val serviceModule = module {
    single<DiscountCodeService> {
        DiscountCodeServiceImpl(
            get(qualifier = named(accessoryDiscountCodeTypedColToken)),
        )
    }

    single<OrderStateMachine> {
        OrderStateMachine(get(qualifier = named(accessoryOrderTypedColToken)))
    }

    single<OrderCodeService> {
        OrderCodeService(get(qualifier = named(accessoryOrderSeqTypedColToken)))
    }

    single<ExpirySweepService> {
        val env: ApplicationEnvironment by inject()
        val ttl = env.config.property("accessory.orderTtlMinutes").getString().toLong()
        // Optional key so existing server confs keep working without edits.
        val staleProcessing = env.config.propertyOrNull("accessory.paymentProcessingStaleMinutes")
            ?.getString()?.toLongOrNull() ?: 60L
        ExpirySweepService(get<OrderDatabaseService>(), get<AccessoryOrderService>(), ttl, staleProcessing)
    }

    single<AccessoryShowOpenService> {
        val env: ApplicationEnvironment by inject()
        val scheduleOnJobrunr = env.config.propertyOrNull("accessory.scheduleOpenShowOnJobrunr")
            ?.getString()?.toBooleanStrictOrNull() ?: true
        AccessoryShowOpenService(
            get<CatalogDatabaseService>(),
            get<JobrunrGateway>(),
            scheduleOnJobrunr,
        )
    }

    single<AccessoryOrderService> {
        val env: ApplicationEnvironment by inject()
        val ttl = env.config.property("accessory.orderTtlMinutes").getString().toLong()
        AccessoryOrderServiceImpl(
            get<CatalogDatabaseService>(),
            get<OrderDatabaseService>(),
            get<DiscountCodeService>(),
            get<OrderCodeService>(),
            get<PaymentGateway>(),
            get<OrderStateMachine>(),
            get<ReconciliationDatabaseService>(),
            get<AccessoryMailGateway>(),
            get<JobrunrGateway>(),
            orderTtlMinutes = ttl,
        )
    }
}

/**
 * gRPC server hosting the accessory-side impls (task 16.2):
 * - [OrderNotificationServiceImpl] — `service.payment` dials this for `orderType = ACCESSORY`
 *   payment status callbacks (Req 8.2). It uses the SAME `OrderNotificationService` proto that
 *   `service.order` uses; `service.payment`'s routing map (task 9.2) points `ACCESSORY` here.
 * - [AccessoryJobExecutorServiceImpl] — `service.jobrunr` dials this for payment-status fallbacks
 *   and the one-shot [metaticket.accessory.jobrunr.AccessoryJobType.OpenShow] auto-open. The
 *   pre-terminal expiry sweep (Req 11.2) is NOT scheduled on JobRunr — it runs in-process from [StartUp].
 *
 * Bound to `grpc.server.port`; started in [StartUp.start].
 */
val grpcServiceModule = module {
    single<OrderNotificationServiceImpl> { OrderNotificationServiceImpl(get<AccessoryOrderService>()) }
    single<AccessoryJobExecutorServiceImpl> {
        AccessoryJobExecutorServiceImpl(get<AccessoryOrderService>(), get<CatalogDatabaseService>())
    }

    single<Server> {
        val config: ApplicationConfig by inject()
        val port: Int = config.property("grpc.server.port").getString().toInt()

        val orderNotificationService: OrderNotificationServiceImpl by inject()
        val jobExecutorService: AccessoryJobExecutorServiceImpl by inject()

        ServerBuilder.forPort(port)
            .addService(orderNotificationService)
            .addService(jobExecutorService)
            .build()
    }
}

val controllerModule = module {
    single<AccessoryController> {
        AccessoryController(
            get<CatalogDatabaseService>(),
            get<AccessoryOrderService>(),
            get<DiscountCodeService>(),
            get<DiscountDatabaseService>(),
            get<OrderDatabaseService>(),
            get<AccessoryReportFilterDatabaseService>(),
            get<ObjectMapper>(),
            get<JsonNodeFactory>(),
            get<AccessoryShowOpenService>(),
        )
    }
}

val startUpModule = module {
    single<StartUp> {
        val env: ApplicationEnvironment by inject()
        val interval = env.config.property("accessory.expirySweepIntervalSeconds").getString().toLong()
        val saleOpenInterval = env.config.propertyOrNull("accessory.saleOpenSweepIntervalSeconds")
            ?.getString()?.toLongOrNull() ?: 15L
        StartUp(
            get(), get(), get(), get(), get(),
            get<ExpirySweepService>(), interval,
            get<AccessoryShowOpenService>(), saleOpenInterval,
        )
    }
}

class ApplicationDI(ktorEnv: ApplicationEnvironment, monitor: Events) : DIContext(ktorEnv, monitor) {

    val startUp: StartUp by inject()
    val accessoryController: AccessoryController by inject()
}

abstract class DIContext(ktorEnv: ApplicationEnvironment, monitor: Events) : KoinComponent {

    private val koinApp = koinApplication {
        modules(
            configurationModule(ktorEnv, monitor),
            databaseModule,
            grpcChannelModule,
            gatewayModule,
            serviceModule,
            grpcServiceModule,
            controllerModule,
            startUpModule
        )
    }

    private val koin = koinApp.koin
    override fun getKoin(): Koin {
        return koin
    }
}
