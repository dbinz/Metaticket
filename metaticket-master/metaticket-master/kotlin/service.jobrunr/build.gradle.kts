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
	mainClass.set("metaticket.jobrunr.Launcher")
}

dependencies {

	implementation(project(":service.jobrunr", "api"))
	implementation(project(":service.jobrunr", "grpc"))
	implementation(project(":common", "logger"))
	implementation(project(":common", "coroutine"))
	implementation(project(":common", "checksum"))

	implementation("io.insert-koin:koin-core:${Versions.koinVs}")

	implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:${Versions.coroutineVs}")
	implementation("org.jetbrains.kotlinx:kotlinx-coroutines-reactor:${Versions.coroutineVs}")

	// Job Runr
	implementation("org.jobrunr:jobrunr:${Versions.jobrunrVs}")
	implementation("org.ow2.asm:asm:9.5")
	implementation("org.mongodb:mongodb-driver-sync:$${Versions.mongoDriverVS}")
	implementation("com.fasterxml.jackson.core:jackson-databind:${Versions.jacksonVs}")
	implementation("com.fasterxml.jackson.datatype:jackson-datatype-jsr310:${Versions.jacksonVs}")
	implementation("com.fasterxml.jackson.module:jackson-module-kotlin:${Versions.jacksonVs}")

	implementation("org.mongodb:bson:${Versions.mongoDriverVS}")
	implementation("org.mongodb:bson-kotlinx:${Versions.mongoDriverVS}")

	// GRPC server
	implementation("io.grpc:grpc-netty-shaded:${Versions.grpcVs}")
	implementation("io.grpc:grpc-stub:${Versions.grpcVs}")

	// --- GENERATE PROTO
	implementation("com.hubspot.jackson:jackson-datatype-protobuf:${Versions.jacksonProtobufVs}")
	"grpcImplementation"("io.grpc:grpc-protobuf:${Versions.grpcVs}")
	"grpcImplementation"("io.grpc:grpc-kotlin-stub:${Versions.grpcKotlinVs}")
	"grpcImplementation"("com.google.protobuf:protobuf-kotlin:${Versions.protobufVs}")
	"grpcImplementation"("io.grpc:protoc-gen-grpc-kotlin:${Versions.grpcKotlinVs}")
	"grpcImplementation"("com.google.protobuf:protobuf-java:${Versions.protobufVs}")
	"grpcImplementation"("org.jetbrains.kotlinx:kotlinx-serialization-json:${Versions.kotlinxSerialization}")

	// --- KTOR Server
	"apiImplementation"("io.ktor:ktor-server-request-validation:${Versions.requestValidationVs}")
	implementation("io.ktor:ktor-server-core:${Versions.ktorVs}")
	implementation("io.ktor:ktor-server-content-negotiation:${Versions.ktorVs}")
	implementation("io.ktor:ktor-serialization-jackson:${Versions.ktorVs}")
	implementation("io.ktor:ktor-server-netty:${Versions.ktorVs}")
	implementation("io.ktor:ktor-server-cors:${Versions.ktorVs}")
	implementation("io.ktor:ktor-server-status-pages:${Versions.ktorVs}")
	implementation("io.ktor:ktor-server-jvm:${Versions.ktorVs}")

	// --- KTOR Client
	implementation("io.ktor:ktor-client-core:${Versions.ktorVs}")
	implementation("io.ktor:ktor-client-cio:${Versions.ktorVs}")
	implementation("io.ktor:ktor-client-content-negotiation:${Versions.ktorVs}")
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