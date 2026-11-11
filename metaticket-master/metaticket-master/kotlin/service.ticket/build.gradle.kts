import Script.creatingArtifactsConf
import Script.exportSourceSet
import Script.gettingArtifactsConf

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
	mainClass.set("metaticket.ticket.Launcher")
}

dependencies {

	implementation(project(":common", "coroutine"))
	implementation(project(":common", "checksum"))
	implementation(project(":common", "logger"))
	implementation(project(":common", "redis"))
	implementation(project(":common", "utils"))

	implementation(project(":service.ticket", "api"))
	implementation(project(":service.ticket", "grpc"))
	implementation(project(":service.show", "api"))
	implementation(project(":service.show", "grpc"))
	implementation(project(":service.order", "api"))
	implementation(project(":service.jobrunr", "api"))
	implementation(project(":service.jobrunr", "grpc"))

	implementation("io.insert-koin:koin-core:${Versions.koinVs}")

	implementation("com.google.guava:guava:${Versions.guavaVs}")

	implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:${Versions.coroutineVs}")
	implementation("org.jetbrains.kotlinx:kotlinx-coroutines-reactor:${Versions.coroutineVs}")

	"apiImplementation"("org.mongodb:bson:${Versions.mongoDriverVS}")
	implementation("org.mongodb:bson-kotlinx:${Versions.mongoDriverVS}")
	implementation("org.mongodb:mongodb-driver-kotlin-coroutine:${Versions.mongoDriverVS}")

	implementation("com.fasterxml.jackson.datatype:jackson-datatype-jsr310:${Versions.jacksonVs}")

	// --- GENERATE PROTO
	implementation("com.hubspot.jackson:jackson-datatype-protobuf:${Versions.jacksonProtobufVs}")
	"grpcImplementation"("io.grpc:grpc-protobuf:${Versions.grpcVs}")
	"grpcImplementation"("io.grpc:grpc-kotlin-stub:${Versions.grpcKotlinVs}")
	"grpcImplementation"("com.google.protobuf:protobuf-kotlin:${Versions.protobufVs}")
	"grpcImplementation"("io.grpc:protoc-gen-grpc-kotlin:${Versions.grpcKotlinVs}")
	"grpcImplementation"("com.google.protobuf:protobuf-java:${Versions.protobufVs}")
	"grpcImplementation"("org.jetbrains.kotlinx:kotlinx-serialization-json:${Versions.kotlinxSerialization}")

	// GRPC server
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
	implementation("io.ktor:ktor-server-jvm:${Versions.ktorVs}")

	// --- Test
	testImplementation("org.jetbrains.kotlin:kotlin-test:${Versions.kotlinVs}")
	testImplementation("org.jetbrains.kotlin:kotlin-test-junit5:${Versions.kotlinVs}")
	testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:${Versions.coroutineVs}")
	testImplementation("io.mockk:mockk:1.13.10")
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