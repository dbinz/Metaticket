import Script.exportSourceSet
import Script.gettingArtifactsConf

plugins {
	id("application")
	kotlin("jvm")
}

version = "3.0.0"

sourceSets {
	val api by creating
	main {
		resources {
			srcDirs(files("conf"))
		}
	}
}

val api by gettingArtifactsConf("api")
exportSourceSet("api")

application {
	mainClass.set("metaticket.authentication.Launcher")
}

dependencies {

	implementation(project(":common", "logger"))

	implementation("com.google.guava:guava:${Versions.guavaVs}")

	implementation("io.insert-koin:koin-core:${Versions.koinVs}")

	implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:${Versions.coroutineVs}")
	implementation("org.jetbrains.kotlinx:kotlinx-coroutines-reactor:${Versions.coroutineVs}")

	"apiImplementation"("org.mongodb:bson:${Versions.mongoDriverVS}")
	implementation("org.mongodb:bson-kotlinx:${Versions.mongoDriverVS}")
	implementation("org.mongodb:mongodb-driver-kotlin-coroutine:${Versions.mongoDriverVS}")
	implementation("com.fasterxml.jackson.datatype:jackson-datatype-jsr310:2.14.2")
	implementation("org.mindrot:jbcrypt:0.4")

	// --- KTOR server
	implementation("io.ktor:ktor-server-core:${Versions.ktorVs}")
	implementation("io.ktor:ktor-server-content-negotiation:${Versions.ktorVs}")
	implementation("io.ktor:ktor-serialization-jackson:${Versions.ktorVs}")
	implementation("io.ktor:ktor-server-netty:${Versions.ktorVs}")
	implementation("io.ktor:ktor-server-cors:${Versions.ktorVs}")
	implementation("io.ktor:ktor-server-status-pages:${Versions.ktorVs}")
	implementation("io.ktor:ktor-server-jvm:${Versions.ktorVs}")
	implementation("io.ktor:ktor-server-auth:${Versions.ktorVs}")
	implementation("io.ktor:ktor-server-auth-jwt:${Versions.ktorVs}")
	implementation("io.ktor:ktor-server-sessions:${Versions.ktorVs}")
	implementation("io.ktor:ktor-server-html-builder:${Versions.ktorVs}")

	// --- KTOR Client
	implementation("io.ktor:ktor-client-auth:${Versions.ktorVs}")

}
