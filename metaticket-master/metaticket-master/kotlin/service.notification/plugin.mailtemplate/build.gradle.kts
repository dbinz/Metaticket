plugins {
	kotlin("jvm")
}

version = "1.0.0"

dependencies {
	compileOnly(project(":service.notification", "grpc"))
	compileOnly("org.mongodb:bson:${Versions.mongoDriverVS}")

	// --- KTOR Client
	compileOnly("io.ktor:ktor-client-core:${Versions.ktorVs}")
	compileOnly("io.ktor:ktor-client-cio:${Versions.ktorVs}")
	compileOnly("io.ktor:ktor-client-content-negotiation:${Versions.ktorVs}")
}
