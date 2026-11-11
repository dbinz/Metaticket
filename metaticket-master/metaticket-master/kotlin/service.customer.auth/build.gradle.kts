plugins {
	kotlin("plugin.serialization")
	kotlin("jvm")
	id("application")
}

version = "3.0.0"

sourceSets {
	main {
		resources {
			srcDirs(files("conf"))
		}
	}
}

application {
	mainClass.set("metaticket.customer.auth.Launcher")
}

dependencies {

	implementation(project(":common", "logger"))
	implementation(project(":common", "redis"))
	implementation(project(":common", "utils"))
	implementation(project(":service.customer.user", "grpc"))

	implementation("io.insert-koin:koin-core:${Versions.koinVs}")

	implementation("com.fasterxml.jackson.core:jackson-databind:${Versions.jacksonVs}")
	implementation("com.fasterxml.jackson.datatype:jackson-datatype-jsr310:${Versions.jacksonVs}")
	implementation("com.fasterxml.jackson.module:jackson-module-kotlin:${Versions.jacksonVs}")

	implementation("io.grpc:grpc-kotlin-stub:${Versions.grpcKotlinVs}")

	// GRPC server
	implementation("io.grpc:grpc-netty-shaded:${Versions.grpcVs}")
	implementation("io.grpc:grpc-stub:${Versions.grpcVs}")

	// --- KTOR server
	implementation("io.ktor:ktor-server-core:${Versions.ktorVs}")
	implementation("io.ktor:ktor-server-content-negotiation:${Versions.ktorVs}")
	implementation("io.ktor:ktor-serialization-jackson:${Versions.ktorVs}")
	implementation("io.ktor:ktor-server-netty:${Versions.ktorVs}")
	implementation("io.ktor:ktor-server-cors:${Versions.ktorVs}")
	implementation("io.ktor:ktor-server-status-pages:${Versions.ktorVs}")
	implementation("io.ktor:ktor-server-jvm:${Versions.ktorVs}")
	implementation("io.ktor:ktor-server-auth-jwt:${Versions.ktorVs}")

	// --- KTOR Client
	implementation("io.ktor:ktor-client-core:${Versions.ktorVs}")
	implementation("io.ktor:ktor-client-cio:${Versions.ktorVs}")
	implementation("io.ktor:ktor-client-content-negotiation:${Versions.ktorVs}")
	implementation("io.ktor:ktor-serialization-kotlinx-json:${Versions.ktorVs}")
}