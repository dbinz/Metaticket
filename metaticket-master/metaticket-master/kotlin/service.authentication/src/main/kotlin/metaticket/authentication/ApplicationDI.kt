package metaticket.authentication

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.JsonNodeFactory
import com.fasterxml.jackson.module.kotlin.KotlinModule
import com.mongodb.ConnectionString
import com.mongodb.MongoClientSettings
import com.mongodb.kotlin.client.coroutine.MongoClient
import com.mongodb.kotlin.client.coroutine.MongoCollection
import com.mongodb.kotlin.client.coroutine.MongoDatabase
import io.ktor.server.application.*
import metaticket.authentication.controller.AuthenticationController
import metaticket.authentication.pojo.db.AuthInformationDocument
import metaticket.authentication.redis.RedisClient
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

const val authInformationColToken = "authInformationCol"

fun configurationModule(ktorEnv: ApplicationEnvironment) = module {
    single<ApplicationEnvironment> {  ktorEnv }
    single<JsonNodeFactory> { JsonNodeFactory.instance }
    single<ObjectMapper> { ObjectMapper().registerModule(KotlinModule.Builder().build()) }
}

val redisClientModule = module {
    single<RedisClient> { RedisClient(get()) }
}

val controllerModule = module {
    single<AuthenticationController> { AuthenticationController(get(), get(), get(), get()) }
}

val databaseModule = module {
    single<CodecRegistry> {
        CodecRegistries.fromRegistries(
                MongoClientSettings.getDefaultCodecRegistry(),
                CodecRegistries.fromProviders(
                        EnumCodecProvider(),
                        PojoCodecProvider.builder()
                                .automatic(true)
                                .register("metaticket.authentication.pojo")
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
    single<MongoCollection<AuthInformationDocument>>(qualifier = named(authInformationColToken)) {
        val env : ApplicationEnvironment by inject()
        val db : MongoDatabase by inject()
        val colName = env.config.property("db.authInformationColName").getString()
        val col = db.getCollection(colName, AuthInformationDocument::class.java)

        col
    }

    // provide the database service
    single<DatabaseService> {
        val userCol : MongoCollection<AuthInformationDocument> by inject(qualifier = named(authInformationColToken))
        DatabaseService(userCol)
    }

}

class ApplicationDI(ktorEnv: ApplicationEnvironment) : DIContext(ktorEnv) {

    val documentCtrl : AuthenticationController by inject()
}

abstract class DIContext(ktorEnv : ApplicationEnvironment) : KoinComponent {

    private val koinApp = koinApplication {
        // declare used modules
        modules(configurationModule(ktorEnv), redisClientModule, controllerModule, databaseModule)
    }

    private val koin = koinApp.koin
    override fun getKoin(): Koin {
        return koin
    }
}
