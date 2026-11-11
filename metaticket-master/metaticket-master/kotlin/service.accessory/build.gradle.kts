plugins {
	kotlin("plugin.serialization")
	kotlin("jvm")
	id("application")
}

version = "3.1.0"

sourceSets {
	main {
		resources {
			srcDirs(files("conf"))
		}
	}
}

application {
	mainClass.set("metaticket.accessory.Launcher")
}

dependencies {

	implementation(project(":common", "logger"))
	implementation(project(":common", "coroutine"))
	implementation(project(":common", "checksum"))

	// --- gRPC stubs of the reused services (consume only; accessory defines no proto of its own).
	// PaymentGateway dials service.payment.CreatePayment (additive orderType=ACCESSORY),
	implementation(project(":service.payment", "api"))
	implementation(project(":service.payment", "grpc"))
	// AccessoryMailGateway dials the dedicated service.notification.SendMailAccessoryOrder RPC.
	implementation(project(":service.notification", "grpc"))
	// `api` artifact carries NotificationJobType — the job-name contract scheduled on JobRunr for
	// the durable confirmation-email fallback (ResendMailAccessoryOrder).
	implementation(project(":service.notification", "api"))

	// --- OrderNotificationService proto lives in service.order's `grpc` artifact. The accessory
	// service implements the SAME proto (OrderNotificationServiceCoroutineImplBase) so that
	// service.payment can route ACCESSORY status callbacks here (design "OrderNotificationService
	// (gRPC, accessory-side impl)"; Req 8.2). Consume-only: no proto is defined here.
	implementation(project(":service.order", "grpc"))
	// `api` artifact carries OrderJobType — the job-name contract service.payment schedules on
	// JobRunr for payment-status callbacks (mirrors how service.order's executor dispatches).
	implementation(project(":service.order", "api"))

	// --- JobRunr scheduling: dial service.jobrunr to schedule the recurring expiry sweep
	// (JobrunrServiceGrpcKt) and host the executor callback (JobExecutorServiceGrpcKt). The
	// `api` artifact carries the shared JobExecutorServiceName / JobRequest datastructures.
	implementation(project(":service.jobrunr", "api"))
	implementation(project(":service.jobrunr", "grpc"))

	implementation("io.insert-koin:koin-core:${Versions.koinVs}")

	implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:${Versions.coroutineVs}")
	implementation("org.jetbrains.kotlinx:kotlinx-coroutines-reactor:${Versions.coroutineVs}")

	implementation("org.mongodb:bson:${Versions.mongoDriverVS}")
	implementation("org.mongodb:bson-kotlinx:${Versions.mongoDriverVS}")
	implementation("org.mongodb:mongodb-driver-kotlin-coroutine:${Versions.mongoDriverVS}")

	implementation("org.jetbrains.kotlinx:kotlinx-serialization-core:${Versions.kotlinxSerialization}")

	implementation("com.fasterxml.jackson.datatype:jackson-datatype-jsr310:${Versions.jacksonVs}")

	// Jackson <-> protobuf: serialize SendMailAccessoryOrderRequest into the JobRunr jobDesc for the
	// durable email fallback (matches service.order / service.notification ObjectMapper config).
	implementation("com.hubspot.jackson:jackson-datatype-protobuf:${Versions.jacksonProtobufVs}")

	// --- gRPC runtime (needed to resolve the generated stubs consumed above and to dial channels).
	implementation("io.grpc:grpc-protobuf:${Versions.grpcVs}")
	implementation("io.grpc:grpc-kotlin-stub:${Versions.grpcKotlinVs}")
	implementation("com.google.protobuf:protobuf-kotlin:${Versions.protobufVs}")
	implementation("com.google.protobuf:protobuf-java:${Versions.protobufVs}")
	implementation("io.grpc:grpc-netty-shaded:${Versions.grpcVs}")
	implementation("io.grpc:grpc-stub:${Versions.grpcVs}")

	// --- KTOR Server
	implementation("io.ktor:ktor-server-request-validation:${Versions.requestValidationVs}")
	implementation("io.ktor:ktor-server-core:${Versions.ktorVs}")
	implementation("io.ktor:ktor-server-content-negotiation:${Versions.ktorVs}")
	implementation("io.ktor:ktor-serialization-jackson:${Versions.ktorVs}")
	implementation("io.ktor:ktor-server-netty:${Versions.ktorVs}")
	implementation("io.ktor:ktor-server-cors:${Versions.ktorVs}")
	implementation("io.ktor:ktor-server-status-pages:${Versions.ktorVs}")
	implementation("io.ktor:ktor-server-rate-limit:${Versions.ktorVs}")
	implementation("io.ktor:ktor-server-call-logging:${Versions.ktorVs}")
	implementation("io.ktor:ktor-server-compression:${Versions.ktorVs}")
	implementation("io.ktor:ktor-server-jvm:${Versions.ktorVs}")

	// --- KTOR Client
	implementation("io.ktor:ktor-client-core:${Versions.ktorVs}")
	implementation("io.ktor:ktor-client-cio:${Versions.ktorVs}")
	implementation("io.ktor:ktor-client-content-negotiation:${Versions.ktorVs}")

	// --- Test
	testImplementation("org.jetbrains.kotlin:kotlin-test:${Versions.kotlinVs}")
	testImplementation("org.jetbrains.kotlin:kotlin-test-junit5:${Versions.kotlinVs}")
	testImplementation("net.jqwik:jqwik:${Versions.jqwikVs}")
	// Property-based testing (design "Testing Strategy" → kotest-property) + JUnit5 platform runner.
	testImplementation("io.kotest:kotest-runner-junit5:${Versions.kotestVs}")
	testImplementation("io.kotest:kotest-property:${Versions.kotestVs}")
	testImplementation("io.kotest:kotest-assertions-core:${Versions.kotestVs}")
	testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:${Versions.coroutineVs}")
	// Mock the concrete DB/gateway collaborators so checkout error/success paths can be driven
	// without a live Mongo/gRPC (matches service.payment's mockk version).
	testImplementation("io.mockk:mockk:1.13.10")
	testImplementation("org.testcontainers:mongodb:${Versions.testcontainersVs}")
	testImplementation("io.ktor:ktor-server-test-host:${Versions.ktorVs}")
}
