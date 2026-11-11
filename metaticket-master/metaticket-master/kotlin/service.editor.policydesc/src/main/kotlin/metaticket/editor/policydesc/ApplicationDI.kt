package metaticket.editor.policydesc

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.JsonNodeFactory
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.KotlinModule
import com.mongodb.ConnectionString
import com.mongodb.MongoClientSettings
import com.mongodb.kotlin.client.coroutine.MongoClient
import com.mongodb.kotlin.client.coroutine.MongoCollection
import com.mongodb.kotlin.client.coroutine.MongoDatabase
import io.ktor.server.application.*
import metaticket.editor.policydesc.controller.DocumentController
import metaticket.editor.policydesc.pojo.db.policydesc.PolicyDescDocument
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

const val policydescColToken = "policydescCol"

fun configurationModule(ktorEnv: ApplicationEnvironment) = module {
    single {  ktorEnv }

    single<JsonNodeFactory> { JsonNodeFactory.instance }
    single<ObjectMapper> { ObjectMapper().registerModules(KotlinModule.Builder().build(), JavaTimeModule()) }
}

val controllerModule = module {
    single {
        DocumentController(get())
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
                    .register("metaticket.editor.policydesc.pojo.db")
                    .build(),
            )
        )
    }

    // provide mongo db
    single {
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
    single(qualifier = named(policydescColToken)) {
        val db : MongoDatabase by inject()
        db.getCollection("policydesc", PolicyDescDocument::class.java)
    }

    // provide the database service
    single {
        val seatPlanCol : MongoCollection<PolicyDescDocument> by inject(qualifier = named(policydescColToken))
        DatabaseService(seatPlanCol)
    }

}


class ApplicationDI(ktorEnv: ApplicationEnvironment) : DIContext(ktorEnv) {

    val documentCtrl : DocumentController by inject()
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
