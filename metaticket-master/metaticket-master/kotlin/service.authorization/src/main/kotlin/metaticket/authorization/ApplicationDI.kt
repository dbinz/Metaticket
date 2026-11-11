package metaticket.authorization

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.JsonNodeFactory
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.KotlinModule
import com.mongodb.ConnectionString
import com.mongodb.MongoClientSettings
import com.mongodb.client.MongoClient
import com.mongodb.client.MongoClients
import com.mongodb.client.MongoDatabase
import io.ktor.server.application.*
import metaticket.authorization.adapter.CasbinMongoAdapter
import metaticket.authorization.controller.AuthorizationController
import org.bson.codecs.EnumCodecProvider
import org.bson.codecs.configuration.CodecRegistries
import org.bson.codecs.configuration.CodecRegistry
import org.bson.codecs.pojo.PojoCodecProvider
import org.casbin.jcasbin.main.Enforcer
import org.casbin.jcasbin.persist.file_adapter.FileAdapter
import org.casbin.jcasbin.util.BuiltInFunctions
import org.jim.jcasbin.MongoAdapter
import org.koin.core.Koin
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import org.koin.dsl.koinApplication
import org.koin.dsl.module

fun configurationModule(ktorEnv: ApplicationEnvironment) = module {
    single {  ktorEnv }

    single<JsonNodeFactory> { JsonNodeFactory.instance }
    single<ObjectMapper> { ObjectMapper().registerModules(KotlinModule.Builder().build(), JavaTimeModule()) }
}

val controllerModule = module {
    single {
        AuthorizationController(get(), get())
    }
}

val databaseModule = module {

    single {
        CodecRegistries.fromRegistries(
            MongoClientSettings.getDefaultCodecRegistry(),
            CodecRegistries.fromProviders(
                EnumCodecProvider(),
                PojoCodecProvider.builder()
                    .automatic(true)
                    .register("metaticket.authorization.pojo")
                    .build(),
            )
        )
    }

    // provide mongo db
    single<MongoDatabase> {
        val env : ApplicationEnvironment by inject()
        val dbName = env.config.property("db.dbName").getString()
        val mongoClient: MongoClient by inject<MongoClient>()
        mongoClient.getDatabase(dbName)
    }

    single<MongoClient> {
        val env : ApplicationEnvironment by inject()
        val connectionString = env.config.property("db.connection").getString()
        val registry : CodecRegistry by inject()
        val settings = MongoClientSettings
            .builder()
            .applyConnectionString(ConnectionString(connectionString))
            .codecRegistry(registry)
            .build()

        MongoClients.create(settings)
    }

    // provide the database service
    single<CasbinMongoAdapter> {
        val env : ApplicationEnvironment by inject()
        val dbName = env.config.property("db.dbName").getString()
        val policyCol = env.config.property("db.policyCol").getString()
        val mongoClient: MongoClient by inject<MongoClient>()
        CasbinMongoAdapter(mongoClient, dbName, policyCol)
    }
    single<MongoAdapter> {
        val adapter: CasbinMongoAdapter by inject()
        adapter
    }

    single<Enforcer> {
        val adapter: MongoAdapter by inject()
//        val adapter = FileAdapter("conf/policy.csv")
        val enforce = Enforcer("conf/rbac_model.conf", adapter)
        enforce.addNamedMatchingFunc("g3", "KeyMatch2") { key1, key2 ->
            return@addNamedMatchingFunc BuiltInFunctions.keyMatch2(key1, key2)
        }
        enforce.enableAutoSave(true)
        enforce
    }
}

class ApplicationDI(ktorEnv: ApplicationEnvironment) : DIContext(ktorEnv) {

    val authorizationCtrl : AuthorizationController by inject()
}

abstract class DIContext(ktorEnv : ApplicationEnvironment) : KoinComponent {

    private val koinApp = koinApplication {
        // declare used modules
        modules(configurationModule(ktorEnv), controllerModule, databaseModule)
    }

    private val koin = koinApp.koin
    override fun getKoin(): Koin {
        return koin
    }
}
