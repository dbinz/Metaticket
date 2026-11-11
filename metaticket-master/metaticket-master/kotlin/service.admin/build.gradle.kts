import Script.creatingArtifactsConf
import Script.exportSourceSet
import Script.gettingArtifactsConf
import Versions.coroutineVs
import Versions.koinVs
import Versions.ktorVs
import Versions.mongoDriverVS
import Versions.requestValidationVs

plugins {
	kotlin("plugin.serialization")
	kotlin("jvm")
	id("application")
	id("com.google.protobuf")
}

version = "3.0.0"

sourceSets {
	val api by creating
	val grpc by creating
	main {
		resources {
			srcDirs(files("conf"))
		}
	}
}

val api by gettingArtifactsConf("api")
val grpc by creatingArtifactsConf("grpc")
exportSourceSet(api.name)
exportSourceSet(grpc.name)

application {
	mainClass.set("metaticket.admin.Launcher")
}

dependencies {

	implementation(project(":common", "logger"))
	implementation(project(":common", "coroutine"))
	implementation(project(":common", "checksum"))
	implementation(project(":service.admin", "grpc"))
	implementation(project(":service.ticket", "grpc"))
	implementation(project(":service.show", "api"))
	implementation(project(":service.show", "grpc"))
	implementation(project(":service.order", "api"))
	implementation(project(":service.order", "grpc"))
	implementation(project(":service.payment", "api"))
	implementation(project(":service.payment", "grpc"))
	implementation(project(":service.customer.user", "grpc"))


	implementation("io.insert-koin:koin-core:${koinVs}")

	implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:${coroutineVs}")
	implementation("org.jetbrains.kotlinx:kotlinx-coroutines-reactor:${coroutineVs}")

	"apiImplementation"("org.mongodb:bson:${mongoDriverVS}")
	implementation("org.mongodb:bson-kotlinx:${mongoDriverVS}")
	implementation("org.mongodb:mongodb-driver-kotlin-coroutine:${mongoDriverVS}")

	// Core POI library
	implementation("org.apache.poi:poi:5.4.1")

	// POI library for OOXML formats (.xlsx)
	implementation("org.apache.poi:poi-ooxml:5.4.1")

	// *** ADD THIS LINE FOR FULL OOXML SCHEMAS ***
	implementation("org.apache.poi:poi-ooxml-full:5.4.1")

	// For CSV Parsing
	implementation("com.github.doyaaaaaken:kotlin-csv-jvm:1.9.3")

	implementation("org.jetbrains.kotlinx:kotlinx-serialization-core:1.5.1")

	implementation("com.fasterxml.jackson.datatype:jackson-datatype-jsr310:2.14.2")

	// --- GENERATE PROTO
	"grpcImplementation"("io.grpc:grpc-protobuf:${Versions.grpcVs}")
	"grpcImplementation"("io.grpc:grpc-kotlin-stub:${Versions.grpcKotlinVs}")
	"grpcImplementation"("com.google.protobuf:protobuf-kotlin:${Versions.protobufVs}")
	"grpcImplementation"("io.grpc:protoc-gen-grpc-kotlin:${Versions.grpcKotlinVs}")
	"grpcImplementation"("com.google.protobuf:protobuf-java:${Versions.protobufVs}")
	"grpcImplementation"("org.jetbrains.kotlinx:kotlinx-serialization-json:${Versions.kotlinxSerialization}")

	// GRPC server
	implementation("io.grpc:grpc-netty-shaded:${Versions.grpcVs}")
	implementation("io.grpc:grpc-stub:${Versions.grpcVs}")

	// --- KTOR
	implementation("io.ktor:ktor-server-request-validation:${requestValidationVs}")
	implementation("io.ktor:ktor-server-core:${ktorVs}")
	implementation("io.ktor:ktor-server-content-negotiation:${ktorVs}")
	implementation("io.ktor:ktor-serialization-jackson:${ktorVs}")
	implementation("io.ktor:ktor-server-netty:${ktorVs}")
	implementation("io.ktor:ktor-server-cors:${ktorVs}")
	implementation("io.ktor:ktor-server-status-pages:${ktorVs}")
	implementation("io.ktor:ktor-server-jvm:${ktorVs}")
	implementation("io.ktor:ktor-server-auth-jwt:${ktorVs}")

	implementation("io.ktor:ktor-client-core:${ktorVs}")
	implementation("io.ktor:ktor-client-cio:${ktorVs}")
	implementation("io.ktor:ktor-client-content-negotiation:${ktorVs}")
}

protobuf {
	protoc {
		artifact = "com.google.protobuf:protoc:${Versions.protobufVs}"
	}
	plugins {
		create("grpc") {
			artifact = "io.grpc:protoc-gen-grpc-java:${Versions.grpcVs}"
		}
		create("grpckt") {
			artifact = "io.grpc:protoc-gen-grpc-kotlin:${Versions.grpcKotlinVs}:jdk8@jar"
		}
	}
	generateProtoTasks {
		all().forEach { task ->
//            task.builtins {
//                create("kotlin")
//            }
			task.plugins {
				create("grpc") {
					option("generate_kotlin")
				}
				create("grpckt")
//                create("kotlin")
			}
		}
	}
}

tasks.getByName("build") {
	dependsOn("generateProto")
}