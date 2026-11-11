package metaticket.customer.auth

import io.grpc.ManagedChannelBuilder
import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.events.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.config.*
import metaticket.common.redis.RedisClusterManager
import metaticket.customer.auth.controller.AuthCtrl
import metaticket.customer.auth.gateway.CustomerUserGateway
import metaticket.customer.auth.gateway.CaptchaGateway
import metaticket.customer.auth.repository.UserTokenRepository
import metaticket.customer.auth.repository.VoteRepository
import metaticket.customer.auth.controller.VoteCtrl
import metaticket.customer.user.grpc.CustomerUserServiceGrpcKt
import metaticket.customer.user.grpc.CustomerUserServiceGrpcKt.CustomerUserServiceCoroutineStub
import org.koin.core.Koin
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import org.koin.dsl.koinApplication
import org.koin.dsl.module

/**
 * Module providing application environment configuration.
 *
 * This module provides the Ktor [ApplicationEnvironment] as a singleton.
 *
 * @param ktorEnv The application environment.
 */
fun configurationModule(ktorEnv: ApplicationEnvironment, monitor: Events) = module {
    single<ApplicationEnvironment> { ktorEnv }
    single<ApplicationConfig> { ktorEnv.config }
    single<Events> { monitor }
}

val redisClusterModule = module {
    single<RedisClusterManager<String>> {
        val config: ApplicationConfig by inject()
        val redisNodes = config.property("redis.cluster.nodes").getList()
        RedisClusterManager(redisNodes)
    }

    single<UserTokenRepository> {
        UserTokenRepository(get())
    }

    single<VoteRepository> {
        VoteRepository(get())
    }
}

val gatewayModule = module {
    single<CaptchaGateway> { CaptchaGateway(get(), get()) }
    single<CustomerUserGateway> { CustomerUserGateway(get()) }
}

val httpClientModule = module {
    single<HttpClient> {
        HttpClient(CIO) {
            install(ContentNegotiation) {
                json()
            }
        }
    }
}

/**
 * Module for gRPC channel and stub.
 *
 * Provides a singleton [CustomerUserServiceGrpcKt.CustomerUserServiceCoroutineStub]
 * for interacting with the Customer User Service via gRPC.
 */
val grpcChannelModule = module {
    single<CustomerUserServiceCoroutineStub> {
        val environment: ApplicationEnvironment by inject()
        val host = environment.config.property("grpc.channel.userS.host").getString()
        val port = environment.config.property("grpc.channel.userS.port").getString().toInt()
        val channel = ManagedChannelBuilder.forAddress(host, port)
            .usePlaintext()
            //.useTransportSecurity() // Consider using TLS in production
            .build()
        CustomerUserServiceCoroutineStub(channel)
    }
}

/**
 * Module for JWT utility.
 *
 * Provides a singleton [JWTUtil] for handling JWT operations,
 * configuring it with values from the application environment.
 */
val jwtModule = module {
    single<JWTUtil> {
        val environment: ApplicationEnvironment by inject()
        val jwtSecret = environment.config.property("jwt.secret").getString()
        val jwtIssuer = environment.config.property("jwt.issuer").getString()
        val jwtAudience = environment.config.property("jwt.audience").getString()
        JWTUtil(jwtSecret, jwtIssuer, jwtAudience)
    }
}

val controllerModule = module {
    single<AuthCtrl> {
        AuthCtrl(get(), get(), get(), get(), get())
    }

    single<VoteCtrl> {
        VoteCtrl(get(), get())
    }
}

val startUpModule = module {
    single<StartUp> {
        StartUp(get(), get(), get())
    }
}

/**
 * Context class for dependency injection in the application.
 *
 * This class extends [DIContext] and provides access to injected dependencies
 * such as [UserTokenRepository], [CustomerUserServiceGrpcKt.CustomerUserServiceCoroutineStub], and [JWTUtil].
 *
 * @param ktorEnv The application environment.
 */
class ApplicationDI(ktorEnv: ApplicationEnvironment, monitor: Events) : DIContext(ktorEnv, monitor) {
    val jwtUtil: JWTUtil by inject()
    val authCtrl: AuthCtrl by inject()
    val voteCtrl: VoteCtrl by inject()
    val startUp: StartUp by inject()
}

/**
 * Abstract base class for dependency injection contexts.
 *
 * This class initializes a Koin application and provides access to the Koin instance.
 *
 * @param ktorEnv The application environment.
 */
abstract class DIContext(ktorEnv: ApplicationEnvironment, monitor: Events) : KoinComponent {

    private val koinApp = koinApplication {
        // Declare used modules
        modules(
            configurationModule(ktorEnv, monitor), redisClusterModule, grpcChannelModule, jwtModule, controllerModule,
            httpClientModule, gatewayModule, startUpModule
        )
    }

    private val koin: Koin = koinApp.koin

    override fun getKoin(): Koin {
        return koin
    }
}