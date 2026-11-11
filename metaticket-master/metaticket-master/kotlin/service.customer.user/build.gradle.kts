import Script.creatingArtifactsConf
import Script.exportSourceSet

plugins {
	kotlin("plugin.serialization")
	kotlin("jvm")
	id("application")
	id("com.google.protobuf")
}

version = "3.0.0"

sourceSets {
	val grpc by creating
	main {
		resources {
			srcDirs(files("conf"))
		}
	}
}

val grpc by creatingArtifactsConf("grpc")
exportSourceSet(grpc.name)

application {
	mainClass.set("metaticket.customer.user.Launcher")
}

dependencies {

	implementation(project(":common", "logger"))
	implementation(project(":common", "coroutine"))
	implementation(project(":common", "redis"))
	implementation(project(":common", "utils"))
	implementation(project(":service.customer.user", "grpc"))
	implementation(project(":service.notification", "grpc"))

	implementation("io.insert-koin:koin-core:${Versions.koinVs}")

	implementation("org.litote.kmongo:kmongo-coroutine:${Versions.kmongoDriverVS}")

	implementation("com.fasterxml.jackson.core:jackson-databind:${Versions.jacksonVs}")
	implementation("com.fasterxml.jackson.datatype:jackson-datatype-jsr310:${Versions.jacksonVs}")
	implementation("com.fasterxml.jackson.module:jackson-module-kotlin:${Versions.jacksonVs}")

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

	// --- KTOR Server
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
	implementation("io.ktor:ktor-serialization-kotlinx-json:${Versions.ktorVs}")
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